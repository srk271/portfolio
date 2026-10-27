package dev.bidu.app.cloud

import dev.bidu.app.security.SecretStore
import dev.bidu.app.settings.SettingsRepository
import dev.bidu.core.agent.LogSanitizer
import dev.bidu.core.model.Language
import dev.bidu.core.model.RiskLevel
import dev.bidu.core.plan.DeclineReason
import dev.bidu.core.plan.Plan
import dev.bidu.core.plan.PlanRequest
import dev.bidu.core.plan.PlanSource
import dev.bidu.core.plan.Planner
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

/**
 * Optional cloud fallback for utterances the offline NLU could not understand. Runs only when cloud
 * reasoning is enabled, consent was given, a model is chosen in Settings and an API key is stored.
 * Sends the utterance, current time, reply language, installed app names and tool schemas; never
 * notes, contacts, reminders or memory. Any transport, auth, quota or validation problem becomes a
 * Decline with a clear reason; nothing is guessed.
 */
class AnthropicPlanner(
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
) : Planner {

    override suspend fun plan(request: PlanRequest): Plan {
        val language = request.context.language
        val current = settings.current()
        if (!current.cloudAllowed()) return decline(language, Failure.Disabled)
        val model = current.cloudModel?.trim()?.takeIf { it.isNotEmpty() } ?: return decline(language, Failure.NoModel)
        val apiKey = secrets.get(SecretStore.CLOUD_API_KEY)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return decline(language, Failure.NoKey)

        val tools = request.tools.filter { it.risk != RiskLevel.CRITICAL }
        if (tools.isEmpty() || request.request.text.isBlank()) return decline(language, Failure.NoPlan)

        val body = CloudPlanCodec.buildRequest(model, MAX_TOKENS, request, tools).toString()
        val response = sendWithRetry { send("POST", MESSAGES_URL, apiKey, body) }
        if (response !is Http.Response || response.code !in 200..299) return decline(language, failureOf(response, model))

        return when (val parsed = CloudPlanCodec.parseResponse(response.body, tools)) {
            is CloudPlanCodec.Parsed.Calls -> Plan.Execute(
                goal = request.request.text.trim().take(GOAL_LENGTH),
                calls = parsed.calls,
                source = PlanSource.CLOUD,
                confidence = CLOUD_CONFIDENCE,
            )
            CloudPlanCodec.Parsed.NoToolUse -> decline(language, Failure.NoPlan)
            CloudPlanCodec.Parsed.Refused -> decline(language, Failure.Refused)
            CloudPlanCodec.Parsed.Truncated -> decline(language, Failure.InvalidPlan("response was cut off"))
            is CloudPlanCodec.Parsed.Invalid -> decline(language, Failure.InvalidPlan(parsed.reason))
        }
    }

    /**
     * Model ids this key can use (Models API), for the Settings picker. Needs consent and a stored key;
     * sends nothing but the key.
     */
    suspend fun listModels(): Result<List<String>> {
        if (!settings.current().cloudConsentGiven) return Result.failure(CloudException("Allow cloud reasoning in Settings first"))
        val apiKey = secrets.get(SecretStore.CLOUD_API_KEY)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return Result.failure(CloudException("Add your API key in Settings"))
        val ids = mutableListOf<String>()
        var afterId: String? = null
        repeat(MAX_MODEL_PAGES) {
            val url = buildString {
                append(MODELS_URL).append("?limit=").append(MODEL_PAGE_SIZE)
                afterId?.let { append("&after_id=").append(URLEncoder.encode(it, Charsets.UTF_8.name())) }
            }
            val response = sendWithRetry { send("GET", url, apiKey, null) }
            if (response !is Http.Response || response.code !in 200..299) {
                return Result.failure(CloudException(describe(failureOf(response, null), Language.ENGLISH)))
            }
            val (page, next) = try {
                CloudPlanCodec.parseModelPage(response.body)
            } catch (e: JSONException) {
                return Result.failure(CloudException("The model list could not be read"))
            }
            ids += page
            afterId = next ?: return Result.success(ids.distinct())
        }
        return Result.success(ids.distinct())
    }

    private sealed interface Http {
        data class Response(val code: Int, val body: String, val retryAfterSeconds: Long?) : Http
        data class NetworkError(val timeout: Boolean, val offline: Boolean) : Http
    }

    private suspend fun sendWithRetry(call: suspend () -> Http): Http {
        val first = call()
        val transient = first is Http.Response && (first.code >= 500)
        if (!transient) return first
        delay(RETRY_DELAY_MILLIS)
        return call()
    }

    /** One HTTPS exchange. Cancelling the caller disconnects the socket so the blocking read ends. */
    private suspend fun send(method: String, url: String, apiKey: String, body: String?): Http = coroutineScope {
        val connection = URL(url).openConnection() as HttpURLConnection
        val watchdog = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                connection.disconnect()
            }
        }
        try {
            withContext(Dispatchers.IO) {
                try {
                    connection.requestMethod = method
                    connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                    connection.readTimeout = READ_TIMEOUT_MILLIS
                    connection.useCaches = false
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("x-api-key", apiKey)
                    connection.setRequestProperty("anthropic-version", API_VERSION)
                    connection.setRequestProperty("accept", "application/json")
                    if (body != null) {
                        val bytes = body.toByteArray(Charsets.UTF_8)
                        connection.setRequestProperty("content-type", "application/json")
                        connection.doOutput = true
                        connection.setFixedLengthStreamingMode(bytes.size)
                        connection.outputStream.use { it.write(bytes) }
                    }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val text = stream?.use { readBounded(it) }.orEmpty()
                    val retryAfter = connection.getHeaderField("retry-after")?.trim()?.toLongOrNull()
                    Http.Response(code, text, retryAfter)
                } catch (e: SocketTimeoutException) {
                    Http.NetworkError(timeout = true, offline = false)
                } catch (e: UnknownHostException) {
                    Http.NetworkError(timeout = false, offline = true)
                } catch (e: IOException) {
                    Http.NetworkError(timeout = false, offline = false)
                } finally {
                    connection.disconnect()
                }
            }
        } finally {
            watchdog.cancel()
        }
    }

    private fun readBounded(input: InputStream): String {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            total += n
            if (total > MAX_RESPONSE_BYTES) throw IOException("Response too large")
            buffer.write(chunk, 0, n)
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    private sealed interface Failure {
        data object Disabled : Failure
        data object NoModel : Failure
        data object NoKey : Failure
        data object Offline : Failure
        data object Timeout : Failure
        data object Network : Failure
        data object BadKey : Failure
        data object AccountBlocked : Failure
        data class ModelUnavailable(val model: String?) : Failure
        data class RateLimited(val retryAfterSeconds: Long?) : Failure
        data object Busy : Failure
        data class Rejected(val detail: String?) : Failure
        data object Refused : Failure
        data object NoPlan : Failure
        data class InvalidPlan(val reason: String) : Failure
    }

    private fun failureOf(result: Http, model: String?): Failure = when (result) {
        is Http.NetworkError -> when {
            result.offline -> Failure.Offline
            result.timeout -> Failure.Timeout
            else -> Failure.Network
        }
        is Http.Response -> {
            val error = CloudPlanCodec.parseError(result.body)
            when {
                result.code == 401 -> Failure.BadKey
                result.code == 402 || result.code == 403 -> Failure.AccountBlocked
                result.code == 404 -> Failure.ModelUnavailable(model)
                result.code == 429 -> Failure.RateLimited(result.retryAfterSeconds)
                result.code >= 500 -> Failure.Busy
                else -> Failure.Rejected(error.message?.let { LogSanitizer.sanitize(it).take(160) })
            }
        }
    }

    private fun decline(language: Language, failure: Failure): Plan =
        Plan.Decline(DeclineReason.NOT_UNDERSTOOD, describe(failure, language), PlanSource.CLOUD)

    private fun describe(failure: Failure, language: Language): String {
        val hinglish = language != Language.ENGLISH
        return when (failure) {
            Failure.Disabled -> if (hinglish) "Ye samajh nahi aaya, aur cloud reasoning band hai." else "I didn't understand that, and cloud reasoning is off."
            Failure.NoModel -> if (hinglish) "Cloud model set nahi hai. Set a cloud model in Settings." else "Set a cloud model in Settings."
            Failure.NoKey -> if (hinglish) "Cloud API key nahi hai; Settings mein daalo." else "Add your cloud API key in Settings."
            Failure.Offline -> if (hinglish) "Internet nahi hai, cloud se pooch nahi paaya." else "No internet, so I couldn't ask the cloud."
            Failure.Timeout -> if (hinglish) "Cloud ne time pe jawab nahi diya." else "The cloud didn't answer in time."
            Failure.Network -> if (hinglish) "Cloud tak connection nahi ho paaya." else "Couldn't connect to the cloud."
            Failure.BadKey -> if (hinglish) "Cloud API key reject ho gayi; Settings mein check karo." else "The cloud API key was rejected; check it in Settings."
            Failure.AccountBlocked -> if (hinglish) "Cloud account abhi use nahi ho sakta (billing ya permission)." else "The cloud account can't be used right now (billing or permission)."
            is Failure.ModelUnavailable -> {
                val name = failure.model?.let { "'$it' " }.orEmpty()
                if (hinglish) "Cloud model ${name}is key ke liye available nahi hai; Settings mein doosra chuno." else "Cloud model ${name}isn't available for this key; pick another in Settings."
            }
            is Failure.RateLimited -> {
                val wait = failure.retryAfterSeconds?.let { if (hinglish) " $it second baad" else " in $it seconds" }.orEmpty()
                if (hinglish) "Cloud limit hit ho gayi; thodi der$wait phir try karo." else "Cloud rate limit reached; try again$wait."
            }
            Failure.Busy -> if (hinglish) "Cloud service abhi busy hai; thodi der baad try karo." else "The cloud service is busy; try again shortly."
            is Failure.Rejected -> {
                val detail = failure.detail?.let { ": $it" }.orEmpty()
                if (hinglish) "Cloud ne request reject kar di$detail" else "The cloud rejected the request$detail"
            }
            Failure.Refused -> if (hinglish) "Cloud model ne ye request mana kar di." else "The cloud model declined this request."
            Failure.NoPlan -> if (hinglish) "Ye kaam main apne tools se nahi kar sakta, ya samajh nahi aaya." else "I couldn't map that to anything I can do."
            is Failure.InvalidPlan -> if (hinglish) "Cloud ka plan safe nahi laga, isliye kuch nahi kiya." else "The cloud's plan didn't pass checks, so I did nothing."
        }
    }

    class CloudException(message: String) : Exception(message)

    private companion object {
        const val MESSAGES_URL = "https://api.anthropic.com/v1/messages"
        const val MODELS_URL = "https://api.anthropic.com/v1/models"
        const val API_VERSION = "2026-10-21"
        const val MAX_TOKENS = 8_192
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val RETRY_DELAY_MILLIS = 800L
        const val MAX_RESPONSE_BYTES = 1_048_576
        const val MAX_MODEL_PAGES = 5
        const val MODEL_PAGE_SIZE = 100
        const val GOAL_LENGTH = 120

        /** The API returns no confidence; a fixed prior for cloud plans (HIGH risk is still confirmed by policy). */
        const val CLOUD_CONFIDENCE = 0.7
    }
}

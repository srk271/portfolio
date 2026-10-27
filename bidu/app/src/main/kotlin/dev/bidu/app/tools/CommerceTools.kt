package dev.bidu.app.tools

import android.content.Intent
import android.net.Uri
import dev.bidu.core.memory.MemoryKeys
import dev.bidu.core.memory.MemoryStore
import dev.bidu.core.model.Clock
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.Tool
import dev.bidu.core.tool.ToolArgs
import dev.bidu.core.tool.ToolContext
import dev.bidu.core.tool.ToolResult
import dev.bidu.core.tool.ToolSpec

/**
 * Opens a cab app with the destination filled in where that is possible without coordinates.
 * Bidu has no access to live fares or ETAs and never books: the user confirms and pays in the app.
 */
class BookCabTool(private val memory: MemoryStore, private val launcher: IntentLauncher) : Tool {
    override val spec: ToolSpec = BuiltinTools.BOOK_CAB

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val spokenDestination = args.string("destination")?.trim()
        val destination = when (val resolved = resolveDestination(spokenDestination)) {
            is Destination.Missing -> return ToolResult.needsUser(
                "No saved address for '${resolved.place}'",
                freeTextQuestion("${resolved.place.replaceFirstChar { it.uppercase() }} ka address save nahi hai. Bolo: \"yaad rakh ${resolved.place} ka address ... hai\", ya destination bolo."),
                mapOf("missing_memory_key" to resolved.memoryKey),
            )
            is Destination.Known -> resolved.address
            Destination.None -> null
        }

        val preferred = args.string("provider")?.takeIf { it != "any" }
            ?: memory.get(MemoryKeys.PREFERRED_CAB_PROVIDER)?.value?.lowercase()?.trim()?.takeIf { it in CabLinks.PROVIDERS }
        val provider = chooseProvider(preferred)

        val baseData = buildMap<String, String> {
            put("provider", provider.label)
            destination?.let { put("destination", it) }
            put("fare_known", "false")
            put("booking_done", "false")
        }

        return when (provider.id) {
            CabLinks.UBER -> {
                // Universal link: opens the Uber app when installed, m.uber.com otherwise.
                val uri = Uri.parse(CabLinks.uberUrl(destination))
                val prefilled = destination != null
                val where = if (provider.installed) "Uber" else "Uber's website (the app is not installed)"
                val summary = if (destination != null) {
                    "Opened $where with pickup at your location and destination '${destination.preview(60)}'; choose the ride and confirm it there. Bidu cannot see fares or ETAs"
                } else {
                    "Opened $where with pickup at your location; enter the destination and confirm the ride there. Bidu cannot see fares or ETAs"
                }
                launcher.launch(Intent(Intent.ACTION_VIEW, uri), "Uber").toToolResult(
                    handedOffSummary = summary,
                    action = Intent.ACTION_VIEW,
                    label = "Uber",
                    data = baseData + mapOf("destination_prefilled" to prefilled.toString(), "installed" to provider.installed.toString()),
                )
            }
            else -> openWithoutDestination(provider, destination, baseData)
        }
    }

    /** Ola needs coordinates for a deep link and Rapido has no public one: open the app (or its store page). */
    private suspend fun openWithoutDestination(provider: Provider, destination: String?, baseData: Map<String, String>): ToolResult {
        val launch = launcher.launchIntentFor(provider.packageName)
        val destinationNote = destination?.let { " Enter '${it.preview(60)}' as the destination there." }.orEmpty()
        return if (launch != null) {
            launcher.launch(launch, provider.label).toToolResult(
                handedOffSummary = "Opened ${provider.label}.$destinationNote Choose and confirm the ride in ${provider.label}; Bidu cannot see fares or ETAs",
                action = Intent.ACTION_MAIN,
                label = provider.label,
                data = baseData + mapOf("destination_prefilled" to "false", "installed" to "true"),
            )
        } else {
            val store = Intent(Intent.ACTION_VIEW, Uri.parse(CabLinks.playStoreUrl(provider.packageName)))
            launcher.launch(store, "${provider.label} on Play Store").toToolResult(
                handedOffSummary = "${provider.label} is not installed; opened its Play Store page",
                action = Intent.ACTION_VIEW,
                label = "${provider.label} on Play Store",
                data = baseData + mapOf("destination_prefilled" to "false", "installed" to "false"),
            )
        }
    }

    private fun chooseProvider(preferred: String?): Provider {
        val all = CabLinks.PROVIDERS.map { id ->
            val pkg = CabLinks.packageOf(id)
            Provider(id, CabLinks.labelOf(id), pkg, launcher.launchIntentFor(pkg) != null)
        }
        preferred?.let { p -> all.firstOrNull { it.id == p }?.let { return it } }
        // Nothing asked for: the first installed provider; Uber's web link works even when none is.
        return all.firstOrNull { it.installed } ?: all.first { it.id == CabLinks.UBER }
    }

    private suspend fun resolveDestination(spoken: String?): Destination {
        if (spoken.isNullOrBlank()) return Destination.None
        val key = TextMatch.normalize(spoken)
        val wellKnown = when (key) {
            in HOME_WORDS -> MemoryKeys.HOME_ADDRESS to "ghar"
            in WORK_WORDS -> MemoryKeys.WORK_ADDRESS to "office"
            else -> null
        }
        if (wellKnown != null) {
            val saved = memory.get(wellKnown.first)?.value
            return if (saved.isNullOrBlank()) Destination.Missing(wellKnown.second, wellKnown.first) else Destination.Known(saved)
        }
        val place = memory.get(MemoryKeys.place(spoken))?.value
        return Destination.Known(place?.takeIf { it.isNotBlank() } ?: spoken)
    }

    private sealed interface Destination {
        data class Known(val address: String) : Destination
        data class Missing(val place: String, val memoryKey: String) : Destination
        data object None : Destination
    }

    private data class Provider(val id: String, val label: String, val packageName: String, val installed: Boolean)

    private companion object {
        val HOME_WORDS = setOf("ghar", "home", "mera ghar", "my home", "ghar pe", "ghar par", "घर", "मेरा घर")
        val WORK_WORDS = setOf("office", "work", "daftar", "offc", "ofis", "ऑफिस", "दफ्तर")
    }
}

/**
 * Opens BookMyShow or District when installed, else a web search for showtimes. Seats, prices and
 * payment are always chosen and confirmed by the user in that app or site.
 */
class MovieTicketsTool(
    private val memory: MemoryStore,
    private val launcher: IntentLauncher,
    private val clock: Clock,
) : Tool {
    override val spec: ToolSpec = BuiltinTools.MOVIE_TICKETS

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val query = args.string("query")?.trim()
        val city = args.string("city")?.trim() ?: memory.get(MemoryKeys.CITY)?.value?.trim()?.takeIf { it.isNotEmpty() }
        val baseData = buildMap<String, String> {
            query?.let { put("query", it) }
            city?.let { put("city", it) }
            put("booking_done", "false")
        }
        val searchFor = query?.let { " Search for '${it.preview(60)}' there." }.orEmpty()

        for ((label, pkg) in TICKET_APPS) {
            val launch = launcher.launchIntentFor(pkg) ?: continue
            return launcher.launch(launch, label).toToolResult(
                handedOffSummary = "Opened $label.$searchFor Seats, prices and payment are confirmed by you in $label",
                action = Intent.ACTION_MAIN,
                label = label,
                data = baseData + mapOf("app" to label),
            )
        }

        val date = Formats.dateLong(clock.nowZoned())
        val webQuery = listOfNotNull(query ?: "movies", "showtimes", city, date).joinToString(" ")
        return launcher.launch(Intent(Intent.ACTION_VIEW, googleSearchUri(webQuery)), "showtimes").toToolResult(
            handedOffSummary = "BookMyShow and District are not installed; opened a web search for '$webQuery'. Booking and payment happen on the site you choose",
            action = Intent.ACTION_VIEW,
            label = "showtimes",
            data = baseData + mapOf("web_query" to webQuery),
        )
    }

    private companion object {
        val TICKET_APPS = listOf("BookMyShow" to "com.bt.bms", "District" to "com.zomato.district")
    }
}

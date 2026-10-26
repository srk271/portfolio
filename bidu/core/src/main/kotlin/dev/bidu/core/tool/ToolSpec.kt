package dev.bidu.core.tool

import dev.bidu.core.model.Capability
import dev.bidu.core.model.RiskLevel

enum class ParamType { STRING, INT, LONG, DOUBLE, BOOLEAN, TIMESTAMP_MILLIS, DURATION_MILLIS, STRING_LIST }

/** One argument of a tool, with the checks the registry runs before execution. */
data class ParamSpec(
    val name: String,
    val type: ParamType,
    val description: String,
    val required: Boolean = true,
    val allowedValues: Set<String>? = null,
    val minValue: Long? = null,
    val maxValue: Long? = null,
    val maxLength: Int? = null,
) {
    /** Returns the normalised value, or an error message when the raw value is unacceptable. */
    fun normalize(args: ToolArgs): Result<Any?> {
        if (!args.has(name)) {
            return if (required) Result.failure(IllegalArgumentException("'$name' is required")) else Result.success(null)
        }
        return when (type) {
            ParamType.STRING -> {
                val v = args.string(name) ?: return fail("'$name' must be a non-empty string")
                if (maxLength != null && v.length > maxLength) return fail("'$name' is longer than $maxLength characters")
                if (allowedValues != null && v.lowercase() !in allowedValues) return fail("'$name' must be one of $allowedValues")
                Result.success(if (allowedValues != null) v.lowercase() else v)
            }
            ParamType.INT -> rangeChecked(args.int(name)?.toLong(), "integer").map { it?.toInt() }
            ParamType.LONG, ParamType.TIMESTAMP_MILLIS, ParamType.DURATION_MILLIS -> rangeChecked(args.long(name), "number")
            ParamType.DOUBLE -> {
                val v = args.double(name) ?: return fail("'$name' must be a number")
                Result.success(v)
            }
            ParamType.BOOLEAN -> {
                val v = args.boolean(name) ?: return fail("'$name' must be true or false")
                Result.success(v)
            }
            ParamType.STRING_LIST -> {
                val v = args.stringList(name) ?: return fail("'$name' must be a list of strings")
                Result.success(v)
            }
        }
    }

    private fun rangeChecked(value: Long?, label: String): Result<Long?> {
        if (value == null) return fail("'$name' must be a $label")
        if (minValue != null && value < minValue) return fail("'$name' must be >= $minValue")
        if (maxValue != null && value > maxValue) return fail("'$name' must be <= $maxValue")
        return Result.success(value)
    }

    private fun <T> fail(message: String): Result<T> = Result.failure(IllegalArgumentException(message))
}

/** Retry behaviour applied by the registry. Only idempotent tools may have attempts > 1. */
data class RetryPolicy(val maxAttempts: Int = 1, val initialBackoffMillis: Long = 300, val backoffMultiplier: Double = 2.0) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
    }

    companion object {
        val NONE = RetryPolicy(maxAttempts = 1)
        val TWICE = RetryPolicy(maxAttempts = 2)
    }
}

/**
 * Static description of a tool: what it does, which arguments it takes, how risky it is and
 * which capabilities it needs. Specs are the shared contract between the planner (which emits
 * [ToolCall]s by name) and the Android implementations (which consume validated args).
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val params: List<ParamSpec> = emptyList(),
    val risk: RiskLevel = RiskLevel.LOW,
    val requiredCapabilities: Set<Capability> = emptySet(),
    /** Capabilities that improve the outcome but have a fallback (for example overlay permission). */
    val optionalCapabilities: Set<Capability> = emptySet(),
    val idempotent: Boolean = false,
    val timeoutMillis: Long = 10_000,
    val retry: RetryPolicy = RetryPolicy.NONE,
    /** Example utterances, used by the help reply and by tests. */
    val examples: List<String> = emptyList(),
) {
    init {
        require(name.matches(Regex("[a-z][a-z0-9_]*"))) { "Tool name must be snake_case: $name" }
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
        require(idempotent || retry.maxAttempts == 1) { "Only idempotent tools may retry: $name" }
        require(params.map { it.name }.toSet().size == params.size) { "Duplicate param names in $name" }
    }

    fun param(name: String): ParamSpec? = params.firstOrNull { it.name == name }

    /** Validates and normalises [args]; unknown keys are dropped, never rejected. */
    fun validate(args: ToolArgs): ValidationResult {
        val errors = mutableListOf<String>()
        val normalized = mutableMapOf<String, Any?>()
        for (param in params) {
            param.normalize(args).fold(
                onSuccess = { value -> if (value != null) normalized[param.name] = value },
                onFailure = { errors += it.message ?: "'${param.name}' is invalid" },
            )
        }
        return if (errors.isEmpty()) ValidationResult.Valid(ToolArgs(normalized)) else ValidationResult.Invalid(errors)
    }
}

sealed interface ValidationResult {
    data class Valid(val args: ToolArgs) : ValidationResult
    data class Invalid(val errors: List<String>) : ValidationResult
}

package dev.bidu.core.tool

/**
 * Arguments passed to a tool. Values are plain JVM types (String, Long, Int, Double, Boolean,
 * List<String>) so they can be built by the rule planner, decoded from cloud JSON and logged.
 * Accessors coerce leniently (a numeric string is accepted for a Long) because both sources
 * produce strings freely; [ToolSpec.validate] normalises before a tool ever sees the args.
 */
class ToolArgs(values: Map<String, Any?>) {
    private val values: Map<String, Any?> = values.filterValues { it != null }

    val keys: Set<String> get() = values.keys
    fun has(name: String): Boolean = values.containsKey(name)
    fun raw(name: String): Any? = values[name]
    fun asMap(): Map<String, Any?> = values

    fun string(name: String): String? = values[name]?.toString()?.takeIf { it.isNotBlank() }
    fun requireString(name: String): String = string(name) ?: throw IllegalArgumentException("Missing argument '$name'")

    fun long(name: String): Long? = when (val v = values[name]) {
        null -> null
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull() ?: v.trim().toDoubleOrNull()?.toLong()
        else -> null
    }
    fun requireLong(name: String): Long = long(name) ?: throw IllegalArgumentException("Missing argument '$name'")

    fun int(name: String): Int? = long(name)?.toInt()
    fun requireInt(name: String): Int = int(name) ?: throw IllegalArgumentException("Missing argument '$name'")

    fun double(name: String): Double? = when (val v = values[name]) {
        null -> null
        is Number -> v.toDouble()
        is String -> v.trim().toDoubleOrNull()
        else -> null
    }

    fun boolean(name: String): Boolean? = when (val v = values[name]) {
        null -> null
        is Boolean -> v
        is String -> when (v.trim().lowercase()) {
            "true", "yes", "on", "1", "haan", "ha" -> true
            "false", "no", "off", "0", "nahi", "na" -> false
            else -> null
        }
        is Number -> v.toInt() != 0
        else -> null
    }

    fun stringList(name: String): List<String>? = when (val v = values[name]) {
        null -> null
        is List<*> -> v.map { it.toString() }
        is String -> v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        else -> null
    }

    fun with(name: String, value: Any?): ToolArgs = ToolArgs(values + (name to value))

    override fun equals(other: Any?): Boolean = other is ToolArgs && other.values == values
    override fun hashCode(): Int = values.hashCode()
    override fun toString(): String = "ToolArgs($values)"

    companion object {
        val EMPTY = ToolArgs(emptyMap())
        fun of(vararg pairs: Pair<String, Any?>): ToolArgs = ToolArgs(mapOf(*pairs))
    }
}

package dev.bidu.app.tools

import dev.bidu.core.memory.MemoryKeys
import dev.bidu.core.memory.MemoryStore
import dev.bidu.core.model.MemoryFact
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.Tool
import dev.bidu.core.tool.ToolArgs
import dev.bidu.core.tool.ToolContext
import dev.bidu.core.tool.ToolErrorCode
import dev.bidu.core.tool.ToolResult
import dev.bidu.core.tool.ToolSpec

/** Spoken names for the well-known memory keys, so "ghar" and "home_address" are the same fact. */
internal object MemoryKeyResolver {
    private val ALIASES: Map<String, String> = buildMap<String, String> {
        listOf("ghar", "home", "ghar ka address", "ghar ka pata", "home address", "mera ghar", "घर", "घर का पता")
            .forEach { put(it, MemoryKeys.HOME_ADDRESS) }
        listOf("office", "work", "daftar", "office ka address", "work address", "office address", "ऑफिस", "दफ्तर")
            .forEach { put(it, MemoryKeys.WORK_ADDRESS) }
        listOf("naam", "mera naam", "name", "my name", "नाम", "मेरा नाम").forEach { put(it, MemoryKeys.USER_NAME) }
        listOf("city", "shehar", "sheher", "mera shehar", "my city", "शहर").forEach { put(it, MemoryKeys.CITY) }
        listOf("cab", "cab provider", "preferred cab", "taxi").forEach { put(it, MemoryKeys.PREFERRED_CAB_PROVIDER) }
    }

    fun canonical(key: String): String {
        val trimmed = key.trim().lowercase()
        return ALIASES[TextMatch.normalize(trimmed)] ?: trimmed
    }

    /** Exact key first, then the closest stored key by spoken similarity. */
    fun lookup(spoken: String, facts: List<MemoryFact>): MemoryFact? {
        val canonical = canonical(spoken)
        facts.firstOrNull { it.key == canonical }?.let { return it }
        val scored = facts
            .map { it to TextMatch.score(spoken, readable(it.key)) }
            .filter { it.second >= 0.8 }
            .sortedByDescending { it.second }
        val best = scored.firstOrNull() ?: return null
        val tied = scored.count { best.second - it.second < 0.05 }
        return if (tied == 1) best.first else null
    }

    private fun readable(key: String): String =
        key.removePrefix(MemoryKeys.CONTACT_ALIAS_PREFIX).removePrefix(MemoryKeys.PLACE_PREFIX).replace('_', ' ').replace('.', ' ')
}

class RememberTool(private val memory: MemoryStore) : Tool {
    override val spec: ToolSpec = BuiltinTools.REMEMBER

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val key = MemoryKeyResolver.canonical(args.requireString("key"))
        val value = args.requireString("value").trim()
        if (SecretGuard.isSecretKey(key) || SecretGuard.looksSecret(value)) {
            return ToolResult.failed(ToolErrorCode.INVALID_ARGS, SecretGuard.REFUSAL)
        }
        val saved = memory.put(key, value)
        val readBack = memory.get(saved.key)
        if (readBack == null || readBack.value != saved.value) {
            return ToolResult.failed(ToolErrorCode.INTERNAL, "Memory could not be verified after saving")
        }
        return ToolResult.observed(
            "Remembered ${readBack.key}",
            "Memory entry '${readBack.key}' read back",
            mapOf("key" to readBack.key, "value" to readBack.value),
        )
    }
}

class RecallTool(private val memory: MemoryStore) : Tool {
    override val spec: ToolSpec = BuiltinTools.RECALL

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val spoken = args.requireString("key")
        val fact = memory.get(MemoryKeyResolver.canonical(spoken)) ?: MemoryKeyResolver.lookup(spoken, memory.all())
            ?: return ToolResult.failed(ToolErrorCode.NOT_FOUND, "Nothing saved for '${spoken.preview(40)}'")
        return ToolResult.observed(
            "Recalled ${fact.key}",
            "Memory entry '${fact.key}' read",
            mapOf("key" to fact.key, "value" to fact.value, "updated_at" to fact.updatedAt.toString()),
        )
    }
}

class ForgetTool(private val memory: MemoryStore) : Tool {
    override val spec: ToolSpec = BuiltinTools.FORGET

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val spoken = args.requireString("key")
        val fact = memory.get(MemoryKeyResolver.canonical(spoken)) ?: MemoryKeyResolver.lookup(spoken, memory.all())
            ?: return ToolResult.failed(ToolErrorCode.NOT_FOUND, "Nothing saved for '${spoken.preview(40)}'")
        memory.remove(fact.key)
        if (memory.get(fact.key) != null) {
            return ToolResult.failed(ToolErrorCode.INTERNAL, "Memory entry is still present after delete")
        }
        return ToolResult.observed("Forgot ${fact.key}", "Memory entry '${fact.key}' no longer exists", mapOf("key" to fact.key))
    }
}

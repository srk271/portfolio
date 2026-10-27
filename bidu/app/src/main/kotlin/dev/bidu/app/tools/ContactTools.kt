package dev.bidu.app.tools

import android.content.Intent
import android.net.Uri
import dev.bidu.core.model.Capability
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.Tool
import dev.bidu.core.tool.ToolArgs
import dev.bidu.core.tool.ToolContext
import dev.bidu.core.tool.ToolErrorCode
import dev.bidu.core.tool.ToolResult
import dev.bidu.core.tool.ToolSpec

class FindContactTool(private val contacts: ContactsLookup) : Tool {
    override val spec: ToolSpec = BuiltinTools.FIND_CONTACT

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val name = args.requireString("name")
        return when (val match = contacts.find(name)) {
            ContactMatch.NoPermission -> ToolResult.needsPermission(setOf(Capability.READ_CONTACTS), "Contacts permission is needed")
            is ContactMatch.NotFound -> notFound(name)
            is ContactMatch.Ambiguous -> ambiguous(name, match)
            is ContactMatch.Found -> {
                val contact = match.contact
                val preferred = contact.preferredNumber()
                ToolResult.observed(
                    "Found ${contact.name}",
                    "Matched 1 contact in the Contacts provider",
                    buildMap<String, String> {
                        put("name", contact.name)
                        put("numbers_count", contact.numbers.size.toString())
                        preferred?.let { put("number", it.number) }
                        match.aliasFrom?.let { put("alias", it) }
                    },
                )
            }
        }
    }
}

class DialContactTool(private val contacts: ContactsLookup, private val launcher: IntentLauncher) : Tool {
    override val spec: ToolSpec = BuiltinTools.DIAL_CONTACT

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val explicitNumber = args.string("number")
        val name = args.string("name")
        val (number, who) = when {
            explicitNumber != null -> {
                val cleaned = cleanNumber(explicitNumber)
                    ?: return ToolResult.failed(ToolErrorCode.INVALID_ARGS, "That does not look like a phone number")
                cleaned to (name ?: cleaned)
            }
            name != null -> when (val match = contacts.find(name)) {
                ContactMatch.NoPermission -> return ToolResult.needsPermission(setOf(Capability.READ_CONTACTS), "Contacts permission is needed")
                is ContactMatch.NotFound -> return notFound(name)
                is ContactMatch.Ambiguous -> return ambiguous(name, match)
                is ContactMatch.Found -> {
                    val preferred = match.contact.preferredNumber()?.number?.let { cleanNumber(it) }
                        ?: return ToolResult.failed(ToolErrorCode.NOT_FOUND, "${match.contact.name} has no phone number saved")
                    preferred to match.contact.name
                }
            }
            else -> return ToolResult.failed(ToolErrorCode.INVALID_ARGS, "Say whom to call")
        }

        val dial = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
        return launcher.launch(dial, "the dialer for $who").toToolResult(
            handedOffSummary = "Opened the dialer with $who's number; press call to start the call",
            action = Intent.ACTION_DIAL,
            label = "the dialer",
            data = mapOf("name" to who, "number" to number),
        )
    }

    private fun cleanNumber(raw: String): String? {
        val kept = raw.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        return kept.takeIf { s -> s.count { it.isDigit() } >= 3 }
    }
}

private fun notFound(name: String): ToolResult = ToolResult.failed(
    ToolErrorCode.NOT_FOUND,
    "No contact matches '${name.preview(40)}'. Save an alias like \"${name.preview(20)} matlab <contact name>\" if they are saved under another name",
)

private fun ambiguous(name: String, match: ContactMatch.Ambiguous): ToolResult = needsChoice(
    "${match.candidates.size} contacts match '${name.preview(40)}'",
    "Which one?",
    match.candidates.map { it.name },
)

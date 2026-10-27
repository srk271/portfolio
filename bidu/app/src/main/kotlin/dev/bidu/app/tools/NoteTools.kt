package dev.bidu.app.tools

import dev.bidu.app.data.NoteRepository
import dev.bidu.core.model.Note
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.Tool
import dev.bidu.core.tool.ToolArgs
import dev.bidu.core.tool.ToolContext
import dev.bidu.core.tool.ToolErrorCode
import dev.bidu.core.tool.ToolResult
import dev.bidu.core.tool.ToolSpec

class CreateNoteTool(private val notes: NoteRepository) : Tool {
    override val spec: ToolSpec = BuiltinTools.CREATE_NOTE

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val body = args.requireString("text").trim()
        val title = args.string("title")?.trim()
        if (SecretGuard.looksSecret(body) || (title != null && SecretGuard.looksSecret(title))) {
            return ToolResult.failed(ToolErrorCode.INVALID_ARGS, SecretGuard.REFUSAL)
        }
        val created = notes.create(body, title)
        val stored = notes.get(created.id)
        if (stored == null || stored.body != created.body) {
            return ToolResult.failed(ToolErrorCode.INTERNAL, "Note could not be verified after saving")
        }
        return ToolResult.observed(
            "Saved the note '${stored.label()}'",
            "Note row ${stored.id} read back",
            mapOf("id" to stored.id.toString(), "title" to stored.title.orEmpty(), "preview" to stored.body.preview()),
        )
    }
}

class SearchNotesTool(private val notes: NoteRepository) : Tool {
    override val spec: ToolSpec = BuiltinTools.SEARCH_NOTES

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val query = args.string("query")
        val limit = args.int("limit") ?: DEFAULT_LIMIT
        val found = notes.search(query, limit)
        val lines = found.mapIndexed { i, n -> "${i + 1}. ${n.label()}" }
        val data = buildMap<String, String> {
            put("count", found.size.toString())
            put("list", lines.joinToString("\n"))
            if (query != null) put("query", query)
            found.firstOrNull()?.let { put("first_body", it.body.preview(MAX_FIRST_BODY)) }
        }
        val summary = when {
            found.isEmpty() && query != null -> "No notes match '${query.preview(40)}'"
            found.isEmpty() -> "There are no notes yet"
            else -> "Found ${found.size} note(s)"
        }
        return ToolResult.observed(summary, "Searched ${found.size} matching note rows", data)
    }

    private companion object {
        const val DEFAULT_LIMIT = 5
        const val MAX_FIRST_BODY = 400
    }
}

class DeleteNoteTool(private val notes: NoteRepository) : Tool {
    override val spec: ToolSpec = BuiltinTools.DELETE_NOTE

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val id = args.long("id")
        val query = args.string("query")
        val target: Note = when {
            id != null -> notes.get(id) ?: return ToolResult.failed(ToolErrorCode.NOT_FOUND, "No note with id $id")
            query != null -> {
                val matches = notes.search(query, MAX_MATCHES)
                when (matches.size) {
                    0 -> return ToolResult.failed(ToolErrorCode.NOT_FOUND, "No note matches '${query.preview(40)}'")
                    1 -> matches.single()
                    else -> return needsChoice(
                        "${matches.size} notes match '${query.preview(40)}'",
                        "Which note should I delete?",
                        matches.take(5).map { it.label() },
                        mapOf("ids" to matches.take(5).joinToString(",") { it.id.toString() }),
                    )
                }
            }
            else -> return ToolResult.failed(ToolErrorCode.INVALID_ARGS, "Say which note to delete")
        }

        notes.delete(target.id)
        if (notes.get(target.id) != null) {
            return ToolResult.failed(ToolErrorCode.INTERNAL, "Note is still present after delete")
        }
        return ToolResult.observed(
            "Deleted the note '${target.label()}'",
            "Note row ${target.id} no longer exists",
            mapOf("id" to target.id.toString(), "title" to target.title.orEmpty(), "preview" to target.body.preview()),
        )
    }

    private companion object {
        const val MAX_MATCHES = 10
    }
}

private fun Note.label(): String = title?.takeIf { it.isNotBlank() } ?: body.preview(40)

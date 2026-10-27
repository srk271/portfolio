package dev.bidu.app.tools

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import dev.bidu.core.memory.AppCatalog
import dev.bidu.core.memory.KnownApp
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.Tool
import dev.bidu.core.tool.ToolArgs
import dev.bidu.core.tool.ToolContext
import dev.bidu.core.tool.ToolErrorCode
import dev.bidu.core.tool.ToolResult
import dev.bidu.core.tool.ToolSpec
import java.net.URLEncoder

class OpenAppTool(private val catalog: AppCatalog, private val launcher: IntentLauncher) : Tool {
    override val spec: ToolSpec = BuiltinTools.OPEN_APP

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val spoken = args.requireString("app")
        val requestedPackage = args.string("package")
        val apps = catalog.apps()

        val app: KnownApp = requestedPackage?.let { pkg -> apps.firstOrNull { it.packageName == pkg } }
            ?: when (val match = AppMatcher.match(spoken, apps)) {
                is AppMatch.Found -> match.app
                is AppMatch.Ambiguous -> return needsChoice(
                    "Several apps match '${spoken.preview(40)}'",
                    "Which app should I open?",
                    match.candidates.map { it.label },
                    mapOf("packages" to match.candidates.joinToString(",") { it.packageName }),
                )
                AppMatch.NotFound -> return ToolResult.failed(
                    ToolErrorCode.APP_NOT_INSTALLED,
                    "No installed app matches '${spoken.preview(40)}'",
                )
            }

        val intent = launcher.launchIntentFor(app.packageName)
            ?: return ToolResult.failed(ToolErrorCode.APP_NOT_INSTALLED, "${app.label} has no launcher screen")
        return launcher.launch(intent, app.label).toToolResult(
            handedOffSummary = "Opened ${app.label}",
            action = Intent.ACTION_MAIN,
            label = app.label,
            data = mapOf("app" to app.label, "package" to app.packageName),
        )
    }
}

class WebSearchTool(private val launcher: IntentLauncher) : Tool {
    override val spec: ToolSpec = BuiltinTools.WEB_SEARCH

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val query = args.requireString("query").trim()
        val webSearch = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        val intent = if (launcher.canResolve(webSearch)) webSearch else Intent(Intent.ACTION_VIEW, googleSearchUri(query))
        val action = intent.action ?: Intent.ACTION_VIEW
        return launcher.launch(intent, "search results").toToolResult(
            handedOffSummary = "Opened a web search for '${query.preview(60)}'; Bidu does not read the results",
            action = action,
            label = "search results",
            data = mapOf("query" to query),
        )
    }
}

internal fun googleSearchUri(query: String): Uri =
    Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(query, Charsets.UTF_8.name()))

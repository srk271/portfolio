package dev.bidu.app.tools

import dev.bidu.app.voice.ListeningSwitch
import dev.bidu.app.voice.VoiceController
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.Tool
import dev.bidu.core.tool.ToolArgs
import dev.bidu.core.tool.ToolContext
import dev.bidu.core.tool.ToolErrorCode
import dev.bidu.core.tool.ToolResult
import dev.bidu.core.tool.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class StopListeningTool(private val voice: VoiceController, private val listening: ListeningSwitch) : Tool {
    override val spec: ToolSpec = BuiltinTools.STOP_LISTENING

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        if (!voice.state.value.serviceRunning) {
            return ToolResult.observed("Background listening is already off", "Voice service reports not running", mapOf("listening" to "false"))
        }
        withContext(Dispatchers.Main.immediate) { listening.setListening(false) }
        val stopped = withTimeoutOrNull(STOP_TIMEOUT_MILLIS) { voice.state.first { !it.serviceRunning } }
        return if (stopped != null) {
            ToolResult.observed("Stopped background listening", "Voice service reported stopped", mapOf("listening" to "false"))
        } else {
            ToolResult.failed(ToolErrorCode.TIMEOUT, "The listening service did not report stopping", retryable = true)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 2_500L
    }
}

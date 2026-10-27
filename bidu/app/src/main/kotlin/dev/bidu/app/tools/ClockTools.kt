package dev.bidu.app.tools

import android.content.Intent
import android.provider.AlarmClock
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.Tool
import dev.bidu.core.tool.ToolArgs
import dev.bidu.core.tool.ToolContext
import dev.bidu.core.tool.ToolErrorCode
import dev.bidu.core.tool.ToolResult
import dev.bidu.core.tool.ToolSpec
import java.time.LocalTime

// The Clock app accepts these intents only from callers holding com.android.alarm.permission.SET_ALARM;
// without it the start throws SecurityException, reported as PERMISSION_DENIED.

class SetAlarmTool(private val launcher: IntentLauncher) : Tool {
    override val spec: ToolSpec = BuiltinTools.SET_ALARM

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val hour = args.requireInt("hour")
        val minute = args.requireInt("minute")
        val label = args.string("label")
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label != null) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        if (!launcher.canResolve(intent)) {
            return ToolResult.failed(ToolErrorCode.UNAVAILABLE, "No clock app on this phone accepts alarm requests")
        }
        val time = LocalTime.of(hour, minute)
        val shown = Formats.time12(time)
        return launcher.launch(intent, "the Clock app").toToolResult(
            handedOffSummary = "Asked the Clock app to set an alarm for $shown; check the Clock app to confirm it",
            action = AlarmClock.ACTION_SET_ALARM,
            label = "the Clock app",
            data = buildMap<String, String> {
                put("time", time.toString())
                put("time_12h", shown)
                label?.let { put("label", it) }
            },
        )
    }
}

class SetTimerTool(private val launcher: IntentLauncher) : Tool {
    override val spec: ToolSpec = BuiltinTools.SET_TIMER

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val seconds = args.requireInt("seconds")
        val label = args.string("label")
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label != null) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        if (!launcher.canResolve(intent)) {
            return ToolResult.failed(ToolErrorCode.UNAVAILABLE, "No clock app on this phone accepts timer requests")
        }
        val shown = describeDuration(seconds)
        return launcher.launch(intent, "the Clock app").toToolResult(
            handedOffSummary = "Asked the Clock app to start a $shown timer; check the Clock app to confirm it",
            action = AlarmClock.ACTION_SET_TIMER,
            label = "the Clock app",
            data = buildMap<String, String> {
                put("seconds", seconds.toString())
                put("duration_display", shown)
                label?.let { put("label", it) }
            },
        )
    }

    private fun describeDuration(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return listOfNotNull(
            h.takeIf { it > 0 }?.let { "$it h" },
            m.takeIf { it > 0 }?.let { "$it min" },
            s.takeIf { it > 0 }?.let { "$it s" },
        ).joinToString(" ")
    }
}

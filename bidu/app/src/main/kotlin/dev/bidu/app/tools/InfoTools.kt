package dev.bidu.app.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dev.bidu.core.model.Clock
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.Tool
import dev.bidu.core.tool.ToolArgs
import dev.bidu.core.tool.ToolContext
import dev.bidu.core.tool.ToolErrorCode
import dev.bidu.core.tool.ToolResult
import dev.bidu.core.tool.ToolSpec

class TellTimeTool(private val clock: Clock) : Tool {
    override val spec: ToolSpec = BuiltinTools.TELL_TIME

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val now = clock.nowZoned()
        val data = mapOf(
            "time" to Formats.time24(now),
            "time_12h" to Formats.time12(now),
            "hour" to now.hour.toString(),
            "minute" to now.minute.toString(),
            "zone" to now.zone.id,
        )
        return ToolResult.measured("It is ${Formats.time12(now)}", "Read the system clock (${now.zone.id})", data)
    }
}

class TellDateTool(private val clock: Clock) : Tool {
    override val spec: ToolSpec = BuiltinTools.TELL_DATE

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val now = clock.nowZoned()
        val data = mapOf(
            "date" to now.toLocalDate().toString(),
            "date_display" to Formats.dateLong(now),
            "weekday" to Formats.weekday(now),
            "day_of_week" to now.dayOfWeek.value.toString(),
        )
        return ToolResult.measured("Today is ${Formats.weekday(now)}, ${Formats.dateLong(now)}", "Read the system clock (${now.zone.id})", data)
    }
}

class BatteryLevelTool(context: Context) : Tool {
    private val app = context.applicationContext
    override val spec: ToolSpec = BuiltinTools.BATTERY_LEVEL

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val manager = app.getSystemService(BatteryManager::class.java)
            ?: return ToolResult.failed(ToolErrorCode.UNAVAILABLE, "Battery service unavailable")
        // Sticky system broadcast: a null receiver just returns the last value, nothing stays registered.
        val sticky: Intent? = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
            ?: sticky?.let { intent ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) (level * 100 / scale) else null
            }
            ?: return ToolResult.failed(ToolErrorCode.UNAVAILABLE, "Battery level could not be read")

        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = manager.isCharging ||
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = when (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            0 -> "none"
            else -> "other"
        }
        val data = mapOf("percent" to percent.toString(), "charging" to charging.toString(), "plugged" to plugged)
        val summary = "Battery is at $percent%" + if (charging) " and charging" else ""
        return ToolResult.measured(summary, "Read BatteryManager capacity", data)
    }
}

package dev.bidu.app.tools

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.Evidence
import dev.bidu.core.tool.EvidenceKind
import dev.bidu.core.tool.Tool
import dev.bidu.core.tool.ToolArgs
import dev.bidu.core.tool.ToolContext
import dev.bidu.core.tool.ToolErrorCode
import dev.bidu.core.tool.ToolResult
import dev.bidu.core.tool.ToolSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withTimeoutOrNull

/** Torch on/off/toggle, VERIFIED only when CameraManager's TorchCallback reports the requested state. */
class FlashlightTool(context: Context) : Tool {
    private val app = context.applicationContext
    override val spec: ToolSpec = BuiltinTools.FLASHLIGHT

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        val requested = args.requireString("state").lowercase()
        val cameras = app.getSystemService(CameraManager::class.java)
            ?: return ToolResult.failed(ToolErrorCode.UNAVAILABLE, "Camera service unavailable")
        val cameraId = torchCameraId(cameras)
            ?: return ToolResult.failed(ToolErrorCode.UNAVAILABLE, "This phone has no camera flash")

        val torchState = MutableStateFlow<Boolean?>(null)
        val unavailable = MutableStateFlow(false)
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) {
                if (id == cameraId) {
                    unavailable.value = false
                    torchState.value = enabled
                }
            }

            override fun onTorchModeUnavailable(id: String) {
                if (id == cameraId) unavailable.value = true
            }
        }
        cameras.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        try {
            // Registration immediately reports the current state of every flash unit.
            val current = withTimeoutOrNull(INITIAL_STATE_TIMEOUT_MILLIS) { torchState.filterNotNull().first() }
            if (unavailable.value) {
                return ToolResult.failed(ToolErrorCode.UNAVAILABLE, "The flash is in use by the camera right now")
            }
            val target = when (requested) {
                "on" -> true
                "off" -> false
                else -> current?.not()
                    ?: return ToolResult.failed(ToolErrorCode.UNAVAILABLE, "Could not read the current torch state to toggle it")
            }
            if (current == target) {
                return verified(target, alreadyThere = true)
            }
            try {
                cameras.setTorchMode(cameraId, target)
            } catch (e: CameraAccessException) {
                return ToolResult.failed(ToolErrorCode.UNAVAILABLE, "The flash is busy (camera in use)", retryable = true)
            } catch (e: IllegalArgumentException) {
                return ToolResult.failed(ToolErrorCode.UNAVAILABLE, "This camera has no controllable flash")
            }
            val confirmed = withTimeoutOrNull(CONFIRM_TIMEOUT_MILLIS) { torchState.first { it == target } }
            return if (confirmed != null) {
                verified(target, alreadyThere = false)
            } else {
                ToolResult.failed(ToolErrorCode.TIMEOUT, "The torch did not confirm the change", retryable = true)
            }
        } finally {
            cameras.unregisterTorchCallback(callback)
        }
    }

    private fun verified(on: Boolean, alreadyThere: Boolean): ToolResult {
        val state = if (on) "on" else "off"
        val summary = if (alreadyThere) "Torch was already $state" else "Torch is $state"
        return ToolResult.verified(
            summary,
            Evidence(EvidenceKind.SYSTEM_CALLBACK, "CameraManager.TorchCallback reported torch $state"),
            mapOf("state" to state, "changed" to (!alreadyThere).toString()),
        )
    }

    private fun torchCameraId(cameras: CameraManager): String? = try {
        val withFlash = cameras.cameraIdList.filter { id ->
            cameras.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        withFlash.firstOrNull { id ->
            cameras.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    } catch (e: CameraAccessException) {
        null
    }

    private companion object {
        const val INITIAL_STATE_TIMEOUT_MILLIS = 1_000L
        const val CONFIRM_TIMEOUT_MILLIS = 2_500L
    }
}

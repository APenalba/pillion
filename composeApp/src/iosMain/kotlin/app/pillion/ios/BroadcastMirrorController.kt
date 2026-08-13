package app.pillion.ios

import app.pillion.core.MirrorController
import app.pillion.core.MirrorSettings
import app.pillion.core.MirrorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

/**
 * Backs the Pillion "Start mirroring" button on iOS with the ReplayKit Broadcast Upload Extension.
 *
 * iOS can only mirror the *whole* screen (any app, e.g. Waze) from a broadcast extension, which runs
 * out of process and keeps going while Pillion is backgrounded. So this controller doesn't stream
 * itself: [start]/[stop] just trigger the system broadcast picker — the Swift shell injects that as
 * [onToggle] — and the extension does the capture + NaviLite streaming. The extension posts Darwin
 * notifications on start/finish/status, which the Swift shell relays here via [setActive]/[applyStatus]
 * so the shared UI reflects live transport/handshake/fps without the app owning the link.
 */
class BroadcastMirrorController : MirrorController {
    /** Set by the Swift shell: shows the system broadcast picker (start or stop). */
    var onToggle: (() -> Unit)? = null

    private val _state = MutableStateFlow<MirrorState>(MirrorState.Idle)
    override val state: StateFlow<MirrorState> = _state.asStateFlow()

    override fun start(settings: MirrorSettings) {
        // Hand the live settings to the out-of-process broadcast extension via the shared App Group
        // (it can't read the app's own UserDefaults). The extension reads these at broadcastStarted.
        NSUserDefaults(suiteName = APP_GROUP)?.apply {
            setInteger(settings.maxFps.toLong(), forKey = "stream.maxFps")
            setInteger(settings.quality.toLong(), forKey = "stream.quality")
        }
        onToggle?.invoke()
    }

    override fun stop() { onToggle?.invoke() }

    private companion object {
        const val APP_GROUP = "group.app.pillion"
    }

    /** Called by the Swift shell from the extension's broadcast start/finish Darwin notifications. */
    fun setActive(active: Boolean) {
        if (active) {
            // Keep any richer status already applied; only seed a placeholder if still Idle/Error.
            if (_state.value is MirrorState.Idle || _state.value is MirrorState.Error) {
                _state.value = MirrorState.Broadcasting(
                    headline = "Starting broadcast…",
                    detail = "Waiting for extension status…",
                )
            }
        } else {
            _state.value = MirrorState.Idle
        }
    }

    /**
     * Live diagnostics from the extension (App Group + Darwin). Maps phase → UI state so a silent
     * TCP fallback or handshake failure is visible on the phone, not only in Console.
     */
    fun applyStatus(
        phase: String,
        transport: String,
        bikeFound: Boolean,
        accessories: String,
        message: String,
        fps: Double,
        kbPerFrame: Int,
    ) {
        val transportLine = when (transport) {
            "bike" -> "Transport: bike (External Accessory / NaviLite)"
            "emulator" -> "Transport: TCP emulator — bike will NOT show this"
            else -> "Transport: $transport"
        }
        val detail = buildString {
            append(transportLine)
            append("\nCCU protocol: "); append(if (bikeFound) "found" else "NOT found")
            append("\nAccessories:\n"); append(accessories)
            if (message.isNotBlank()) {
                append("\n\n"); append(message)
            }
        }
        when (phase) {
            "error" -> {
                // Broadcast is still live (ReplayKit) — keep Broadcasting so Stop works; Error would
                // flip the button back to Start while the extension keeps capturing.
                _state.value = MirrorState.Broadcasting(
                    headline = "Connection failed — tap Stop",
                    detail = detail,
                )
            }
            "stopped" -> _state.value = MirrorState.Idle
            "looking", "connecting", "handshake" -> {
                _state.value = MirrorState.Broadcasting(
                    headline = when (phase) {
                        "looking" -> "Looking for CCU…"
                        "connecting" -> "Connecting…"
                        else -> "Handshake…"
                    },
                    detail = detail,
                )
            }
            else -> { // streaming (and any unknown live phase)
                _state.value = MirrorState.Broadcasting(
                    headline = if (bikeFound) "Mirroring to bike" else "Mirroring (emulator only)",
                    detail = detail,
                    fps = fps.takeIf { it > 0.0 },
                    kbPerFrame = kbPerFrame.takeIf { it > 0 },
                )
            }
        }
    }
}

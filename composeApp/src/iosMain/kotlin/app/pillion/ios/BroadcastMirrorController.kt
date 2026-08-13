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
 * [start]/[stop] only open the system broadcast picker; the extension owns capture + NaviLite.
 * Darwin notifications + [setActive]/[applyStatus] reflect live state. [setPreflight] is an in-app
 * MFi scan so Connection status works before the extension starts.
 */
class BroadcastMirrorController : MirrorController {
    var onToggle: (() -> Unit)? = null

    private var lastPreflight: String? = null
    private val _state = MutableStateFlow<MirrorState>(MirrorState.Idle())
    override val state: StateFlow<MirrorState> = _state.asStateFlow()

    override fun start(settings: MirrorSettings) {
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

    fun setPreflight(hint: String) {
        lastPreflight = hint
        // Don't clobber an in-progress "awaiting sheet / extension" message with a bare accessory scan.
        if (_state.value is MirrorState.Idle) {
            val cur = (_state.value as MirrorState.Idle).hint.orEmpty()
            if (cur.contains("Opening system") || cur.contains("did not start") ||
                cur.contains("Could not trigger") || cur.contains("not ready")
            ) {
                // Keep the action message; refresh the accessory block above it if we can.
                return
            }
            _state.value = MirrorState.Idle(hint = hint)
        }
    }

    /** Start was tapped — sheet should appear. Stay Idle so the button still says Start until the extension is live. */
    fun setAwaitingBroadcast() {
        _state.value = MirrorState.Idle(
            hint = buildString {
                append(lastPreflight ?: "CCU preflight pending…")
                append("\n\n→ Opening system broadcast sheet…")
                append("\nSelect Pillion Mirror → Start Broadcast.")
                append("\nStop mirroring appears only after the extension actually starts.")
                append("\nIf no sheet appears, tap the red record icon below.")
            },
        )
    }

    fun setPickerFailed(reason: String) {
        _state.value = MirrorState.Idle(
            hint = buildString {
                append(lastPreflight ?: "")
                append("\n\n⚠ "); append(reason)
            },
        )
    }

    /** Sheet was confirmed (or dismissed) but the extension never posted started — usually a dead appex. */
    fun setBroadcastDidNotStart() {
        if (_state.value !is MirrorState.Idle) return
        _state.value = MirrorState.Idle(
            hint = buildString {
                append(lastPreflight ?: "")
                append("\n\n⚠ Broadcast extension did not start.")
                append("\nThe CCU is fine — this is an iOS install/signing issue with Pillion Mirror.")
                append("\nTry: force-quit Pillion → delete app → reinstall IPA (AltStore/SideStore) →")
                append(" tap the red record icon → Pillion Mirror → Start.")
            },
        )
    }

    fun setActive(active: Boolean) {
        if (active) {
            if (_state.value is MirrorState.Idle || _state.value is MirrorState.Error) {
                _state.value = MirrorState.Broadcasting(
                    headline = "Starting broadcast…",
                    detail = "Waiting for extension status…\n\nLast preflight:\n${lastPreflight ?: "(none yet)"}",
                )
            }
        } else {
            _state.value = MirrorState.Idle(hint = lastPreflight)
        }
    }

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
                _state.value = MirrorState.Broadcasting(
                    headline = "Connection failed — tap Stop",
                    detail = detail,
                )
            }
            "stopped" -> _state.value = MirrorState.Idle(hint = lastPreflight)
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
            else -> {
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

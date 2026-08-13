package app.pillion.core

/** The single source of truth the UI renders. */
sealed interface MirrorState {
    /**
     * Not mirroring. [hint] is optional live preflight (e.g. iOS MFi accessory scan) so the home
     * screen can show CCU found/missing *before* Start mirroring — not only after broadcast starts.
     */
    data class Idle(val hint: String? = null) : MirrorState

    data object Connecting : MirrorState
    data class Streaming(val fps: Double, val kbPerFrame: Int) : MirrorState

    /**
     * An out-of-process broadcaster (iOS ReplayKit extension, or SDL screen-mirror) is projecting.
     * [detail] carries live diagnostics from the extension (transport, accessories, handshake) so a
     * bike that isn't receiving frames is diagnosable in-app instead of only in Console logs.
     */
    data class Broadcasting(
        val headline: String = "Broadcasting",
        val detail: String? = null,
        val fps: Double? = null,
        val kbPerFrame: Int? = null,
    ) : MirrorState

    data class Error(val message: String) : MirrorState
}

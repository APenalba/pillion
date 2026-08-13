package app.pillion.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A no-op controller for Compose previews and bring-up before platform wiring exists. */
class PreviewController(initial: MirrorState = MirrorState.Idle()) : MirrorController {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<MirrorState> = _state
    override fun start(settings: MirrorSettings) { _state.value = MirrorState.Streaming(fps = 14.6, kbPerFrame = 9) }
    override fun stop() { _state.value = MirrorState.Idle() }
}

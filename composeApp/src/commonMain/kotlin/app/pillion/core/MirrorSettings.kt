package app.pillion.core

/**
 * User-tunable session settings. Lower values trade smoothness for battery, heat and bandwidth.
 *
 * @param quality JPEG quality of each frame (10–80).
 * @param maxFps  upper bound on frames sent per second; the engine paces to this.
 * @param dashResolution off-screen display size for dedicated dash mode; output is scaled to 480x240.
 */
data class MirrorSettings(
    val quality: Int = 40,
    val maxFps: Int = 15,
    val dashResolution: DashResolution = DashResolution.DEFAULT,
    /**
     * Compatibility mode. Both platforms now always stream one frame at a time (stop-and-wait), so
     * this is currently inert — kept as a user-facing escape hatch for future transport tuning.
     */
    val compatMode: Boolean = false,
)

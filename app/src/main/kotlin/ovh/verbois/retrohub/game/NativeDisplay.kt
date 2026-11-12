package ovh.verbois.retrohub.game

/** Rotation adapter for the pinned LibretroDroid host, called on its emulation thread. */
internal object NativeDisplay {
    init {
        System.loadLibrary("retrohub_display")
    }

    external fun configure(corePath: String, clockwiseDegrees: Int, aspect: Float): Boolean
}

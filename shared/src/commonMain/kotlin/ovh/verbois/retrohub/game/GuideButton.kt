package ovh.verbois.retrohub.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The release opens the menu; a three-second hold fires once and consumes that release. */
class GuideButton(private val scope: CoroutineScope, private val onPress: () -> Unit, private val onHold: () -> Unit) {
    private var pressed = false
    private var fired = false
    private var timer: Job? = null

    fun event(down: Boolean, cancelled: Boolean = false) {
        if (cancelled) {
            cancel()
            return
        }
        if (down) {
            if (pressed) return
            pressed = true
            fired = false
            timer = scope.launch {
                delay(3000)
                fired = true
                onHold()
            }
        } else if (pressed) {
            timer?.cancel()
            pressed = false
            if (!fired) onPress()
        }
    }

    fun cancel() {
        timer?.cancel()
        pressed = false
        fired = false
    }
}

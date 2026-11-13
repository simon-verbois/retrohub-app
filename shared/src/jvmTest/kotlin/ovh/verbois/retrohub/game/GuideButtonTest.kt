@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package ovh.verbois.retrohub.game

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GuideButtonTest {
    @Test
    fun releaseOpensTheMenuAndCancelsThePendingHomeAction() = runTest {
        var menu = 0
        var home = 0
        val button = GuideButton(backgroundScope, { menu++ }, { home++ })
        button.event(true)
        advanceTimeBy(2999)
        button.event(false)
        advanceTimeBy(5000)
        assertEquals(1, menu)
        assertEquals(0, home)
    }

    @Test
    fun threeSecondsReturnsHomeOnlyOnceAndDoesNotOpenTheMenuOnRelease() = runTest {
        var menu = 0
        var home = 0
        val button = GuideButton(backgroundScope, { menu++ }, { home++ })
        button.event(true)
        advanceTimeBy(2000)
        button.event(true) // Android key repeat must not reset the timer.
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(1, home)
        button.event(true)
        advanceTimeBy(6000)
        button.event(false)
        assertEquals(1, home)
        assertEquals(0, menu)
    }

    @Test
    fun interruptedPressDoesNothingAndTheNextPressStillWorks() = runTest {
        val actions = mutableListOf<String>()
        val button = GuideButton(backgroundScope, { actions += "menu" }, { actions += "home" })
        button.event(true)
        advanceTimeBy(2000)
        button.cancel()
        advanceTimeBy(2000)
        button.event(false)
        assertEquals(emptyList(), actions)
        button.event(true)
        button.event(false)
        assertEquals(listOf("menu"), actions)
    }
}

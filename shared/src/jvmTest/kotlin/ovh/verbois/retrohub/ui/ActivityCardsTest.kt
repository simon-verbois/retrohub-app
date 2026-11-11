package ovh.verbois.retrohub.ui

import ovh.verbois.retrohub.api.LibraryGame
import ovh.verbois.retrohub.data.LocalSave
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.home.ActivityCard
import ovh.verbois.retrohub.ui.home.playTimeCard
import ovh.verbois.retrohub.ui.home.resumeCard
import ovh.verbois.retrohub.ui.home.syncCard
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityCardsTest {
    private val game = Game(LibraryGame(id = "g", playGameId = "p", title = "Jeu", console = "snes", playedSeconds = 3720), null)

    private fun save(slot: Int, at: String, kind: String = "state", base: String? = "a", local: String? = "a", conflict: Int? = null) =
        LocalSave(gameId = "p", kind = kind, slot = slot, console = "snes", baseSha = base, localSha = local, localPlayedAt = at, conflictRevision = conflict)

    @Test
    fun resumePointsToTheMostRecentSave() {
        val card = resumeCard(listOf(save(0, "2026-11-01T10:00:00Z"), save(2, "2026-11-09T10:00:00Z"), save(0, "2026-11-02T10:00:00Z", kind = "sram")))
        assertEquals("Reprendre", card.kicker)
        assertEquals("Emplacement 2", card.value)
        assertEquals("Nouvelle partie", resumeCard(emptyList()).value)
    }

    @Test
    fun playTimeAndSyncStates() {
        assertEquals("1 h 2 min", playTimeCard(game).value)
        assertEquals("À jour", syncCard(listOf(save(0, "x")), online = true).value)
        assertEquals("En attente", syncCard(listOf(save(0, "x", local = "b")), online = false).value)
        assertEquals(ActivityCard.Tone.WARNING, syncCard(listOf(save(1, "x", conflict = 3)), online = true).tone)
    }
}

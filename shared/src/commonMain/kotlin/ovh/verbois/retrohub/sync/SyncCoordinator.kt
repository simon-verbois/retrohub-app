package ovh.verbois.retrohub.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.core.AppClock
import ovh.verbois.retrohub.data.RoomSaveStore
import ovh.verbois.retrohub.library.LibraryRepository
import kotlin.time.Instant

enum class SyncPhase { IDLE, SYNCING, OFFLINE, MAINTENANCE, SIGNED_OUT, ERROR }

data class SyncStatus(
    val phase: SyncPhase = SyncPhase.IDLE,
    val pendingSaves: Int = 0,
    val conflicts: Int = 0,
    val pendingSessions: Int = 0,
    val lastSuccess: Instant? = null,
    val message: String? = null,
) {
    val upToDate: Boolean get() = phase == SyncPhase.IDLE && pendingSaves == 0 && pendingSessions == 0 && conflicts == 0
}

sealed interface SyncOutcome {
    data object Done : SyncOutcome

    /** Réessayer plus tard ([afterSeconds] : délai annoncé par le serveur en maintenance). */
    data class Retry(val afterSeconds: Long? = null) : SyncOutcome

    data object SignedOut : SyncOutcome
}

/**
 * Une synchronisation complète : catalogue, sauvegardes en attente, sessions hors ligne.
 * Appelée par la tâche de fond de la plateforme, au retour au premier plan et après une partie.
 */
class SyncCoordinator(
    private val library: LibraryRepository,
    private val saveSync: SaveSync,
    private val saves: RoomSaveStore,
    private val sessions: SessionUploader,
    private val pendingSessions: suspend () -> Int,
    private val clock: AppClock = AppClock.System,
    /** Après une synchro réussie : préférences du compte, mises à jour, favoris, nettoyage. */
    private val afterSync: suspend () -> Unit = {},
) {
    private val state = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = state.asStateFlow()
    private val lock = Mutex()

    suspend fun refreshCounts() {
        val all = saves.all()
        state.update {
            it.copy(
                pendingSaves = all.count { s ->
                    s.pendingUpload && s.lastError == null
                },
                conflicts = all.count { s -> s.inConflict },
                pendingSessions = pendingSessions(),
            )
        }
    }

    fun markOffline() = state.update { if (it.phase == SyncPhase.SYNCING) it else it.copy(phase = SyncPhase.OFFLINE) }

    fun markOnline() = state.update { if (it.phase == SyncPhase.OFFLINE) it.copy(phase = SyncPhase.IDLE) else it }

    suspend fun syncNow(): SyncOutcome = lock.withLock {
        state.update { it.copy(phase = SyncPhase.SYNCING, message = null) }
        val outcome =
            try {
                library.refresh()
                val epoch = library.saveEpoch()
                for (save in saves.all().filter { it.pendingUpload && it.lastError == null }) {
                    saveSync.push(save, epoch)
                }
                // Les sessions changent le temps de jeu et la dernière partie : on relit la bibliothèque.
                if (sessions.upload().recorded > 0) library.refresh()
                runCatching { afterSync() }.onFailure { if (it is CancellationException) throw it }
                state.update { it.copy(phase = SyncPhase.IDLE, lastSuccess = clock.now()) }
                SyncOutcome.Done
            } catch (error: CancellationException) {
                state.update { it.copy(phase = SyncPhase.IDLE) }
                throw error
            } catch (error: ApiException.Unauthorized) {
                state.update { it.copy(phase = SyncPhase.SIGNED_OUT) }
                SyncOutcome.SignedOut
            } catch (error: ApiException.Maintenance) {
                state.update { it.copy(phase = SyncPhase.MAINTENANCE, message = error.message) }
                SyncOutcome.Retry(error.retryAfterSeconds)
            } catch (error: ApiException.Offline) {
                state.update { it.copy(phase = SyncPhase.OFFLINE) }
                SyncOutcome.Retry()
            } catch (error: ApiException) {
                state.update { it.copy(phase = SyncPhase.ERROR, message = error.message) }
                SyncOutcome.Retry()
            }
        refreshCounts()
        outcome
    }
}

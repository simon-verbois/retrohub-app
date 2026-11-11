package ovh.verbois.retrohub.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.verbois.retrohub.emulation.AppSettings

class SettingsRepository(private val store: KeyValueStore, private val scope: CoroutineScope) {
    private val state = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = state.asStateFlow()
    private val lock = Mutex()

    suspend fun load() {
        state.value = store.get(KEY, AppSettings.serializer()) ?: AppSettings()
    }

    fun update(change: (AppSettings) -> AppSettings) {
        val previous = state.value
        val next = state.updateAndGet(change)
        if (previous == next) return
        scope.launch { lock.withLock { store.put(KEY, AppSettings.serializer(), state.value) } }
    }

    suspend fun updateNow(change: (AppSettings) -> AppSettings) {
        val previous = state.value
        val next = state.updateAndGet(change)
        if (previous == next) return
        lock.withLock { store.put(KEY, AppSettings.serializer(), state.value) }
    }

    private companion object {
        const val KEY = "app_settings"
    }
}

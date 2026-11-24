package ovh.verbois.retrohub.ui.catalogue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.AppContainer
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.CatalogueGame
import ovh.verbois.retrohub.api.CatalogueVersion
import ovh.verbois.retrohub.api.LibraryUpdate
import ovh.verbois.retrohub.api.ServerJob
import ovh.verbois.retrohub.api.SourceFile

/**
 * What a game page knows about one catalogue entry: its versions, the chosen one and the server job that
 * fetches it. Shared logic; each interface draws it.
 */
@Stable
class CatalogueGameState internal constructor(private val container: AppContainer, private val scope: CoroutineScope, val id: String) {
    var detail by mutableStateOf<CatalogueGame?>(null)
        internal set
    var selectedId by mutableStateOf<String?>(null)
        internal set
    var job by mutableStateOf<ServerJob?>(null)
        internal set
    internal var jobId by mutableStateOf<String?>(null)
    internal var jobRun by mutableIntStateOf(0)
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        internal set
    var refresh by mutableIntStateOf(0)
        private set

    /** Files to choose from before a server download; null when no choice is pending. */
    var files by mutableStateOf<List<SourceFile>?>(null)
        private set

    val version: CatalogueVersion? get() = detail?.versions?.firstOrNull { it.id == selectedId }

    /** True when the version would change the server selection (asks first). */
    val changesServerVersion: Boolean get() = detail?.versionId != null && detail?.versionId != selectedId

    fun reload() {
        refresh++
    }

    fun select(versionId: String) {
        if (busy || job?.active == true) return
        selectedId = versionId
        jobId = null
        job = null
    }

    /** Downloads the selected version to this device (selecting it on the server first when needed). */
    fun downloadToDevice() {
        val chosen = version ?: return
        val current = detail ?: return
        busy = true
        scope.launch {
            try {
                if (current.versionId != chosen.id) container.api.updateLibrary(id, LibraryUpdate(versionId = chosen.id))
                container.library.refresh()
                val ready = container.library.find(id)
                if (ready?.downloadable == true) {
                    container.downloads.start(ready)
                } else {
                    error = "Ce jeu ne peut pas encore être téléchargé sur ce téléphone."
                }
            } catch (failure: ApiException) {
                error = failure.message
            } finally {
                busy = false
            }
        }
    }

    /** Asks the server for its files; with a single file the download starts right away. */
    fun prepareServerDownload() {
        val chosen = version ?: return
        busy = true
        scope.launch {
            try {
                val current = job
                val choices = if (current?.needsFileChoice == true) container.api.serverJobFiles(current.id) else container.api.sourceFiles(id, chosen.id)
                error = null
                if (choices.size <= 1) {
                    busy = false
                    startServerDownload(choices.firstOrNull()?.id)
                } else {
                    files = choices
                }
            } catch (failure: ApiException) {
                error = failure.message
            } finally {
                busy = false
            }
        }
    }

    fun dismissFiles() {
        files = null
    }

    fun startServerDownload(fileId: String?) {
        val chosen = version ?: return
        files = null
        busy = true
        scope.launch {
            try {
                val current = job
                val started = if (current?.status ==
                    "failed"
                ) {
                    container.api.retryServerDownload(current.id, fileId)
                } else {
                    container.api.downloadToServer(id, chosen.id, fileId)
                }
                job = started
                jobId = started.id
                jobRun++
            } catch (failure: ApiException) {
                error = failure.message
            } finally {
                busy = false
            }
        }
    }

    fun cancelServerDownload() {
        val current = job ?: return
        scope.launch {
            try {
                container.api.cancelServerDownload(current.id)
                job = container.api.serverJob(current.id)
            } catch (failure: ApiException) {
                error = failure.message
            }
        }
    }
}

@Composable
fun rememberCatalogueGameState(container: AppContainer, id: String): CatalogueGameState {
    val scope = rememberCoroutineScope()
    val online by container.platform.online.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember(id) { CatalogueGameState(container, scope, id) }
    var savedVersion by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var savedJob by rememberSaveable(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(state) {
        if (state.selectedId == null) state.selectedId = savedVersion
        if (state.jobId == null) state.jobId = savedJob
    }
    savedVersion = state.selectedId
    savedJob = state.jobId
    LaunchedEffect(id, online, state.refresh) {
        if (!online) return@LaunchedEffect
        try {
            val detail = container.api.catalogueGame(id)
            state.detail = detail
            if (detail.versions.none { it.id == state.selectedId }) {
                state.selectedId = detail.versionId
                    ?: detail.versions.firstOrNull { container.settings.settings.value.preferFrench && "fr" in it.languages }?.id
                    ?: detail.versions.firstOrNull()?.id
            }
            state.error = null
        } catch (failure: ApiException) {
            state.error = failure.message ?: "La fiche ne se charge pas. Réessayez."
        }
    }
    LaunchedEffect(state.jobId, state.jobRun, online, lifecycle) {
        val active = state.jobId ?: return@LaunchedEffect
        if (!online) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                try {
                    val latest = container.api.serverJob(active)
                    state.job = latest
                    if (!latest.active) {
                        if (latest.status == "completed") {
                            container.library.refresh()
                            state.reload()
                        }
                        break
                    }
                } catch (_: ApiException) { }
                delay(2000)
            }
        }
    }
    return state
}

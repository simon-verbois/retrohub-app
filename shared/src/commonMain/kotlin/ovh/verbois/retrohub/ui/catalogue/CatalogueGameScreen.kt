package ovh.verbois.retrohub.ui.catalogue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.CatalogueGame
import ovh.verbois.retrohub.api.LibraryUpdate
import ovh.verbois.retrohub.api.ServerJob
import ovh.verbois.retrohub.api.SourceFile
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.library.DownloadState
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.ChoiceRow
import ovh.verbois.retrohub.ui.components.ConfirmDialog
import ovh.verbois.retrohub.ui.components.GameArt
import ovh.verbois.retrohub.ui.components.GlassButton
import ovh.verbois.retrohub.ui.components.LocalNavigationFocus
import ovh.verbois.retrohub.ui.components.PrimaryButton
import ovh.verbois.retrohub.ui.components.ProgressLine
import ovh.verbois.retrohub.ui.components.SettingsGroup
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

/** Installing on the server and copying to this phone are two explicit player actions. */
@Composable
fun CatalogueGameScreen(controller: UiController, id: String) {
    val container = controller.container
    val online by container.platform.online.collectAsState()
    val library by container.library.games.collectAsState()
    val downloads by container.downloads.states.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var game by remember(id) { mutableStateOf<CatalogueGame?>(null) }
    var selected by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var jobId by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<ServerJob?>(null) }
    var jobRun by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var confirmPhone by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var files by remember { mutableStateOf<List<SourceFile>?>(null) }
    var fileId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id, online, refresh) {
        if (!online) return@LaunchedEffect
        try {
            val detail = container.api.catalogueGame(id)
            game = detail
            if (detail.versions.none { it.id == selected }) {
                selected =
                    detail.versionId ?: detail.versions.firstOrNull { container.settings.settings.value.preferFrench && "fr" in it.languages }?.id
                        ?: detail.versions.firstOrNull()?.id
            }
            error = null
        } catch (failure: ApiException) {
            error = failure.message ?: "La fiche ne se charge pas. Réessayez."
        }
    }
    LaunchedEffect(jobId, jobRun, online, lifecycle) {
        val activeId = jobId ?: return@LaunchedEffect
        if (!online) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                try {
                    val latest = container.api.serverJob(activeId)
                    job = latest
                    if (!latest.active) {
                        if (latest.status == "completed") {
                            container.library.refresh()
                            refresh++
                        }
                        break
                    }
                } catch (_: ApiException) { }
                delay(2000)
            }
        }
    }
    val detail = game
    val first = remember { FocusRequester() }
    val tree = LocalNavigationFocus.current
    SideEffect { tree.content = if (detail != null) first else tree.header }
    if (detail == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (online && error == null) CircularProgressIndicator(color = Palette.Accent)
                Text(error ?: if (online) "Chargement…" else "Connectez le téléphone pour ouvrir cette fiche.", color = Palette.TextDim)
                if (error != null) GlassButton("Réessayer", { refresh++ }, enabled = online)
            }
        }
        return
    }
    val version = detail.versions.firstOrNull { it.id == selected }
    val known = library.firstOrNull { it.id == id }
    val download = known?.let { downloads[it.playGameId] }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).focusRequester(first).focusProperties {
            up = tree.header
        },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
            GameArt(
                detail.coverUrl,
                detail.console,
                detail.title,
                Modifier.widthIn(max = 144.dp).fillMaxWidth(0.3f).aspectRatio(1f),
                showTitleOnFallback = false,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(detail.title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    listOfNotNull(
                        Consoles.name(detail.console),
                        detail.year?.toString(),
                        detail.genre?.takeIf {
                            it.isNotBlank()
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                )
            }
        }
        if (detail.versions.isNotEmpty()) {
            SettingsGroup {
                ChoiceRow("Version", detail.versions, version ?: detail.versions.first(), { entry ->
                    listOf(entry.label, entry.source, entry.languages.joinToString(", ").uppercase()).filter { it.isNotBlank() }.joinToString(" · ")
                }, {
                    if (!busy && job?.active != true) {
                        selected = it.id
                        jobId = null
                        job = null
                    }
                })
            }
        }
        error?.let { Text(it, color = Palette.Warning, style = MaterialTheme.typography.bodyMedium) }
        val currentJob = job
        if (currentJob?.active == true) {
            Text("Téléchargement sur le serveur · ${currentJob.progress.toInt()} %", style = MaterialTheme.typography.titleMedium)
            ProgressLine(currentJob.progress / 100f, Modifier.fillMaxWidth())
            GlassButton("Annuler sur le serveur", {
                scope.launch {
                    try {
                        container.api.cancelServerDownload(currentJob.id)
                        job = container.api.serverJob(currentJob.id)
                    } catch (
                        failure: ApiException,
                    ) {
                        error = failure.message
                    }
                }
            }, enabled = online)
        } else if (version != null && version.available) {
            if (download is DownloadState.Running) {
                Text("Téléchargement sur le téléphone · ${(download.fraction * 100).toInt()} %", style = MaterialTheme.typography.titleMedium)
                ProgressLine(download.fraction, Modifier.fillMaxWidth())
                GlassButton("Annuler sur le téléphone", { container.downloads.cancel(known!!.playGameId) })
            } else if (known?.downloaded == true && detail.versionId == version.id && !known.updateAvailable) {
                PrimaryButton("Jouer", { controller.play(known) }, icon = Lucide.Play, enabled = known.supported)
            } else {
                PrimaryButton("Télécharger sur le téléphone", {
                    confirmPhone = true
                }, icon = Lucide.Download, enabled = online && !busy && Consoles[detail.console] != null, loading = busy)
            }
            if (Consoles[detail.console] == null) Text("Cette console n’est pas encore jouable sur ce téléphone.", color = Palette.TextDim)
        } else if (version?.downloadable == true) {
            if (currentJob?.status ==
                "failed"
            ) {
                Text("Le serveur n’a pas pu télécharger ce jeu. Réessayez ou choisissez une autre version.", color = Palette.Warning)
            }
            PrimaryButton(if (currentJob?.status == "failed") "Réessayer sur le serveur" else "Télécharger sur le serveur", {
                busy = true
                scope.launch {
                    try {
                        val choices = if (currentJob?.needsFileChoice ==
                            true
                        ) {
                            container.api.serverJobFiles(currentJob.id)
                        } else {
                            container.api.sourceFiles(id, version.id)
                        }
                        files = choices
                        fileId = choices.firstOrNull()?.id
                        error = null
                    } catch (failure: ApiException) {
                        error = failure.message
                    } finally {
                        busy = false
                    }
                }
            }, icon = Lucide.Download, enabled = online && !busy, loading = busy)
        } else {
            Text("Aucune version téléchargeable pour le moment.", color = Palette.TextDim)
        }
        if (detail.description.isNotBlank()) Text(detail.description, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
    }
    AutoFocus(first)
    if (confirmPhone && version != null) {
        ConfirmDialog(
            "Télécharger cette version ?",
            "${detail.title} · ${version.label}" + if (detail.versionId != version.id) "\nCette version sera aussi sélectionnée sur le serveur." else "",
            "Télécharger",
            onConfirm = {
                confirmPhone = false
                busy = true
                scope.launch {
                    try {
                        if (detail.versionId != version.id) container.api.updateLibrary(id, LibraryUpdate(versionId = version.id))
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
            },
            onDismiss = { confirmPhone = false },
        )
    }
    files?.let { choices ->
        CatalogueDialog("Télécharger sur le serveur ?", { if (!busy) files = null }) {
            Text(detail.title, style = MaterialTheme.typography.titleMedium)
            Text(version?.label.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            if (choices.isNotEmpty()) {
                SettingsGroup {
                    ChoiceRow(
                        "Fichier",
                        choices,
                        choices.firstOrNull {
                            it.id == fileId
                        } ?: choices.first(),
                        { listOf(it.name, it.size).filter(String::isNotBlank).joinToString(" · ") },
                        {
                            fileId =
                                it.id
                        },
                    )
                }
            }
            PrimaryButton("Télécharger", {
                val chosen = version ?: return@PrimaryButton
                busy = true
                scope.launch {
                    try {
                        val started = if (job?.status ==
                            "failed"
                        ) {
                            container.api.retryServerDownload(job!!.id, fileId)
                        } else {
                            container.api.downloadToServer(id, chosen.id, fileId)
                        }
                        job = started
                        jobId = started.id
                        jobRun++
                        files = null
                    } catch (failure: ApiException) {
                        error = failure.message
                        files = null
                    } finally {
                        busy = false
                    }
                }
            }, loading = busy, enabled = !busy && online)
            GlassButton("Annuler", { files = null }, enabled = !busy)
        }
    }
}

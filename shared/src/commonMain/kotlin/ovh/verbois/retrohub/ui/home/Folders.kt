package ovh.verbois.retrohub.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ovh.verbois.retrohub.core.searchKey
import ovh.verbois.retrohub.emulation.HomeFolder
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.*
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.theme.Palette

@Composable
fun FolderArtwork(folder: HomeFolder, modifier: Modifier = Modifier, showName: Boolean = false, compact: Boolean = false) {
    Column(modifier.background(Palette.GlassLight), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            folder.consoleId?.let {
                ovh.verbois.retrohub.ui.icons.ConsoleIcons.forConsole(it)
            } ?: Lucide.FolderOpen,
            null,
            Modifier.size(
                if (compact) {
                    24.dp
                } else if (showName) {
                    32.dp
                } else {
                    40.dp
                },
            ),
            tint = Palette.Text,
        )
        if (showName) {
            val label = if (compact) {
                when (folder.consoleId) {
                    "mastersystem" -> "MS"
                    "megadrive" -> "MD"
                    "gamecube" -> "GC"
                    "dreamcast" -> "DC"
                    "atari2600" -> "2600"
                    "psx" -> "PS"
                    "nds" -> "DS"
                    else -> folder.consoleId?.uppercase() ?: folder.name
                }
            } else {
                folder.name
            }
            Text(
                label,
                Modifier.padding(horizontal = 6.dp),
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!compact) {
            Text(
                "${folder.gameIds.size} ${if (folder.gameIds.size == 1) "jeu" else "jeux"}",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextDim,
            )
        }
    }
}

@Composable
fun FolderCard(folder: HomeFolder, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null) {
    Column(
        modifier.consoleFocus(RoundedCornerShape(18.dp), onClick, label = folder.name, onLongClick = onLongClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp))) {
            FolderArtwork(folder, Modifier.fillMaxSize())
            if (folder.favorite) Icon(Lucide.HeartFilled, "Favori", Modifier.align(Alignment.TopStart).padding(10.dp).size(16.dp), tint = Palette.Accent)
        }
        Text(folder.name, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun FolderActions(controller: UiController, folder: HomeFolder, play: FocusRequester, options: FocusRequester, actionWidth: Dp) {
    var deleting by remember(folder.id) { mutableStateOf(false) }
    if (folder.consoleId != null) {
        PrimaryButton("Ouvrir", {
            controller.nav.go(Route.Library(folder.consoleId))
        }, Modifier.widthIn(max = actionWidth).focusRequester(play), icon = Lucide.FolderOpen)
        RoundButton(if (folder.favorite) Lucide.HeartFilled else Lucide.Heart, "Favori", {
            controller.container.settings.update {
                it.copy(
                    homeConsoleFavorites = if (folder.favorite) {
                        it.homeConsoleFavorites - folder.consoleId!!
                    } else {
                        it.homeConsoleFavorites +
                            folder.consoleId!!
                    },
                )
            }
        }, selected = folder.favorite, selectedSurface = false)
        Box {
            RoundButton(Lucide.SlidersHorizontal, "Options du dossier", { controller.gameMenu = true }, Modifier.focusRequester(options))
            DropdownMenu(controller.gameMenu, {
                controller.gameMenu = false
            }, modifier = Modifier.controllerDialog({ controller.gameMenu = false }), containerColor = Palette.Deep) {
                PinMenuItems(controller, folder.key)
            }
        }
        return
    }
    val change: ((HomeFolder) -> HomeFolder) -> Unit = { update ->
        controller.container.settings.update { current -> current.copy(homeFolders = current.homeFolders.map { if (it.id == folder.id) update(it) else it }) }
    }
    PrimaryButton("Ouvrir", { controller.nav.go(Route.Folder(folder.id)) }, Modifier.widthIn(max = actionWidth).focusRequester(play), icon = Lucide.FolderOpen)
    RoundButton(
        if (folder.favorite) Lucide.HeartFilled else Lucide.Heart,
        if (folder.favorite) "Retirer des favoris" else "Ajouter aux favoris",
        { change { it.copy(favorite = !it.favorite) } },
        selected = folder.favorite,
        selectedSurface = false,
    )
    Box {
        RoundButton(Lucide.SlidersHorizontal, "Options du dossier", { controller.gameMenu = true }, Modifier.focusRequester(options))
        DropdownMenu(controller.gameMenu, {
            controller.gameMenu = false
        }, modifier = Modifier.controllerDialog({ controller.gameMenu = false }, true), containerColor = Palette.Deep) {
            val first = remember { FocusRequester() }
            ConsoleMenuItem("Modifier le dossier", modifier = Modifier.focusRequester(first), icon = Lucide.FolderOpen, onClick = {
                controller.gameMenu = false
                controller.folderEditor =
                    folder
            })
            PinMenuItems(controller, folder.key)
            ConsoleMenuItem("Retirer de l’accueil", icon = Lucide.X, onClick = {
                controller.gameMenu = false
                controller.container.settings.update { current ->
                    current.copy(
                        homeFolders = current.homeFolders.map {
                            if (it.id ==
                                folder.id
                            ) {
                                it.copy(onHome = false)
                            } else {
                                it
                            }
                        },
                        homePins = current.homePins - folder.key,
                    )
                }
            })
            ConsoleMenuItem("Supprimer le dossier", icon = Lucide.Trash2, tint = Palette.Danger, onClick = {
                controller.gameMenu = false
                deleting = true
            })
            if (controller.gameMenu) AutoFocus(first)
        }
    }
    if (deleting) {
        ConfirmDialog("Supprimer ce dossier ?", "Les jeux et leurs sauvegardes sont conservés.", "Supprimer", onConfirm = {
            controller.container.settings.update {
                it.copy(
                    homeFolders = it.homeFolders.filterNot { item -> item.id == folder.id },
                    homePins =
                    it.homePins - folder.key,
                )
            }
            deleting = false
        }, onDismiss = { deleting = false }, destructive = true)
    }
}

@Composable
fun FolderEditor(controller: UiController, folder: HomeFolder) {
    val all by controller.container.library.games.collectAsState()
    val settings by controller.container.settings.settings.collectAsState()
    var deleting by remember(folder.id) { mutableStateOf(false) }
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    var query by remember(folder.id) { mutableStateOf("") }
    var chosen by remember(folder.id) { mutableStateOf(folder.gameIds.toSet()) }
    var onHome by remember(folder.id) { mutableStateOf(folder.onHome) }
    val games = remember(all, query, settings.hiddenConsoles) {
        all.filter { it.console !in settings.hiddenConsoles && searchKey(it.title).contains(searchKey(query)) }.sortedBy { it.sortTitle }
    }
    val close = { controller.folderEditor = null }
    val first = remember { FocusRequester() }
    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = androidx.compose.ui.platform.LocalFocusManager.current
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val wide = maxWidth > 500.dp
            Column(
                Modifier.widthIn(max = 760.dp).fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Palette.Deep)
                    .controllerDialog(close).padding(16.dp).testTag("folder-editor"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        name,
                        {
                            name = it.take(80)
                        },
                        Modifier.weight(1f).focusRequester(first).testTag("folder-name"),
                        label = { Text("Nom du dossier") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboard?.hide()
                            focus.clearFocus()
                        }),
                    )
                    if (wide) OutlinedTextField(query, { query = it }, Modifier.weight(1f), label = { Text("Chercher un jeu") }, singleLine = true)
                }
                if (!wide) {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Chercher un jeu") }, singleLine = true)
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("folder-games")) {
                    items(games, key = { it.id }) { game ->
                        Row(
                            Modifier.fillMaxWidth().consoleFocus(RoundedCornerShape(12.dp), {
                                chosen =
                                    if (game.id in
                                        chosen
                                    ) {
                                        chosen - game.id
                                    } else {
                                        chosen + game.id
                                    }
                            }).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(game.id in chosen, null)
                            Text(
                                game.title,
                                Modifier.weight(1f).padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(Lucide.House, "Afficher à l’accueil", { onHome = !onHome }, size = 48.dp, selected = onHome)
                    if (settings.homeFolders.any { it.id == folder.id }) RoundButton(Lucide.Trash2, "Supprimer le dossier", { deleting = true }, size = 48.dp)
                    Spacer(Modifier.weight(1f))
                    GlassButton("Annuler", close)
                    PrimaryButton("Enregistrer", {
                        val updated = folder.copy(name = name.trim(), gameIds = chosen.toList(), onHome = onHome)
                        controller.container.settings.update { current ->
                            current.copy(
                                homeFolders =
                                current.homeFolders.filterNot { it.id == folder.id } + updated,
                            )
                        }
                        close()
                    }, enabled = name.isNotBlank())
                }
            }
        }
        AutoFocus(first)
    }
    if (deleting) {
        ConfirmDialog("Supprimer ce dossier ?", "Les jeux et leurs sauvegardes sont conservés.", "Supprimer", onConfirm = {
            controller.deleteFolder(folder)
        }, onDismiss = { deleting = false }, destructive = true)
    }
}

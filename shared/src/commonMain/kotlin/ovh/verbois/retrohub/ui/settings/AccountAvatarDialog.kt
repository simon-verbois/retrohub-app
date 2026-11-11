package ovh.verbois.retrohub.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.account.Account
import ovh.verbois.retrohub.api.AccountAvatar
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.AvatarCatalog
import ovh.verbois.retrohub.api.FEATURE_ACCOUNT_AVATARS
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.ActionButtons
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.GlassButton
import ovh.verbois.retrohub.ui.components.GlassPanel
import ovh.verbois.retrohub.ui.components.MessageBanner
import ovh.verbois.retrohub.ui.components.ProfileAvatar
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

@Composable
internal fun AccountAvatarDialog(controller: UiController, account: Account, onDismiss: () -> Unit) {
    val owner = remember(account.server, account.userId, account.deviceId) { account }
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<AvatarCatalog?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(retry) {
        error = null
        try {
            catalog = controller.container.api.avatarCatalog()
        } catch (_: ApiException.Offline) {
            error = "Connectez-vous au serveur pour choisir une photo de profil."
        } catch (failure: ApiException) {
            error = failure.message ?: "Les avatars ne sont pas disponibles. Réessayez."
        }
    }
    AvatarPicker(owner, catalog, error, saving, onRetry = { retry++ }, onSave = { id ->
        if (!saving) {
            saving = true
            error = null
            scope.launch {
                try {
                    val supported = controller.container.serverStatus.value?.features?.contains(FEATURE_ACCOUNT_AVATARS) == true
                    controller.container.account.chooseAvatar(id, supported, owner)
                    onDismiss()
                } catch (_: ApiException.Offline) {
                    error = "Photo non enregistrée. Reconnectez-vous au serveur puis réessayez."
                } catch (failure: ApiException) {
                    error = failure.message ?: "La photo n’a pas été enregistrée. Réessayez."
                } finally {
                    saving = false
                }
            }
        }
    }, onDismiss = onDismiss)
}

/** Presentation separated from network state so controller navigation and failed saves can be exercised. */
@Composable
fun AvatarPicker(
    owner: Account,
    catalog: AvatarCatalog?,
    error: String?,
    saving: Boolean,
    onRetry: () -> Unit,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(owner.avatar?.id?.takeUnless { it == catalog?.defaultAvatar?.id }) }
    var initialized by remember { mutableStateOf(catalog != null) }
    LaunchedEffect(catalog) {
        if (!initialized && catalog != null) {
            chosen = owner.avatar?.id?.takeIf { id -> id != catalog.defaultAvatar?.id && catalog.avatars.any { it.id == id } }
            initialized = true
        }
    }
    val cancel = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val initialId = owner.avatar?.id?.takeIf { id -> catalog?.avatars?.any { it.id == id } == true && id != catalog.defaultAvatar?.id }
    val initialKey = initialId?.let { "avatar:$it" } ?: "initial"
    var lastFocus by remember { mutableStateOf(initialKey) }
    val dismiss = { if (!saving) onDismiss() }
    val density = LocalDensity.current
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides density) {
            GlassPanel(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(0.9f).padding(16.dp).onPreviewKeyEvent { event ->
                    if (event.key == Key.ButtonB || event.key == Key.Escape) {
                        if (event.type == KeyEventType.KeyUp) dismiss()
                        true
                    } else {
                        false
                    }
                },
            ) {
                Column(Modifier.fillMaxSize().background(Palette.SkyLow).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Photo de profil", style = MaterialTheme.typography.headlineMedium)
                    error?.let { MessageBanner(it) }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        if (catalog == null) {
                            Column(
                                Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (error == null) {
                                    CircularProgressIndicator(color = Palette.Accent)
                                } else {
                                    GlassButton("Réessayer", onRetry, Modifier.focusRequester(retryFocus))
                                }
                            }
                            if (error != null) AutoFocus(retryFocus)
                        } else {
                            val options = listOf<AccountAvatar?>(null) + catalog.avatars.distinctBy { it.id }.filter { it.id != catalog.defaultAvatar?.id }
                            val columns = ((maxWidth + 12.dp) / 100.dp).toInt().coerceAtLeast(1)
                            val start = options.indexOfFirst { it?.id == initialId }.coerceAtLeast(0)
                            val grid = rememberLazyGridState(initialFirstVisibleItemIndex = start)
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                state = grid,
                                modifier = Modifier.fillMaxSize().focusGroup(),
                                contentPadding = PaddingValues(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                itemsIndexed(options, key = { _, avatar -> avatar?.id?.let { "avatar:$it" } ?: "initial" }) { index, avatar ->
                                    val key = avatar?.id?.let { "avatar:$it" } ?: "initial"
                                    val focus = requesters.getOrPut(key) { FocusRequester() }
                                    AvatarChoice(
                                        owner,
                                        avatar,
                                        catalog.defaultAvatar,
                                        chosen == avatar?.id,
                                        !saving,
                                        {
                                            chosen = avatar?.id
                                        },
                                        Modifier.focusRequester(focus).onFocusChanged { if (it.isFocused) lastFocus = key }.focusProperties {
                                            if (index < columns) up = FocusRequester.Cancel
                                            if (index % columns == 0) left = FocusRequester.Cancel
                                            if (index % columns == columns - 1 || index == options.lastIndex) right = FocusRequester.Cancel
                                            if (index + columns > options.lastIndex) down = cancel
                                        },
                                    )
                                }
                            }
                            val first = requesters.getOrPut(initialKey) { FocusRequester() }
                            AutoFocus(first)
                        }
                    }
                    if (catalog != null) {
                        ActionButtons(
                            "Enregistrer",
                            { onSave(chosen) },
                            modifier = Modifier.focusGroup(),
                            parentFocus = requesters[lastFocus] ?: FocusRequester.Default,
                            entryFocus = cancel,
                            secondary = "Annuler",
                            onSecondary = dismiss,
                            loading = saving,
                        )
                    } else {
                        GlassButton("Annuler", dismiss, Modifier.focusRequester(cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarChoice(
    owner: Account,
    avatar: AccountAvatar?,
    defaultAvatar: AccountAvatar?,
    chosen: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = avatar?.name ?: if (defaultAvatar != null) "Par défaut" else "Initiale"
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.testTag("account-avatar-${avatar?.id ?: "default"}").semantics {
            contentDescription = name
            selected = chosen
        }.consoleFocus(shape, onClick, enabled = enabled)
            .background(if (chosen) Palette.GlassLight else Color.Transparent, shape).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            ProfileAvatar(owner.username, owner.server, avatar ?: defaultAvatar, Modifier.size(64.dp))
            if (chosen) {
                Icon(
                    Lucide.Check,
                    null,
                    Modifier.align(Alignment.TopEnd).size(18.dp)
                        .background(Palette.Success, CircleShape).border(1.dp, Palette.SkyLow, CircleShape).padding(2.dp),
                    tint = Palette.SkyLow,
                )
            }
        }
        Text(name, style = MaterialTheme.typography.labelSmall, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

package ovh.verbois.retrohub.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ovh.verbois.retrohub.AppContainer
import ovh.verbois.retrohub.account.AuthState
import ovh.verbois.retrohub.account.QrCameraException
import ovh.verbois.retrohub.account.pairingCodeFromQr
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.resources.Res
import ovh.verbois.retrohub.resources.action_start
import ovh.verbois.retrohub.resources.qr_camera_permission
import ovh.verbois.retrohub.resources.qr_invalid
import ovh.verbois.retrohub.resources.qr_opening
import ovh.verbois.retrohub.resources.qr_scan
import ovh.verbois.retrohub.resources.welcome_description
import ovh.verbois.retrohub.resources.welcome_title
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.FilterChip
import ovh.verbois.retrohub.ui.components.GlassButton
import ovh.verbois.retrohub.ui.components.GlassPanel
import ovh.verbois.retrohub.ui.components.MessageBanner
import ovh.verbois.retrohub.ui.components.MessageKind
import ovh.verbois.retrohub.ui.components.PrimaryButton
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

private enum class Step { WELCOME, SERVER, LOGIN }

/** Mise en forme d’un code d’appairage pendant la saisie : « ABCD-EFGH ». */
fun formatPairingCode(input: String): String {
    val raw = input.uppercase().filter { it.isLetterOrDigit() }.take(8)
    return if (raw.length > 4) raw.take(4) + "-" + raw.drop(4) else raw
}

/** Premier lancement et reconnexion : serveur, puis code d’appairage ou mot de passe. */
@Composable
fun Onboarding(container: AppContainer, state: AuthState.SignedOut) {
    var step by remember { mutableStateOf(if (state.server != null && state.message != null) Step.LOGIN else Step.WELCOME) }
    var server by remember { mutableStateOf(state.server?.removePrefix("https://") ?: "") }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var serverLabel by remember { mutableStateOf<String?>(null) }

    fun checkServer(next: Boolean) {
        checking = true
        error = null
        scope.launch {
            try {
                val status = container.account.checkServer(server)
                serverLabel = "${status.name} ${status.serverVersion}".trim()
                if (next) step = Step.LOGIN
            } catch (e: ApiException) {
                error = e.message
                if (!next) step = Step.SERVER
            } finally {
                checking = false
            }
        }
    }
    LaunchedEffect(Unit) { if (step == Step.LOGIN) checkServer(next = false) }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Palette.SkyTop, Palette.SkyLow)),
        ).windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (step == Step.WELCOME) {
                WelcomeScreen { step = Step.SERVER }
                return@BoxWithConstraints
            }
            val wide = maxWidth > maxHeight && maxWidth > 640.dp
            val brand: @Composable (Modifier) -> Unit = { modifier -> Brand(modifier, compact = !wide && step != Step.WELCOME) }
            val form: @Composable () -> Unit = {
                AnimatedContent(step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "step") { current ->
                    when (current) {
                        Step.WELCOME -> Unit

                        Step.SERVER ->
                            ServerStep(server, {
                                server = it
                                error = null
                            }, checking, error) { checkServer(next = true) }

                        Step.LOGIN ->
                            LoginStep(container, serverLabel ?: server, state.message, onChangeServer = { step = Step.SERVER })
                    }
                }
            }
            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    brand(Modifier.weight(1f))
                    Column(
                        Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { form() }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
                ) {
                    brand(Modifier)
                    form()
                }
            }
        }
    }
}

@Composable
fun Logo(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(
            size,
        ).shadow(
            30.dp,
            CircleShape,
            ambientColor = Palette.Select,
            spotColor = Palette.Select,
        ).clip(CircleShape).background(Brush.linearGradient(listOf(Palette.Accent, Palette.Select))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.Gamepad2, null, Modifier.size(size * 0.55f), tint = Palette.OnAccent)
    }
}

@Composable
private fun Brand(modifier: Modifier, compact: Boolean) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Logo(if (compact) 64.dp else 112.dp)
        Text("RetroHub", style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayLarge)
        if (!compact) {
            Text(
                "Votre collection rétro dans la poche.\nJouez hors ligne, reprenez sur le web.",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.TextDim,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FormCard(content: @Composable () -> Unit) {
    GlassPanel(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
}

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val focus = remember { FocusRequester() }
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Palette.SkyTop, Palette.SkyLow)))
            .verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Logo(88.dp)
        Text(
            stringResource(Res.string.welcome_title),
            Modifier.widthIn(max = 480.dp).semantics {
                heading()
            },
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(Res.string.welcome_description),
            Modifier.widthIn(max = 360.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.TextDim,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(
            stringResource(Res.string.action_start),
            onStart,
            Modifier.widthIn(max = 360.dp).fillMaxWidth().focusRequester(focus),
            icon = Lucide.ChevronRight,
        )
    }
    AutoFocus(focus)
}

@Composable
fun FieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Select,
    unfocusedBorderColor = Palette.Line,
    focusedLabelColor = Palette.Select,
    unfocusedLabelColor = Palette.TextDim,
    focusedContainerColor = Palette.Field,
    unfocusedContainerColor = Palette.Field,
    cursorColor = Palette.Accent,
)

@Composable
private fun ServerStep(server: String, onChange: (String) -> Unit, checking: Boolean, error: String?, onContinue: () -> Unit) {
    val focus = remember { FocusRequester() }
    FormCard {
        Text("Votre serveur", style = MaterialTheme.typography.headlineMedium)
        Text(
            "L’adresse de votre RetroHub, celle que vous ouvrez dans le navigateur.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextDim,
        )
        OutlinedTextField(
            server,
            onChange,
            Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text("Adresse du serveur") },
            placeholder = { Text("retrohub.mondomaine.fr") },
            leadingIcon = { Icon(Lucide.Globe, null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = FieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = { onContinue() }),
        )
        error?.let { MessageBanner(it, MessageKind.ERROR, Modifier.fillMaxWidth()) }
        PrimaryButton(
            "Continuer",
            onContinue,
            Modifier.fillMaxWidth(),
            loading = checking,
            enabled = server.isNotBlank(),
            icon = Lucide.ChevronRight,
        )
    }
    AutoFocus(focus)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoginStep(container: AppContainer, server: String, message: String?, onChangeServer: () -> Unit) {
    var withCode by remember { mutableStateOf(true) }
    var codeField by remember { mutableStateOf(TextFieldValue("")) }
    val code = codeField.text
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var device by remember { mutableStateOf(container.settings.settings.value.deviceName.ifBlank { container.platform.defaultDeviceName }) }
    var busy by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val invalidQr = stringResource(Res.string.qr_invalid)
    val cameraDenied = stringResource(Res.string.qr_camera_permission)
    fun submit() {
        busy = true
        error = null
        scope.launch {
            try {
                if (withCode) {
                    container.account.signInWithCode(
                        code,
                        device,
                    )
                } else {
                    container.account.signInWithPassword(username, password, device)
                }
                container.settings.updateNow { it.copy(deviceName = device) }
                container.afterSignIn()
            } catch (e: ApiException.Http) {
                error = if (e.status == 400 && withCode) "Ce code n’est plus valable. Affichez un nouveau code sur le web." else e.message
            } catch (e: ApiException) {
                error = e.message
            } catch (e: IllegalStateException) {
                error = "Vérifiez d’abord l’adresse du serveur."
                onChangeServer()
            } finally {
                busy = false
            }
        }
    }
    val ready =
        device.isNotBlank() && if (withCode) code.count { it.isLetterOrDigit() } == 8 else username.isNotBlank() && password.isNotBlank()
    FormCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Relier ce téléphone", style = MaterialTheme.typography.headlineMedium)
                Text(server, style = MaterialTheme.typography.bodySmall)
            }
            GlassButton("Changer", onChangeServer, height = 38.dp)
        }
        message?.let { MessageBanner(it, MessageKind.WARNING, Modifier.fillMaxWidth()) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("Code d’appairage", withCode, {
                withCode = true
                error = null
            })
            FilterChip("Mot de passe", !withCode, {
                withCode = false
                error = null
            })
        }
        if (withCode) {
            if (container.platform.canScanQrCode) {
                GlassButton(
                    stringResource(if (scanning) Res.string.qr_opening else Res.string.qr_scan),
                    {
                        if (!scanning) {
                            scope.launch {
                                scanning = true
                                error = null
                                try {
                                    container.platform.scanQrCode()?.let { payload ->
                                        val scanned = pairingCodeFromQr(payload)
                                        if (scanned == null) error = invalidQr else codeField = TextFieldValue(scanned, TextRange(scanned.length))
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: QrCameraException) {
                                    error = cameraDenied
                                } catch (e: Exception) {
                                    error = cameraDenied
                                } finally {
                                    scanning = false
                                }
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                    icon = Lucide.Camera,
                    enabled = !busy && !scanning,
                )
            }
            Text(
                "Sur le web : Paramètres → Applications et appareils → Relier un appareil. Le code est valable 10 minutes.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextDim,
            )
            OutlinedTextField(
                codeField,
                {
                    // Le tiret s’insère tout seul ; le curseur reste en fin de saisie.
                    val formatted = formatPairingCode(it.text)
                    codeField = TextFieldValue(formatted, TextRange(formatted.length))
                },
                Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text("Code d’appairage") },
                placeholder = { Text("ABCD-EFGH") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = FieldColors(),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 26.sp,
                    letterSpacing = 4.sp,
                    color = Palette.Text,
                    textAlign = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onDone = { if (ready) submit() }),
            )
        } else {
            OutlinedTextField(
                username,
                { username = it },
                Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text("Nom du compte") },
                leadingIcon = { Icon(Lucide.User, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = FieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
            )
            OutlinedTextField(
                password,
                { password = it },
                Modifier.fillMaxWidth(),
                label = { Text("Mot de passe") },
                leadingIcon = { Icon(Lucide.KeyRound, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = FieldColors(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (ready) submit() }),
            )
        }
        OutlinedTextField(
            device,
            { device = it.take(80) },
            Modifier.fillMaxWidth(),
            label = { Text("Nom de cet appareil") },
            leadingIcon = { Icon(Lucide.Smartphone, null) },
            supportingText = { Text("Affiché à côté de vos sauvegardes et dans la liste des appareils.") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = FieldColors(),
        )
        error?.let { MessageBanner(it, MessageKind.ERROR, Modifier.fillMaxWidth()) }
        PrimaryButton("Relier cet appareil", { submit() }, Modifier.fillMaxWidth(), loading = busy, enabled = ready, icon = Lucide.Link)
    }
    AutoFocus(focus)
}

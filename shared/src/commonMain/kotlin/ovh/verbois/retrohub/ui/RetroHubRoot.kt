package ovh.verbois.retrohub.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import ovh.verbois.retrohub.AppContainer
import ovh.verbois.retrohub.account.AuthState
import ovh.verbois.retrohub.api.FEATURE_LIBRARY_EVENTS
import ovh.verbois.retrohub.ui.components.AnimatedBackground
import ovh.verbois.retrohub.ui.components.LocalServer
import ovh.verbois.retrohub.ui.components.PinDialog
import ovh.verbois.retrohub.ui.components.ProvideHaptics
import ovh.verbois.retrohub.ui.components.trackFocus
import ovh.verbois.retrohub.ui.console.kit.Hint
import ovh.verbois.retrohub.ui.console.kit.Pad
import ovh.verbois.retrohub.ui.console.kit.StandaloneHints
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.onboarding.Onboarding
import ovh.verbois.retrohub.ui.onboarding.SetupWizard
import ovh.verbois.retrohub.ui.theme.RetroHubTheme
import ovh.verbois.retrohub.ui.theme.consoleColor

/** Point d’entrée de l’interface partagée (Android aujourd’hui, iOS demain). */
@Composable
fun RetroHubRoot(container: AppContainer, onExit: () -> Unit = {}, onController: (UiController) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val controller = remember { UiController(container, scope).also(onController) }
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { container.artworkClient })) }
            .diskCache { DiskCache.Builder().directory(container.paths.covers).maxSizeBytes(512L * 1024 * 1024).build() }
            .crossfade(true)
            .build()
    }
    val auth by container.account.auth.collectAsState()
    val settings by container.settings.settings.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val gamepad by container.platform.gamepadConnected.collectAsState()
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(gamepad, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (gamepad) inputMode.requestInputMode(InputMode.Keyboard)
        }
    }
    val activeAccount = (auth as? AuthState.SignedIn)?.account
    val libraryVisible = settings.setupDone && when (controller.nav.contentRoute) {
        Route.Home, is Route.Game, is Route.Library, is Route.Folder, Route.Favorites, Route.Downloads,
        Route.Catalogue, is Route.CatalogueGame, Route.Search,
        -> true

        else -> false
    }
    LaunchedEffect(activeAccount?.server, activeAccount?.userId, activeAccount?.deviceId, libraryVisible, lifecycle) {
        if (activeAccount != null && libraryVisible) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                combine(container.platform.online, container.serverStatus) { online, status ->
                    online to (status?.features?.contains(FEATURE_LIBRARY_EVENTS) == true)
                }.distinctUntilChanged().collectLatest { (online, events) ->
                    if (online) container.foregroundLibrary.run(useEvents = events)
                }
            }
        }
    }
    RetroHubTheme(settings.theme, reducedMotion = settings.reduceMotion || container.platform.reducedMotion) {
        val sound = if (settings.navigationSounds) ({ container.platform.playClick() }) else null
        val density = LocalDensity.current
        ProvideHaptics(settings.haptics, { container.platform.vibrate() }, sound) {
            CompositionLocalProvider(
                ovh.verbois.retrohub.ui.components.LocalReturnHome provides { controller.returnHome() },
                LocalServer provides (auth as? AuthState.SignedIn)?.account?.server,
                LocalDensity provides Density(density.density, density.fontScale * settings.textSize.scale),
            ) {
                BoxWithConstraints(Modifier.fillMaxSize().trackFocus()) {
                    val portrait = maxHeight > maxWidth
                    LaunchedEffect(portrait) { controller.portrait = portrait }
                    AnimatedBackground(
                        controller.backdrop.url,
                        controller.backdrop.console,
                        controller.backdrop.console?.let { consoleColor(it) } ?: ovh.verbois.retrohub.ui.theme.Palette.SkyTop,
                        animated = settings.animatedBackground && !settings.reduceMotion && !container.platform.reducedMotion,
                        reducedMotion = settings.reduceMotion || container.platform.reducedMotion,
                        paused = controller.homeMoving,
                    )
                    InterfaceStyle(portrait) {
                        if (controller.exitRequested) {
                            ovh.verbois.retrohub.ui.components.ConfirmDialog(
                                "Quitter RetroHub ?",
                                "Votre progression est conservée.",
                                "Quitter",
                                onConfirm = {
                                    controller.exitRequested = false
                                    onExit()
                                },
                                onDismiss = { controller.exitRequested = false },
                                focusDismiss = true,
                            )
                        }
                        when (val state = auth) {
                            AuthState.Loading -> Box(Modifier.fillMaxSize())

                            is AuthState.SignedOut -> StandaloneHints(!portrait, listOf(Hint(Pad.A, "Choisir"), Hint(Pad.B, "Retour"))) {
                                Onboarding(container, state)
                            }

                            is AuthState.SignedIn ->
                                if (!settings.setupDone) {
                                    StandaloneHints(!portrait, listOf(Hint(Pad.A, "Choisir"), Hint(Pad.START, "Continuer"), Hint(Pad.B, "Retour"))) {
                                        SetupWizard(container, state.account.username)
                                    }
                                } else {
                                    LaunchedEffect(Unit) {
                                        // La bibliothèque locale se charge vite ; on attend qu’elle soit là.
                                        container.library.games.first { it.isNotEmpty() }
                                        controller.autoStart()
                                    }
                                    AppShell(controller, state.account) { route -> AppScreen(controller, route) }
                                    controller.pinRequest?.let { (reason, action) ->
                                        PinDialog(settings.pinHash, reason, onUnlocked = {
                                            controller.pinRequest = null
                                            action()
                                        }, onDismiss = { controller.pinRequest = null })
                                    }
                                }
                        }
                        ovh.verbois.retrohub.ui.components.ReportDialogs(container)
                    }
                }
            }
        }
    }
}

package ovh.verbois.retrohub

import androidx.compose.ui.window.ComposeUIViewController
import ovh.verbois.retrohub.ui.RetroHubRoot

/**
 * Point d’entrée iOS : l’interface console partagée dans un UIViewController.
 * La plateforme iOS (Trousseau, frontend libretro, BGTaskScheduler) est décrite dans docs/ios.md.
 */
@Suppress("FunctionName", "unused")
fun MainViewController(container: AppContainer) = ComposeUIViewController { RetroHubRoot(container) }

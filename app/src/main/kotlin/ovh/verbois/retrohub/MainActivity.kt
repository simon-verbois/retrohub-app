package ovh.verbois.retrohub

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.account.AuthState
import ovh.verbois.retrohub.ui.RetroHubRoot
import ovh.verbois.retrohub.ui.UiController

class MainActivity : ComponentActivity() {
    private var controller: UiController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        immersive()
        val container = (application as RetroHubApp).container
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (controller?.back() != true) moveTaskToBack(true)
                }
            },
        )
        setContent { RetroHubRoot(container) { controller = it } }
        // Notifications (téléchargements, conflits) : demandées une fois la configuration faite.
        lifecycleScope.launch {
            container.settings.settings.first { it.setupDone }
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    /**
     * Les boutons « système » de la manette (B, Y, LB, RB, Start, Select) agissent même quand
     * aucun élément n’a encore le focus (mode tactile) ; A et la croix restent à Compose.
     */
    // Faux positif de lint : super.dispatchKeyEvent est l’API publique d’Activity.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        controller?.keyCapture?.let { capture ->
            if (KeyEvent.isGamepadButton(event.keyCode) ||
                event.keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT
            ) {
                if (event.action == KeyEvent.ACTION_UP) capture(event.keyCode)
                return true
            }
        }
        val button =
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_B -> UiController.Button.B
                KeyEvent.KEYCODE_BUTTON_Y -> UiController.Button.Y
                KeyEvent.KEYCODE_BUTTON_L1 -> UiController.Button.LB
                KeyEvent.KEYCODE_BUTTON_R1 -> UiController.Button.RB
                KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> UiController.Button.START
                KeyEvent.KEYCODE_BUTTON_SELECT -> UiController.Button.SELECT
                else -> null
            }
        val ui = controller
        if (button != null && ui != null && ui.container.account.auth.value is AuthState.SignedIn &&
            ui.container.settings.settings.value.setupDone
        ) {
            if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) {
                if (!ui.onButton(button)) moveTaskToBack(true)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        immersive()
        // Retour au premier plan : un 304 coûte peu, la bibliothèque reste à jour.
        val container = (application as RetroHubApp).container
        lifecycleScope.launch {
            container.start()
            if (container.account.auth.value is AuthState.SignedIn) container.platform.scheduleSync()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    /** Plein écran immersif, comme une console : les barres système reviennent d’un glissement. */
    private fun immersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

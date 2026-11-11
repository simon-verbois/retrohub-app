package ovh.verbois.retrohub.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.AppContainer
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.game.LaunchCheck
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.nav.Navigator
import ovh.verbois.retrohub.ui.nav.Route

/** Fond de l’écran courant ; [hero] montre l’illustration en grand (accueil façon console). */
data class Backdrop(val url: String?, val console: String?, val hero: Boolean = false)

/** État partagé de l’interface : navigation, fond, messages et lancement des jeux. */
class UiController(val container: AppContainer, private val scope: CoroutineScope) {
    val nav = Navigator()
    var backdrop by mutableStateOf(Backdrop(null, null))
    var toast by mutableStateOf<String?>(null)
        private set
    var launching by mutableStateOf<String?>(null)
        private set
    var searching by mutableStateOf(false)
    var menuOpen by mutableStateOf(false)
    var portrait by mutableStateOf(false)

    /** Demande du code parental en cours : la raison affichée et ce qu’on fera une fois déverrouillé. */
    var pinRequest by mutableStateOf<Pair<String, () -> Unit>?>(null)

    /** Réglages déverrouillés jusqu’à la fermeture de l’application. */
    var settingsUnlocked by mutableStateOf(false)

    /** Capture de la prochaine touche de manette (page « Boutons de la manette ») : code natif. */
    var keyCapture by mutableStateOf<((Int) -> Unit)?>(null)

    /** Jeu choisi sur l’accueil : la bibliothèque et la recherche y renvoient (plus de page de fiche). */
    var homeGame by mutableStateOf<String?>(null)

    /** Menu du jeu choisi, qui descend de son titre (appui long ou bouton □ de la manette). */
    var gameMenu by mutableStateOf(false)

    fun showGame(id: String) {
        homeGame = id
        searching = false
        nav.section(ovh.verbois.retrohub.ui.nav.Section.HOME)
    }
    private var autoStarted = false
    private var toastJob: Job? = null

    fun show(message: String) {
        toast = message
        toastJob?.cancel()
        toastJob =
            scope.launch {
                delay(4200)
                toast = null
            }
    }

    /** Boutons « système » de la manette, valables partout dans l’interface. */
    enum class Button { B, Y, LB, RB, START, SELECT }

    fun onButton(button: Button): Boolean = when (button) {
        Button.B -> back()

        Button.Y -> {
            searching = true
            true
        }

        Button.LB -> {
            nav.cycleSection(-1)
            true
        }

        Button.RB -> {
            nav.cycleSection(1)
            true
        }

        Button.START -> {
            menuOpen = !menuOpen
            true
        }

        Button.SELECT -> {
            nav.go(ovh.verbois.retrohub.ui.nav.Route.Settings())
            true
        }
    }

    fun back(): Boolean = when {
        searching -> {
            searching = false
            true
        }

        gameMenu -> {
            gameMenu = false
            true
        }

        menuOpen -> {
            menuOpen = false
            true
        }

        else -> nav.back()
    }

    /** Jouer : vérifie tout ce qui peut l’être, corrige ce qui peut l’être, puis lance. */
    fun play(game: Game, fresh: Boolean = false) {
        if (launching != null) return
        val settings = container.settings.settings.value
        if (settings.pinHash.isNotEmpty() && game.playGameId in settings.lockedGames && pinRequest == null) {
            pinRequest = "Ce jeu est protégé par le code parental." to { launch(game, fresh) }
            return
        }
        launch(game, fresh)
    }

    /** Au démarrage, si le joueur l’a demandé : relance le dernier jeu joué, s’il est sur le téléphone. */
    fun autoStart() {
        if (autoStarted) return
        autoStarted = true
        if (!container.settings.settings.value.startOnLastGame) return
        val last = container.library.games.value.filter { it.downloaded && it.lastPlayed != null }.maxByOrNull { it.lastPlayed!! } ?: return
        play(last)
    }

    private fun launch(game: Game, fresh: Boolean) {
        if (launching != null) return
        launching = game.id
        scope.launch {
            try {
                val check =
                    container.launcher.prepare(
                        game,
                        container.settings.settings.value,
                        online = container.platform.online.value,
                        portrait = portrait,
                        fresh = fresh,
                    )
                when (check) {
                    is LaunchCheck.Ready -> container.platform.launchGame(check.plan)

                    LaunchCheck.NeedsDownload -> {
                        container.downloads.start(game)
                        show("Téléchargement du jeu…")
                        showGame(game.id)
                    }

                    LaunchCheck.NeedsProgressionChoice -> nav.go(Route.Progression(game.id))

                    is LaunchCheck.Conflicts -> nav.go(Route.Conflict(game.playGameId))

                    is LaunchCheck.Blocked -> show(check.message)
                }
            } catch (error: ApiException) {
                show(error.message ?: "Lancement impossible. Réessayez.")
            } catch (error: Exception) {
                show("Lancement impossible. Réessayez.")
            } finally {
                launching = null
            }
        }
    }

    fun syncNow() {
        scope.launch {
            container.sync.syncNow()
            container.sync.status.value.message?.let { show(it) }
        }
    }

    fun toggleFavorite(game: Game) {
        scope.launch {
            try {
                container.library.setFavorite(game, !game.favorite)
            } catch (error: ApiException.Offline) {
                show("Les favoris se modifient avec une connexion au serveur.")
            } catch (error: ApiException) {
                show(error.message ?: "Impossible de modifier les favoris.")
            }
        }
    }
}

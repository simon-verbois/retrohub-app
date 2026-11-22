package ovh.verbois.retrohub.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.AppContainer
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.game.LaunchCheck
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.home.*
import ovh.verbois.retrohub.ui.nav.Navigator
import ovh.verbois.retrohub.ui.nav.Route

/** Artwork and console palette behind the current screen. */
data class Backdrop(val url: String?, val console: String?)

/** État partagé de l’interface : navigation, fond, messages et lancement des jeux. */
class UiController(val container: AppContainer, private val scope: CoroutineScope) {
    val nav = Navigator()
    val home: StateFlow<HomeLibrary> = combine(
        container.library.games,
        container.settings.settings.map { HomePreferences(it) }.distinctUntilChanged(),
    ) { games, preferences -> homeLibrary(games, preferences) }
        .flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, HomeLibrary.Empty)

    var homeMoving by mutableStateOf(false)
        private set
    private var homeMotionJob: Job? = null

    private fun homeMoved() {
        homeMoving = true
        homeMotionJob?.cancel()
        homeMotionJob = scope.launch {
            delay(180)
            homeMoving = false
        }
    }

    fun homeSelection(model: HomeLibrary = home.value): HomeSelection {
        val column = model.byColumn[homeColumn]
            ?: model.byId[homeGame]?.let { model.byColumn["console:${it.console}"] }
            ?: model.byColumn.getValue("favorites")
        return HomeSelection(column, model.columnIndices.getValue(column.key), column.indices[homeGame] ?: 0)
    }

    fun moveHomeGame(step: Int) {
        val selection = homeSelection()
        selection.column.games.getOrNull((selection.gameIndex + step).coerceIn(0, (selection.column.games.size - 1).coerceAtLeast(0)))?.let {
            selectHomeGame(it.id)
        }
    }

    fun selectHomeGame(id: String) {
        if (homeGame == id) return
        homeGame = id
        homeMoved()
    }

    fun activateHomeGame() {
        homeSelection().game?.let { gameAction(this, it, container.downloads.states.value[it.playGameId]).run() }
    }
    var backdrop by mutableStateOf(Backdrop(null, null))
    var toast by mutableStateOf<String?>(null)
        private set
    var launching by mutableStateOf<String?>(null)
        private set
    var searching: Boolean
        get() = nav.current == Route.Search
        set(value) {
            if (value) {
                gameMenu = false
                nav.section(ovh.verbois.retrohub.ui.nav.Section.SEARCH)
            } else if (searching) {
                nav.back()
            }
        }
    var menuOpen: Boolean
        get() = nav.current == Route.User
        set(value) {
            if (value) {
                gameMenu = false
                nav.section(ovh.verbois.retrohub.ui.nav.Section.USER)
            } else if (menuOpen) {
                nav.back()
            }
        }
    var exitRequested by mutableStateOf(false)
    var launchIssue by mutableStateOf<Route?>(null)
    var portrait by mutableStateOf(false)

    /** Demande du code parental en cours : la raison affichée et ce qu’on fera une fois déverrouillé. */
    var pinRequest by mutableStateOf<Pair<String, () -> Unit>?>(null)

    /** Réglages déverrouillés jusqu’à la fermeture de l’application. */
    var settingsUnlocked by mutableStateOf(false)

    /** Capture de la prochaine touche de manette (page « Boutons de la manette ») : code natif. */
    var keyCapture by mutableStateOf<((Int) -> Unit)?>(null)

    /** Jeu choisi sur l’accueil : la bibliothèque et la recherche y renvoient (plus de page de fiche). */
    var homeGame by mutableStateOf<String?>(null)

    var homeCategoryLane by mutableStateOf(false)

    var homeColumn by mutableStateOf<String?>(null)
    private val homeSelections = mutableMapOf<String, String>()

    fun selectHomeColumn(key: String) {
        if (key == homeColumn) return
        homeMoved()
        homeColumn?.let { column -> homeGame?.let { homeSelections[column] = it } }
        homeCategoryLane = false
        homeColumn = key
        val column = home.value.byColumn[key]
        homeGame = homeSelections[key]?.takeIf { column?.indices?.containsKey(it) == true } ?: column?.games?.firstOrNull()?.id
        gameMenu = false
    }

    fun cycleHomeColumn(step: Int) {
        val columns = home.value.columns
        val index = columns.indexOfFirst { it.key == (homeColumn ?: "favorites") }.coerceAtLeast(0)
        columns.getOrNull((index + step).coerceIn(0, columns.lastIndex))?.let { selectHomeColumn(it.key) }
    }

    /** Menu du jeu choisi, qui descend de son titre (appui long ou bouton □ de la manette). */
    var gameMenu by mutableStateOf(false)
    fun returnHome() {
        autoStarted = true
        gameMenu = false
        exitRequested = false
        downloadRequest = null
        launchIssue = null
        pinRequest = null
        keyCapture = null
        nav.section(ovh.verbois.retrohub.ui.nav.Section.HOME)
    }

    /** An explicit notification action takes priority over automatic last-game launch. */
    fun reviewSaveConflicts() {
        if (launchIssue == Route.Conflict() || nav.contentRoute == Route.Conflict()) return
        returnHome()
        launchIssue = Route.Conflict()
    }
    var downloadRequest by mutableStateOf<Game?>(null)
        private set

    fun requestDownload(game: Game) {
        gameMenu = false
        downloadRequest = game
    }

    fun dismissDownload() {
        downloadRequest = null
    }

    fun confirmDownload() {
        val game = downloadRequest ?: return
        downloadRequest = null
        container.settings.update { it.copy(homeActivity = it.homeActivity + (game.id to container.clock.now().toString())) }
        container.downloads.start(game)
    }

    fun deleteDownload(game: Game) {
        scope.launch {
            container.downloads.delete(game.playGameId)
            show("« ${game.title} » a été supprimé de cet appareil. Vos sauvegardes sont conservées.")
        }
    }

    fun showGame(id: String) {
        homeCategoryLane = false
        if (!portrait) {
            val game = home.value.byId[id]
            val column = home.value.byColumn[homeColumn]
            if (column?.indices?.containsKey(id) != true && game != null) selectHomeColumn("console:${game.console}")
        }
        selectHomeGame(id)
        searching = false
        if (portrait) nav.go(Route.Game(id)) else nav.section(ovh.verbois.retrohub.ui.nav.Section.HOME)
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

    fun onButton(button: Button): Boolean = when {
        exitRequested || downloadRequest != null || pinRequest != null || launchIssue != null -> {
            if (button == Button.B) back()
            true
        }

        else -> handleButton(button)
    }

    private fun handleButton(button: Button): Boolean = when (button) {
        Button.B -> back()

        Button.Y -> {
            searching = true
            true
        }

        Button.LB -> {
            gameMenu = false
            if (!portrait && nav.contentRoute == Route.Home && !searching && !menuOpen) {
                cycleHomeColumn(-1)
            } else if (portrait) {
                nav.cycleSection(-1)
            }
            true
        }

        Button.RB -> {
            gameMenu = false
            if (!portrait && nav.contentRoute == Route.Home && !searching && !menuOpen) {
                cycleHomeColumn(1)
            } else if (portrait) {
                nav.cycleSection(1)
            }
            true
        }

        Button.START -> {
            if (!portrait && nav.contentRoute == Route.Home && !menuOpen && !searching && homeGame != null) gameMenu = true else menuOpen = !menuOpen
            true
        }

        Button.SELECT -> {
            if (!portrait) {
                if (nav.contentRoute == Route.Home && !menuOpen && !searching && homeGame != null) gameMenu = true else menuOpen = !menuOpen
            } else {
                nav.go(ovh.verbois.retrohub.ui.nav.Route.Settings())
            }
            true
        }
    }

    fun back(): Boolean = when {
        exitRequested -> {
            exitRequested = false
            true
        }

        pinRequest != null -> {
            pinRequest = null
            true
        }

        launchIssue != null -> {
            launchIssue = null
            true
        }

        downloadRequest != null -> {
            dismissDownload()
            true
        }

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

        else -> {
            if (!nav.back()) exitRequested = true
            true
        }
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
        if (container.platform.reportCapture?.reports?.value?.any { it.prompt && (it.owner == null || it.owner == container.reportOwner()) } == true) return
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
                        showGame(game.id)
                        requestDownload(game)
                    }

                    LaunchCheck.NeedsProgressionChoice -> launchIssue = Route.Progression(game.id)

                    is LaunchCheck.Conflicts -> launchIssue = Route.Conflict(check.gameId ?: game.playGameId)

                    is LaunchCheck.Blocked -> show(check.message)
                }
            } catch (error: ApiException) {
                show(error.message ?: "Lancement impossible. Réessayez.")
            } catch (error: CancellationException) {
                throw error
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

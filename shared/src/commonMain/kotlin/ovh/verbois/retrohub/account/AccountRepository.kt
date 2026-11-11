package ovh.verbois.retrohub.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import ovh.verbois.retrohub.api.AccountAvatar
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.ApiSession
import ovh.verbois.retrohub.api.PairRequest
import ovh.verbois.retrohub.api.RetroHubApi
import ovh.verbois.retrohub.api.ServerAddress
import ovh.verbois.retrohub.api.ServerStatus
import ovh.verbois.retrohub.api.TokenRequest
import ovh.verbois.retrohub.api.TokenResponse
import ovh.verbois.retrohub.core.Platform
import ovh.verbois.retrohub.data.KeyValueStore

/** Ce que l’application garde du compte relié (le jeton, lui, est dans le coffre de la plateforme). */
@Serializable
data class Account(
    val server: String,
    val serverName: String = "RetroHub",
    val serverVersion: String = "",
    val userId: String,
    val username: String,
    val deviceId: String,
    val deviceName: String,
    val avatar: AccountAvatar? = null,
)

sealed interface AuthState {
    data object Loading : AuthState

    /** Aucun compte relié, ou jeton oublié après un 401 ([message] explique pourquoi). */
    data class SignedOut(val server: String? = null, val message: String? = null) : AuthState

    data class SignedIn(val account: Account) : AuthState
}

class AccountRepository(private val platform: Platform, private val store: KeyValueStore) : ApiSession {
    private val accountLock = Mutex()
    private val state = MutableStateFlow<AuthState>(AuthState.Loading)
    val auth: StateFlow<AuthState> = state.asStateFlow()

    /** Branché après la construction de l’API (dépendance circulaire évitée). */
    lateinit var api: RetroHubApi

    private var cachedToken: String? = null
    private var pendingServer: String? = null

    override val server: String?
        get() = (state.value as? AuthState.SignedIn)?.account?.server ?: pendingServer

    override val token: String?
        get() = cachedToken

    val account: Account? get() = (state.value as? AuthState.SignedIn)?.account

    suspend fun load() = accountLock.withLock {
        val account = store.get(KEY, Account.serializer())
        cachedToken = platform.tokenVault.read()
        state.value =
            if (account != null && cachedToken != null) {
                AuthState.SignedIn(account)
            } else {
                AuthState.SignedOut(server = account?.server ?: store.string(LAST_SERVER))
            }
    }

    /** Vérifie l’adresse avec /native/status et la retient pour la connexion. */
    suspend fun checkServer(input: String): ServerStatus {
        val address =
            ServerAddress.normalize(input) ?: throw ApiException.UnsupportedServer("Adresse invalide. Exemple : retrohub.mondomaine.fr")
        val status = api.status(address)
        pendingServer = address
        store.putString(LAST_SERVER, address)
        lastStatus = status
        return status
    }

    var lastStatus: ServerStatus? = null
        private set

    suspend fun signInWithPassword(username: String, password: String, deviceName: String): Account {
        val address = pendingServer ?: error("Serveur non vérifié")
        return finish(
            address,
            api.login(
                address,
                TokenRequest(
                    username.trim(),
                    password,
                    deviceName.ifBlank {
                        platform.defaultDeviceName
                    },
                    platform.name,
                ),
            ),
        )
    }

    suspend fun signInWithCode(code: String, deviceName: String): Account {
        val address = pendingServer ?: error("Serveur non vérifié")
        return finish(
            address,
            api.pair(
                address,
                PairRequest(
                    code.trim(),
                    deviceName.ifBlank {
                        platform.defaultDeviceName
                    },
                    platform.name,
                ),
            ),
        )
    }

    private suspend fun finish(address: String, response: TokenResponse): Account = accountLock.withLock {
        val previous = store.get(KEY, Account.serializer())
        val account =
            Account(
                server = address,
                serverName = lastStatus?.name ?: "RetroHub",
                serverVersion = lastStatus?.serverVersion ?: "",
                userId = response.user.id,
                username = response.user.username,
                deviceId = response.device.id,
                deviceName = response.device.name,
                avatar = response.user.avatar,
            )
        platform.tokenVault.write(response.token)
        cachedToken = response.token
        store.put(KEY, Account.serializer(), account)
        accountChanged = previous != null && (previous.userId != account.userId || previous.server != account.server)
        state.value = AuthState.SignedIn(account)
        account
    }

    /** Nouveau nom de cet appareil, déjà accepté par le serveur. */
    suspend fun renamed(name: String) = accountLock.withLock {
        val current = account ?: return@withLock
        val updated = current.copy(deviceName = name)
        store.put(KEY, Account.serializer(), updated)
        state.value = AuthState.SignedIn(updated)
    }

    /** Refresh the server-owned profile only when the advertised capability is available. */
    suspend fun refreshAvatar(supported: Boolean) {
        if (!supported) return
        val owner = account ?: return
        acceptAvatar(owner, api.accountAvatar().avatar)
    }

    suspend fun chooseAvatar(avatarId: String?, supported: Boolean, owner: Account) {
        if (!supported) throw ApiException.UnsupportedServer("Mettez le serveur à jour pour choisir une photo de profil.")
        if (!sameAccount(account, owner)) throw ApiException.Unauthorized("Rouvrez les réglages de votre compte.")
        val chosen = api.updateAvatar(avatarId)
        acceptAvatar(owner, chosen.avatar)
    }

    private suspend fun acceptAvatar(owner: Account, avatar: AccountAvatar?) = accountLock.withLock {
        val current = account ?: return@withLock
        if (!sameAccount(current, owner) || current.avatar == avatar) return@withLock
        val updated = current.copy(avatar = avatar)
        store.put(KEY, Account.serializer(), updated)
        state.value = AuthState.SignedIn(updated)
    }

    private fun sameAccount(current: Account?, owner: Account): Boolean =
        current != null && current.server == owner.server && current.userId == owner.userId && current.deviceId == owner.deviceId

    /** Vrai si le compte relié n’est plus celui des données locales (elles doivent être vidées). */
    var accountChanged: Boolean = false
        private set

    fun acknowledgeAccountChange() {
        accountChanged = false
    }

    /** Déconnexion volontaire : le jeton est révoqué si possible ; les sauvegardes locales restent. */
    suspend fun signOut() {
        runCatching { api.revokeOwnToken() }
        forget(null)
    }

    override suspend fun onUnauthorized(message: String) {
        forget(message)
    }

    private suspend fun forget(message: String?) = accountLock.withLock {
        val server = server
        platform.tokenVault.clear()
        cachedToken = null
        pendingServer = server
        state.value = AuthState.SignedOut(server = server, message = message)
    }

    private companion object {
        const val KEY = "account"
        const val LAST_SERVER = "last_server"
    }
}

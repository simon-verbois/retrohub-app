package ovh.verbois.retrohub.account

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import ovh.verbois.retrohub.api.AccountAvatar
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.RetroHubApi
import ovh.verbois.retrohub.api.apiHttpClient
import ovh.verbois.retrohub.core.DeviceClass
import ovh.verbois.retrohub.core.Platform
import ovh.verbois.retrohub.core.TokenVault
import ovh.verbois.retrohub.data.KeyValueDao
import ovh.verbois.retrohub.data.KeyValueEntity
import ovh.verbois.retrohub.data.KeyValueStore
import ovh.verbois.retrohub.game.LaunchPlan
import ovh.verbois.retrohub.json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AccountAvatarTest {
    private val server = MockWebServer().apply { start() }
    private val client = apiHttpClient()
    private val platform = AvatarPlatform()
    private val store = KeyValueStore(AvatarValues())
    private val repo = AccountRepository(platform, store).also { it.api = RetroHubApi(client, it) }
    private val oldAvatar = AccountAvatar("old", "Pixel 1", "/api/v1/account/avatars/old.png")
    private val owner =
        Account(server.url("/").toString().trimEnd('/'), userId = "u", username = "Camille", deviceId = "d", deviceName = "Phone", avatar = oldAvatar)

    @AfterTest
    fun close() {
        client.close()
        server.close()
    }

    private suspend fun signedIn(account: Account = owner) {
        store.put("account", Account.serializer(), account)
        platform.tokenVault.write("device-token")
        repo.load()
    }

    @Test
    fun confirmedChoiceUpdatesTheHeaderStateAndSurvivesReload() = runTest {
        signedIn()
        server.enqueue(json("""{"avatar":{"id":"new","name":"Pixel 2","url":"/api/v1/account/avatars/new.png"}}"""))
        repo.chooseAvatar("new", supported = true, owner = owner)
        assertEquals("new", repo.account?.avatar?.id)
        assertEquals("Phone", repo.account?.deviceName)
        repo.load()
        assertEquals("new", repo.account?.avatar?.id)
    }

    @Test
    fun aRejectedChoiceKeepsThePreviousAvatarInMemoryAndOnDisk() = runTest {
        signedIn()
        server.enqueue(json("""{"detail":"Avatar inconnu"}""", 404))
        assertFailsWith<ApiException.Http> { repo.chooseAvatar("missing", supported = true, owner = owner) }
        assertEquals(oldAvatar, repo.account?.avatar)
        repo.load()
        assertEquals(oldAvatar, repo.account?.avatar)
    }

    @Test
    fun resetAndWebChangesAreReflectedInTheCachedProfile() = runTest {
        signedIn()
        server.enqueue(json("""{"avatar":{"id":"default","name":"Pixel 4","url":"/api/v1/account/avatars/default.png"}}"""))
        repo.chooseAvatar(null, supported = true, owner = owner)
        assertEquals("default", repo.account?.avatar?.id)
        server.enqueue(json("""{"avatar":{"id":"web","name":"Pixel 3","url":"/api/v1/account/avatars/web.png"}}"""))
        repo.refreshAvatar(supported = true)
        assertEquals("web", repo.account?.avatar?.id)
    }

    @Test
    fun olderServersAreNotCalledAndAnOldPickerCannotModifyAnotherAccount() = runTest {
        signedIn()
        repo.refreshAvatar(supported = false)
        assertFailsWith<ApiException.UnsupportedServer> { repo.chooseAvatar("new", supported = false, owner = owner) }
        signedIn(owner.copy(userId = "other", avatar = null))
        assertFailsWith<ApiException.Unauthorized> { repo.chooseAvatar("new", supported = true, owner = owner) }
        assertNull(repo.account?.avatar)
        assertEquals(0, server.requestCount)
    }
}

private class AvatarValues : KeyValueDao {
    private val values = MutableStateFlow<Map<String, String>>(emptyMap())
    override suspend fun get(key: String) = values.value[key]
    override fun observe(key: String): Flow<String?> = values.map { it[key] }
    override suspend fun put(entry: KeyValueEntity) {
        values.value += entry.key to entry.value
    }
    override suspend fun remove(key: String) {
        values.value -= key
    }
}

private class AvatarPlatform : Platform {
    override val name = "test"
    override val appVersion = "0.1.0"
    override val defaultDeviceName = "Phone"
    override val fs = FakeFileSystem()
    override val filesDir = "/app".toPath()
    override val tokenVault = object : TokenVault {
        private var token: String? = null
        override fun read() = token
        override fun write(token: String) {
            this.token = token
        }
        override fun clear() {
            token = null
        }
    }
    override val online = MutableStateFlow(true)
    override val gamepadConnected = MutableStateFlow(false)
    override val deviceClass = DeviceClass.CAPABLE
    override val reducedMotion = false
    override fun scheduleSync(delaySeconds: Long) = Unit
    override fun availableCores(): Set<String> = emptySet()
    override fun launchGame(plan: LaunchPlan) = Unit
    override fun freeBytes() = 0L
    override fun meteredNetwork() = false
    override fun openUrl(url: String) = Unit
    override fun vibrate(milliseconds: Long) = Unit
    override fun rumble(strength: Float) = Unit
    override fun schedulePeriodicSync(hours: Int) = Unit
    override fun notify(id: Int, title: String, text: String) = Unit
    override fun startBackgroundDownload(playGameId: String, title: String) = Unit
    override fun sdCardRoot() = null
    override fun playClick() = Unit
    override fun batteryLow() = false
    override fun shareLogs(summary: String) = Unit
}

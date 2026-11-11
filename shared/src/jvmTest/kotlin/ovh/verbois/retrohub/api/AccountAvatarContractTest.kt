package ovh.verbois.retrohub.api

import kotlinx.coroutines.test.runTest
import ovh.verbois.retrohub.TestApi
import ovh.verbois.retrohub.account.Account
import ovh.verbois.retrohub.json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Proposed avatar contract, kept behind account_avatars until server request 8 is implemented. */
class AccountAvatarContractTest {
    private val t = TestApi()
    private val avatar = """{"id":"pixel-001","name":"Pixel 1","url":"/api/v1/account/avatars/pixel-001.png?v=abc"}"""

    @AfterTest
    fun close() = t.close()

    @Test
    fun catalogueAndProfileUseAuthenticatedAccountRoutes() = runTest {
        t.enqueue(json("""{"avatars":[$avatar],"default_avatar":$avatar}"""))
        val catalog = t.api.avatarCatalog()
        assertEquals("pixel-001", catalog.avatars.single().id)
        assertEquals("pixel-001", catalog.defaultAvatar?.id)
        val request = t.take()
        assertEquals("/api/v1/account/avatars", request.url.encodedPath)
        assertEquals("Bearer device-token", request.headers["Authorization"])
        t.enqueue(json("""{"avatar":$avatar}"""))
        assertEquals("Pixel 1", t.api.accountAvatar().avatar?.name)
        assertEquals("/api/v1/account/avatar", t.take().url.encodedPath)
    }

    @Test
    fun chooseAndResetSendExplicitAvatarIdsWithoutReplacingPreferences() = runTest {
        t.enqueue(json("""{"avatar":$avatar}"""))
        assertEquals("pixel-001", t.api.updateAvatar("pixel-001").avatar?.id)
        val choose = t.take()
        assertEquals("PUT", choose.method)
        assertEquals("/api/v1/account/avatar", choose.url.encodedPath)
        assertEquals("""{"avatar_id":"pixel-001"}""", choose.body!!.utf8())
        t.enqueue(json("""{"avatar":null}"""))
        assertNull(t.api.updateAvatar(null).avatar)
        assertEquals("""{"avatar_id":null}""", t.take().body!!.utf8())
    }

    @Test
    fun oldAccountsAndAuthResponsesStillDecodeAndNewAvatarsAreCached() {
        val old = """{"server":"https://retrohub.example","userId":"u","username":"Camille","deviceId":"d","deviceName":"Phone"}"""
        val account = ApiJson.decodeFromString(Account.serializer(), old)
        assertNull(account.avatar)
        val chosen = account.copy(avatar = ApiJson.decodeFromString(AccountAvatar.serializer(), avatar))
        assertEquals(chosen, ApiJson.decodeFromString(Account.serializer(), ApiJson.encodeToString(Account.serializer(), chosen)))
        val user = ApiJson.decodeFromString(AccountUser.serializer(), """{"id":"u","username":"Camille"}""")
        assertNull(user.avatar)
    }

    @Test
    fun imagesCannotResolveToThirdPartyServers() {
        val safe = AccountAvatar("id", "Pixel", "/api/v1/account/avatars/id.png?v=123")
        assertEquals("https://retrohub.example/api/v1/account/avatars/id.png?v=123", avatarImageUrl("https://retrohub.example", safe))
        for (url in listOf("https://other.example/photo.png", "//other.example/photo.png", "/api/v1/games/a/cover", "data:image/png;base64,x")) {
            assertNull(avatarImageUrl("https://retrohub.example", safe.copy(url = url)))
        }
    }
}

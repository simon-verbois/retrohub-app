package ovh.verbois.retrohub.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerAddressTest {
    @Test
    fun addsHttpsAndDropsPathsAndSlashes() {
        assertEquals("https://retrohub.example.org", ServerAddress.normalize(" retrohub.example.org/ "))
        assertEquals("https://retrohub.example.org", ServerAddress.normalize("https://retrohub.example.org/api/v1/native/status"))
        assertEquals("https://retrohub.example.org", ServerAddress.normalize("retrohub.example.org/library?x=1"))
        assertEquals("http://127.0.0.1:8011", ServerAddress.normalize("http://127.0.0.1:8011"))
    }

    @Test
    fun refusesWhatIsNotAnAddress() {
        assertNull(ServerAddress.normalize(""))
        assertNull(ServerAddress.normalize("ftp://x"))
        assertNull(ServerAddress.normalize("deux mots"))
    }

    @Test
    fun resolvesServerRelativeUrls() {
        assertEquals("https://h/api/v1/games/x/cover?v=1", ServerAddress.resolve("https://h/", "/api/v1/games/x/cover?v=1"))
        assertEquals("https://cdn/x.png", ServerAddress.resolve("https://h", "https://cdn/x.png"))
    }
}

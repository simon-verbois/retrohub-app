package ovh.verbois.retrohub.api

import kotlinx.coroutines.test.runTest
import ovh.verbois.retrohub.TestApi
import ovh.verbois.retrohub.json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogueContractTest {
    private val t = TestApi()

    @AfterTest fun close() = t.close()

    @Test
    fun browsingUsesTheWholeCatalogueWithEncodedFiltersAndPagination() = runTest {
        t.enqueue(json("""{"items":[{"id":"g","title":"Jeu","console":"nds","installed":false}],"total":35,"page":2,"limit":24}"""))
        val result = t.api.catalogue(
            CatalogueQuery(
                search = "A & B + été", console = "nds", language = "fr", installed = false,
                source = "source", genre = "Action", year = 2005, sort = "title", page = 2,
            ),
        )
        assertEquals(35, result.total)
        assertEquals("g", result.items.single().id)
        val request = t.take()
        assertEquals("/api/v1/games", request.url.encodedPath)
        assertEquals("Bearer device-token", request.headers["Authorization"])
        for ((key, value) in mapOf(
            "q" to "A & B + été", "console" to "nds", "language" to "fr", "installed" to "false", "page" to "2", "limit" to "24",
            "source" to "source", "genre" to "Action", "year" to "2005", "sort" to "title",
        )) {
            assertEquals(value, request.url.queryParameter(key))
        }
        assertEquals(null, request.url.queryParameter("library"))
    }

    @Test
    fun serverDownloadTracksTheJobAndSendsAnExplicitVersionAndFile() = runTest {
        t.enqueue(json("""{"id":"job","status":"queued","progress":0}"""))
        val started = t.api.downloadToServer("game", "version", "file")
        assertTrue(started.active)
        val request = t.take()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/games/game/download", request.url.encodedPath)
        assertEquals("""{"version_id":"version","file_id":"file"}""", request.body!!.utf8())
        t.enqueue(json("""{"id":"job","status":"completed","progress":100}"""))
        assertFalse(t.api.serverJob("job").active)
        assertEquals("/api/v1/jobs/job", t.take().url.encodedPath)
    }

    @Test
    fun missingSourceFilesCanBeChosenBeforeRetryingAndJobsCanBeCancelled() = runTest {
        t.enqueue(json("""[{"id":"file","name":"Version française","size":"12 Mo","languages":["fr"]}]"""))
        assertEquals("file", t.api.serverJobFiles("job").single().id)
        assertEquals("/api/v1/jobs/job/files", t.take().url.encodedPath)
        t.enqueue(json("""{"id":"job","status":"queued"}"""))
        assertTrue(t.api.retryServerDownload("job", "file").active)
        assertEquals("""{"file_id":"file"}""", t.take().body!!.utf8())
        t.enqueue(json("{}"))
        t.api.cancelServerDownload("job")
        assertEquals("/api/v1/jobs/job/cancel", t.take().url.encodedPath)
    }
}

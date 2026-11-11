package ovh.verbois.retrohub.api

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import ovh.verbois.retrohub.core.sha256

val ApiJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

/** Ce que l’API sait de la session en cours : adresse du serveur et jeton de l’appareil. */
interface ApiSession {
    val server: String?
    val token: String?

    /** Un 401 sur une route authentifiée : oublier le jeton, revenir à la connexion. */
    suspend fun onUnauthorized(message: String)
}

sealed interface LibraryResult {
    data object NotModified : LibraryResult

    data class Updated(val manifest: LibraryManifest, val etag: String?) : LibraryResult
}

data class DownloadedSave(val revision: Int, val core: String, val romSha256: String, val sha256: String)

/**
 * Client de l’API native de RetroHub. Les routes sont sous /api/v1 ; les erreurs deviennent des
 * [ApiException]. Un 503 de maintenance est réessayé sur place quand l’attente est courte, sinon
 * remonte en [ApiException.Maintenance] pour que la tâche de fond se replanifie.
 */
class RetroHubApi(
    private val http: HttpClient,
    private val session: ApiSession,
    private val wait: suspend (Long) -> Unit = { delay(it * 1000) },
    private val maxInlineWaitSeconds: Long = 30,
    private val maxMaintenanceRetries: Int = 3,
) {
    // ---- Découverte et connexion ------------------------------------------------------------

    suspend fun status(server: String): ServerStatus {
        val text =
            request(HttpMethod.Get, "$server/api/v1/native/status", authenticated = false) { it.bodyAsText() }
        val status =
            try {
                ApiJson.decodeFromString(ServerStatus.serializer(), text)
            } catch (_: SerializationException) {
                throw ApiException.UnsupportedServer("Aucun serveur RetroHub à cette adresse.")
            } catch (_: IllegalArgumentException) {
                throw ApiException.UnsupportedServer("Aucun serveur RetroHub à cette adresse.")
            }
        if (status.apiVersion != SUPPORTED_API_VERSION) {
            throw ApiException.UnsupportedServer(
                "Ce serveur RetroHub (${status.serverVersion}) n’est pas compatible avec cette version de l’application. " +
                    "Mettez à jour l’application ou le serveur.",
            )
        }
        return status
    }

    suspend fun login(server: String, body: TokenRequest): TokenResponse = decode(
        request(HttpMethod.Post, "$server/api/v1/auth/token", authenticated = false, json = encode(TokenRequest.serializer(), body)) {
            it.bodyAsText()
        },
        TokenResponse.serializer(),
    )

    suspend fun pair(server: String, body: PairRequest): TokenResponse = decode(
        request(HttpMethod.Post, "$server/api/v1/auth/pair", authenticated = false, json = encode(PairRequest.serializer(), body)) {
            it.bodyAsText()
        },
        TokenResponse.serializer(),
    )

    suspend fun revokeOwnToken() {
        request(HttpMethod.Delete, api("/auth/token")) { }
    }

    /** Consume the native event stream inside the response scope so cancellation closes it. */
    suspend fun libraryEvents(lastEventId: String?, receive: suspend (LibraryEvent) -> Unit) {
        val headers = listOfNotNull(
            HttpHeaders.Accept to "text/event-stream",
            lastEventId?.takeIf { it.length <= 256 }?.let { "Last-Event-ID" to it },
        )
        request(HttpMethod.Get, api("/native/events"), headers = headers, streaming = true) { response ->
            val type = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()
            if (response.status.value != 200 || !type.equals("text/event-stream", ignoreCase = true)) {
                throw ApiException.UnsupportedServer("Le serveur ne propose pas les mises à jour instantanées.")
            }
            readLibraryEvents(response.bodyAsChannel(), lastEventId, receive) {
                session.onUnauthorized("Cet appareil n’est plus connecté. Reliez-le de nouveau à votre compte.")
            }
        }
    }

    /** Proposed routes: callers require the account_avatars capability (server request 8). */
    suspend fun avatarCatalog(): AvatarCatalog = get("/account/avatars", AvatarCatalog.serializer())

    suspend fun accountAvatar(): AvatarSelection = get("/account/avatar", AvatarSelection.serializer())

    suspend fun updateAvatar(avatarId: String?): AvatarSelection {
        // Keep an explicit null for reset even though other API models omit nullable fields.
        val value = avatarId?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull
        val body = JsonObject(mapOf("avatar_id" to value))
        return decode(request(HttpMethod.Put, api("/account/avatar"), json = body.toString()) { it.bodyAsText() }, AvatarSelection.serializer())
    }

    suspend fun devices(): List<DeviceItem> = get("/account/devices", kotlinx.serialization.builtins.ListSerializer(DeviceItem.serializer()))

    // ---- Compte ----

    /** Préférences du compte, partagées avec le web (GET/PUT /settings, objet complet). */
    suspend fun preferences(): kotlinx.serialization.json.JsonObject =
        ApiJson.parseToJsonElement(request(HttpMethod.Get, api("/settings")) { it.bodyAsText() }) as kotlinx.serialization.json.JsonObject

    suspend fun putPreferences(preferences: kotlinx.serialization.json.JsonObject) {
        request(HttpMethod.Put, api("/settings"), json = preferences.toString()) { }
    }

    suspend fun changePassword(change: PasswordChange) {
        request(HttpMethod.Post, api("/account/password"), json = encode(PasswordChange.serializer(), change)) { }
    }

    suspend fun security(): AccountSecurity = get("/account/security", AccountSecurity.serializer())

    suspend fun revokeSession(id: String) {
        request(HttpMethod.Delete, api("/account/sessions/$id")) { }
    }

    suspend fun revokeDevice(id: String) {
        request(HttpMethod.Delete, api("/account/devices/$id")) { }
    }

    suspend fun createPairingCode(): PairingCode =
        decode(request(HttpMethod.Post, api("/account/devices/pairing"), json = "{}") { it.bodyAsText() }, PairingCode.serializer())

    /** Renommer un appareil (route demandée au serveur, voir docs/server-requests.md). */
    suspend fun renameDevice(id: String, name: String) {
        request(HttpMethod.Patch, api("/account/devices/$id"), json = encode(DeviceRename.serializer(), DeviceRename(name))) { }
    }

    // ---- Bibliothèque -------------------------------------------------------------------------

    suspend fun library(etag: String?): LibraryResult =
        request(HttpMethod.Get, api("/native/library"), headers = listOfNotNull(etag?.let { HttpHeaders.IfNoneMatch to it })) {
            if (it.status.value == 304) {
                LibraryResult.NotModified
            } else {
                LibraryResult.Updated(decode(it.bodyAsText(), LibraryManifest.serializer()), it.headers[HttpHeaders.ETag])
            }
        }

    suspend fun consoles(): List<ConsoleCapabilities> = get("/native/consoles", kotlinx.serialization.builtins.ListSerializer(ConsoleCapabilities.serializer()))

    suspend fun updateLibrary(id: String, update: LibraryUpdate) {
        request(HttpMethod.Put, api("/library/$id"), json = encode(LibraryUpdate.serializer(), update)) { }
    }

    /** Demande au serveur d’installer une version d’une source (tâche de fond côté serveur). */
    suspend fun installOnServer(gameId: String, versionId: String) {
        request(
            HttpMethod.Post,
            api("/games/$gameId/download"),
            json = encode(LibraryUpdate.serializer(), LibraryUpdate(versionId = versionId)),
        ) {
        }
    }

    // ---- Fichiers (ROM, BIOS, illustrations) --------------------------------------------------

    /**
     * Télécharge [url] (adresse du serveur, relative ou absolue) et passe la réponse à [consume].
     * [rangeStart] > 0 demande la suite d’un fichier partiel.
     */
    suspend fun <T> download(url: String, rangeStart: Long = 0, consume: suspend (status: Int, contentRange: String?, body: ByteReadChannel) -> T): T = request(
        HttpMethod.Get,
        resolve(url),
        headers = if (rangeStart > 0) listOf(HttpHeaders.Range to "bytes=$rangeStart-") else emptyList(),
        okStatuses = setOf(206),
    ) { consume(it.status.value, it.headers[HttpHeaders.ContentRange], it.bodyAsChannel()) }

    // ---- Sauvegardes --------------------------------------------------------------------------

    suspend fun saveVersions(gameId: String, kind: String, slot: Int): List<RemoteSave> =
        get("/games/$gameId/saves/$kind/$slot/versions", kotlinx.serialization.builtins.ListSerializer(RemoteSave.serializer()))

    /**
     * Télécharge une révision dans [target] et vérifie X-Content-SHA256 ; un corps abîmé est
     * retéléchargé une fois avant d’abandonner.
     */
    suspend fun readSave(gameId: String, kind: String, slot: Int, revision: Int?, fs: FileSystem, target: Path): DownloadedSave {
        repeat(2) { attempt ->
            val query = revision?.let { "?revision=$it" } ?: ""
            val result =
                request(HttpMethod.Get, api("/games/$gameId/saves/$kind/$slot$query")) { response ->
                    val partial = target.parent!!.resolve(target.name + ".part")
                    fs.sink(partial).buffer().use { sink -> copy(response.bodyAsChannel()) { b, n -> sink.write(b, 0, n) } }
                    val actual = fs.sha256(partial)
                    val expected = response.headers["X-Content-SHA256"]
                    if (expected != null && !expected.equals(actual, ignoreCase = true)) {
                        fs.delete(partial)
                        null
                    } else {
                        fs.atomicMove(partial, target)
                        DownloadedSave(
                            revision =
                            response.headers[HttpHeaders.ETag]?.trim('"', ' ')?.removePrefix("W/")?.trim('"')?.toIntOrNull() ?: revision
                                ?: 0,
                            core = response.headers["X-Save-Core"] ?: "",
                            romSha256 = response.headers["X-ROM-SHA256"] ?: "",
                            sha256 = actual,
                        )
                    }
                }
            if (result != null) return result
            if (attempt == 1) throw ApiException.Offline()
        }
        throw ApiException.Offline()
    }

    data class SaveUpload(
        val gameId: String,
        val kind: String,
        val slot: Int,
        val baseRevision: Int,
        val saveEpoch: String,
        val romSha256: String,
        val core: String,
        val playedAt: String?,
    )

    /** Envoie le fichier [source] ; renvoie la nouvelle révision, ou lève SaveConflict sur 409. */
    suspend fun putSave(upload: SaveUpload, fs: FileSystem, source: Path): RemoteSave {
        val size = fs.metadata(source).size ?: 0L
        val digest = fs.sha256(source)
        repeat(2) { attempt ->
            try {
                val text =
                    request(
                        HttpMethod.Put,
                        api("/games/${upload.gameId}/saves/${upload.kind}/${upload.slot}"),
                        headers =
                        listOfNotNull(
                            HttpHeaders.IfMatch to upload.baseRevision.toString(),
                            "X-Save-Epoch" to upload.saveEpoch,
                            "X-ROM-SHA256" to upload.romSha256,
                            "X-Emulator-Core" to upload.core,
                            upload.playedAt?.let { "X-Played-At" to it },
                            "X-Content-SHA256" to digest,
                        ),
                        body = FileContent(fs, source, size),
                    ) { it.bodyAsText() }
                return decode(text, RemoteSave.serializer())
            } catch (error: ApiException.Http) {
                // 400 : le corps reçu ne correspond pas à X-Content-SHA256 (envoi coupé) → on renvoie.
                if (error.status != 400 || attempt == 1) throw error
            }
        }
        error("unreachable")
    }

    // ---- Sessions hors ligne ------------------------------------------------------------------

    suspend fun postSessions(sessions: List<OfflineSession>): SessionResults {
        require(sessions.size <= MAX_SESSIONS_PER_BATCH)
        val text =
            request(HttpMethod.Post, api("/native/sessions"), json = encode(SessionBatch.serializer(), SessionBatch(sessions))) {
                it.bodyAsText()
            }
        return decode(text, SessionResults.serializer())
    }

    // ---- Mécanique ----------------------------------------------------------------------------

    fun resolve(url: String): String = ServerAddress.resolve(requireServer(), url)

    private fun requireServer(): String = session.server ?: throw ApiException.Unauthorized("Choisissez d’abord votre serveur RetroHub.")

    private fun api(path: String): String = requireServer().trimEnd('/') + "/api/v1" + path

    private suspend fun <T> get(path: String, serializer: kotlinx.serialization.KSerializer<T>): T =
        decode(request(HttpMethod.Get, api(path)) { it.bodyAsText() }, serializer)

    private fun <T> encode(serializer: kotlinx.serialization.KSerializer<T>, value: T): String = ApiJson.encodeToString(serializer, value)

    private fun <T> decode(text: String, serializer: kotlinx.serialization.KSerializer<T>): T = try {
        ApiJson.decodeFromString(serializer, text)
    } catch (error: SerializationException) {
        throw ApiException.Http(0, "Réponse inattendue du serveur. Mettez à jour l’application.")
    } catch (error: IllegalArgumentException) {
        throw ApiException.Http(0, "Réponse inattendue du serveur. Mettez à jour l’application.")
    }

    private suspend fun <T> request(
        method: HttpMethod,
        url: String,
        authenticated: Boolean = true,
        headers: List<Pair<String, String>> = emptyList(),
        json: String? = null,
        body: OutgoingContent? = null,
        okStatuses: Set<Int> = emptySet(),
        streaming: Boolean = false,
        handle: suspend (HttpResponse) -> T,
    ): T {
        var retries = 0
        while (true) {
            try {
                return http.prepareRequest {
                    this.method = method
                    url(url)
                    if (authenticated) {
                        val token = session.token ?: throw ApiException.Unauthorized("Reliez cet appareil à votre compte.")
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    headers.forEach { (name, value) -> header(name, value) }
                    applyBody(json, body)
                }.execute { response ->
                    val code = response.status.value
                    if (code in 200..299 || code == 304 || code in okStatuses) {
                        handle(response)
                    } else {
                        throw failure(response, authenticated)
                    }
                }
            } catch (error: ApiException.Maintenance) {
                if (streaming || retries >= maxMaintenanceRetries || error.retryAfterSeconds > maxInlineWaitSeconds) throw error
                retries++
                wait(error.retryAfterSeconds)
            } catch (error: ApiException) {
                throw error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                throw ApiException.Offline(error)
            }
        }
    }

    private fun HttpRequestBuilder.applyBody(json: String?, body: OutgoingContent?) {
        when {
            json != null -> {
                contentType(ContentType.Application.Json)
                setBody(json)
            }

            body != null -> setBody(body)
        }
    }

    private suspend fun failure(response: HttpResponse, authenticated: Boolean): ApiException {
        val code = response.status.value
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val detail = runCatching { ApiJson.parseToJsonElement(text).jsonObject["detail"] }.getOrNull()
        val message =
            when (detail) {
                is JsonPrimitive -> detail.content
                is JsonObject -> detail["message"]?.jsonPrimitive?.content
                is JsonArray -> "Requête refusée par le serveur. Mettez à jour l’application."
                else -> null
            } ?: defaultMessage(code)
        return when {
            code == 401 -> {
                if (authenticated) session.onUnauthorized(message)
                ApiException.Unauthorized(message)
            }

            code == 503 -> ApiException.Maintenance(response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull() ?: 30)

            code == 409 && detail is JsonObject && detail["current_revision"] != null ->
                ApiException.SaveConflict(detail["current_revision"]!!.jsonPrimitive.intOrNull ?: 0, message)

            else -> ApiException.Http(code, message, response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull())
        }
    }

    private fun defaultMessage(code: Int): String = when (code) {
        401 -> "Cet appareil n’est plus connecté. Reliez-le de nouveau à votre compte."
        404 -> "Introuvable sur le serveur."
        413 -> "Sauvegarde trop volumineuse pour le serveur. Elle reste sur cet appareil."
        429 -> "Trop de tentatives. Réessayez dans une minute."
        in 500..599 -> "Le serveur rencontre un problème. Réessayez plus tard."
        else -> "Le serveur a refusé la demande ($code)."
    }

    companion object {
        const val MAX_SESSIONS_PER_BATCH = 200
    }
}

/** Corps d’envoi lu depuis un fichier, sans tout charger en mémoire (états GameCube jusqu’à 128 Mo). */
private class FileContent(private val fs: FileSystem, private val path: Path, private val size: Long) : OutgoingContent.WriteChannelContent() {
    override val contentLength: Long get() = size
    override val contentType: ContentType get() = ContentType.Application.OctetStream

    override suspend fun writeTo(channel: ByteWriteChannel) {
        fs.source(path).buffer().use { source ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = source.read(buffer, 0, buffer.size)
                if (read == -1) break
                channel.writeFully(buffer, 0, read)
            }
        }
    }
}

/** Copie un canal Ktor par blocs de 64 Kio. */
suspend fun copy(channel: ByteReadChannel, write: (ByteArray, Int) -> Unit): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = channel.readAvailable(buffer, 0, buffer.size)
        if (read == -1) break
        if (read > 0) {
            write(buffer, read)
            total += read
        }
    }
    return total
}

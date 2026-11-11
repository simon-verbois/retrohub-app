package ovh.verbois.retrohub.e2e

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ovh.verbois.retrohub.api.ApiJson
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Le backend jetable du monorepo (scripts/e2e-backend.py), joint par adb reverse : il joue le rôle
 * du web (import d’une ROM, code d’appairage, sauvegarde concurrente, révocation de l’appareil).
 */
class Backend(val server: String) {
    private val http = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    private val json = "application/json".toMediaType()
    val token: String = login()

    private fun call(
        method: String,
        path: String,
        body: okhttp3.RequestBody? = null,
        headers: Map<String, String> = emptyMap(),
        auth: Boolean = true,
    ): Pair<Int, String> {
        val request =
            Request
                .Builder()
                .url("$server/api/v1$path")
                .method(method, body)
                .apply {
                    if (auth) header("Authorization", "Bearer $token")
                    headers.forEach { (k, v) -> header(k, v) }
                }.build()
        http.newCall(request).execute().use { return it.code to (it.body?.string() ?: "") }
    }

    private fun parse(text: String): JsonElement = ApiJson.parseToJsonElement(text)

    private fun login(): String {
        val body = """{"username":"$USER","password":"$PASSWORD","device_name":"Tests e2e","platform":"script"}"""
        val (code, text) = call("POST", "/auth/token", body.toRequestBody(json), auth = false)
        check(code == 201) { "Connexion au backend de test impossible ($code) : $text" }
        return parse(text).jsonObject["token"]!!.jsonPrimitive.content
    }

    /** Importe une ROM NES et attend la fin de l’installation ; renvoie l’id du jeu. */
    fun importRom(filename: String, data: ByteArray): String {
        val form =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("files", filename, data.toRequestBody("application/octet-stream".toMediaType()))
                .build()
        val (code, text) = call("POST", "/imports", form)
        check(code == 202) { "Import refusé ($code) : $text" }
        val job = parse(text).jsonObject["id"]!!.jsonPrimitive.content
        repeat(120) {
            val status = parse(call("GET", "/jobs/$job").second).jsonObject
            when (status["status"]!!.jsonPrimitive.content) {
                "completed" -> return status["result"]!!.jsonObject["game_ids"]!!.jsonArray.first().jsonPrimitive.content
                "failed" -> error("Import en échec : $status")
            }
            Thread.sleep(500)
        }
        error("Import trop long")
    }

    fun pairingCode(): String =
        parse(call("POST", "/account/devices/pairing", ByteArray(0).toRequestBody(json)).second).jsonObject["code"]!!.jsonPrimitive.content

    fun library(): JsonObject = parse(call("GET", "/native/library").second).jsonObject

    fun game(playGameId: String): JsonObject = library()["games"]!!.jsonArray.map { it.jsonObject }.first {
        it["play_game_id"]!!.jsonPrimitive.content ==
            playGameId
    }

    fun saves(gameId: String): List<JsonObject> = (parse(call("GET", "/games/$gameId/saves").second) as JsonArray).map { it.jsonObject }

    /** Une partie envoyée « depuis le web » sur un emplacement, par-dessus la révision courante. */
    fun putSave(gameId: String, kind: String, slot: Int, data: ByteArray): Int {
        val library = library()
        val epoch = library["save_epoch"]!!.jsonPrimitive.content
        val game = game(gameId)
        val rom = game["rom"]!!.jsonObject["sha256"]!!.jsonPrimitive.content
        val current = saves(gameId).firstOrNull {
            it["kind"]!!.jsonPrimitive.content == kind &&
                it["slot"]!!.jsonPrimitive.content.toInt() == slot
        }
        val revision = current?.get("revision")?.jsonPrimitive?.content?.toInt() ?: 0
        val (code, text) =
            call(
                "PUT",
                "/games/$gameId/saves/$kind/$slot",
                data.toRequestBody("application/octet-stream".toMediaType()),
                mapOf("If-Match" to "$revision", "X-Save-Epoch" to epoch, "X-ROM-SHA256" to rom, "X-Emulator-Core" to "fceumm"),
            )
        check(code == 200) { "Sauvegarde concurrente refusée ($code) : $text" }
        return parse(text).jsonObject["revision"]!!.jsonPrimitive.content.toInt()
    }

    fun devices(): List<JsonObject> = (parse(call("GET", "/account/devices").second) as JsonArray).map { it.jsonObject }

    fun revoke(deviceId: String) {
        check(call("DELETE", "/account/devices/$deviceId").first == 200)
    }

    fun postSessions(body: String): JsonObject = parse(call("POST", "/native/sessions", body.toRequestBody(json)).second).jsonObject

    companion object {
        const val USER = "e2e-admin"
        const val PASSWORD = "test-browser-password-only"

        fun sha256(data: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

        /** ROM NES homebrew minimale à en-tête iNES valide (reprise de frontend/tests/library.spec.ts). */
        fun nesRom(seed: Int = 0): ByteArray {
            val prg = ByteArray(16384) { 0xEA.toByte() }
            val code = "78d8a2ff9ae600a9008d00208d0120ee0060a93f8d0620a9008d0620a9218d0720a9008d05208d0520a9088d01204c2d80".chunked(2).map {
                it.toInt(16).toByte()
            }
            code.forEachIndexed { i, b -> prg[i] = b }
            listOf(0x00, 0x80, 0x00, 0x80, 0x00, 0x80).forEachIndexed { i, b -> prg[prg.size - 6 + i] = b.toByte() }
            prg[200] = seed.toByte()
            val header = byteArrayOf(78, 69, 83, 26, 1, 1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0)
            return header + prg + ByteArray(8192)
        }
    }
}

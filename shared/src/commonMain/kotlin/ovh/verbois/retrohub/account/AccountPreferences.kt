package ovh.verbois.retrohub.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.RetroHubApi
import ovh.verbois.retrohub.data.SettingsRepository
import ovh.verbois.retrohub.emulation.Consoles

/**
 * Préférences du compte partagées avec le web : « Privilégier les versions françaises »,
 * l’intervalle de sauvegarde automatique et l’émulateur par console (quand il existe aussi sur le
 * web). PUT /settings remplace tout l’objet : on relit, on modifie une clé, on renvoie le reste
 * intact (commandes de manette, volume et thème du web compris).
 */
class AccountPreferences(
    private val api: RetroHubApi,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    /** Cœurs du navigateur par console (GET /native/consoles), les seuls acceptés par le web. */
    private val webCores: suspend () -> Map<String, List<String>>,
) {
    /** Lecture du compte (à chaque synchronisation) : le compte fait foi pour ces réglages. */
    suspend fun pull() {
        val remote = api.preferences()
        settings.updateNow { current -> merge(current, remote) }
    }

    fun setPreferFrench(value: Boolean) {
        settings.update { it.copy(preferFrench = value) }
        push { JsonObject(it + ("prefer_french" to JsonPrimitive(value))) }
    }

    fun setAutoSave(seconds: Int) {
        settings.update { it.copy(autoSaveSeconds = seconds) }
        push { JsonObject(it + ("auto_save_seconds" to JsonPrimitive(seconds.coerceIn(15, 600)))) }
    }

    /** Émulateur choisi : partagé seulement si le web sait aussi le faire tourner. */
    fun setCore(console: String, core: String) {
        settings.update { it.withConsole(console) { c -> c.copy(core = core) } }
        scope.launch {
            val web = runCatching { webCores()[console].orEmpty() }.getOrDefault(emptyList())
            if (core !in web) return@launch
            pushNow { prefs ->
                val cores = (prefs["cores"] as? JsonObject).orEmpty() + (console to JsonPrimitive(core))
                JsonObject(prefs + ("cores" to JsonObject(cores)))
            }
        }
    }

    private fun push(change: (JsonObject) -> JsonObject) {
        scope.launch { pushNow(change) }
    }

    private suspend fun pushNow(change: (JsonObject) -> JsonObject) {
        try {
            api.putPreferences(JsonObject(change(api.preferences())))
        } catch (_: ApiException) {
            // Hors ligne ou refusé : le réglage local reste, le compte sera relu à la prochaine synchro.
        }
    }

    companion object {
        fun merge(current: ovh.verbois.retrohub.emulation.AppSettings, remote: JsonObject): ovh.verbois.retrohub.emulation.AppSettings {
            var next = current
            remote["prefer_french"]?.jsonPrimitive?.booleanOrNull?.let { next = next.copy(preferFrench = it) }
            remote["auto_save_seconds"]?.jsonPrimitive?.intOrNull?.let { next = next.copy(autoSaveSeconds = it.coerceIn(15, 600)) }
            (remote["cores"] as? JsonObject)?.forEach { (console, value) ->
                val core = (value as? JsonPrimitive)?.content ?: return@forEach
                if (Consoles[console]?.cores?.contains(core) == true) next = next.withConsole(console) { it.copy(core = core) }
            }
            return next
        }
    }
}

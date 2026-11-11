package ovh.verbois.retrohub.game

import kotlinx.serialization.builtins.ListSerializer
import ovh.verbois.retrohub.data.KeyValueStore

/** Codes de triche par jeu, gardés sur l’appareil. */
class CheatsRepository(private val store: KeyValueStore) {
    suspend fun get(playGameId: String): List<Cheat> = store.get(key(playGameId), ListSerializer(Cheat.serializer())) ?: emptyList()

    suspend fun put(playGameId: String, cheats: List<Cheat>) = store.put(key(playGameId), ListSerializer(Cheat.serializer()), cheats)

    private fun key(playGameId: String) = "cheats:$playGameId"
}

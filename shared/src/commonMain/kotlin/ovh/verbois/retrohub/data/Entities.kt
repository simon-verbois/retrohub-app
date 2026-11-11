package ovh.verbois.retrohub.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Une fiche du catalogue, gardée pour le hors ligne. [json] contient la fiche complète du serveur. */
@Entity(tableName = "games", indices = [Index("console"), Index("playGameId")])
data class GameEntity(
    @PrimaryKey val id: String,
    val playGameId: String,
    val title: String,
    val console: String,
    val favorite: Boolean,
    val lastPlayedAt: String?,
    val playedSeconds: Long,
    val position: Int,
    val json: String,
)

/** Une ROM téléchargée sur l’appareil (par play_game_id). */
@Entity(tableName = "local_roms")
data class LocalRomEntity(
    @PrimaryKey val playGameId: String,
    val gameId: String,
    val console: String,
    val title: String,
    val path: String,
    val sha256: String,
    val size: Long,
    val downloadedAt: String,
)

/**
 * Un emplacement de sauvegarde local et sa révision de base (dernière révision serveur
 * téléchargée ou envoyée). La copie a changé localement quand [localSha] ≠ [baseSha].
 * Un conflit garde la version du serveur à côté (conflict*) jusqu’au choix du joueur.
 */
@Entity(tableName = "local_saves", primaryKeys = ["gameId", "kind", "slot"])
data class LocalSave(
    val gameId: String,
    val kind: String,
    val slot: Int,
    val console: String,
    val baseRevision: Int = 0,
    val baseSha: String? = null,
    val localSha: String? = null,
    val localPlayedAt: String? = null,
    val core: String = "",
    val romSha: String = "",
    val conflictRevision: Int? = null,
    val conflictSha: String? = null,
    val conflictPlayedAt: String? = null,
    val conflictDevice: String? = null,
    val conflictCore: String? = null,
    val lastError: String? = null,
) {
    val changedLocally: Boolean get() = localSha != null && localSha != baseSha
    val inConflict: Boolean get() = conflictRevision != null
    val pendingUpload: Boolean get() = changedLocally && !inConflict
}

/** Une session de jeu en attente d’envoi au serveur. */
@Entity(tableName = "play_sessions")
data class PlaySessionEntity(@PrimaryKey val clientId: String, val gameId: String, val startedAt: String, val endedAt: String, val playedSeconds: Long)

/** Réglages et petites valeurs (JSON), partagés par toutes les plateformes. */
@Entity(tableName = "settings")
data class KeyValueEntity(@PrimaryKey val key: String, val value: String)

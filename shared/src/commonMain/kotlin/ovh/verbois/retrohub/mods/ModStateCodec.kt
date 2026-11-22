package ovh.verbois.retrohub.mods

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import ovh.verbois.retrohub.api.ModCatalogue
import ovh.verbois.retrohub.api.ModProfile
import ovh.verbois.retrohub.api.ModRelease
import ovh.verbois.retrohub.api.ModSelection

/** Large texture file manifests are stored/decoded once, regardless of profile history size. */
object ModStateSerializer : KSerializer<ModLocalState> {
    override val descriptor = StoredModState.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ModLocalState) {
        val releases = (value.catalogue?.releases.orEmpty() + value.installed + value.active?.releases.orEmpty() + value.history.flatMap { it.releases })
            .distinctBy { it.selection }
        fun installation(item: ModInstallation) = StoredInstallation(item.profile, item.releases.map { it.selection })
        encoder.encodeSerializableValue(
            StoredModState.serializer(),
            StoredModState(
                releases = releases,
                catalogue = value.catalogue?.let {
                    StoredCatalogue(it.schemaVersion, it.gameId, it.releases.map { release -> release.selection }, it.profile, it.profileHistory)
                },
                active = value.active?.let(::installation),
                history = value.history.map(::installation),
                installed = value.installed.map { it.selection },
                pending = value.pending,
                recoveryRequired = value.recoveryRequired,
                profileConflict = value.profileConflict,
                pendingBaseRevision = value.pendingBaseRevision,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): ModLocalState {
        val stored = decoder.decodeSerializableValue(StoredModState.serializer())
        require(stored.version == 1)
        val releases = stored.releases.associateBy { it.selection }
        require(releases.size == stored.releases.size)
        fun resolve(keys: List<ModSelection>) = keys.map { releases.getValue(it) }
        fun installation(item: StoredInstallation) = ModInstallation(item.profile, resolve(item.selections))
        return ModLocalState(
            catalogue = stored.catalogue?.let { ModCatalogue(it.schemaVersion, it.gameId, resolve(it.selections), it.profile, it.history) },
            active = stored.active?.let(::installation),
            history = stored.history.map(::installation),
            installed = resolve(stored.installed),
            pending = stored.pending,
            recoveryRequired = stored.recoveryRequired,
            profileConflict = stored.profileConflict,
            pendingBaseRevision = stored.pendingBaseRevision,
        )
    }
}

@Serializable
private data class StoredModState(
    val version: Int = 1,
    val releases: List<ModRelease>,
    val catalogue: StoredCatalogue? = null,
    val active: StoredInstallation? = null,
    val history: List<StoredInstallation> = emptyList(),
    val installed: List<ModSelection> = emptyList(),
    val pending: Boolean = false,
    val recoveryRequired: Boolean = false,
    val profileConflict: Boolean = false,
    val pendingBaseRevision: Int? = null,
)

@Serializable
private data class StoredInstallation(val profile: ModProfile, val selections: List<ModSelection>)

@Serializable
private data class StoredCatalogue(
    val schemaVersion: Int,
    val gameId: String,
    val selections: List<ModSelection>,
    val profile: ModProfile,
    val history: List<ModProfile>,
)

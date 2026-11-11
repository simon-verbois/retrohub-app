package ovh.verbois.retrohub.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY position")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id OR playGameId = :id LIMIT 1")
    suspend fun find(id: String): GameEntity?

    @Query("SELECT * FROM games WHERE id = :id OR playGameId = :id LIMIT 1")
    fun observe(id: String): Flow<GameEntity?>

    @Query("SELECT * FROM games")
    suspend fun all(): List<GameEntity>

    @Upsert
    suspend fun upsert(games: List<GameEntity>)

    @Query("DELETE FROM games WHERE id NOT IN (:keep)")
    suspend fun deleteOthers(keep: List<String>)

    @Query("DELETE FROM games")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(games: List<GameEntity>) {
        if (games.isEmpty()) clear() else deleteOthers(games.map { it.id })
        upsert(games)
    }
}

@Dao
interface LocalRomDao {
    @Query("SELECT * FROM local_roms ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<LocalRomEntity>>

    @Query("SELECT * FROM local_roms WHERE playGameId = :playGameId")
    suspend fun find(playGameId: String): LocalRomEntity?

    @Query("SELECT * FROM local_roms")
    suspend fun all(): List<LocalRomEntity>

    @Upsert
    suspend fun upsert(rom: LocalRomEntity)

    @Delete
    suspend fun delete(rom: LocalRomEntity)
}

@Dao
interface LocalSaveDao {
    @Query("SELECT * FROM local_saves WHERE gameId = :gameId AND kind = :kind AND slot = :slot")
    suspend fun find(gameId: String, kind: String, slot: Int): LocalSave?

    @Query("SELECT * FROM local_saves WHERE gameId = :gameId ORDER BY kind, slot")
    suspend fun forGame(gameId: String): List<LocalSave>

    @Query("SELECT * FROM local_saves WHERE gameId = :gameId ORDER BY kind, slot")
    fun observeForGame(gameId: String): Flow<List<LocalSave>>

    @Query("SELECT * FROM local_saves")
    suspend fun all(): List<LocalSave>

    @Query("SELECT * FROM local_saves")
    fun observeAll(): Flow<List<LocalSave>>

    @Upsert
    suspend fun upsert(save: LocalSave)

    @Delete
    suspend fun delete(save: LocalSave)
}

@Dao
interface PlaySessionDao {
    @Query("SELECT * FROM play_sessions ORDER BY startedAt LIMIT :limit")
    suspend fun oldest(limit: Int): List<PlaySessionEntity>

    @Query("SELECT COUNT(*) FROM play_sessions")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM play_sessions")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(session: PlaySessionEntity)

    @Query("DELETE FROM play_sessions WHERE clientId IN (:ids)")
    suspend fun delete(ids: List<String>)
}

@Dao
interface KeyValueDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Upsert
    suspend fun put(entry: KeyValueEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun remove(key: String)
}

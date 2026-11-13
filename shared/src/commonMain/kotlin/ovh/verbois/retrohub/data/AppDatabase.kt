package ovh.verbois.retrohub.data

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [GameEntity::class, LocalRomEntity::class, LocalSave::class, PlaySessionEntity::class, KeyValueEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun games(): GameDao

    abstract fun roms(): LocalRomDao

    abstract fun saves(): LocalSaveDao

    abstract fun sessions(): PlaySessionDao

    abstract fun settings(): KeyValueDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Pilote SQLite embarqué : même comportement sur Android, iOS et la JVM de test. */
fun RoomDatabase.Builder<AppDatabase>.configured(): AppDatabase = setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()

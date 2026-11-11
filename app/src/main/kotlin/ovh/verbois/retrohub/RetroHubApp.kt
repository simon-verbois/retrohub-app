package ovh.verbois.retrohub

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.data.AppDatabase
import ovh.verbois.retrohub.data.configured
import ovh.verbois.retrohub.platform.AndroidPlatform
import ovh.verbois.retrohub.platform.Notifications

class RetroHubApp : Application() {
    lateinit var platform: AndroidPlatform
        private set
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        platform = AndroidPlatform(this)
        val database = Room.databaseBuilder<AppDatabase>(this, getDatabasePath(DATABASE).absolutePath).configured()
        Notifications.createChannels(this)
        container = AppContainer(platform, database)
        container.downloads.backgroundCanceller = { platform.cancelBackgroundDownload(it) }
        container.scope.launch { container.start() }
    }

    companion object {
        const val DATABASE = "retrohub.db"
    }
}

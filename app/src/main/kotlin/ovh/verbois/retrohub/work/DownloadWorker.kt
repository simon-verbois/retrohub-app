package ovh.verbois.retrohub.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ovh.verbois.retrohub.R
import ovh.verbois.retrohub.RetroHubApp
import ovh.verbois.retrohub.library.DownloadState
import ovh.verbois.retrohub.platform.Notifications
import java.util.concurrent.TimeUnit

/**
 * Téléchargement d’un jeu en tâche de premier plan : il continue application fermée, avec sa
 * barre de progression dans les notifications, puis annonce que le jeu est prêt.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val playGameId get() = inputData.getString(KEY_ID).orEmpty()
    private val title get() = inputData.getString(KEY_TITLE).orEmpty()
    private val notificationId get() = Notifications.DOWNLOAD_BASE + playGameId.hashCode().and(0xFFFF)

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(0f)

    override suspend fun doWork(): Result {
        val container = (applicationContext as RetroHubApp).container
        container.start()
        runCatching { setForeground(foreground(0f)) }
        var last = -1
        val ok =
            container.downloads.perform(playGameId) { fraction ->
                val percent = (fraction * 100).toInt()
                if (percent != last && percent % 5 == 0) {
                    last = percent
                    runCatching { setForegroundAsync(foreground(fraction)) }
                }
            }
        if (ok) Notifications.show(applicationContext, notificationId, "« $title » est prêt", "Le jeu est sur le téléphone : il se joue même sans connexion.")
        if (!ok && (container.downloads.states.value[playGameId] as? DownloadState.Failed)?.retryable == true) return Result.retry()
        return Result.success()
    }

    private fun foreground(fraction: Float): ForegroundInfo {
        val notification =
            NotificationCompat
                .Builder(applicationContext, Notifications.CHANNEL_DOWNLOADS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Téléchargement de « $title »")
                .setProgress(100, (fraction * 100).toInt(), fraction <= 0f)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        private const val KEY_ID = "play_game_id"
        private const val KEY_TITLE = "title"

        private fun name(playGameId: String) = "download-$playGameId"

        fun enqueue(context: Context, playGameId: String, title: String) {
            val cellular = (context.applicationContext as RetroHubApp).container.settings.settings.value.cellularDownloads
            val request =
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(workDataOf(KEY_ID to playGameId, KEY_TITLE to title))
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(
                            if (cellular) NetworkType.CONNECTED else NetworkType.UNMETERED,
                        ).build(),
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(name(playGameId), ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context, playGameId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(name(playGameId))
        }
    }
}

package ovh.verbois.retrohub.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ovh.verbois.retrohub.RetroHubApp
import ovh.verbois.retrohub.account.AuthState
import ovh.verbois.retrohub.sync.SyncOutcome
import java.util.concurrent.TimeUnit

/**
 * Synchronisation en tâche de fond, dès que le réseau est là : catalogue, sauvegardes en attente,
 * sessions hors ligne. En maintenance, elle se replanifie après le délai annoncé par le serveur.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RetroHubApp).container
        container.start()
        if (container.account.auth.value !is AuthState.SignedIn) return Result.success()
        return when (val outcome = container.sync.syncNow()) {
            SyncOutcome.Done, SyncOutcome.SignedOut -> Result.success()

            is SyncOutcome.Retry -> {
                val after = outcome.afterSeconds
                if (after != null) {
                    enqueue(applicationContext, after)
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        }
    }

    companion object {
        const val NAME = "retrohub-sync"
        const val PERIODIC = "retrohub-periodic-sync"

        /** Synchro toutes les [hours] heures quand le réseau est là ; 0 = aucune. */
        fun schedulePeriodic(context: Context, hours: Int) {
            val manager = WorkManager.getInstance(context)
            if (hours <= 0) {
                manager.cancelUniqueWork(PERIODIC)
                return
            }
            val request =
                androidx.work
                    .PeriodicWorkRequestBuilder<SyncWorker>(hours.toLong(), TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            manager.enqueueUniquePeriodicWork(PERIODIC, androidx.work.ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun enqueue(context: Context, delaySeconds: Long = 0) {
            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                if (delaySeconds >
                    0
                ) {
                    ExistingWorkPolicy.REPLACE
                } else {
                    ExistingWorkPolicy.APPEND_OR_REPLACE
                },
                request,
            )
        }
    }
}

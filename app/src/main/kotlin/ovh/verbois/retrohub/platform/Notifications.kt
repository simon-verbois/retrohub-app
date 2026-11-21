package ovh.verbois.retrohub.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ovh.verbois.retrohub.AppContainer
import ovh.verbois.retrohub.MainActivity
import ovh.verbois.retrohub.R

object Notifications {
    const val CHANNEL_DOWNLOADS = "downloads"
    const val CHANNEL_SYNC = "sync"
    const val DOWNLOAD_BASE = 20_000
    const val ACTION_REVIEW_SAVE_CONFLICTS = "ovh.verbois.retrohub.action.REVIEW_SAVE_CONFLICTS"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_DOWNLOADS, "Téléchargements", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_SYNC, "Synchronisation des sauvegardes", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, id: Int, title: String, text: String, channel: String = CHANNEL_DOWNLOADS) {
        if (!allowed(context)) return
        val notification =
            NotificationCompat
                .Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent(context, id))
                .setAutoCancel(true)
                .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    internal fun openIntent(context: Context, id: Int): Intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).apply {
        if (id == AppContainer.NOTIFY_CONFLICT) {
            action = ACTION_REVIEW_SAVE_CONFLICTS
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    internal fun contentIntent(context: Context, id: Int): PendingIntent = PendingIntent.getActivity(
        context,
        id,
        openIntent(context, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

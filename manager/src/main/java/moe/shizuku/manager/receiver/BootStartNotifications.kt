package moe.shizuku.manager.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R

object BootStartNotifications {

    fun showWaitingForNetwork(context: Context) {
        show(context, context.getString(R.string.boot_start_waiting_for_wifi))
    }

    fun showConnecting(context: Context) {
        show(context, context.getString(R.string.boot_start_connecting))
    }

    fun showFailure(context: Context, message: String) {
        show(context, message)
    }

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(AppConstants.NOTIFICATION_ID_WORK)
    }

    private fun show(context: Context, message: String) {
        createChannel(context)

        val retryPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, BootStartActionReceiver::class.java).setAction(BootStartActionReceiver.ACTION_RETRY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, BootStartActionReceiver::class.java).setAction(BootStartActionReceiver.ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AppConstants.NOTIFICATION_CHANNEL_WORK)
            .setSmallIcon(R.drawable.ic_system_icon)
            .setColor(context.getColor(R.color.notification))
            .setContentTitle(context.getString(R.string.boot_start_notification_title))
            .setContentText(message)
            .setOngoing(true)
            .addAction(0, context.getString(R.string.action_retry), retryPendingIntent)
            .addAction(0, context.getString(R.string.cancel), cancelPendingIntent)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(AppConstants.NOTIFICATION_ID_WORK, notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    AppConstants.NOTIFICATION_CHANNEL_WORK,
                    context.getString(R.string.notification_channel_boot_start),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
    }
}

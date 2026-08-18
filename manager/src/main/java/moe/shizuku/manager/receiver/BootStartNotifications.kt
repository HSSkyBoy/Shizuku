package moe.shizuku.manager.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R

object BootStartNotifications {

    enum class State {
        WAITING_FOR_WIFI,
        CONNECTING,
        WAITING_FOR_RETRY,
        WAITING_FOR_UNLOCK
    }

    fun showWaitingForNetwork(context: Context) {
        show(context, context.getString(R.string.boot_start_waiting_for_wifi), State.WAITING_FOR_WIFI)
    }

    fun showConnecting(context: Context) {
        show(context, context.getString(R.string.boot_start_connecting), State.CONNECTING)
    }

    fun showWaitingForRetry(context: Context) {
        show(context, context.getString(R.string.boot_start_waiting_for_retry), State.WAITING_FOR_RETRY)
    }

    fun showWaitingForUnlock(context: Context) {
        show(context, context.getString(R.string.boot_start_waiting_for_unlock), State.WAITING_FOR_UNLOCK)
    }

    fun showPermissionError(context: Context) {
        createChannel(context)

        val helpIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/guide/setup/")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingHelpIntent = PendingIntent.getActivity(
            context, 0, helpIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val msg = context.getString(R.string.permission_write_secure_settings_required)
        val notification = NotificationCompat.Builder(context, AppConstants.NOTIFICATION_CHANNEL_WORK)
            .setSmallIcon(R.drawable.ic_system_icon)
            .setColor(context.getColor(R.color.notification))
            .setContentTitle(context.getString(R.string.boot_start_notification_title))
            .setContentText(msg)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(pendingHelpIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(AppConstants.NOTIFICATION_ID_WORK, notification)
    }

    fun showFailure(context: Context, message: String) {
        show(context, message, null)
    }

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(AppConstants.NOTIFICATION_ID_WORK)
    }

    private fun show(context: Context, message: String, state: State?) {
        createChannel(context)

        val attemptNowPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, NotifAttemptReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, NotifCancelReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wifiIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val wifiPendingIntent = PendingIntent.getActivity(
            context,
            2,
            wifiIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, AppConstants.NOTIFICATION_CHANNEL_WORK)
            .setSmallIcon(R.drawable.ic_system_icon)
            .setColor(context.getColor(R.color.notification))
            .setContentTitle(context.getString(R.string.boot_start_notification_title))
            .setContentText(message)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(wifiPendingIntent)
            .addAction(
                R.drawable.ic_system_icon,
                context.getString(R.string.boot_start_action_attempt_now),
                attemptNowPendingIntent
            )
            .addAction(
                R.drawable.ic_close_24,
                context.getString(R.string.cancel),
                cancelPendingIntent
            )

        context.getSystemService(NotificationManager::class.java)
            .notify(AppConstants.NOTIFICATION_ID_WORK, builder.build())
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

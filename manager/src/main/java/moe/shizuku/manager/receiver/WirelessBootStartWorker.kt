package moe.shizuku.manager.receiver

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.adb.AdbWirelessHelper
import moe.shizuku.manager.starter.SelfStarterService
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

class WirelessBootStartWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val adbWirelessHelper = AdbWirelessHelper()

    override suspend fun doWork(): Result {
        if (Shizuku.pingBinder()) {
            UserPresentRestartReceiver.setEnabled(applicationContext, false)
            BootStartNotifications.dismiss(applicationContext)
            return Result.success()
        }

        val startablePort = adbWirelessHelper.getStartableAdbPort()
        val hasSecureSettingsPermission =
            applicationContext.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        if (!hasSecureSettingsPermission && startablePort == null) {
            Log.w(AppConstants.TAG, "Wireless boot worker missing WRITE_SECURE_SETTINGS")
            BootStartNotifications.showFailure(
                applicationContext,
                applicationContext.getString(moe.shizuku.manager.R.string.permission_write_secure_settings_required)
            )
            return Result.failure()
        }

        val keyguardManager =
            applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isDeviceLocked) {
            Log.i(AppConstants.TAG, "Wireless boot worker waiting for user unlock")
            UserPresentRestartReceiver.setEnabled(applicationContext, true)
            BootStartNotifications.showFailure(
                applicationContext,
                applicationContext.getString(moe.shizuku.manager.R.string.boot_start_waiting_for_unlock)
            )
            return Result.success()
        }

        UserPresentRestartReceiver.setEnabled(applicationContext, false)

        val wirelessAdbEnabled = if (hasSecureSettingsPermission) {
            try {
                adbWirelessHelper.validateThenEnableWirelessAdb(
                    applicationContext.contentResolver,
                    applicationContext,
                    false
                )
            } catch (e: SecurityException) {
                Log.w(
                    AppConstants.TAG,
                    "Wireless boot worker permission denied enabling wireless ADB",
                    e
                )
                BootStartNotifications.showFailure(
                    applicationContext,
                    applicationContext.getString(moe.shizuku.manager.R.string.permission_write_secure_settings_required)
                )
                return Result.failure()
            }
        } else {
            false
        }

        if (!wirelessAdbEnabled && startablePort == null) {
            Log.i(AppConstants.TAG, "Wireless boot worker waiting for Wi-Fi or TCP ADB port")
            BootStartNotifications.showWaitingForNetwork(applicationContext)
            return Result.retry()
        }

        BootStartNotifications.showConnecting(applicationContext)
        applicationContext.startForegroundService(
            Intent(applicationContext, SelfStarterService::class.java).apply {
                putExtra(SelfStarterService.EXTRA_AUTO_ENABLE_WIRELESS_DEBUGGING, wirelessAdbEnabled)
                putExtra(SelfStarterService.EXTRA_FORCE_RESTART, false)
                putExtra(
                    SelfStarterService.EXTRA_DISABLE_WIRELESS_DEBUGGING_WHEN_FINISHED,
                    wirelessAdbEnabled
                )
            }
        )

        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "wireless_boot_start"

        fun getStartableAdbPort(): Int? = AdbWirelessHelper().getStartableAdbPort()

        fun enqueue(context: Context) {
            val constraints = if (getStartableAdbPort() == null) {
                BootStartNotifications.showWaitingForNetwork(context)
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            } else {
                BootStartNotifications.showConnecting(context)
                Constraints.NONE
            }

            val request = OneTimeWorkRequestBuilder<WirelessBootStartWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            UserPresentRestartReceiver.setEnabled(context, false)
            BootStartNotifications.dismiss(context)
        }
    }
}

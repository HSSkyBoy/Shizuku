package moe.shizuku.manager.receiver

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbClient
import moe.shizuku.manager.adb.AdbKey
import moe.shizuku.manager.adb.AdbKeyException
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.AdbWirelessHelper
import moe.shizuku.manager.adb.PreferenceAdbKeyStore
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.UserHandleCompat
import moe.shizuku.manager.watchdog.WatchdogService
import rikka.shizuku.Shizuku
import java.io.EOFException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class WirelessBootStartWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val adbWirelessHelper = AdbWirelessHelper()

    override suspend fun doWork(): Result {
        if (UserHandleCompat.myUserId() > 0 || Shizuku.pingBinder()) {
            UserPresentRestartReceiver.setEnabled(applicationContext, false)
            BootStartNotifications.dismiss(applicationContext)
            return Result.success()
        }

        val startablePort = adbWirelessHelper.getStartableAdbPort()
        val hasSecureSettingsPermission =
            applicationContext.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        if (!hasSecureSettingsPermission && startablePort == null) {
            Log.w(AppConstants.TAG, "Wireless boot worker missing WRITE_SECURE_SETTINGS")
            BootStartNotifications.showPermissionError(applicationContext)
            return Result.failure()
        }

        val keyguardManager =
            applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isDeviceLocked || keyguardManager.isKeyguardLocked) {
            Log.i(AppConstants.TAG, "Wireless boot worker waiting for user unlock")
            UserPresentRestartReceiver.setEnabled(applicationContext, true)
            BootStartNotifications.showWaitingForUnlock(applicationContext)
            return Result.success()
        }

        UserPresentRestartReceiver.setEnabled(applicationContext, false)

        try {
            BootStartNotifications.showConnecting(applicationContext)

            val cr = applicationContext.contentResolver
            if (hasSecureSettingsPermission) {
                try {
                    Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
                    Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
                } catch (e: Exception) {
                    Log.w(AppConstants.TAG, "Failed setting adb_allowed_connection_time", e)
                }
            }

            val resolvedPort = startablePort ?: withContext(Dispatchers.IO) {
                discoverWirelessAdbPort()
            }

            if (resolvedPort <= 0) {
                Log.w(AppConstants.TAG, "Failed to discover wireless ADB port")
                if (runAttemptCount >= 5) {
                    BootStartNotifications.showFailure(
                        applicationContext,
                        applicationContext.getString(R.string.wireless_boot_wifi_required)
                    )
                    return Result.failure()
                }
                BootStartNotifications.showWaitingForRetry(applicationContext)
                return Result.retry()
            }

            Log.i(AppConstants.TAG, "Starting Shizuku with wireless ADB on port $resolvedPort")
            startViaAdbWithRetry("127.0.0.1", resolvedPort)

            val binderStarted = withTimeoutOrNull(10_000L) {
                while (!Shizuku.pingBinder()) {
                    delay(300L)
                }
                true
            } == true

            if (binderStarted) {
                Log.i(AppConstants.TAG, "Shizuku started successfully via WirelessBootStartWorker")
                ShizukuSettings.setLastLaunchMode(ShizukuSettings.LaunchMethod.ADB)
                
                if (hasSecureSettingsPermission) {
                    try {
                        Settings.Global.putInt(cr, "adb_wifi_enabled", 0)
                    } catch (_: Exception) {
                    }
                }

                if (ShizukuSettings.getPreferences().getBoolean(ShizukuSettings.WATCHDOG_ENABLED_ADB, false)) {
                    WatchdogService.start(applicationContext)
                }

                BootStartNotifications.dismiss(applicationContext)
                return Result.success()
            } else {
                Log.w(AppConstants.TAG, "Timed out waiting for Shizuku binder after adb start")
                if (runAttemptCount >= 5) {
                    BootStartNotifications.showFailure(
                        applicationContext,
                        applicationContext.getString(R.string.start_service_timeout)
                    )
                    return Result.failure()
                }
                BootStartNotifications.showWaitingForRetry(applicationContext)
                return Result.retry()
            }

        } catch (e: CancellationException) {
            Log.i(AppConstants.TAG, "WirelessBootStartWorker cancelled", e)
            BootStartNotifications.showWaitingForRetry(applicationContext)
            throw e
        } catch (e: Exception) {
            Log.e(AppConstants.TAG, "WirelessBootStartWorker error", e)
            if (Shizuku.pingBinder()) {
                BootStartNotifications.dismiss(applicationContext)
                return Result.success()
            }
            if (runAttemptCount >= 5) {
                BootStartNotifications.showFailure(
                    applicationContext,
                    "${e.message ?: applicationContext.getString(R.string.notification_service_start_failed)}"
                )
                return Result.failure()
            }
            BootStartNotifications.showWaitingForRetry(applicationContext)
            return Result.retry()
        }
    }

    private suspend fun discoverWirelessAdbPort(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return -1
        }

        return callbackFlow {
            val cr = applicationContext.contentResolver
            val km = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            var unlockReceiver: BroadcastReceiver? = null
            var timeoutJob: Job? = null

            val adbMdns = AdbMdns(applicationContext, AdbMdns.TLS_CONNECT) { p ->
                if (p in 1..65535) {
                    trySend(p)
                }
            }

            fun startDiscoveryWithTimeout() {
                try {
                    adbMdns.start()
                } catch (e: Exception) {
                    Log.w(AppConstants.TAG, "mDNS start failed", e)
                }
                timeoutJob?.cancel()
                timeoutJob = launch {
                    delay(15_000L)
                    close(TimeoutException("Timed out during mDNS port discovery"))
                }
            }

            fun handleKeyguardOrAuth() {
                if (km.isKeyguardLocked || km.isDeviceLocked) {
                    val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
                    unlockReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            if (intent.action == Intent.ACTION_USER_PRESENT) {
                                runCatching { context.unregisterReceiver(this) }
                                unlockReceiver = null
                                Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                            }
                        }
                    }
                    ContextCompat.registerReceiver(
                        applicationContext,
                        unlockReceiver,
                        filter,
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                }
                timeoutJob?.cancel()
                adbMdns.stop()
            }

            val observer = object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    when (Settings.Global.getInt(cr, "adb_wifi_enabled", 0)) {
                        0 -> handleKeyguardOrAuth()
                        1 -> startDiscoveryWithTimeout()
                    }
                }
            }

            Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
            cr.registerContentObserver(Settings.Global.getUriFor("adb_wifi_enabled"), false, observer)
            startDiscoveryWithTimeout()

            awaitClose {
                adbMdns.stop()
                timeoutJob?.cancel()
                cr.unregisterContentObserver(observer)
                unlockReceiver?.let {
                    runCatching { applicationContext.unregisterReceiver(it) }
                }
            }
        }.first()
    }

    private suspend fun startViaAdbWithRetry(host: String, port: Int) = withContext(Dispatchers.IO) {
        val key = try {
            AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
        } catch (e: Throwable) {
            throw AdbKeyException(e)
        }

        AdbClient(host, port, key).use { client ->
            connectWithRetry(client)
            client.shellCommand(Starter.internalCommand) {
                Log.d(AppConstants.TAG, "starter output: ${String(it)}")
            }
        }
    }

    private suspend fun connectWithRetry(client: AdbClient) {
        var delayTime = 0L
        val maxAttempts = 5
        for (attempt in 1..maxAttempts) {
            try {
                if (delayTime > 0) delay(delayTime)
                client.connect()
                break
            } catch (e: Exception) {
                if (
                    attempt == maxAttempts ||
                    e is CancellationException ||
                    e is SocketTimeoutException
                ) throw e
                delayTime += 800L
            }
        }
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

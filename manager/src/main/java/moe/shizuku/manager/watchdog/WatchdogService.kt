package moe.shizuku.manager.watchdog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.starter.SelfStarterService
import rikka.shizuku.Shizuku

class WatchdogService : Service() {

    companion object {
        private const val ACTION_START = "moe.shizuku.manager.watchdog.action.START"
        private const val ACTION_STOP = "moe.shizuku.manager.watchdog.action.STOP"
        private const val MAX_RESTART_ATTEMPTS = 5
        private const val STABLE_WINDOW_MILLIS = 300_000L
        private const val RESTART_IN_FLIGHT_RESET_MILLIS = 15_000L
        private const val NOTIFICATION_ID = AppConstants.NOTIFICATION_ID_STATUS + 1
        private const val CHANNEL_ID = "watchdog_status"

        fun start(context: Context) {
            val intent = Intent(context, WatchdogService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WatchdogService::class.java).setAction(ACTION_STOP)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var restartAttempts = 0
    private var lastStableSinceElapsedRealtime = 0L
    private var isRestartInFlight = false
    private var watchdogEnabledForThisSession = false
    private var listenersRegistered = false
    private var stableResetJob: Job? = null
    private var restartInFlightResetJob: Job? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.i(AppConstants.TAG, "Watchdog observed Shizuku binder received")
        isRestartInFlight = false
        restartInFlightResetJob?.cancel()
        lastStableSinceElapsedRealtime = SystemClock.elapsedRealtime()
        scheduleStableReset()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener binderDead@{
        Log.w(AppConstants.TAG, "Watchdog observed Shizuku binder death")
        stableResetJob?.cancel()
        lastStableSinceElapsedRealtime = 0L

        if (!watchdogEnabledForThisSession) {
            stopSelf()
            return@binderDead
        }
        if (ShizukuSettings.getLastLaunchMode() != ShizukuSettings.LaunchMethod.ADB) {
            Log.i(AppConstants.TAG, "Watchdog ignored binder death because launch mode is not ADB")
            stopSelf()
            return@binderDead
        }
        if (isRestartInFlight) {
            Log.i(AppConstants.TAG, "Watchdog restart already in flight, ignoring duplicate death")
            return@binderDead
        }
        if (restartAttempts >= MAX_RESTART_ATTEMPTS) {
            Log.w(AppConstants.TAG, "Watchdog reached restart limit, stopping")
            stopSelf()
            return@binderDead
        }

        restartAttempts += 1
        isRestartInFlight = true
        startSelfStarterService()
        scheduleRestartInFlightReset()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        watchdogEnabledForThisSession = isWatchdogSessionEnabled()
        if (!watchdogEnabledForThisSession) {
            Log.i(AppConstants.TAG, "Watchdog start ignored because session is not eligible")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!listenersRegistered) {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            listenersRegistered = true
        }

        if (Shizuku.pingBinder()) {
            binderReceivedListener.onBinderReceived()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stableResetJob?.cancel()
        restartInFlightResetJob?.cancel()
        serviceScope.cancel()
        if (listenersRegistered) {
            runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
            runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
            listenersRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun isWatchdogSessionEnabled(): Boolean {
        val preferences = ShizukuSettings.getPreferences()
        return preferences.getBoolean(ShizukuSettings.WATCHDOG_ENABLED_ADB, false)
            && ShizukuSettings.getLastLaunchMode() == ShizukuSettings.LaunchMethod.ADB
    }

    private fun scheduleStableReset() {
        stableResetJob?.cancel()
        stableResetJob = serviceScope.launch {
            delay(STABLE_WINDOW_MILLIS)
            if (Shizuku.pingBinder()) {
                restartAttempts = 0
                lastStableSinceElapsedRealtime = SystemClock.elapsedRealtime()
                Log.i(AppConstants.TAG, "Watchdog reset restart attempts after stable window")
            }
        }
    }

    private fun scheduleRestartInFlightReset() {
        restartInFlightResetJob?.cancel()
        restartInFlightResetJob = serviceScope.launch {
            delay(RESTART_IN_FLIGHT_RESET_MILLIS)
            if (!Shizuku.pingBinder()) {
                isRestartInFlight = false
                Log.w(AppConstants.TAG, "Watchdog cleared restart in-flight state after timeout")
            }
        }
    }

    private fun startSelfStarterService() {
        Log.i(
            AppConstants.TAG,
            "Watchdog requesting SelfStarterService restart attempt=$restartAttempts/$MAX_RESTART_ATTEMPTS"
        )
        val intent = Intent(this, SelfStarterService::class.java).apply {
            putExtra(SelfStarterService.EXTRA_AUTO_ENABLE_WIRELESS_DEBUGGING, false)
            putExtra(SelfStarterService.EXTRA_FORCE_RESTART, false)
            putExtra(SelfStarterService.EXTRA_DISABLE_WIRELESS_DEBUGGING_WHEN_FINISHED, false)
            putExtra(SelfStarterService.EXTRA_STARTED_BY_WATCHDOG, true)
        }
        startForegroundService(intent)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_watchdog),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_system_icon)
            .setColor(getColor(R.color.notification))
            .setContentTitle(getString(R.string.notification_watchdog_title))
            .setContentText(getString(R.string.notification_watchdog_content))
            .setOngoing(true)
            .build()
    }
}

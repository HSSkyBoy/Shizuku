package moe.shizuku.manager.starter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.adb.AdbKeyException
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.AdbWirelessHelper
import moe.shizuku.manager.utils.EnvironmentUtils
import rikka.shizuku.Shizuku
import java.net.ConnectException

class SelfStarterService : Service(), LifecycleOwner {

    companion object {
        const val EXTRA_AUTO_ENABLE_WIRELESS_DEBUGGING =
            "moe.shizuku.manager.extra.AUTO_ENABLE_WIRELESS_DEBUGGING"
        const val EXTRA_FORCE_RESTART =
            "moe.shizuku.manager.extra.FORCE_RESTART"
        const val EXTRA_DISABLE_WIRELESS_DEBUGGING_WHEN_FINISHED =
            "moe.shizuku.manager.extra.DISABLE_WIRELESS_DEBUGGING_WHEN_FINISHED"
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private val portLive = MutableLiveData<Int>()
    private var adbMdns: AdbMdns? = null
    private val adbWirelessHelper = AdbWirelessHelper()
    private var disableWirelessDebuggingWhenFinished = false

    private val portObserver = Observer<Int> { p ->
        if (p in 1..65535) {
            Log.i(
                AppConstants.TAG, "Discovered adb port via mDNS: $p, starting Shizuku directly"
            )
            // Do not launch activity, start ADB connection directly
            startShizukuViaAdb("127.0.0.1", p, disableWirelessDebuggingWhenFinished)
        } else {
            Log.w(AppConstants.TAG, "mDNS returned invalid port: $p")
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        startServiceNotification()
        Log.i(AppConstants.TAG, "SelfStarterService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        Log.i(AppConstants.TAG, "SelfStarterService starting command")

        val forceRestart = intent?.getBooleanExtra(EXTRA_FORCE_RESTART, false) == true
        disableWirelessDebuggingWhenFinished =
            intent?.getBooleanExtra(EXTRA_DISABLE_WIRELESS_DEBUGGING_WHEN_FINISHED, false) == true

        // Already running? Manual start can choose to force restart.
        if (Shizuku.pingBinder()) {
            if (!forceRestart) {
                Log.i(AppConstants.TAG, "Shizuku is already running, stopping service.")
                stopSelf()
                return START_NOT_STICKY
            }
            Log.i(AppConstants.TAG, "Shizuku is running, forcing stop before restart.")
            try {
                Shizuku.exit()
                Thread.sleep(300)
            } catch (tr: Throwable) {
                Log.w(AppConstants.TAG, "Failed to force stop Shizuku before restart", tr)
            }
        }

        val autoEnableWirelessDebugging =
            intent?.getBooleanExtra(EXTRA_AUTO_ENABLE_WIRELESS_DEBUGGING, false) == true
        if (autoEnableWirelessDebugging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            adbWirelessHelper.validateThenEnableWirelessAdb(contentResolver, this, false)
        }

        val wirelessEnabled = Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1
        Log.d(AppConstants.TAG, "Wireless Debugging enabled setting: $wirelessEnabled")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wirelessEnabled) {
            Log.i(AppConstants.TAG, "Starting mDNS discovery for wireless ADB port.")

            // Remove potential previous observer before adding a new one
            portLive.removeObserver(portObserver)
            portLive.observeForever(portObserver)

            if (adbMdns == null) {
                adbMdns =
                    AdbMdns(
                        context = this, serviceType = AdbMdns.TLS_CONNECT, observer = portObserver
                    )
            }
            adbMdns?.start()
        } else {
            Log.i(
                AppConstants.TAG,
                "Using fallback: SystemProperties/Custom Port for ADB port (or wireless debugging setting off)."
            )
            val systemPort = EnvironmentUtils.getAdbTcpPort()
            val customPort = adbWirelessHelper.getConfiguredTcpipPort() ?: -1
            val port = if (systemPort > 0) systemPort else customPort

            if (port > 0) {
                Log.i(
                    AppConstants.TAG,
                    "Found adb port: $port, starting Shizuku directly."
                )
                startShizukuViaAdb("127.0.0.1", port, disableWirelessDebuggingWhenFinished)
            } else {
                Log.e(
                    AppConstants.TAG,
                    "Could not determine ADB TCP port, aborting."
                )
                stopSelf()
            }
        }

        // Service should only run once per trigger
        return START_NOT_STICKY
    }

    private fun startShizukuViaAdb(
        host: String,
        port: Int,
        disableWirelessDebuggingWhenFinished: Boolean
    ) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(this@SelfStarterService, "Starting Shizuku service...", Toast.LENGTH_SHORT)
                .show()
        }

        adbWirelessHelper.startShizukuViaAdb(
            host = host,
            port = port,
            coroutineScope = lifecycleScope,
            onOutput = { /* No UI to update in service */ },
            onError = { e ->
                lifecycleScope.launch(Dispatchers.Main) {
                    when (e) {
                        is AdbKeyException -> Toast.makeText(
                            applicationContext,
                            "ADB Key error during Shizuku start",
                            Toast.LENGTH_LONG
                        ).show()

                        is ConnectException -> Toast.makeText(
                            applicationContext,
                            "ADB Connection failed to $host:$port",
                            Toast.LENGTH_LONG
                        ).show()

                        else -> Toast.makeText(
                            applicationContext, "Error: ${e.message}", Toast.LENGTH_LONG
                        ).show()
                    }
                    if (disableWirelessDebuggingWhenFinished) {
                        adbWirelessHelper.disableWirelessAdb(contentResolver)
                    }
                    stopSelf()
                }
            },
            onSuccess = {
                lifecycleScope.launch(Dispatchers.Main) {
                    if (disableWirelessDebuggingWhenFinished) {
                        adbWirelessHelper.disableWirelessAdb(contentResolver)
                    }
                    stopSelf()
                }
            })
    }

    private fun startServiceNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    AppConstants.NOTIFICATION_CHANNEL_STATUS,
                    getString(R.string.notification_channel_service_status),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val notification = Notification.Builder(this, AppConstants.NOTIFICATION_CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_system_icon)
            .setColor(getColor(R.color.notification))
            .setContentTitle(getString(R.string.notification_service_starting))
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                AppConstants.NOTIFICATION_ID_STATUS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            )
        } else {
            startForeground(AppConstants.NOTIFICATION_ID_STATUS, notification)
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.i(AppConstants.TAG, "SelfStarterService destroying")
            adbMdns?.stop()
        }

        portLive.removeObserver(portObserver) // Clean up the observer
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}


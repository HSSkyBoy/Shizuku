package moe.shizuku.manager.receiver

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbWirelessHelper
import moe.shizuku.manager.starter.SelfStarterService
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.shizuku.Shizuku

class BootCompleteReceiver : BroadcastReceiver() {
    private val adbWirelessHelper = AdbWirelessHelper()

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED != intent.action
            && Intent.ACTION_BOOT_COMPLETED != intent.action
        ) {
            return
        }

        if (UserHandleCompat.myUserId() > 0 || Shizuku.pingBinder()) return

        val startOnBootRootIsEnabled = ShizukuSettings.getPreferences()
            .getBoolean(ShizukuSettings.KEEP_START_ON_BOOT, false)
        val startOnBootWirelessIsEnabled = ShizukuSettings.getPreferences()
            .getBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false)

        if (startOnBootRootIsEnabled) {
            rootStart(context)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU // https://r.android.com/2128832
            && context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
            && startOnBootWirelessIsEnabled
        ) {
            adbStart(context)
        } else {
            Log.w(AppConstants.TAG, "No support start on boot")
        }
    }

    private fun rootStart(context: Context) {
        if (!Shell.getShell().isRoot) {
            //NotificationHelper.notify(context, AppConstants.NOTIFICATION_ID_STATUS, AppConstants.NOTIFICATION_CHANNEL_STATUS, R.string.notification_service_start_no_root)
            Shell.getCachedShell()?.close()
            return
        }

        Shell.cmd(Starter.internalCommand).exec()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun adbStart(context: Context) {
//        val cr = context.contentResolver
//        Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
//        Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
//        Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
//        val pending = goAsync()
//        CoroutineScope(Dispatchers.IO).launch {
//            val latch = CountDownLatch(1)
//            val adbMdns = AdbMdns(context, AdbMdns.TLS_CONNECT) { port ->
//                if (port <= 0) return@AdbMdns
//                try {
//                    val keystore = PreferenceAdbKeyStore(ShizukuSettings.getPreferences())
//                    val key = AdbKey(keystore, "shizuku")
//                    val client = AdbClient("127.0.0.1", port, key)
//                    client.connect()
//                    client.shellCommand(Starter.internalCommand, null)
//                    client.close()
//                } catch (_: Exception) {
//                }
//                latch.countDown()
//            }
//            if (Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 1) {
//                adbMdns.start()
//                latch.await(3, TimeUnit.SECONDS)
//                adbMdns.stop()
//            }
//            pending.finish()
//        }
        Log.i(
            AppConstants.TAG,
            "WRITE_SECURE_SETTINGS is enabled and user has Start on boot is enabled for wireless ADB"
        )

        try {
            val wirelessAdbStatus = adbWirelessHelper.validateThenEnableWirelessAdb(
                context.contentResolver, context, true
            )
            if (wirelessAdbStatus) {
                val intentService = Intent(context, SelfStarterService::class.java)
                context.startForegroundService(intentService)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}

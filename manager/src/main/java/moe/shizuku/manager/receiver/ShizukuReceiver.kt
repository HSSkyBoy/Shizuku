package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbWirelessHelper
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.shell.ShellBinderRequestHandler
import moe.shizuku.manager.starter.SelfStarterService

class ShizukuReceiver : BroadcastReceiver() {
    private val adbWirelessHelper = AdbWirelessHelper()

    override fun onReceive(context: Context, intent: Intent) {
        if ("rikka.shizuku.intent.action.REQUEST_BINDER" == intent.action) {
            ShellBinderRequestHandler.handleRequest(context, intent)
        }
        if (!ServiceStatus().isRunning) {
            val startOnBootWirelessIsEnabled = ShizukuSettings.getPreferences()
                .getBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false)
            if (startOnBootWirelessIsEnabled) {
                val startablePort = adbWirelessHelper.getStartableAdbPort()
                val wirelessAdbStatus = adbWirelessHelper.validateThenEnableWirelessAdb(
                    context.contentResolver, context
                )
                if (wirelessAdbStatus || startablePort != null) {
                    val intentService = Intent(context, SelfStarterService::class.java).apply {
                        putExtra(SelfStarterService.EXTRA_AUTO_ENABLE_WIRELESS_DEBUGGING, wirelessAdbStatus)
                        putExtra(SelfStarterService.EXTRA_FORCE_RESTART, false)
                        putExtra(
                            SelfStarterService.EXTRA_DISABLE_WIRELESS_DEBUGGING_WHEN_FINISHED,
                            wirelessAdbStatus
                        )
                    }
                    context.startForegroundService(intentService)
                } else {
                    Log.w(AppConstants.TAG, "No Wi-Fi or TCP ADB port available to restart Shizuku")
                }
            }
        }
    }
}

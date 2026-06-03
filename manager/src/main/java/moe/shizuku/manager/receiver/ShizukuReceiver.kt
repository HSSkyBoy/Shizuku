package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.shell.ShellBinderRequestHandler

class ShizukuReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if ("rikka.shizuku.intent.action.REQUEST_BINDER" == intent.action) {
            ShellBinderRequestHandler.handleRequest(context, intent)
        }
        if (!ServiceStatus().isRunning) {
            val startOnBootWirelessIsEnabled = ShizukuSettings.getPreferences()
                .getBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false)
            if (startOnBootWirelessIsEnabled) {
                ShizukuReceiverStarter.startWireless(context)
            }
        }
    }
}

package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.watchdog.WatchdogService

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED != intent.action
            && Intent.ACTION_BOOT_COMPLETED != intent.action
        ) {
            return
        }

        ShizukuReceiverStarter.startOnBoot(context)

        val preferences = ShizukuSettings.getPreferences()
        if (preferences.getBoolean(ShizukuSettings.WATCHDOG_ENABLED_ADB, false)) {
            WatchdogService.start(context)
        }
    }
}

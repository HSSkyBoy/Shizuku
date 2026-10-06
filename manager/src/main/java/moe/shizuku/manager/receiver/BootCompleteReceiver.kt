package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.watchdog.WatchdogService
import kotlin.concurrent.thread

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED != intent.action
            && Intent.ACTION_BOOT_COMPLETED != intent.action
        ) {
            return
        }

        // Root shell acquisition and the starter command block; keep them off the main thread.
        val pendingResult = goAsync()
        thread(name = "ShizukuBootStart") {
            try {
                ShizukuReceiverStarter.startOnBoot(context)

                val preferences = ShizukuSettings.getPreferences()
                if (preferences.getBoolean(ShizukuSettings.WATCHDOG_ENABLED_ADB, false)) {
                    WatchdogService.start(context)
                }
            } catch (t: Throwable) {
                Log.e(AppConstants.TAG, "Boot start failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

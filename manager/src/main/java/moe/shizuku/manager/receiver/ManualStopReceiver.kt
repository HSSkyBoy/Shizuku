package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.watchdog.WatchdogService
import rikka.shizuku.Shizuku

class ManualStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == "${BuildConfig.APPLICATION_ID}.STOP" ||
            action == "moe.shizuku.privileged.api.STOP"
        ) {
            Log.i(AppConstants.TAG, "Received manual stop broadcast")
            try {
                WatchdogService.stop(context)
                if (Shizuku.pingBinder()) {
                    Shizuku.exit()
                }
            } catch (t: Throwable) {
                Log.w(AppConstants.TAG, "Error stopping Shizuku service via broadcast", t)
            }
        }
    }
}

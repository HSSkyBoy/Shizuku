package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.BuildConfig

class ManualStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == "${BuildConfig.APPLICATION_ID}.START" ||
            action == "moe.shizuku.privileged.api.START"
        ) {
            ShizukuReceiverStarter.start(context, forceStart = true)
        }
    }
}

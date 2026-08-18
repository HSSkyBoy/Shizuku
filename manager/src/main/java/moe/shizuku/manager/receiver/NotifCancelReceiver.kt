package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotifCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WirelessBootStartWorker.cancel(context)
    }
}

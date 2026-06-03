package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootStartActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_RETRY -> ShizukuReceiverStarter.startWireless(context, true)
                    ACTION_CANCEL -> WirelessBootStartWorker.cancel(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_RETRY = "moe.shizuku.manager.action.BOOT_START_RETRY"
        const val ACTION_CANCEL = "moe.shizuku.manager.action.BOOT_START_CANCEL"
    }
}

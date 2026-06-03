package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings

class WirelessAdbStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val autoStartEnabled = ShizukuSettings.getPreferences()
            .getBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false)
        
        if (!autoStartEnabled) return

        if (intent.action == "android.net.wifi.STATE_CHANGE" || 
            intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return
            val capabilities = cm.getNetworkCapabilities(network) ?: return
            
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                Log.i(AppConstants.TAG, "Wi-Fi connected, triggering Shizuku wireless start")
                ShizukuReceiverStarter.startWireless(context, force = false, requireBootSupport = false)
            }
        }
    }
}

package moe.shizuku.manager.adb

import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings

@RequiresApi(Build.VERSION_CODES.R)
class AdbPairingNotificationListener : NotificationListenerService() {

    private val pairingCodeRegex = Regex("(\\d{6})")

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (!ShizukuSettings.getPreferences().getBoolean(ShizukuSettings.AUTO_PAIRING_ENABLED, false)) {
            return
        }

        if (sbn?.packageName != "com.android.settings") {
            return
        }

        val notification = sbn.notification
        val title = notification.extras.getCharSequence("android.title")?.toString() ?: ""
        val text = notification.extras.getCharSequence("android.text")?.toString() ?: ""

        Log.d(AppConstants.TAG, "onNotificationPosted from Settings: title=$title, text=$text")

        // Search for 6-digit code in title or text
        val matchResult = pairingCodeRegex.find(title) ?: pairingCodeRegex.find(text)
        if (matchResult != null) {
            val code = matchResult.value
            Log.i(AppConstants.TAG, "Found potential pairing code: $code")
            AdbPairingService.setAutoPairCode(code)
        }
    }
}

package moe.shizuku.manager.receiver

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbWirelessHelper
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.shizuku.Shizuku

object ShizukuReceiverStarter {

    fun startOnBoot(context: Context) {
        if (UserHandleCompat.myUserId() > 0 || Shizuku.pingBinder()) {
            return
        }

        val preferences = ShizukuSettings.getPreferences()
        val startOnBootRootEnabled =
            preferences.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT, false)
        val startOnBootWirelessEnabled =
            preferences.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false)

        if (startOnBootRootEnabled) {
            rootStart()
            return
        }

        if (startOnBootWirelessEnabled) {
            startWireless(context, requireBootSupport = true)
            return
        }

        Log.w(AppConstants.TAG, "No support start on boot")
    }

    fun startWireless(
        context: Context,
        force: Boolean = false,
        requireBootSupport: Boolean = false
    ) {
        if (UserHandleCompat.myUserId() > 0 || (Shizuku.pingBinder() && !force)) {
            BootStartNotifications.dismiss(context)
            return
        }

        if (requireBootSupport && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.w(AppConstants.TAG, "Wireless boot start requires Android 13 or above")
            BootStartNotifications.showFailure(
                context,
                context.getString(R.string.wireless_boot_wifi_required)
            )
            return
        }

        val hasSecureSettingsPermission =
            context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        val startablePort = AdbWirelessHelper().getStartableAdbPort()

        if (!hasSecureSettingsPermission && startablePort == null) {
            Log.w(AppConstants.TAG, "Wireless boot worker missing WRITE_SECURE_SETTINGS")
            BootStartNotifications.showFailure(
                context,
                context.getString(R.string.permission_write_secure_settings_required)
            )
            return
        }

        WirelessBootStartWorker.enqueue(context)
    }

    private fun rootStart() {
        if (!Shell.getShell().isRoot) {
            Shell.getCachedShell()?.close()
            return
        }

        Shell.cmd(Starter.internalCommand).exec()
    }
}

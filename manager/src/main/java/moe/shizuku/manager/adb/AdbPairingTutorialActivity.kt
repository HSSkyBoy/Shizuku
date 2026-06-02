package moe.shizuku.manager.adb

import android.app.AppOpsManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.app.AppActivity
import rikka.compatibility.DeviceCompatibility

@RequiresApi(Build.VERSION_CODES.R)
class AdbPairingTutorialActivity : AppActivity() {

    companion object {
        private const val ANDROID_17_API = 37
        private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        private const val REQUEST_LOCAL_NETWORK_PERMISSION = 1001
    }

    private var state by mutableStateOf(PairingTutorialState())
    private var localNetworkPermissionRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        syncState()
        startPairingIfReady()

        setContent {
            AdbPairingTutorialComposeScreen(
                state = state,
                showMiuiHint = DeviceCompatibility.isMiui(),
                onNavigateUp = { finish() },
                onOpenDeveloperOptions = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    intent.putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
                    try {
                        startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                    }
                },
                onOpenNotificationOptions = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    try {
                        startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                    }
                },
                onRequestLocalNetworkPermission = {
                    localNetworkPermissionRequested = false
                    ensureLocalNetworkPermissionOrStartPairing()
                }
            )
        }
    }

    private fun isNotificationEnabled(): Boolean {
        val context = this

        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(AdbPairingService.NOTIFICATION_CHANNEL)
        return nm.areNotificationsEnabled() &&
                (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }

    override fun onResume() {
        super.onResume()

        val oldState = state
        syncState()
        if (
            state.notificationEnabled &&
            state.localNetworkPermissionGranted &&
            (!oldState.notificationEnabled || !oldState.localNetworkPermissionGranted || state.pairingServiceStartFailed)
        ) {
            startPairingService()
        }
    }

    private fun hasLocalNetworkPermission(): Boolean {
        if (Build.VERSION.SDK_INT < ANDROID_17_API) {
            return true
        }
        return checkSelfPermission(ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureLocalNetworkPermissionOrStartPairing() {
        if (hasLocalNetworkPermission()) {
            syncState()
            startPairingService()
            return
        }
        if (localNetworkPermissionRequested) {
            syncState()
            return
        }
        localNetworkPermissionRequested = true
        requestPermissions(arrayOf(ACCESS_LOCAL_NETWORK), REQUEST_LOCAL_NETWORK_PERMISSION)
        syncState()
    }

    private fun startPairingIfReady() {
        if (state.notificationEnabled) {
            ensureLocalNetworkPermissionOrStartPairing()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_LOCAL_NETWORK_PERMISSION) {
            return
        }
        localNetworkPermissionRequested = false
        syncState()
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startPairingService()
        }
    }

    private fun startPairingService() {
        val intent = AdbPairingService.startIntent(this)
        try {
            startForegroundService(intent)
            state = state.copy(pairingServiceStartFailed = false)
        } catch (e: Throwable) {
            Log.e(AppConstants.TAG, "startForegroundService", e)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException
            ) {
                val mode = getSystemService(AppOpsManager::class.java)
                    .noteOpNoThrow("android:start_foreground", android.os.Process.myUid(), packageName, null, null)
                if (mode == AppOpsManager.MODE_ERRORED) {
                    Toast.makeText(this, "OP_START_FOREGROUND is denied. What are you doing?", Toast.LENGTH_LONG).show()
                }
                startService(intent)
                state = state.copy(pairingServiceStartFailed = false)
            } else {
                state = state.copy(pairingServiceStartFailed = true)
            }
        }
    }

    private fun syncState() {
        state = state.copy(
            notificationEnabled = isNotificationEnabled(),
            localNetworkPermissionGranted = hasLocalNetworkPermission()
        )
    }
}

data class PairingTutorialState(
    val notificationEnabled: Boolean = false,
    val localNetworkPermissionGranted: Boolean = true,
    val pairingServiceStartFailed: Boolean = false
)

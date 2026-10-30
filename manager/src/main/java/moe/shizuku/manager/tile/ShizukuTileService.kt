package moe.shizuku.manager.tile

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.ContextThemeWrapper
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbWirelessHelper
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.receiver.WirelessBootStartWorker
import moe.shizuku.manager.settings.SettingsActivity
import moe.shizuku.manager.starter.SelfStarterService
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.watchdog.WatchdogService
import rikka.shizuku.Shizuku

@RequiresApi(Build.VERSION_CODES.N)
class ShizukuTileService : TileService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        mainHandler.post { updateTileState() }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        mainHandler.post { updateTileState() }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onStopListening() {
        super.onStopListening()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = Shizuku.pingBinder()

        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_title)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (isRunning) R.string.tile_running else R.string.tile_stopped)
        }

        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { handleClick() }
        } else {
            handleClick()
        }
    }

    private fun handleClick() {
        if (Shizuku.pingBinder()) {
            showActionDialog()
        } else {
            startServiceFlow()
        }
    }

    private fun showActionDialog() {
        val items = arrayOf(
            getString(R.string.tile_action_open),
            getString(R.string.tile_action_settings),
            getString(R.string.tile_action_restart),
            getString(R.string.tile_action_stop)
        )

        val themedContext = ContextThemeWrapper(this, R.style.AppTheme)
        val dialog = MaterialAlertDialogBuilder(themedContext)
            .setTitle(R.string.tile_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivityAndCollapseCompat(intent)
                    }
                    1 -> {
                        val intent = Intent(this, SettingsActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivityAndCollapseCompat(intent)
                    }
                    2 -> {
                        restartService()
                    }
                    3 -> {
                        stopService()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        showDialog(dialog)
    }

    private fun stopService() {
        if (!Shizuku.pingBinder()) return
        WatchdogService.stop(this)
        try {
            Shizuku.exit()
        } catch (_: Throwable) {
        }
        mainHandler.postDelayed({ updateTileState() }, 500)
        Toast.makeText(this, R.string.tile_service_stopped_toast, Toast.LENGTH_SHORT).show()
    }

    private fun restartService() {
        Toast.makeText(this, R.string.tile_service_starting_toast, Toast.LENGTH_SHORT).show()
        val isRoot = ShizukuSettings.getLastLaunchMode() == ShizukuSettings.LaunchMethod.ROOT && EnvironmentUtils.isRooted()
        if (isRoot) {
            ShizukuReceiverStarter.start(this, forceStart = true)
            mainHandler.postDelayed({ updateTileState() }, 1500)
        } else {
            val starterIntent = Intent(this, SelfStarterService::class.java).apply {
                putExtra(SelfStarterService.EXTRA_FORCE_RESTART, true)
                putExtra(SelfStarterService.EXTRA_AUTO_ENABLE_WIRELESS_DEBUGGING, true)
            }
            ContextCompat.startForegroundService(this, starterIntent)

            val port = AdbWirelessHelper().getStartableAdbPort()
            if (port == null || port !in 1..65535) {
                openWirelessSettings()
            }
        }
    }

    private fun startServiceFlow() {
        val isRoot = ShizukuSettings.getLastLaunchMode() == ShizukuSettings.LaunchMethod.ROOT && EnvironmentUtils.isRooted()
        if (isRoot) {
            Toast.makeText(this, R.string.tile_service_starting_toast, Toast.LENGTH_SHORT).show()
            ShizukuReceiverStarter.start(this, forceStart = false)
            mainHandler.postDelayed({ updateTileState() }, 1500)
        } else {
            val port = AdbWirelessHelper().getStartableAdbPort()
            if (port != null && port in 1..65535) {
                Toast.makeText(this, R.string.tile_service_starting_toast, Toast.LENGTH_SHORT).show()
                WirelessBootStartWorker.enqueue(this)
            } else {
                // Background starter listens for mDNS
                val starterIntent = Intent(this, SelfStarterService::class.java).apply {
                    putExtra(SelfStarterService.EXTRA_AUTO_ENABLE_WIRELESS_DEBUGGING, true)
                }
                ContextCompat.startForegroundService(this, starterIntent)

                // Direct jump to wireless settings
                openWirelessSettings()
            }
        }
    }

    private fun openWirelessSettings() {
        val wirelessSettingsIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent("android.settings.WIFI_DEBUG_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        } else null

        val devSettingsIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
        }

        try {
            if (wirelessSettingsIntent != null) {
                startActivityAndCollapseCompat(wirelessSettingsIntent)
            } else {
                startActivityAndCollapseCompat(devSettingsIntent)
            }
        } catch (_: ActivityNotFoundException) {
            try {
                startActivityAndCollapseCompat(devSettingsIntent)
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

    private fun startActivityAndCollapseCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

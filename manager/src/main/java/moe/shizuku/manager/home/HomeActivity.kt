package moe.shizuku.manager.home

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.Helps
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.AdbPairingService
import moe.shizuku.manager.adb.AdbWirelessHelper
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.management.AppsManagementActivity
import moe.shizuku.manager.management.appsViewModel
import moe.shizuku.manager.settings.SettingsActivity
import moe.shizuku.manager.shell.ShellTutorialActivity
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.watchdog.WatchdogService
import rikka.core.util.ClipboardUtils
import rikka.lifecycle.Status
import rikka.lifecycle.viewModels
import rikka.shizuku.Shizuku

abstract class HomeActivity : AppActivity() {

    companion object {
        const val EXTRA_START_SERVICE_VIA_WADB = "moe.shizuku.manager.extra.START_SERVICE_VIA_WADB"
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkServerStatus()
        appsModel.load()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        checkServerStatus()
    }

    private val homeModel by viewModels { HomeViewModel() }
    private val appsModel by appsViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        homeModel.serviceStatus.observe(this) {
            if (it.status == Status.SUCCESS) {
                val status = it.data ?: return@observe
                ShizukuSettings.setLastLaunchMode(if (status.uid == 0) ShizukuSettings.LaunchMethod.ROOT else ShizukuSettings.LaunchMethod.ADB)
            }
        }
        appsModel.grantedCount.observe(this) { }

        setContent {
            val serviceStatus by homeModel.serviceStatus.observeAsState()
            val grantedCount by appsModel.grantedCount.observeAsState()
            HomeComposeScreen(
                status = serviceStatus?.data,
                grantedCount = grantedCount?.data,
                onOpenSettings = {
                    startActivity(Intent(this, SettingsActivity::class.java))
                },
                onStopService = { stopService() },
                onManageApps = {
                    startActivity(Intent(this, AppsManagementActivity::class.java))
                },
                onOpenTerminal = {
                    startActivity(Intent(this, ShellTutorialActivity::class.java))
                },
                onStartRoot = { startRootService() },
                onRestartRoot = { startRootService() },
                onOpenWirelessGuide = {
                    CustomTabsHelper.launchUrlOrCopy(this, Helps.ADB_ANDROID11.get())
                },
                onPairWireless = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        startActivity(Intent(this, moe.shizuku.manager.adb.AdbPairingTutorialActivity::class.java))
                    }
                },
                onStartWirelessAdb = {
                    val adbWirelessHelper = AdbWirelessHelper()
                    val customPort = adbWirelessHelper.getConfiguredTcpipPort() ?: -1
                    val systemPort = EnvironmentUtils.getAdbTcpPort()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (systemPort in 1..65535) {
                            startWirelessAdb(systemPort)
                        } else if (customPort in 1..65535) {
                            startWirelessAdb(customPort)
                        } else {
                            showWirelessAdbDiscoveryDialog()
                        }
                    } else {
                        val port = if (systemPort > 0) systemPort else customPort
                        if (port > 0) {
                            startWirelessAdb(port)
                        } else {
                            showWirelessAdbNotEnabledDialog()
                        }
                    }
                },
                onCopyAdbCommand = { copyAdbCommand() },
                onSendAdbCommand = { sendAdbCommand() },
                onOpenAdbPermissionHelp = {
                    CustomTabsHelper.launchUrlOrCopy(this, Helps.ADB_PERMISSION.get())
                },
                onOpenLearnMore = {
                    CustomTabsHelper.launchUrlOrCopy(this, Helps.HOME.get())
                }
            )
        }

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onResume() {
        super.onResume()
        checkServerStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_SERVICE_VIA_WADB, false) == true) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.cancel(AdbPairingService.NOTIFICATION_ID)

            val adbWirelessHelper = AdbWirelessHelper()
            val customPort = adbWirelessHelper.getConfiguredTcpipPort() ?: -1
            val systemPort = EnvironmentUtils.getAdbTcpPort()

            if (systemPort in 1..65535) {
                startWirelessAdb(systemPort)
            } else if (customPort in 1..65535) {
                startWirelessAdb(customPort)
            } else {
                showWirelessAdbDiscoveryDialog()
            }
        }
    }

    private fun checkServerStatus() {
        homeModel.reload()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    private fun stopService() {
        if (!Shizuku.pingBinder()) return
        WatchdogService.stop(this)
        try {
            Shizuku.exit()
        } catch (_: Throwable) {
        }
    }

    private fun startRootService() {
        WatchdogService.stop(this)
        startActivity(Intent(this, StarterActivity::class.java).apply {
            putExtra(StarterActivity.EXTRA_IS_ROOT, true)
        })
    }

    private fun copyAdbCommand() {
        if (ClipboardUtils.put(this, Starter.adbCommand)) {
            Toast.makeText(
                this,
                getString(moe.shizuku.manager.R.string.toast_copied_to_clipboard, Starter.adbCommand),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun sendAdbCommand() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, Starter.adbCommand)
        }
        startActivity(Intent.createChooser(intent, getString(moe.shizuku.manager.R.string.home_adb_dialog_view_command_button_send)))
    }

    private fun startWirelessAdb(port: Int) {
        AdbWirelessHelper().launchStarterActivity(this, "127.0.0.1", port)
    }

    private fun showWirelessAdbNotEnabledDialog() {
        MaterialAlertDialogBuilder(this)
            .setMessage(moe.shizuku.manager.R.string.dialog_wireless_adb_not_enabled)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun openDevelopmentSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun showWirelessAdbDiscoveryDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val discoveredPort = MutableLiveData<Int>()
        val adbMdns = AdbMdns(this, AdbMdns.TLS_CONNECT) {
            discoveredPort.postValue(it)
        }
        val currentPort = EnvironmentUtils.getAdbTcpPort()
        var dialog: AlertDialog? = null
        val observer = Observer<Int> {
            if (it in 1..65535) {
                dialog?.dismiss()
                startWirelessAdb(it)
            }
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(moe.shizuku.manager.R.string.dialog_adb_discovery)
            .setMessage(moe.shizuku.manager.R.string.dialog_adb_discovery_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(moe.shizuku.manager.R.string.development_settings, null)
            .apply {
                if (currentPort in 1..65535) {
                    setNeutralButton(currentPort.toString(), null)
                }
            }
            .create()

        val adbDialog = dialog
        adbDialog.setCanceledOnTouchOutside(false)
        adbDialog.setOnShowListener {
            adbMdns.start()
            discoveredPort.observe(this, observer)
            if (checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
                Settings.Global.putInt(contentResolver, "adb_wifi_enabled", 1)
                Settings.Global.putInt(contentResolver, Settings.Global.ADB_ENABLED, 1)
                Settings.Global.putLong(contentResolver, "adb_allowed_connection_time", 0L)
            }
            adbDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                openDevelopmentSettings()
            }
            adbDialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                adbDialog.dismiss()
                startWirelessAdb(EnvironmentUtils.getAdbTcpPort())
            }
        }
        adbDialog.setOnDismissListener {
            discoveredPort.removeObserver(observer)
            adbMdns.stop()
        }
        adbDialog.show()
    }
}

package moe.shizuku.manager.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.management.ApplicationManagementActivity
import moe.shizuku.manager.management.appsViewModel
import moe.shizuku.manager.shell.ShellTutorialActivity
import moe.shizuku.manager.settings.SettingsActivity
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.adb.AdbWirelessHelper
import rikka.lifecycle.Status
import rikka.lifecycle.viewModels
import rikka.shizuku.Shizuku

abstract class HomeActivity : AppActivity() {

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
                    startActivity(Intent(this, ApplicationManagementActivity::class.java))
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
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        val port = moe.shizuku.manager.utils.EnvironmentUtils.getAdbTcpPort()
                        if (port > 0) {
                            AdbWirelessHelper().launchStarterActivity(this, "127.0.0.1", port)
                        } else {
                            startActivity(Intent(this, moe.shizuku.manager.adb.AdbPairingTutorialActivity::class.java))
                        }
                    }
                },
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
        try {
            Shizuku.exit()
        } catch (_: Throwable) {
        }
    }

    private fun startRootService() {
        startActivity(Intent(this, StarterActivity::class.java).apply {
            putExtra(StarterActivity.EXTRA_IS_ROOT, true)
        })
    }

}

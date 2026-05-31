package moe.shizuku.manager.home

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.databinding.AboutDialogBinding
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.management.ApplicationManagementActivity
import moe.shizuku.manager.management.appsViewModel
import moe.shizuku.manager.shell.ShellTutorialActivity
import moe.shizuku.manager.settings.SettingsActivity
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.utils.AppIconCache
import moe.shizuku.manager.utils.CustomTabsHelper
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
                onOpenAbout = { showAboutDialog() },
                onStopService = { showStopDialog() },
                onManageApps = {
                    startActivity(Intent(this, ApplicationManagementActivity::class.java))
                },
                onOpenTerminal = {
                    startActivity(Intent(this, ShellTutorialActivity::class.java))
                },
                onStartRoot = { startRootService() },
                onRestartRoot = { startRootService() },
                onShowAdbCommand = { showAdbCommandDialog() },
                onOpenWirelessGuide = {
                    CustomTabsHelper.launchUrlOrCopy(this, Helps.ADB_ANDROID11.get())
                },
                onPairWireless = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        if (display?.displayId ?: 0 > 0) {
                            AdbPairDialogFragment().show(supportFragmentManager)
                        } else {
                            startActivity(Intent(this, moe.shizuku.manager.adb.AdbPairingTutorialActivity::class.java))
                        }
                    }
                },
                onStartWirelessAdb = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        AdbDialogFragment().show(supportFragmentManager)
                    } else {
                        WadbNotEnabledDialogFragment().show(supportFragmentManager)
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

    private fun showAboutDialog() {
        val binding = AboutDialogBinding.inflate(LayoutInflater.from(this), null, false)
        binding.sourceCode.movementMethod = LinkMovementMethod.getInstance()
        binding.sourceCode.text = getString(
            R.string.about_view_source_code,
            "<b><a href=\"https://github.com/HSSkyBoy/Shizuku\">GitHub</a></b>"
        ).toHtml()
        binding.followChannel.movementMethod = LinkMovementMethod.getInstance()
        binding.followChannel.text = getString(
            R.string.about_follow_channel
        ).toHtml()
        binding.icon.setImageBitmap(
            AppIconCache.getOrLoadBitmap(
                this,
                applicationInfo,
                Process.myUid() / 100000,
                resources.getDimensionPixelOffset(R.dimen.default_app_icon_size)
            )
        )
        binding.versionName.text = packageManager.getPackageInfo(packageName, 0).versionName
        MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .show()
    }

    private fun showStopDialog() {
        if (!Shizuku.pingBinder()) return
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.dialog_stop_message)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                try {
                    Shizuku.exit()
                } catch (_: Throwable) {
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startRootService() {
        startActivity(Intent(this, StarterActivity::class.java).apply {
            putExtra(StarterActivity.EXTRA_IS_ROOT, true)
        })
    }

    private fun showAdbCommandDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.home_adb_button_view_command)
            .setMessage(getString(R.string.home_adb_dialog_view_command_message, Starter.adbCommand).toHtml())
            .setPositiveButton(R.string.home_adb_dialog_view_command_copy_button) { _, _ ->
                rikka.core.util.ClipboardUtils.put(this, Starter.adbCommand)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.home_adb_dialog_view_command_button_send) { _, _ ->
                var intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, Starter.adbCommand)
                intent = Intent.createChooser(intent, getString(R.string.home_adb_dialog_view_command_button_send))
                startActivity(intent)
            }
            .show()
    }

}

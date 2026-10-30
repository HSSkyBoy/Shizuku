package moe.shizuku.manager.utils

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R

sealed class SettingsPage {

    abstract fun buildIntent(context: Context): Intent

    open fun launch(context: Context): Boolean {
        return try {
            val intent = buildIntent(context)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(AppConstants.TAG, "Failed to launch settings page", e)
            false
        }
    }

    object WirelessDebugging : SettingsPage() {

        private const val ACTION_WIFI_DEBUG_SETTINGS = "android.settings.WIFI_DEBUG_SETTINGS"
        private const val FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
        private const val FRAGMENT_ARG_VALUE = "toggle_adb_wireless"

        val defaultFlags: Int =
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS

        fun isDeveloperOptionsEnabled(context: Context): Boolean {
            return runCatching {
                Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                    0
                ) != 0
            }.getOrDefault(true)
        }

        fun showDeveloperOptionsDisabledDialog(context: Context) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_developer_options_required_title)
                .setMessage(R.string.dialog_developer_options_required_message)
                .setPositiveButton(R.string.development_settings) { _, _ ->
                    openGeneralDeveloperSettings(context)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        override fun buildIntent(context: Context): Intent {
            // For Xiaomi, Redmi, POCO and similar skins, highlight fragment args is more reliable
            val isXiaomiOrOem = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                Build.BRAND.equals("Xiaomi", ignoreCase = true) ||
                Build.BRAND.equals("Redmi", ignoreCase = true) ||
                Build.BRAND.equals("POCO", ignoreCase = true)

            if (!isXiaomiOrOem) {
                // AOSP / Pixel devices (Android 11 - Android 17): QS Tile Preferences targets Wireless Debugging directly
                val tileIntent = Intent(TileService.ACTION_QS_TILE_PREFERENCES).apply {
                    val packageName = "com.android.settings"
                    setPackage(packageName)
                    putExtra(
                        Intent.EXTRA_COMPONENT_NAME,
                        ComponentName(
                            packageName,
                            "com.android.settings.development.qstile.DevelopmentTiles\$WirelessDebugging"
                        )
                    )
                    flags = defaultFlags
                }
                if (isIntentResolvable(context, tileIntent)) {
                    return tileIntent
                }
            }

            // Android 11+ official wireless debug settings action
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val wifiDebugIntent = Intent(ACTION_WIFI_DEBUG_SETTINGS).apply {
                    flags = defaultFlags
                }
                if (isIntentResolvable(context, wifiDebugIntent)) {
                    return wifiDebugIntent
                }
            }

            // Fallback: developer settings with highlighted toggle
            return buildHighlightIntent()
        }

        override fun launch(context: Context): Boolean {
            if (!isDeveloperOptionsEnabled(context)) {
                showDeveloperOptionsDisabledDialog(context)
                return false
            }

            val candidates: List<() -> Intent> = listOf(
                { buildIntent(context) },
                { Intent(ACTION_WIFI_DEBUG_SETTINGS).apply { flags = defaultFlags } },
                { buildHighlightIntent() },
                { Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply { flags = defaultFlags } }
            )

            for (builder in candidates) {
                try {
                    val intent = builder()
                    context.startActivity(intent)
                    return true
                } catch (_: ActivityNotFoundException) {
                } catch (e: Exception) {
                    Log.w(AppConstants.TAG, "Settings launch candidate failed", e)
                }
            }

            return false
        }

        fun buildHighlightIntent(): Intent {
            return Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                flags = defaultFlags
                putExtra(FRAGMENT_ARG_KEY, FRAGMENT_ARG_VALUE)
            }
        }

        private fun openGeneralDeveloperSettings(context: Context) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                        flags = defaultFlags
                    }
                )
            } catch (_: Exception) {
            }
        }

        private fun isIntentResolvable(context: Context, intent: Intent): Boolean {
            return intent.resolveActivity(context.packageManager) != null
        }
    }
}

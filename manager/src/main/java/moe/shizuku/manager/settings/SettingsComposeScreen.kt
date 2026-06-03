package moe.shizuku.manager.settings

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.compose.ui.text.input.KeyboardType
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.KEEP_START_ON_BOOT
import moe.shizuku.manager.ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS
import moe.shizuku.manager.ShizukuSettings.TCPIP_PORT
import moe.shizuku.manager.ShizukuSettings.WATCHDOG_ENABLED_ADB
import moe.shizuku.manager.app.ThemeHelper
import moe.shizuku.manager.ktx.isComponentEnabled
import moe.shizuku.manager.ktx.setComponentEnabled
import moe.shizuku.manager.receiver.BootCompleteReceiver
import moe.shizuku.manager.ui.theme.ShizukuComposeTheme
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.watchdog.WatchdogService
import rikka.material.app.LocaleDelegate
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsComposeScreen(
    onNavigateUp: () -> Unit,
    onRecreateRequested: () -> Unit
) {
    ShizukuComposeTheme {
        SettingsScreenContent(
            onNavigateUp = onNavigateUp,
            onRecreateRequested = onRecreateRequested
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    onNavigateUp: () -> Unit,
    onRecreateRequested: () -> Unit
) {
    val context = LocalContext.current
    var dialogState by remember { mutableStateOf<SettingsDialogState?>(null) }
    val model = remember { buildSettingsModel(context) }
    val switchStates = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(model) {
        switchStates.clear()
        model.asSequence()
            .flatMap { it.items.asSequence() }
            .filterIsInstance<SettingsItem.SwitchItem>()
            .forEach { switchStates[it.key] = it.checked }
    }

    fun recreateAfterAnimation() {
        scope.launch {
            delay(200)
            onRecreateRequested()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = context.getString(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = context.getString(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    end = 20.dp,
                    bottom = innerPadding.calculateBottomPadding() + 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(model) { section ->
                    SettingsSectionCard(
                        title = section.title,
                        items = section.items,
                        switchStates = switchStates,
                        onToggle = { item, checked ->
                            when (item.key) {
                                KEEP_START_ON_BOOT -> {
                                    switchStates[KEEP_START_ON_BOOT] = checked
                                    if (checked) switchStates[KEEP_START_ON_BOOT_WIRELESS] = false
                                    saveBoolean(context, KEEP_START_ON_BOOT, checked)
                                    if (checked) saveBoolean(context, KEEP_START_ON_BOOT_WIRELESS, false)
                                    setBootReceiverEnabled(context, checked || getBoolean(context, KEEP_START_ON_BOOT_WIRELESS))
                                    recreateAfterAnimation()
                                }

                                KEEP_START_ON_BOOT_WIRELESS -> {
                                    val hasSecurePermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_SECURE_SETTINGS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (checked && !hasSecurePermission) {
                                        dialogState = SettingsDialogState.WirelessPermission
                                    } else {
                                        switchStates[KEEP_START_ON_BOOT_WIRELESS] = checked
                                        if (checked) switchStates[KEEP_START_ON_BOOT] = false
                                        saveBoolean(context, KEEP_START_ON_BOOT_WIRELESS, checked)
                                        if (checked) saveBoolean(context, KEEP_START_ON_BOOT, false)
                                        setBootReceiverEnabled(context, checked || getBoolean(context, KEEP_START_ON_BOOT))
                                        
                                        recreateAfterAnimation()
                                    }
                                }

                                ShizukuSettings.AUTO_PAIRING_ENABLED -> {
                                    if (checked && !isNotificationListenerEnabled(context)) {
                                        dialogState = SettingsDialogState.NotificationAccess
                                    } else {
                                        switchStates[ShizukuSettings.AUTO_PAIRING_ENABLED] = checked
                                        saveBoolean(context, ShizukuSettings.AUTO_PAIRING_ENABLED, checked)
                                    }
                                }

                                WATCHDOG_ENABLED_ADB -> {
                                    switchStates[WATCHDOG_ENABLED_ADB] = checked
                                    saveBoolean(context, WATCHDOG_ENABLED_ADB, checked)
                                    if (checked) {
                                        if (ShizukuSettings.getLastLaunchMode() == ShizukuSettings.LaunchMethod.ADB
                                            && rikka.shizuku.Shizuku.pingBinder()
                                        ) {
                                            WatchdogService.start(context)
                                        }
                                    } else {
                                        WatchdogService.stop(context)
                                    }
                                    recreateAfterAnimation()
                                }

                                ThemeHelper.KEY_BLACK_NIGHT_THEME,
                                ThemeHelper.KEY_USE_SYSTEM_COLOR -> {
                                    switchStates[item.key] = checked
                                    saveBoolean(context, item.key, checked)
                                    recreateAfterAnimation()
                                }
                            }
                        },
                        onClick = { item ->
                            when (item.key) {
                                "language" -> dialogState = SettingsDialogState.Language
                                ShizukuSettings.NIGHT_MODE -> dialogState = SettingsDialogState.NightMode
                                "translation" -> CustomTabsHelper.launchUrlOrCopy(
                                    context,
                                    context.getString(R.string.translation_url)
                                )

                                "translation_contributors" -> {
                                }

                                TCPIP_PORT -> dialogState = SettingsDialogState.TcpIpPort
                            }
                        }
                    )
                }
            }
        }
    }

    when (val state = dialogState) {
        SettingsDialogState.Language -> {
            val locales = buildLocaleItems(context)
            ChoiceDialog(
                title = context.getString(R.string.settings_language),
                options = locales.map { it.label },
                selectedIndex = locales.indexOfFirst { it.selected },
                onDismiss = { dialogState = null },
                onSelect = { index ->
                    val selected = locales[index]
                    val locale = if (selected.tag == "SYSTEM") LocaleDelegate.systemLocale else Locale.forLanguageTag(selected.tag)
                    ShizukuSettings.getPreferences().edit { putString(ShizukuSettings.LANGUAGE, selected.tag) }
                    LocaleDelegate.defaultLocale = locale
                    dialogState = null
                    onRecreateRequested()
                }
            )
        }

        SettingsDialogState.NightMode -> {
            val labels = context.resources.getStringArray(R.array.night_mode).toList()
            val values = context.resources.getIntArray(R.array.night_mode_value).toList()
            ChoiceDialog(
                title = context.getString(R.string.dark_theme),
                options = labels,
                selectedIndex = values.indexOf(ShizukuSettings.getNightMode()),
                onDismiss = { dialogState = null },
                onSelect = { index ->
                    val mode = values[index]
                    ShizukuSettings.getPreferences().edit { putInt(ShizukuSettings.NIGHT_MODE, mode) }
                    AppCompatDelegate.setDefaultNightMode(mode)
                    dialogState = null
                    onRecreateRequested()
                }
            )
        }

        SettingsDialogState.WirelessPermission -> {
            AlertDialog(
                onDismissRequest = { dialogState = null },
                confirmButton = {
                    TextButton(onClick = {
                        val command = "adb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS"
                        CustomTabsHelper.launchUrlOrCopy(context, "https://shizuku.rikka.app/guide/setup/")
                        dialogState = SettingsDialogState.Command(command)
                    }) {
                        Text(context.getString(R.string.manual))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogState = null }) {
                        Text(context.getString(R.string.cancel))
                    }
                },
                title = { Text(context.getString(R.string.permission_missing)) },
                text = { Text(context.getString(R.string.wireless_boot_permission_tooltip)) }
            )
        }

        SettingsDialogState.NotificationAccess -> {
            AlertDialog(
                onDismissRequest = { dialogState = null },
                confirmButton = {
                    TextButton(onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (_: Exception) {
                        }
                        dialogState = null
                    }) {
                        Text(context.getString(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogState = null }) {
                        Text(context.getString(R.string.cancel))
                    }
                },
                title = { Text(context.getString(R.string.permission_missing)) },
                text = { Text(context.getString(R.string.auto_pairing_notification_access_tooltip)) }
            )
        }

        is SettingsDialogState.Command -> {
            AlertDialog(
                onDismissRequest = { dialogState = null },
                confirmButton = {
                    TextButton(onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, state.command)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.home_adb_dialog_view_command_button_send)))
                        dialogState = null
                    }) {
                        Text(context.getString(R.string.home_adb_dialog_view_command_button_send))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogState = null }) {
                        Text(context.getString(R.string.cancel))
                    }
                },
                title = { Text(context.getString(R.string.home_adb_button_view_command)) },
                text = { Text(state.command) }
            )
        }

        SettingsDialogState.TcpIpPort -> {
            var port by remember { mutableStateOf(ShizukuSettings.getPreferences().getString(TCPIP_PORT, "") ?: "") }
            var errorText by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { dialogState = null },
                confirmButton = {
                    TextButton(onClick = {
                        val trimmed = port.trim()
                        if (trimmed.isEmpty()) {
                            ShizukuSettings.getPreferences().edit { putString(TCPIP_PORT, "") }
                            dialogState = null
                            onRecreateRequested()
                            return@TextButton
                        }
                        val value = trimmed.toIntOrNull()
                        if (value == null || value !in 10..65535) {
                            errorText = context.getString(R.string.dialog_adb_invalid_port)
                            return@TextButton
                        }
                        ShizukuSettings.getPreferences().edit { putString(TCPIP_PORT, trimmed) }
                        dialogState = null
                        onRecreateRequested()
                    }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogState = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
                title = { Text(context.getString(R.string.settings_tcpip_port)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(context.getString(R.string.settings_tcpip_port_summary))
                        OutlinedTextField(
                            value = port,
                            onValueChange = {
                                port = it.filter(Char::isDigit)
                                errorText = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text(context.getString(R.string.settings_tcpip_port_disabled)) },
                            isError = errorText != null,
                            supportingText = {
                                if (errorText != null) {
                                    Text(errorText!!)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            )
        }

        null -> Unit
    }
}

private fun buildSettingsModel(context: Context): List<SettingsSection> {
    val preferences = ShizukuSettings.getPreferences()
    val supportsStartOnBoot = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ||
        EnvironmentUtils.isTelevision(context) ||
        EnvironmentUtils.isRooted()

    val startupItems = buildList {
        if (supportsStartOnBoot) {
            add(
                SettingsItem.SwitchItem(
                    key = KEEP_START_ON_BOOT,
                    title = context.getString(R.string.settings_start_on_boot),
                    summary = context.getString(R.string.settings_start_on_boot_summary),
                    icon = Icons.Outlined.PowerSettingsNew,
                    checked = preferences.getBoolean(KEEP_START_ON_BOOT, false)
                )
            )
            add(
                SettingsItem.SwitchItem(
                    key = KEEP_START_ON_BOOT_WIRELESS,
                    title = context.getString(R.string.settings_start_on_boot_wireless),
                    summary = context.getString(R.string.settings_start_on_boot_wireless_summary),
                    icon = Icons.Outlined.Wifi,
                    checked = preferences.getBoolean(KEEP_START_ON_BOOT_WIRELESS, false)
                )
            )
            add(
                SettingsItem.SwitchItem(
                    key = ShizukuSettings.AUTO_PAIRING_ENABLED,
                    title = context.getString(R.string.settings_auto_pairing),
                    summary = context.getString(R.string.settings_auto_pairing_summary),
                    icon = Icons.Outlined.NotificationsActive,
                    checked = preferences.getBoolean(ShizukuSettings.AUTO_PAIRING_ENABLED, false)
                )
            )
            add(
                SettingsItem.SwitchItem(
                    key = WATCHDOG_ENABLED_ADB,
                    title = context.getString(R.string.settings_watchdog_adb),
                    summary = context.getString(R.string.settings_watchdog_adb_summary),
                    icon = Icons.Outlined.SettingsEthernet,
                    checked = preferences.getBoolean(WATCHDOG_ENABLED_ADB, false)
                )
            )
        }
        add(
            SettingsItem.StaticItem(
                key = TCPIP_PORT,
                title = context.getString(R.string.settings_tcpip_port),
                summary = context.getString(R.string.settings_tcpip_port_summary),
                icon = Icons.Outlined.SettingsEthernet
            )
        )
    }

    return listOf(
        SettingsSection(
            title = context.getString(R.string.settings_startup),
            items = startupItems
        ),
        SettingsSection(
            title = context.getString(R.string.settings_language),
            items = listOf(
                SettingsItem.StaticItem(
                    key = "language",
                    title = context.getString(R.string.settings_language),
                    summary = currentLanguageLabel(context),
                    icon = Icons.Outlined.Language
                ),
                SettingsItem.StaticItem(
                    key = "translation_contributors",
                    title = context.getString(R.string.settings_translation_contributors),
                    summary = context.getString(R.string.translation_contributors).ifBlank { context.getString(R.string.settings_translation_contributors_fallback) },
                    icon = Icons.Outlined.Info
                ),
                SettingsItem.StaticItem(
                    key = "translation",
                    title = context.getString(R.string.settings_translation),
                    summary = context.getString(R.string.settings_translation_summary, context.getString(R.string.app_name)),
                    icon = Icons.AutoMirrored.Outlined.OpenInNew
                )
            )
        ),
        SettingsSection(
            title = context.getString(R.string.settings_user_interface),
            items = listOf(
                SettingsItem.StaticItem(
                    key = ShizukuSettings.NIGHT_MODE,
                    title = context.getString(R.string.dark_theme),
                    summary = currentNightModeLabel(context),
                    icon = Icons.Outlined.DarkMode
                ),
                SettingsItem.SwitchItem(
                    key = ThemeHelper.KEY_BLACK_NIGHT_THEME,
                    title = context.getString(R.string.settings_black_night_theme),
                    summary = context.getString(R.string.settings_black_night_theme_summary),
                    icon = Icons.Outlined.DarkMode,
                    checked = preferences.getBoolean(ThemeHelper.KEY_BLACK_NIGHT_THEME, false)
                ),
                SettingsItem.SwitchItem(
                    key = ThemeHelper.KEY_USE_SYSTEM_COLOR,
                    title = context.getString(R.string.settings_use_system_color),
                    summary = context.getString(R.string.settings_use_system_color_summary),
                    icon = Icons.Outlined.Palette,
                    checked = ThemeHelper.isUsingSystemColor()
                )
            )
        )
    )
}

@Composable
private fun SettingsSectionCard(
    title: String,
    items: List<SettingsItem>,
    switchStates: Map<String, Boolean>,
    onToggle: (SettingsItem.SwitchItem, Boolean) -> Unit,
    onClick: (SettingsItem) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            items.forEachIndexed { index, item ->
                SettingsRow(
                    item = item,
                    switchStates = switchStates,
                    onToggle = onToggle,
                    onClick = onClick
                )
                if (index != items.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    item: SettingsItem,
    switchStates: Map<String, Boolean>,
    onToggle: (SettingsItem.SwitchItem, Boolean) -> Unit,
    onClick: (SettingsItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(item) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(item.summary, style = MaterialTheme.typography.bodyMedium)
        }
        if (item is SettingsItem.SwitchItem) {
            val checked = switchStates[item.key] ?: item.checked
            Switch(
                checked = checked,
                onCheckedChange = { onToggle(item, it) }
            )
        }
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                items(options.size) { index ->
                    val option = options[index]
                    Text(
                        text = if (index == selectedIndex) "$option  ✓" else option,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    )
}

private fun buildLocaleItems(context: Context): List<LocaleChoice> {
    val tags = rikka.shizuku.manager.ShizukuLocales.LOCALES
    val currentTag = ShizukuSettings.getPreferences().getString(ShizukuSettings.LANGUAGE, "SYSTEM") ?: "SYSTEM"
    return tags.mapIndexed { index, tag ->
        LocaleChoice(
            tag = tag,
            label = if (index == 0) {
                context.getString(R.string.settings_language_system)
            } else {
                localeLabel(tag)
            },
            selected = tag == currentTag
        )
    }
}

private fun localeLabel(tag: String): String {
    return when (tag) {
        "ang" -> "Old English (ca. 450-1100)"
        "ar" -> "العربية"
        "ars" -> "العربية النجدية"
        "az" -> "Azərbaycanca"
        "bn" -> "বাংলা"
        "ca" -> "Català"
        "cs" -> "Čeština"
        "de" -> "Deutsch"
        "el" -> "Ελληνικά"
        "en" -> "English"
        "eo" -> "Esperanto"
        "es" -> "Español"
        "es-419" -> "Español (Latinoamérica)"
        "es-CL" -> "Español (Chile)"
        "et" -> "Eesti"
        "fa" -> "فارسی"
        "fil" -> "Filipino"
        "fr" -> "Français"
        "he" -> "עברית"
        "hu" -> "Magyar"
        "hy" -> "Հայերեն"
        "id" -> "Indonesia"
        "it" -> "Italiano"
        "ja" -> "日本語"
        "ka" -> "ქართული"
        "ko" -> "한국어"
        "ms" -> "Melayu"
        "nl" -> "Nederlands"
        "pl" -> "Polski"
        "pt" -> "Português"
        "pt-BR" -> "Português (Brasil)"
        "ro" -> "Română"
        "ru" -> "Русский"
        "sl" -> "Slovenščina"
        "sr" -> "Српски"
        "ta" -> "தமிழ்"
        "th" -> "ไทย"
        "tr" -> "Türkçe"
        "uk" -> "Українська"
        "vi" -> "Tiếng Việt"
        "zh-CN" -> "简体中文"
        "zh-TW" -> "繁體中文"
        else -> tag
    }
}

private fun currentLanguageLabel(context: Context): String {
    return buildLocaleItems(context).firstOrNull { it.selected }?.label ?: context.getString(R.string.settings_language_system)
}

private fun currentNightModeLabel(context: Context): String {
    val labels = context.resources.getStringArray(R.array.night_mode)
    val values = context.resources.getIntArray(R.array.night_mode_value)
    val current = ShizukuSettings.getNightMode()
    val index = values.indexOf(current)
    return labels.getOrElse(index) { context.getString(R.string.follow_system) }
}

private fun saveBoolean(context: Context, key: String, value: Boolean) {
    ShizukuSettings.getPreferences().edit { putBoolean(key, value) }
}

private fun getBoolean(context: Context, key: String): Boolean {
    return ShizukuSettings.getPreferences().getBoolean(key, false)
}

private fun setBootReceiverEnabled(context: Context, enabled: Boolean) {
    val component = ComponentName(context.packageName, BootCompleteReceiver::class.java.name)
    context.packageManager.setComponentEnabled(component, enabled)
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (!flat.isNullOrEmpty()) {
        val names = flat.split(":")
        for (name in names) {
            val cn = ComponentName.unflattenFromString(name)
            if (cn != null) {
                if (pkgName == cn.packageName) {
                    return true
                }
            }
        }
    }
    return false
}

@Immutable
private data class SettingsSection(
    val title: String,
    val items: List<SettingsItem>
)

@Immutable
private sealed interface SettingsItem {
    val key: String
    val title: String
    val summary: String
    val icon: ImageVector

    data class StaticItem(
        override val key: String,
        override val title: String,
        override val summary: String,
        override val icon: ImageVector
    ) : SettingsItem

    data class SwitchItem(
        override val key: String,
        override val title: String,
        override val summary: String,
        override val icon: ImageVector,
        val checked: Boolean
    ) : SettingsItem
}

@Immutable
private data class LocaleChoice(
    val tag: String,
    val label: String,
    val selected: Boolean
)

private sealed interface SettingsDialogState {
    data object Language : SettingsDialogState
    data object NightMode : SettingsDialogState
    data object WirelessPermission : SettingsDialogState
    data object NotificationAccess : SettingsDialogState
    data object TcpIpPort : SettingsDialogState
    data class Command(val command: String) : SettingsDialogState
}

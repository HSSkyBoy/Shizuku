package moe.shizuku.manager.home

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.ui.theme.ShizukuComposeTheme
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.html.text.HtmlCompat
import androidx.core.net.toUri

@Composable
fun HomeComposeScreen(
    status: ServiceStatus?,
    grantedCount: Int?,
    onOpenSettings: () -> Unit,
    onStopService: () -> Unit,
    onManageApps: () -> Unit,
    onOpenTerminal: () -> Unit,
    onStartRoot: () -> Unit,
    onRestartRoot: () -> Unit,
    onOpenWirelessGuide: () -> Unit,
    onPairWireless: () -> Unit,
    onStartWirelessAdb: () -> Unit,
    onCopyAdbCommand: () -> Unit,
    onSendAdbCommand: () -> Unit,
    onOpenAdbPermissionHelp: () -> Unit,
    onOpenLearnMore: () -> Unit
) {
    ShizukuComposeTheme {
        HomeScreenContent(
            status = status,
            grantedCount = grantedCount,
            onOpenSettings = onOpenSettings,
            onStopService = onStopService,
            onManageApps = onManageApps,
            onOpenTerminal = onOpenTerminal,
            onStartRoot = onStartRoot,
            onRestartRoot = onRestartRoot,
            onOpenWirelessGuide = onOpenWirelessGuide,
            onPairWireless = onPairWireless,
            onStartWirelessAdb = onStartWirelessAdb,
            onCopyAdbCommand = onCopyAdbCommand,
            onSendAdbCommand = onSendAdbCommand,
            onOpenAdbPermissionHelp = onOpenAdbPermissionHelp,
            onOpenLearnMore = onOpenLearnMore
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent(
    status: ServiceStatus?,
    grantedCount: Int?,
    onOpenSettings: () -> Unit,
    onStopService: () -> Unit,
    onManageApps: () -> Unit,
    onOpenTerminal: () -> Unit,
    onStartRoot: () -> Unit,
    onRestartRoot: () -> Unit,
    onOpenWirelessGuide: () -> Unit,
    onPairWireless: () -> Unit,
    onStartWirelessAdb: () -> Unit,
    onCopyAdbCommand: () -> Unit,
    onSendAdbCommand: () -> Unit,
    onOpenAdbPermissionHelp: () -> Unit,
    onOpenLearnMore: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
            .orEmpty()
    }
    val items = buildHomeItems(
        context = context,
        status = status,
        grantedCount = grantedCount,
        onManageApps = onManageApps,
        onOpenTerminal = onOpenTerminal,
        onStartRoot = onStartRoot,
        onRestartRoot = onRestartRoot,
        onShowAdbCommand = { dialog = HomeDialog.AdbCommand },
        onOpenWirelessGuide = onOpenWirelessGuide,
        onPairWireless = onPairWireless,
        onStartWirelessAdb = onStartWirelessAdb,
        onOpenAdbPermissionHelp = onOpenAdbPermissionHelp,
        onOpenLearnMore = onOpenLearnMore
    )

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_about)) },
                            onClick = {
                                menuExpanded = false
                                dialog = HomeDialog.About
                            }
                        )
                        if (status?.isRunning == true) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_stop)) },
                                onClick = {
                                    menuExpanded = false
                                    dialog = HomeDialog.Stop
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
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
            items(items) { item ->
                when (item) {
                    is HomeUiItem.Status -> StatusCard(item)
                    is HomeUiItem.Action -> ActionCard(item)
                }
            }
        }
    }

    when (val state = dialog) {
        HomeDialog.About -> {
            AlertDialog(
                onDismissRequest = { dialog = null },
                confirmButton = {
                    TextButton(onClick = { dialog = null }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                title = { Text(stringResource(R.string.action_about)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        Text(text = versionName)
                        LinkRow(
                            label = plainText(context.getString(R.string.about_view_source_code, "GitHub")),
                            url = "https://github.com/HSSkyBoy/Shizuku"
                        )
                        LinkRow(
                            label = plainText(context.getString(R.string.about_follow_channel, "t.me/np_nbcn")),
                            url = "https://t.me/np_nbcn"
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.extraLarge
            )
        }

        HomeDialog.Stop -> {
            AlertDialog(
                onDismissRequest = { dialog = null },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = null
                        onStopService()
                    }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialog = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
                title = { Text(stringResource(R.string.action_stop)) },
                text = { Text(stringResource(R.string.dialog_stop_message)) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.extraLarge
            )
        }

        HomeDialog.AdbCommand -> {
            AlertDialog(
                onDismissRequest = { dialog = null },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = null
                        onCopyAdbCommand()
                    }) {
                        Text(stringResource(R.string.home_adb_dialog_view_command_copy_button))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        dialog = null
                        onSendAdbCommand()
                    }) {
                        Text(stringResource(R.string.home_adb_dialog_view_command_button_send))
                    }
                },
                title = { Text(stringResource(R.string.home_adb_button_view_command)) },
                text = {
                    Text(
                        text = Starter.adbCommand,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.extraLarge
            )
        }

        null -> Unit
    }
}

@Composable
private fun LinkRow(label: String, url: String) {
    val context = LocalContext.current
    Text(
        text = label,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        text = url,
        modifier = Modifier.clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        },
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.bodySmall
    )
}

private enum class HomeDialog {
    About,
    Stop,
    AdbCommand
}

private fun buildHomeItems(
    context: android.content.Context,
    status: ServiceStatus?,
    grantedCount: Int?,
    onManageApps: () -> Unit,
    onOpenTerminal: () -> Unit,
    onStartRoot: () -> Unit,
    onRestartRoot: () -> Unit,
    onShowAdbCommand: () -> Unit,
    onOpenWirelessGuide: () -> Unit,
    onPairWireless: () -> Unit,
    onStartWirelessAdb: () -> Unit,
    onOpenAdbPermissionHelp: () -> Unit,
    onOpenLearnMore: () -> Unit
): List<HomeUiItem> {
    val resolvedStatus = status ?: ServiceStatus()
    val running = resolvedStatus.isRunning
    val items = mutableListOf<HomeUiItem>()

    items += HomeUiItem.Status(
        title = if (running) {
            context.getString(R.string.home_status_service_is_running, context.getString(R.string.app_name))
        } else {
            context.getString(R.string.home_status_service_not_running, context.getString(R.string.app_name))
        },
        summary = if (running) {
            context.getString(
                R.string.home_status_service_version,
                if (resolvedStatus.uid == 0) "root" else "adb",
                resolvedStatus.versionName
            )
        } else {
            ""
        },
        running = running
    )

    if (resolvedStatus.permission) {
        items += HomeUiItem.Action(
            title = context.resources.getQuantityString(
                R.plurals.home_app_management_authorized_apps_count,
                grantedCount ?: 0,
                grantedCount ?: 0
            ),
            summary = if (running) {
                context.getString(R.string.home_app_management_view_authorized_apps)
            } else {
                context.getString(R.string.home_status_service_not_running, context.getString(R.string.app_name))
            },
            icon = Icons.Outlined.Security,
            enabled = running,
            onClick = onManageApps
        )
        items += HomeUiItem.Action(
            title = context.getString(R.string.home_terminal_title),
            summary = if (running) {
                context.getString(R.string.home_terminal_description)
            } else {
                context.getString(R.string.home_status_service_not_running, context.getString(R.string.app_name))
            },
            icon = Icons.Outlined.Terminal,
            enabled = running,
            onClick = onOpenTerminal
        )
    }

    if (running && !resolvedStatus.permission) {
        items += HomeUiItem.Action(
            title = context.getString(R.string.home_adb_is_limited_title),
            summary = context.getString(R.string.home_adb_is_limited_description),
            icon = Icons.Outlined.Warning,
            enabled = true,
            tonal = false,
            primaryActionLabel = context.getString(R.string.home_adb_button_view_help),
            onPrimaryAction = onOpenAdbPermissionHelp
        )
    }

    if (UserHandleCompat.myUserId() == 0) {
        val root = EnvironmentUtils.isRooted()
        val rootRestart = running && resolvedStatus.uid == 0
        if (root) {
            items += rootItem(context, running, rootRestart, onStartRoot, onRestartRoot)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || EnvironmentUtils.getAdbTcpPort() > 0) {
            items += HomeUiItem.Action(
                title = context.getString(R.string.home_wireless_adb_title),
                summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    plainText(context.getString(R.string.home_wireless_adb_description))
                } else {
                    plainText(context.getString(R.string.home_wireless_adb_description_pre_11))
                },
                icon = Icons.Outlined.Wifi,
                enabled = true,
                primaryActionLabel = context.getString(R.string.home_root_button_start),
                secondaryActionLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.getString(R.string.adb_pairing) else null,
                tertiaryActionLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.getString(R.string.home_wireless_adb_view_guide_button) else null,
                onPrimaryAction = onStartWirelessAdb,
                onSecondaryAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) onPairWireless else null,
                onTertiaryAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) onOpenWirelessGuide else null
            )
        }

        items += HomeUiItem.Action(
            title = context.getString(R.string.home_adb_title),
            summary = plainText(context.getString(R.string.home_adb_description, Helps.ADB.get())),
            icon = Icons.Outlined.Computer,
            enabled = true,
            primaryActionLabel = context.getString(R.string.home_adb_button_view_command),
            onPrimaryAction = onShowAdbCommand
        )

        if (!root) {
            items += rootItem(context, running, rootRestart, onStartRoot, onRestartRoot)
        }
    }

    items += HomeUiItem.Action(
        title = context.getString(R.string.home_learn_more_title),
        summary = context.getString(R.string.home_learn_more_description),
        icon = Icons.Outlined.Info,
        enabled = true,
        onClick = onOpenLearnMore
    )
    return items
}

private fun rootItem(
    context: android.content.Context,
    running: Boolean,
    rootRestart: Boolean,
    onStartRoot: () -> Unit,
    onRestartRoot: () -> Unit
) = HomeUiItem.Action(
    title = context.getString(R.string.home_root_title),
    summary = plainText(
        buildString {
            append(context.getString(R.string.home_root_description, "Don't kill my app!"))
            if (running) {
                append("<p>")
                append(
                    context.getString(
                        R.string.home_root_description_sui,
                        "Sui",
                        "Sui"
                    )
                )
            }
        }
    ),
    icon = Icons.Outlined.PlayArrow,
    enabled = true,
    primaryActionLabel = if (rootRestart) context.getString(R.string.home_root_button_restart) else context.getString(R.string.home_root_button_start),
    onPrimaryAction = if (rootRestart) onRestartRoot else onStartRoot
)

@Composable
private fun StatusCard(item: HomeUiItem.Status) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = (if (item.running) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.errorContainer
            }).copy(alpha = 0.7f),
            contentColor = if (item.running) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.running) Icons.Outlined.Link else Icons.Outlined.Warning,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (item.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .fillMaxWidth()
            ) {
                Text(item.title, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Text(item.summary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ActionCard(item: HomeUiItem.Action) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.onClick != null && item.enabled) { item.onClick?.invoke() },
        colors = CardDefaults.cardColors(
            containerColor = (if (item.tonal) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.errorContainer
            }).copy(alpha = 0.7f),
            contentColor = if (item.tonal) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = if (item.tonal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(item.summary, style = MaterialTheme.typography.bodyMedium)
            if (item.primaryActionLabel != null || item.secondaryActionLabel != null || item.tertiaryActionLabel != null) {
                Spacer(modifier = Modifier.height(18.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item.tertiaryActionLabel?.let { label ->
                        OutlinedButton(onClick = { item.onTertiaryAction?.invoke() }) {
                            Text(label)
                        }
                    }
                    item.secondaryActionLabel?.let { label ->
                        OutlinedButton(onClick = { item.onSecondaryAction?.invoke() }) {
                            Text(label)
                        }
                    }
                    item.primaryActionLabel?.let { label ->
                        Button(onClick = { item.onPrimaryAction?.invoke() }) {
                            Text(label)
                        }
                    }
                }
            } else if (item.onClick != null && item.enabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_open),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = stringResource(R.string.action_open),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Immutable
private sealed interface HomeUiItem {
    data class Status(
        val title: String,
        val summary: String,
        val running: Boolean
    ) : HomeUiItem

    data class Action(
        val title: String,
        val summary: String,
        val icon: ImageVector,
        val enabled: Boolean,
        val tonal: Boolean = true,
        val onClick: (() -> Unit)? = null,
        val primaryActionLabel: String? = null,
        val secondaryActionLabel: String? = null,
        val tertiaryActionLabel: String? = null,
        val onPrimaryAction: (() -> Unit)? = null,
        val onSecondaryAction: (() -> Unit)? = null,
        val onTertiaryAction: (() -> Unit)? = null
    ) : HomeUiItem
}

private fun plainText(value: String): String {
    return HtmlCompat.fromHtml(value).toString().replace(Regex("\\s+"), " ").trim()
}

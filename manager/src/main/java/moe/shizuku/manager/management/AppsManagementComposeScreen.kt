package moe.shizuku.manager.management

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.ui.theme.ShizukuComposeTheme
import moe.shizuku.manager.utils.AppIconCache
import moe.shizuku.manager.utils.ShizukuSystemApis
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.html.text.HtmlCompat

@Composable
fun ApplicationManagementComposeScreen(
    packages: List<PackageInfo>,
    onNavigateUp: () -> Unit,
    onTogglePackage: (PackageInfo) -> ToggleResult,
    onBatchToggle: (List<PackageInfo>, Boolean) -> ToggleResult
) {
    ShizukuComposeTheme {
        ApplicationManagementContent(
            packages = packages,
            onNavigateUp = onNavigateUp,
            onTogglePackage = onTogglePackage,
            onBatchToggle = onBatchToggle
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplicationManagementContent(
    packages: List<PackageInfo>,
    onNavigateUp: () -> Unit,
    onTogglePackage: (PackageInfo) -> ToggleResult,
    onBatchToggle: (List<PackageInfo>, Boolean) -> ToggleResult
) {
    var dialogState by remember { mutableStateOf<ManagementDialogState?>(null) }
    val grantStates = remember { mutableStateMapOf<String, Boolean>() }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedKeys = remember { mutableStateMapOf<String, Boolean>() }
    var showMenu by remember { mutableStateOf(false) }

    val validPackages = remember(packages) {
        packages.filter { it.applicationInfo != null }
    }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedKeys.clear()
    }

    LaunchedEffect(packages) {
        val currentKeys = validPackages.mapNotNull { packageInfo ->
            val uid = packageInfo.applicationInfo?.uid ?: return@mapNotNull null
            val key = packageGrantKey(packageInfo.packageName, uid)
            grantStates[key] = AuthorizationManager.granted(packageInfo.packageName, uid)
            key
        }.toSet()
        grantStates.keys.removeAll { it !in currentKeys }
        selectedKeys.keys.removeAll { it !in currentKeys }
    }

    val selectedCount = selectedKeys.values.count { it }
    val allSelected = validPackages.isNotEmpty() && validPackages.all {
        val key = packageGrantKey(it.packageName, it.applicationInfo!!.uid)
        selectedKeys[key] == true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(stringResource(R.string.batch_selected_count, selectedCount))
                    } else {
                        Text(stringResource(R.string.home_app_management_title))
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedKeys.clear()
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cancel)
                            )
                        }
                    } else {
                        IconButton(onClick = onNavigateUp) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            if (allSelected) {
                                selectedKeys.clear()
                            } else {
                                validPackages.forEach {
                                    val key = packageGrantKey(it.packageName, it.applicationInfo!!.uid)
                                    selectedKeys[key] = true
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (allSelected) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
                                contentDescription = stringResource(if (allSelected) R.string.action_deselect_all else R.string.action_select_all)
                            )
                        }
                    } else if (validPackages.isNotEmpty()) {
                        IconButton(onClick = { isSelectionMode = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Checklist,
                                contentDescription = stringResource(R.string.action_batch_management)
                            )
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.more_options)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_grant_all)) },
                                    onClick = {
                                        showMenu = false
                                        dialogState = ManagementDialogState.ConfirmGrantAll
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_revoke_all)) },
                                    onClick = {
                                        showMenu = false
                                        dialogState = ManagementDialogState.ConfirmRevokeAll
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val enabled = selectedCount > 0

                        OutlinedButton(
                            onClick = {
                                val targets = validPackages.filter {
                                    val key = packageGrantKey(it.packageName, it.applicationInfo!!.uid)
                                    selectedKeys[key] == true
                                }
                                val result = onBatchToggle(targets, false)
                                targets.forEach {
                                    val uid = it.applicationInfo!!.uid
                                    grantStates[packageGrantKey(it.packageName, uid)] = false
                                }
                                isSelectionMode = false
                                selectedKeys.clear()
                                if (result == ToggleResult.AdbLimited) {
                                    dialogState = ManagementDialogState.AdbLimited
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = enabled
                        ) {
                            Text(stringResource(R.string.action_revoke_selected))
                        }

                        Button(
                            onClick = {
                                val targets = validPackages.filter {
                                    val key = packageGrantKey(it.packageName, it.applicationInfo!!.uid)
                                    selectedKeys[key] == true
                                }
                                val result = onBatchToggle(targets, true)
                                targets.forEach {
                                    val uid = it.applicationInfo!!.uid
                                    grantStates[packageGrantKey(it.packageName, uid)] = AuthorizationManager.granted(it.packageName, uid)
                                }
                                isSelectionMode = false
                                selectedKeys.clear()
                                if (result == ToggleResult.AdbLimited) {
                                    dialogState = ManagementDialogState.AdbLimited
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = enabled
                        ) {
                            Text(stringResource(R.string.action_grant_selected))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.fillMaxSize()) {
            if (packages.isEmpty()) {
                EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        end = 20.dp,
                        bottom = innerPadding.calculateBottomPadding() + 20.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(validPackages, key = { it.packageName + "#" + it.applicationInfo!!.uid }) { packageInfo ->
                        val uid = packageInfo.applicationInfo!!.uid
                        val grantKey = packageGrantKey(packageInfo.packageName, uid)
                        val granted = grantStates[grantKey] ?: AuthorizationManager.granted(packageInfo.packageName, uid)
                        val isSelected = selectedKeys[grantKey] == true

                        AppCard(
                            packageInfo = packageInfo,
                            granted = granted,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onToggle = {
                                when (onTogglePackage(packageInfo)) {
                                    ToggleResult.Success -> grantStates[grantKey] = AuthorizationManager.granted(packageInfo.packageName, uid)
                                    ToggleResult.AdbLimited -> dialogState = ManagementDialogState.AdbLimited
                                }
                            },
                            onSelectToggle = {
                                if (isSelected) {
                                    selectedKeys.remove(grantKey)
                                } else {
                                    selectedKeys[grantKey] = true
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedKeys[grantKey] = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    when (dialogState) {
        ManagementDialogState.AdbLimited -> {
            AlertDialog(
                onDismissRequest = { dialogState = null },
                confirmButton = {
                    TextButton(onClick = { dialogState = null }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                title = { Text(stringResource(R.string.app_management_dialog_adb_is_limited_title)) },
                text = { Text(plainText(stringResource(R.string.app_management_dialog_adb_is_limited_message, Helps.ADB.get()))) },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                icon = {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            )
        }
        ManagementDialogState.ConfirmGrantAll -> {
            AlertDialog(
                onDismissRequest = { dialogState = null },
                confirmButton = {
                    TextButton(onClick = {
                        dialogState = null
                        val result = onBatchToggle(validPackages, true)
                        validPackages.forEach {
                            val uid = it.applicationInfo!!.uid
                            grantStates[packageGrantKey(it.packageName, uid)] = AuthorizationManager.granted(it.packageName, uid)
                        }
                        if (result == ToggleResult.AdbLimited) {
                            dialogState = ManagementDialogState.AdbLimited
                        }
                    }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogState = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                title = { Text(stringResource(R.string.dialog_confirm_grant_all_title)) },
                text = { Text(stringResource(R.string.dialog_confirm_grant_all_message)) }
            )
        }
        ManagementDialogState.ConfirmRevokeAll -> {
            AlertDialog(
                onDismissRequest = { dialogState = null },
                confirmButton = {
                    TextButton(onClick = {
                        dialogState = null
                        val result = onBatchToggle(validPackages, false)
                        validPackages.forEach {
                            val uid = it.applicationInfo!!.uid
                            grantStates[packageGrantKey(it.packageName, uid)] = false
                        }
                        if (result == ToggleResult.AdbLimited) {
                            dialogState = ManagementDialogState.AdbLimited
                        }
                    }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogState = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                title = { Text(stringResource(R.string.dialog_confirm_revoke_all_title)) },
                text = { Text(stringResource(R.string.dialog_confirm_revoke_all_message)) }
            )
        }
        null -> {}
    }
}

private fun packageGrantKey(packageName: String, uid: Int): String {
    return "$packageName#$uid"
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Surface(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.home_app_management_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCard(
    packageInfo: PackageInfo,
    granted: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onSelectToggle: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val applicationInfo = packageInfo.applicationInfo ?: return
    val userId = UserHandleCompat.getUserId(applicationInfo.uid)
    val label = if (userId != UserHandleCompat.myUserId()) {
        val userInfo = ShizukuSystemApis.getUserInfo(userId)
        "${applicationInfo.loadLabel(context.packageManager)} - ${userInfo.name} ($userId)"
    } else {
        applicationInfo.loadLabel(context.packageManager).toString()
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelectionMode && isSelected) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectToggle()
                    } else {
                        onToggle()
                    }
                },
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                applicationInfo = applicationInfo,
                userId = userId,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = applicationInfo.packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSelectionMode) {
                        Spacer(modifier = Modifier.size(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = stringResource(if (granted) R.string.grant_dialog_button_allow_always else R.string.grant_dialog_button_deny),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (applicationInfo.metaData?.getBoolean("moe.shizuku.client.V3_REQUIRES_ROOT") == true) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.app_management_item_summary_requires_root),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.size(16.dp))
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() }
                )
            } else {
                Switch(
                    checked = granted,
                    onCheckedChange = { onToggle() }
                )
            }
        }
    }
}

@Composable
private fun AppIcon(
    applicationInfo: ApplicationInfo,
    userId: Int,
    modifier: Modifier = Modifier
) {
    val bitmap by rememberAppIconBitmap(
        applicationInfo = applicationInfo,
        userId = userId,
        size = 40.dp
    )

    Box(
        modifier = modifier.clip(MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun rememberAppIconBitmap(
    applicationInfo: ApplicationInfo,
    userId: Int,
    size: Dp
) : androidx.compose.runtime.State<Bitmap?> {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }

    return produceState<Bitmap?>(
        initialValue = null,
        key1 = applicationInfo.packageName,
        key2 = userId,
        key3 = sizePx
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                AppIconCache.getOrLoadBitmap(context, applicationInfo, userId, sizePx)
            }.getOrNull()
        }
    }
}

@Immutable
enum class ToggleResult {
    Success,
    AdbLimited
}

private enum class ManagementDialogState {
    AdbLimited,
    ConfirmGrantAll,
    ConfirmRevokeAll
}

private fun plainText(value: String): String {
    return HtmlCompat.fromHtml(value).toString().replace(Regex("\\s+"), " ").trim()
}

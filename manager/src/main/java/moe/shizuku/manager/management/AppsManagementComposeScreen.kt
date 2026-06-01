package moe.shizuku.manager.management

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
    onTogglePackage: (PackageInfo) -> ToggleResult
) {
    ShizukuComposeTheme {
        ApplicationManagementContent(
            packages = packages,
            onNavigateUp = onNavigateUp,
            onTogglePackage = onTogglePackage
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplicationManagementContent(
    packages: List<PackageInfo>,
    onNavigateUp: () -> Unit,
    onTogglePackage: (PackageInfo) -> ToggleResult
) {
    var dialogState by remember { mutableStateOf<ManagementDialogState?>(null) }
    val grantStates = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(packages) {
        val currentKeys = packages.mapNotNull { packageInfo ->
            val uid = packageInfo.applicationInfo?.uid ?: return@mapNotNull null
            val key = packageGrantKey(packageInfo.packageName, uid)
            grantStates[key] = AuthorizationManager.granted(packageInfo.packageName, uid)
            key
        }.toSet()
        grantStates.keys.removeAll { it !in currentKeys }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_app_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
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
                    items(packages.filter { it.applicationInfo != null }, key = { it.packageName + "#" + it.applicationInfo!!.uid }) { packageInfo ->
                        val uid = packageInfo.applicationInfo!!.uid
                        val grantKey = packageGrantKey(packageInfo.packageName, uid)
                        val granted = grantStates[grantKey] ?: AuthorizationManager.granted(packageInfo.packageName, uid)
                        AppCard(
                            packageInfo = packageInfo,
                            granted = granted,
                            onToggle = {
                                when (onTogglePackage(packageInfo)) {
                                    ToggleResult.Success -> grantStates[grantKey] = AuthorizationManager.granted(packageInfo.packageName, uid)
                                    ToggleResult.AdbLimited -> dialogState = ManagementDialogState.AdbLimited
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (dialogState == ManagementDialogState.AdbLimited) {
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

@Composable
private fun AppCard(
    packageInfo: PackageInfo,
    granted: Boolean,
    onToggle: () -> Unit
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
        onClick = onToggle
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = applicationInfo.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (applicationInfo.metaData?.getBoolean("moe.shizuku.client.V3_REQUIRES_ROOT") == true) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.app_management_item_summary_requires_root),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = granted,
                onCheckedChange = { onToggle() }
            )
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
    AdbLimited
}

private fun plainText(value: String): String {
    return HtmlCompat.fromHtml(value).toString().replace(Regex("\\s+"), " ").trim()
}

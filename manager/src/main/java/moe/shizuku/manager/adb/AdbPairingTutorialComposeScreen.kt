package moe.shizuku.manager.adb

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LooksOne
import androidx.compose.material.icons.outlined.LooksTwo
import androidx.compose.material.icons.outlined.Looks3
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.theme.ShizukuComposeTheme

@Composable
fun AdbPairingTutorialComposeScreen(
    state: PairingTutorialState,
    showMiuiHint: Boolean,
    onNavigateUp: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onOpenNotificationOptions: () -> Unit,
    onRequestLocalNetworkPermission: () -> Unit
) {
    ShizukuComposeTheme {
        AdbPairingTutorialContent(
            state = state,
            showMiuiHint = showMiuiHint,
            onNavigateUp = onNavigateUp,
            onOpenDeveloperOptions = onOpenDeveloperOptions,
            onOpenNotificationOptions = onOpenNotificationOptions,
            onRequestLocalNetworkPermission = onRequestLocalNetworkPermission
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdbPairingTutorialContent(
    state: PairingTutorialState,
    showMiuiHint: Boolean,
    onNavigateUp: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onOpenNotificationOptions: () -> Unit,
    onRequestLocalNetworkPermission: () -> Unit
) {
    val readyForPairing = state.notificationEnabled && state.localNetworkPermissionGranted && !state.pairingServiceStartFailed
    val steps = listOf(
        PairingStep(
            icon = Icons.Outlined.LooksOne,
            title = stringResource(R.string.adb_pairing_tutorial_content_steps),
            summary = stringResource(R.string.adb_pairing_tutorial_content_left_is_clickable),
            action = stringResource(R.string.development_settings),
            onAction = onOpenDeveloperOptions
        ),
        PairingStep(
            icon = Icons.Outlined.LooksTwo,
            title = stringResource(R.string.adb_pairing_tutorial_content_enter_pairing_code)
        ),
        PairingStep(
            icon = Icons.Outlined.Looks3,
            title = stringResource(R.string.adb_pairing_tutorial_content_finish)
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.adb_pairing_tutorial_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                if (state.notificationEnabled) {
                    item {
                        CalloutCard(
                            icon = Icons.Outlined.NotificationsActive,
                            text = stringResource(R.string.adb_pairing_tutorial_content_notification)
                        )
                    }
                } else {
                    item {
                        WarningCard(
                            title = stringResource(R.string.adb_pairing_tutorial_content_notification_blocked),
                            action = stringResource(R.string.notification_settings),
                            onAction = onOpenNotificationOptions
                        )
                    }
                }

                if (state.notificationEnabled) {
                    item {
                        if (state.localNetworkPermissionGranted) {
                            CalloutCard(
                                icon = Icons.Outlined.Info,
                                text = stringResource(R.string.adb_pairing_tutorial_content_network),
                                secondaryText = stringResource(R.string.adb_pairing_tutorial_content_network_limation_not_foreground)
                            )
                        } else {
                            WarningCard(
                                title = stringResource(R.string.adb_pairing_tutorial_content_network_blocked),
                                action = stringResource(android.R.string.ok),
                                onAction = onRequestLocalNetworkPermission
                            )
                        }
                    }
                }

                if (state.pairingServiceStartFailed) {
                    item {
                        WarningCard(
                            title = stringResource(R.string.adb_pairing_tutorial_content_pairing_service_failed),
                            action = stringResource(R.string.action_retry),
                            onAction = onRequestLocalNetworkPermission
                        )
                    }
                }

                if (showMiuiHint) {
                    item {
                        WarningCard(
                            title = stringResource(R.string.adb_pairing_tutorial_content_miui),
                            body = stringResource(R.string.adb_pairing_tutorial_content_miui_2)
                        )
                    }
                }

                if (readyForPairing) {
                    items(steps) { step ->
                        StepCard(step)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalloutCard(icon: ImageVector, text: String, secondaryText: String? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (secondaryText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(secondaryText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun WarningCard(
    title: String,
    body: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(modifier = Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                    if (body != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            if (action != null && onAction != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onAction) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(action)
                }
            }
        }
    }
}

@Composable
private fun StepCard(step: PairingStep) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(step.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(step.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (step.summary != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(step.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                if (step.action != null && step.onAction != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = step.onAction) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(step.action)
                    }
                }
            }
        }
    }
}

@Immutable
private data class PairingStep(
    val icon: ImageVector,
    val title: String,
    val summary: String? = null,
    val action: String? = null,
    val onAction: (() -> Unit)? = null
)

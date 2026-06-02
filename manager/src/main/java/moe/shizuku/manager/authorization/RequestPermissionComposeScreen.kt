package moe.shizuku.manager.authorization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.ui.theme.ShizukuComposeTheme

@Composable
fun RequestPermissionComposeScreen(
    title: String,
    primaryLabel: String,
    secondaryLabel: String,
    tertiaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onTertiary: () -> Unit
) {
    ShizukuComposeTheme {
        Surface(color = androidx.compose.ui.graphics.Color.Transparent) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    PermissionActionButton(primaryLabel, onPrimary)
                    PermissionActionButton(secondaryLabel, onSecondary)
                    PermissionActionButton(tertiaryLabel, onTertiary)
                }
            }
        }
    }
}

@Composable
private fun PermissionActionButton(
    label: String,
    onClick: () -> Unit
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label)
    }
}

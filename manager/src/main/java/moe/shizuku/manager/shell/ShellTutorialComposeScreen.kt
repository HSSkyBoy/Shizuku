package moe.shizuku.manager.shell

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LooksOne
import androidx.compose.material.icons.outlined.LooksTwo
import androidx.compose.material.icons.outlined.Looks3
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.theme.ShizukuComposeTheme
import moe.shizuku.manager.utils.CustomTabsHelper
import rikka.html.text.HtmlCompat

@Composable
fun ShellTutorialComposeScreen(
    shName: String,
    dexName: String,
    onNavigateUp: () -> Unit,
    onExportFiles: () -> Unit,
    onOpenGuide: () -> Unit
) {
    ShizukuComposeTheme {
        ShellTutorialContent(
            shName = shName,
            dexName = dexName,
            onNavigateUp = onNavigateUp,
            onExportFiles = onExportFiles,
            onOpenGuide = onOpenGuide
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellTutorialContent(
    shName: String,
    dexName: String,
    onNavigateUp: () -> Unit,
    onExportFiles: () -> Unit,
    onOpenGuide: () -> Unit
) {
    val context = LocalContext.current
    val steps = listOf(
        TutorialStep(
            icon = Icons.Outlined.LooksOne,
            title = plainText(context.getString(R.string.terminal_tutorial_1, mono(shName), mono(dexName))),
            summary = context.getString(R.string.terminal_tutorial_1_description),
            primaryAction = stringResource(R.string.terminal_export_files),
            onPrimaryAction = onExportFiles
        ),
        TutorialStep(
            icon = Icons.Outlined.LooksTwo,
            title = plainText(context.getString(R.string.terminal_tutorial_2, mono(shName))),
            summary = plainText(
                context.getString(
                    R.string.terminal_tutorial_2_description,
                    "Termux",
                    mono("PKG"),
                    mono("com.termux"),
                    mono("com.termux")
                )
            )
        ),
        TutorialStep(
            icon = Icons.Outlined.Looks3,
            title = plainText(context.getString(R.string.terminal_tutorial_3, mono("sh $shName"))),
            summary = plainText(context.getString(R.string.terminal_tutorial_3_description, mono(shName), mono("PATH")))
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_terminal_title)) },
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
                item {
                    CalloutCard(
                        title = stringResource(R.string.home_terminal_title),
                        text = plainText(context.getString(R.string.rish_description, mono(shName))),
                        onClick = onOpenGuide
                    )
                }
                items(steps) { step ->
                    StepCard(step)
                }
            }
        }
    }
}

@Composable
private fun CalloutCard(
    title: String,
    text: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Outlined.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.size(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = stringResource(R.string.action_open),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StepCard(step: TutorialStep) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(step.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(step.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Text(step.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (step.primaryAction != null && step.onPrimaryAction != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = step.onPrimaryAction) {
                        Text(step.primaryAction)
                    }
                }
            }
        }
    }
}

@Immutable
private data class TutorialStep(
    val icon: ImageVector,
    val title: String,
    val summary: String,
    val primaryAction: String? = null,
    val onPrimaryAction: (() -> Unit)? = null
)

private fun mono(value: String): String = "<font face=\"monospace\">$value</font>"

private fun plainText(value: String): String {
    return HtmlCompat.fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace(Regex("\\s+"), " ")
        .trim()
}

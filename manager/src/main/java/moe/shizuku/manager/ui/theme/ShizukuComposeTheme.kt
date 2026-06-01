package moe.shizuku.manager.ui.theme

import android.app.Activity
import android.view.Window
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import moe.shizuku.manager.app.ThemeHelper

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F4F8B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondaryContainer = Color(0xFFDDE3F1),
    onSecondaryContainer = Color(0xFF151B26),
    tertiaryContainer = Color(0xFFDCEBFF),
    onTertiaryContainer = Color(0xFF001F2F),
    surface = Color(0xFFF7F9FD),
    surfaceVariant = Color(0xFFE1E6F0),
    surfaceContainer = Color(0xFFEFF3FA),
    surfaceContainerHigh = Color(0xFFE8ECF4),
    surfaceContainerLow = Color(0xFFF4F7FC),
    onSurface = Color(0xFF171C24),
    onSurfaceVariant = Color(0xFF434A56),
    outline = Color(0xFF737A87),
    outlineVariant = Color(0xFFC3C8D4),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003061),
    primaryContainer = Color(0xFF004787),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondaryContainer = Color(0xFF3A485D),
    onSecondaryContainer = Color(0xFFDDE3F1),
    tertiaryContainer = Color(0xFF204A63),
    onTertiaryContainer = Color(0xFFD0ECFF),
    surface = Color(0xFF101318),
    surfaceVariant = Color(0xFF434A56),
    surfaceContainer = Color(0xFF1A1F27),
    surfaceContainerHigh = Color(0xFF222730),
    surfaceContainerLow = Color(0xFF151920),
    onSurface = Color(0xFFECEFF7),
    onSurfaceVariant = Color(0xFFC3C8D4),
    outline = Color(0xFF8D94A1),
    outlineVariant = Color(0xFF434A56),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun ShizukuComposeTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colorScheme = if (ThemeHelper.isUsingSystemColor() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (useDarkTheme) DarkColors else LightColors
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            applyTransparentSystemBars(window)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ShizukuTypography,
        content = content
    )
}

@Suppress("DEPRECATION")
private fun applyTransparentSystemBars(window: Window) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()
    }
}

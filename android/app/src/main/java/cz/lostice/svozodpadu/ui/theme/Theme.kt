package cz.lostice.svozodpadu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import cz.lostice.svozodpadu.model.AppThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF002B40),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF4ADE80),
    onSecondary = Color(0xFF052E16),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFF334155)
)

private val OledBlackColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF002B40),
    primaryContainer = Color(0xFF082F49),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF4ADE80),
    onSecondary = Color(0xFF052E16),
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFF1A1A1A),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC)
)

private val ForestDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = Color(0xFFDCFCE7),
    secondary = Color(0xFFA3E635),
    onSecondary = Color(0xFF1A2E05),
    background = Color(0xFF07170E),
    surface = Color(0xFF0F291A),
    surfaceVariant = Color(0xFF1A3F29)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF075985),
    secondary = Color(0xFF16A34A),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F5F9)
)

@Composable
fun SvozOdpaduTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        AppThemeMode.SYSTEM -> if (systemDark) DarkColorScheme else LightColorScheme
        AppThemeMode.LIGHT -> LightColorScheme
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.OLED -> OledBlackColorScheme
        AppThemeMode.FOREST -> ForestDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

package cz.lostice.svozodpadu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

private fun buildCustomColorScheme(primaryRgb: Int, backgroundRgb: Int) = run {
    val primaryColor = Color(0xFF000000 or (primaryRgb.toLong() and 0xFFFFFF))
    val bgColor = Color(0xFF000000 or (backgroundRgb.toLong() and 0xFFFFFF))
    val isDarkBg = bgColor.luminance() < 0.45f
    val onPrimaryColor = if (primaryColor.luminance() > 0.5f) Color(0xFF0A0A0A) else Color.White

    // Odvození barvy karet (surface) a kontejneru z pozadí a primární barvy
    val surfaceColor = if (isDarkBg) {
        Color(
            red = (bgColor.red + 0.06f).coerceAtMost(1f),
            green = (bgColor.green + 0.06f).coerceAtMost(1f),
            blue = (bgColor.blue + 0.06f).coerceAtMost(1f)
        )
    } else {
        Color(
            red = (bgColor.red - 0.04f).coerceAtLeast(0f),
            green = (bgColor.green - 0.04f).coerceAtLeast(0f),
            blue = (bgColor.blue - 0.04f).coerceAtLeast(0f)
        )
    }

    val surfaceVarColor = if (isDarkBg) {
        Color(
            red = (bgColor.red + 0.12f).coerceAtMost(1f),
            green = (bgColor.green + 0.12f).coerceAtMost(1f),
            blue = (bgColor.blue + 0.12f).coerceAtMost(1f)
        )
    } else {
        Color(
            red = (bgColor.red - 0.09f).coerceAtLeast(0f),
            green = (bgColor.green - 0.09f).coerceAtLeast(0f),
            blue = (bgColor.blue - 0.09f).coerceAtLeast(0f)
        )
    }

    val primaryContainerColor = Color(
        red = (bgColor.red * 0.7f + primaryColor.red * 0.3f).coerceIn(0f, 1f),
        green = (bgColor.green * 0.7f + primaryColor.green * 0.3f).coerceIn(0f, 1f),
        blue = (bgColor.blue * 0.7f + primaryColor.blue * 0.3f).coerceIn(0f, 1f)
    )
    val onPrimaryContainerColor = if (primaryContainerColor.luminance() > 0.5f) Color(0xFF0F172A) else Color(0xFFF8FAFC)

    if (isDarkBg) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = onPrimaryColor,
            primaryContainer = primaryContainerColor,
            onPrimaryContainer = onPrimaryContainerColor,
            secondary = primaryColor,
            onSecondary = onPrimaryColor,
            background = bgColor,
            onBackground = Color(0xFFF8FAFC),
            surface = surfaceColor,
            onSurface = Color(0xFFF8FAFC),
            surfaceVariant = surfaceVarColor,
            onSurfaceVariant = Color(0xFFCBD5E1)
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = onPrimaryColor,
            primaryContainer = primaryContainerColor,
            onPrimaryContainer = onPrimaryContainerColor,
            secondary = primaryColor,
            onSecondary = onPrimaryColor,
            background = bgColor,
            onBackground = Color(0xFF0F172A),
            surface = surfaceColor,
            onSurface = Color(0xFF0F172A),
            surfaceVariant = surfaceVarColor,
            onSurfaceVariant = Color(0xFF475569)
        )
    }
}

@Composable
fun SvozOdpaduTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    customPrimaryRgb: Int = 0x10B981,
    customBackgroundRgb: Int = 0x07170E,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        AppThemeMode.SYSTEM -> if (systemDark) DarkColorScheme else LightColorScheme
        AppThemeMode.LIGHT -> LightColorScheme
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.OLED -> OledBlackColorScheme
        AppThemeMode.CUSTOM -> buildCustomColorScheme(customPrimaryRgb, customBackgroundRgb)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

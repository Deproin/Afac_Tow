package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = OnPrimaryBlue,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = SecondaryTeal,
    onSecondary = OnSecondaryTeal,
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = TertiarySlate,
    onTertiary = OnTertiarySlate,
    tertiaryContainer = Color(0xFF1E293B),
    onTertiaryContainer = Color(0xFFF1F5F9),
    background = DarkBackground,
    onBackground = Color(0xFFF1F5F9),
    surface = DarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = DarkOutline,
    error = LossRed,
    onError = Color.White,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimaryBlue,
    primaryContainer = PrimaryContainerBlue,
    onPrimaryContainer = OnPrimaryContainerBlue,
    secondary = SecondaryTeal,
    onSecondary = OnSecondaryTeal,
    secondaryContainer = SecondaryContainerTeal,
    onSecondaryContainer = OnSecondaryContainerTeal,
    tertiary = TertiarySlate,
    onTertiary = OnTertiarySlate,
    tertiaryContainer = TertiaryContainerSlate,
    onTertiaryContainer = OnTertiaryContainerSlate,
    background = LightBackground,
    onBackground = Color(0xFF0F172A),
    surface = LightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = LightOutline,
    error = LossRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

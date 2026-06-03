package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// CompositionLocal to seamlessly propagate manual dark/light status
val LocalThemeIsDark = compositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = StudyBlueCold,
    secondary = StudyGoldSoft,
    tertiary = StudySageSoft,
    background = MidnightSlate,
    surface = CardDarkBlue,
    onPrimary = MidnightSlate,
    onSecondary = MidnightSlate,
    onTertiary = MidnightSlate,
    onBackground = MilkyTextLight,
    onSurface = MilkyTextLight,
    surfaceVariant = Color(0xFF1E2640), // Rich cobalt slate border tone
    onSurfaceVariant = Color(0xFF94A3B8), // Muted blue-grey text
    primaryContainer = Color(0xFF1E1B4B), // Heavy indigo container depth
    onPrimaryContainer = Color(0xFFC7D2FE),
    secondaryContainer = Color(0xFF4C0519), // Heavy ruby rose container
    onSecondaryContainer = Color(0xFFFECDD3),
)

private val LightColorScheme = lightColorScheme(
    primary = ScholarBlue,
    secondary = ScholarAmber,
    tertiary = ScholarGreen,
    background = Color(0xFFF8FAFC), // Elegant soft slate off-white for superb card contrast
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = SlateTextDark,
    onSurface = SlateTextDark,
    surfaceVariant = Color(0xFFEEF2FF), // Soft indigo-wash border/card accent
    onSurfaceVariant = Color(0xFF4F46E5), // Prominent indigo secondary text
    primaryContainer = Color(0xFFE0E7FF), // Bright lavender mist
    onPrimaryContainer = Color(0xFF3730A3),
    secondaryContainer = Color(0xFFFEF3C7), // Bright gold mist
    onSecondaryContainer = Color(0xFF78350F),
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalThemeIsDark provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

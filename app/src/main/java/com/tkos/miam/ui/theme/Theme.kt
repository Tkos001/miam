package com.tkos.miam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tkos.miam.data.settings.AppTheme

// ---- Clair : crème / terracotta ----
private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFFB4552D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF6B5A4E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3DED2),
    onSecondaryContainer = Color(0xFF261811),
    tertiary = Color(0xFF5B6B3C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDEF2B6),
    onTertiaryContainer = Color(0xFF181F03),
    background = Color(0xFFFCF8F4),
    onBackground = Color(0xFF211A16),
    surface = Color(0xFFFCF8F4),
    onSurface = Color(0xFF211A16),
    surfaceVariant = Color(0xFFF2E1D7),
    onSurfaceVariant = Color(0xFF53433C),
    surfaceContainer = Color(0xFFF5EDE6),
    surfaceContainerHigh = Color(0xFFEFE7E0),
    surfaceContainerHighest = Color(0xFFE9E1DA),
    surfaceContainerLow = Color(0xFFF9F2EC),
    outline = Color(0xFF85736B),
    outlineVariant = Color(0xFFD8C2B7),
    error = Color(0xFFBA1A1A),
)

// ---- Sombre : neutre / ambre ----
private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFF2B84B),
    onPrimary = Color(0xFF3F2E00),
    primaryContainer = Color(0xFF5B4300),
    onPrimaryContainer = Color(0xFFFFDF99),
    secondary = Color(0xFFD6C4B4),
    onSecondary = Color(0xFF3A2E24),
    secondaryContainer = Color(0xFF52443A),
    onSecondaryContainer = Color(0xFFF3E0D0),
    tertiary = Color(0xFFB8CF8E),
    onTertiary = Color(0xFF253607),
    tertiaryContainer = Color(0xFF3B4D1C),
    onTertiaryContainer = Color(0xFFD4EBA8),
    background = Color(0xFF141414),
    onBackground = Color(0xFFE8E2DC),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFE8E2DC),
    surfaceVariant = Color(0xFF2A2724),
    onSurfaceVariant = Color(0xFFD0C4BB),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF1C1B1A),
    surfaceContainer = Color(0xFF211F1D),
    surfaceContainerHigh = Color(0xFF2B2927),
    surfaceContainerHighest = Color(0xFF363331),
    outline = Color(0xFF998E86),
    outlineVariant = Color(0xFF4A423D),
    error = Color(0xFFFFB4AB),
)

// ---- Mauve sombre ----
private val PurpleDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF14101C),
    onBackground = Color(0xFFE7E0EC),
    surface = Color(0xFF14101C),
    onSurface = Color(0xFFE7E0EC),
    surfaceVariant = Color(0xFF2E2838),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFF0E0A15),
    surfaceContainerLow = Color(0xFF1C1725),
    surfaceContainer = Color(0xFF211B2B),
    surfaceContainerHigh = Color(0xFF2B2536),
    surfaceContainerHighest = Color(0xFF362F42),
    outline = Color(0xFF948F9A),
    outlineVariant = Color(0xFF49444E),
    error = Color(0xFFFFB4AB),
)

// ---- Rouge sombre ----
private val RedDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB3AD),
    onPrimary = Color(0xFF5F1413),
    primaryContainer = Color(0xFF7D2B27),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB9),
    onSecondary = Color(0xFF442927),
    secondaryContainer = Color(0xFF5D3F3C),
    onSecondaryContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFFE2C28C),
    onTertiary = Color(0xFF402D05),
    tertiaryContainer = Color(0xFF594319),
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFF1B1011),
    onBackground = Color(0xFFF0DEDD),
    surface = Color(0xFF1B1011),
    onSurface = Color(0xFFF0DEDD),
    surfaceVariant = Color(0xFF3A2A2A),
    onSurfaceVariant = Color(0xFFD8C2C0),
    surfaceContainerLowest = Color(0xFF140B0C),
    surfaceContainerLow = Color(0xFF241819),
    surfaceContainer = Color(0xFF2A1C1D),
    surfaceContainerHigh = Color(0xFF352627),
    surfaceContainerHighest = Color(0xFF413031),
    outline = Color(0xFFA08C8B),
    outlineVariant = Color(0xFF524342),
    error = Color(0xFFFFB4AB),
)

val MiamShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

fun AppTheme.isDark(systemDark: Boolean): Boolean = when (this) {
    AppTheme.SYSTEM -> systemDark
    AppTheme.LIGHT -> false
    else -> true
}

@Composable
fun MiamTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val scheme = when (theme) {
        AppTheme.SYSTEM -> if (systemDark) DarkScheme else LightScheme
        AppTheme.LIGHT -> LightScheme
        AppTheme.DARK -> DarkScheme
        AppTheme.PURPLE_DARK -> PurpleDarkScheme
        AppTheme.RED_DARK -> RedDarkScheme
    }
    MaterialTheme(colorScheme = scheme, shapes = MiamShapes, typography = MiamTypography, content = content)
}

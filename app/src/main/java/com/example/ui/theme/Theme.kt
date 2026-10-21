package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PlumPrimaryDark,
    onPrimary = PlumOnPrimaryDark,
    primaryContainer = PlumPrimaryContainerDark,
    onPrimaryContainer = PlumOnPrimaryContainerDark,
    secondary = RoseGoldSecondaryDark,
    onSecondary = RoseGoldOnSecondaryDark,
    secondaryContainer = RoseGoldContainerDark,
    onSecondaryContainer = RoseGoldOnContainerDark,
    tertiary = GoldTertiaryDark,
    onTertiary = GoldOnTertiaryDark,
    tertiaryContainer = GoldTertiaryContainerDark,
    onTertiaryContainer = GoldOnTertiaryContainerDark,
    background = SalonBackgroundDark,
    onBackground = SalonOnBackgroundDark,
    surface = SalonSurfaceDark,
    onSurface = SalonOnSurfaceDark,
    surfaceVariant = SalonSurfaceVariantDark,
    onSurfaceVariant = SalonOnSurfaceVariantDark,
    outline = SalonOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = PlumPrimary,
    onPrimary = PlumOnPrimary,
    primaryContainer = PlumPrimaryContainer,
    onPrimaryContainer = PlumOnPrimaryContainer,
    secondary = RoseGoldSecondary,
    onSecondary = RoseGoldOnSecondary,
    secondaryContainer = RoseGoldContainer,
    onSecondaryContainer = RoseGoldOnContainer,
    tertiary = GoldTertiary,
    onTertiary = GoldOnTertiary,
    tertiaryContainer = GoldTertiaryContainer,
    onTertiaryContainer = GoldOnTertiaryContainer,
    background = SalonBackground,
    onBackground = SalonOnBackground,
    surface = SalonSurface,
    onSurface = SalonOnSurface,
    surfaceVariant = SalonSurfaceVariant,
    onSurfaceVariant = SalonOnSurfaceVariant,
    outline = SalonOutline
)

@Composable
fun SalonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our signature salon palette by default
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

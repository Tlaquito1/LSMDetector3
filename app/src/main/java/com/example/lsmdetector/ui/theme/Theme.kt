package com.example.lsmdetector.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SkinPrimaryDark,
    onPrimary = OnSkinPrimaryDark,
    primaryContainer = PeachContainerDark,
    onPrimaryContainer = OnPeachContainerDark,
    secondary = SageSecondaryDark,
    secondaryContainer = SageContainerDark,
    tertiary = LavenderTertiaryDark,
    tertiaryContainer = LavenderContainerDark,
    background = BlushBackgroundDark,
    surface = WarmSurfaceDark,
    surfaceVariant = WarmSurfaceVariantDark,
    onBackground = WarmInkDark,
    onSurface = WarmInkDark
)

private val LightColorScheme = lightColorScheme(
    primary = SkinPrimary,
    onPrimary = OnSkinPrimary,
    primaryContainer = PeachContainer,
    onPrimaryContainer = OnPeachContainer,
    secondary = SageSecondary,
    secondaryContainer = SageContainer,
    tertiary = LavenderTertiary,
    tertiaryContainer = LavenderContainer,
    background = BlushBackground,
    surface = WarmSurface,
    surfaceVariant = WarmSurfaceVariant,
    onBackground = WarmInk,
    onSurface = WarmInk,
    outline = WarmOutline,
    error = SoftError
)

@Composable
fun LSMDetectorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}

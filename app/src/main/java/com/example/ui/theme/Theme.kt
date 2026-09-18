package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.Black,
    primaryContainer = SpotifyGreenDark,
    onPrimaryContainer = Color.White,
    secondary = SpotifyGreen,
    onSecondary = Color.Black,
    secondaryContainer = SpotifyElevated,
    onSecondaryContainer = SpotifyPrimaryText,
    tertiary = SpotifyLikedRed,
    background = SpotifyDarkBackground,
    onBackground = SpotifyPrimaryText,
    surface = SpotifyDarkSecondary,
    onSurface = SpotifyPrimaryText,
    surfaceVariant = SpotifyCardBackground,
    onSurfaceVariant = SpotifySecondaryText,
    surfaceContainer = SpotifyElevated,
    error = SpotifyError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme for authentic music app experience
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

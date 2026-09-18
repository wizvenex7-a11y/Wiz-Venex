package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import java.io.File
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("music_sources_prefs", android.content.Context.MODE_PRIVATE) }
    val fontPath = prefs.getString("custom_font_path", null)
    val appFontFamily = remember(fontPath) {
        val file = fontPath?.let(::File)
        if (file != null && file.exists()) {
            try {
                android.graphics.Typeface.createFromFile(file).let { FontFamily(it) }
            } catch (_: Exception) {
                FontFamily.Default
            }
        } else FontFamily.Default
    }
    val appTypography = Typography.copy(fontFamily = appFontFamily)
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = appTypography,
        content = content
    )
}

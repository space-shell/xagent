package sh.paseochat.launcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = R1Orange,
    onPrimary = Color.White,
    secondary = Color(0xFF3A6B6B),
    background = Paper,
    surface = Color.White,
    surfaceContainerHigh = Color(0xFFEDEAE4),
    surfaceContainerHighest = Color(0xFFE4E0D9),
)

private val DarkColors = darkColorScheme(
    primary = R1Orange,
    onPrimary = Color.White,
    secondary = Color(0xFF7FB8B8),
    background = Ink,
    surface = SurfaceDark,
    surfaceContainerHigh = SurfaceContainerDark,
    surfaceContainerHighest = Color(0xFF2E2C34),
)

@Composable
fun PaseoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PaseoTypography,
        content = content,
    )
}

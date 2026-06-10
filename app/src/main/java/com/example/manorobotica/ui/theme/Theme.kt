package com.example.manorobotica.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonPurple,
    tertiary = AccentPink,
    background = DeepDarkNavy,
    surface = CardBackgroundGlass,
    onPrimary = DeepDarkNavy,
    onSecondary = TextWhite,
    onBackground = TextWhite,
    onSurface = TextWhite
)

@Composable
fun ManoRoboticaTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DeepDarkNavy.toArgb()
            window.navigationBarColor = DeepDarkNavy.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

package com.laniakea.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PurpleLightColorScheme = lightColorScheme(
    primary = PurplePrimaryLight,
    secondary = PurpleSecondaryLight,
    background = PurpleBackgroundLight,
    surface = PurpleBackgroundLight,
)

private val PurpleDarkColorScheme = darkColorScheme(
    primary = PurplePrimaryDark,
    secondary = PurpleSecondaryDark,
    background = PurpleBackgroundDark,
    surface = PurpleBackgroundDark,
)

private val GreenLightColorScheme = lightColorScheme(
    primary = GreenPrimaryLight,
    secondary = GreenSecondaryLight,
    background = GreenBackgroundLight,
    surface = GreenBackgroundLight,
)

private val GreenDarkColorScheme = darkColorScheme(
    primary = GreenPrimaryDark,
    secondary = GreenSecondaryDark,
    background = GreenBackgroundDark,
    surface = GreenBackgroundDark,
)

private val OrangeLightColorScheme = lightColorScheme(
    primary = OrangePrimaryLight,
    secondary = OrangeSecondaryLight,
    background = OrangeBackgroundLight,
    surface = OrangeBackgroundLight,
)

private val OrangeDarkColorScheme = darkColorScheme(
    primary = OrangePrimaryDark,
    secondary = OrangeSecondaryDark,
    background = OrangeBackgroundDark,
    surface = OrangeBackgroundDark,
)

private val BlueLightColorScheme = lightColorScheme(
    primary = BluePrimaryLight,
    secondary = BlueSecondaryLight,
    background = BlueBackgroundLight,
    surface = BlueBackgroundLight,
)

private val BlueDarkColorScheme = darkColorScheme(
    primary = BluePrimaryDark,
    secondary = BlueSecondaryDark,
    background = BlueBackgroundDark,
    surface = BlueBackgroundDark,
)

@Composable
fun LaniakeaTheme(
    theme: String = "PURPLE",
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when (theme.uppercase()) {
        "PURPLE" -> if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
        "GREEN" -> if (darkTheme) GreenDarkColorScheme else GreenLightColorScheme
        "ORANGE" -> if (darkTheme) OrangeDarkColorScheme else OrangeLightColorScheme
        "BLUE" -> if (darkTheme) BlueDarkColorScheme else BlueLightColorScheme
        else -> if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
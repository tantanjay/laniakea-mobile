package com.laniakea.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PurpleLightColorScheme = lightColorScheme(
    primary = PurplePrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = PurpleSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    background = PurpleBackgroundLight,
    surface = PurpleBackgroundLight,
    surfaceVariant = Color(0xFFE7E0EB)
)

private val PurpleDarkColorScheme = darkColorScheme(
    primary = PurplePrimaryDark,
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = PurpleSecondaryDark,
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    background = PurpleBackgroundDark,
    surface = PurpleBackgroundDark,
    surfaceVariant = Color(0xFF49454F)
)

private val GreenLightColorScheme = lightColorScheme(
    primary = GreenPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBCF296),
    onPrimaryContainer = Color(0xFF052100),
    secondary = GreenSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E7CB),
    onSecondaryContainer = Color(0xFF131F0D),
    tertiary = Color(0xFF386567),
    onTertiary = Color.White,
    background = GreenBackgroundLight,
    surface = GreenBackgroundLight,
    surfaceVariant = Color(0xFFE1E4D5)
)

private val GreenDarkColorScheme = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = Color(0xFF0F3900),
    primaryContainer = Color(0xFF215108),
    onPrimaryContainer = Color(0xFFBCF296),
    secondary = GreenSecondaryDark,
    onSecondary = Color(0xFF283420),
    secondaryContainer = Color(0xFF3E4A35),
    onSecondaryContainer = Color(0xFFD9E7CB),
    tertiary = Color(0xFFA0CFCF),
    onTertiary = Color(0xFF003738),
    background = GreenBackgroundDark,
    surface = GreenBackgroundDark,
    surfaceVariant = Color(0xFF44483D)
)

private val OrangeLightColorScheme = lightColorScheme(
    primary = OrangePrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB3),
    onPrimaryContainer = Color(0xFF291800),
    secondary = OrangeSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDDABE),
    onSecondaryContainer = Color(0xFF271904),
    tertiary = Color(0xFF52643F),
    onTertiary = Color.White,
    background = OrangeBackgroundLight,
    surface = OrangeBackgroundLight,
    surfaceVariant = Color(0xFFF0E0D0)
)

private val OrangeDarkColorScheme = darkColorScheme(
    primary = OrangePrimaryDark,
    onPrimary = Color(0xFF4B2800),
    primaryContainer = Color(0xFF6B3B00),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = OrangeSecondaryDark,
    onSecondary = Color(0xFF3F2D17),
    secondaryContainer = Color(0xFF58432B),
    onSecondaryContainer = Color(0xFFFDDABE),
    tertiary = Color(0xFFB9CCA2),
    onTertiary = Color(0xFF253515),
    background = OrangeBackgroundDark,
    surface = OrangeBackgroundDark,
    surfaceVariant = Color(0xFF4F4539)
)

private val BlueLightColorScheme = lightColorScheme(
    primary = BluePrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = BlueSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E3F7),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color.White,
    background = BlueBackgroundLight,
    surface = BlueBackgroundLight,
    surfaceVariant = Color(0xFFDFE2EB)
)

private val BlueDarkColorScheme = darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = BlueSecondaryDark,
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4758),
    onSecondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2947),
    background = BlueBackgroundDark,
    surface = BlueBackgroundDark,
    surfaceVariant = Color(0xFF43474E)
)

@Composable
fun LaniakeaTheme(
    theme: String = "PURPLE",
    darkTheme: Boolean = isSystemInDarkTheme(),
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

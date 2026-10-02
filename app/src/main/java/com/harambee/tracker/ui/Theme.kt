package com.harambee.tracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harambee.tracker.Settings
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF0B6E4F)
private val GreenLight = Color(0xFF7BD8B1)

private val Light = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE0),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF8C5000),
    secondaryContainer = Color(0xFFFFDCBE),
    tertiary = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFFCDEFE0),
    secondary = Color(0xFFFFB870),
    secondaryContainer = Color(0xFF6B3C00),
    tertiary = Color(0xFFF2B8B5),
)

@Composable
fun HarambeeTheme(content: @Composable () -> Unit) {
    val settings = appContainer().settings
    val mode by settings.themeMode.value.collectAsStateWithLifecycle()
    val dynamic by settings.dynamicColor.value.collectAsStateWithLifecycle()
    val dark = when (mode) {
        Settings.THEME_LIGHT -> false
        Settings.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = when {
        // Android 12+: colours taken from the wallpaper, if the user wants them.
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}

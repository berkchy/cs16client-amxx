package com.pickle.patcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PureDark = Color(0xFF0B0E13)

val AmxxDarkScheme = darkColorScheme(
    primary = Mint80,
    onPrimary = Color(0xFF003828),
    primaryContainer = Mint20,
    onPrimaryContainer = MintG10,
    secondary = Violet80,
    onSecondary = Color(0xFF242E6B),
    secondaryContainer = Violet30,
    onSecondaryContainer = Violet90,
    tertiary = Warn80,
    background = Slate950,
    onBackground = OnDark,
    surface = Slate900,
    onSurface = OnDark,
    surfaceVariant = Slate850,
    onSurfaceVariant = OnDarkMuted,
    surfaceContainerLowest = PureDark,
    surfaceContainerLow = Slate900,
    surfaceContainer = Slate850,
    surfaceContainerHigh = Slate800,
    surfaceContainerHighest = Slate700,
    outline = Slate700,
    error = Alert80,
    onError = Alert40,
)

@Composable
fun AmxxPatcherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmxxDarkScheme,
        typography = AmxxTypography,
        content = content,
    )
}
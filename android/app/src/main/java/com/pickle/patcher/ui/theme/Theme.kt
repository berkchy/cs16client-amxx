package com.pickle.patcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

val AmxxDarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Black,
    primaryContainer = AccentDim,
    onPrimaryContainer = White,
    secondary = Gray30,
    onSecondary = Black,
    secondaryContainer = Gray70,
    onSecondaryContainer = Gray10,
    tertiary = WarnYellow,
    background = Black,
    onBackground = White,
    surface = Gray95,
    onSurface = White,
    surfaceVariant = Gray90,
    onSurfaceVariant = Gray40,
    surfaceContainerLowest = Black,
    surfaceContainerLow = Gray95,
    surfaceContainer = Gray90,
    surfaceContainerHigh = Gray80,
    surfaceContainerHighest = Gray70,
    outline = Gray60,
    outlineVariant = Gray70,
    error = AlertRed,
    onError = White,
    errorContainer = AlertRedDim,
    onErrorContainer = AlertRed,
    inverseSurface = Gray10,
    inverseOnSurface = Gray90,
    inversePrimary = AccentDim,
    scrim = Black,
)

@Composable
fun AmxxPatcherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmxxDarkScheme,
        typography = AmxxTypography,
        content = content,
    )
}

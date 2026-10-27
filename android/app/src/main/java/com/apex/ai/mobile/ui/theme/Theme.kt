package com.apex.ai.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    secondary = AccentBlue,
    tertiary = AccentProcessing,
    background = BgMain,
    surface = PanelBg,
    onPrimary = BgDeep,
    onSecondary = BgDeep,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun ApexMobileTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = ApexTypography,
        content = content
    )
}

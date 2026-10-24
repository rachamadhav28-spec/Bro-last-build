package com.bro.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MinimalDarkPalette = darkColorScheme(
    primary = BroDesign.PrimaryAccent,
    secondary = BroDesign.SecondaryAccent,
    background = BroDesign.Background,
    surface = BroDesign.Surface,
    onPrimary = BroDesign.CrispWhite,
    onSecondary = BroDesign.CrispWhite,
    onBackground = BroDesign.CrispWhite,
    onSurface = BroDesign.CrispWhite
)

@Composable
fun BroTheme(
    themeMode: String = "DARK",
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MinimalDarkPalette,
        content = content
    )
}

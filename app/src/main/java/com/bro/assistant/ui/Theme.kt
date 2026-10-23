package com.bro.assistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BroDarkPalette = darkColorScheme(
    primary = BroDesign.VoltLime,
    secondary = BroDesign.HyperOrange,
    tertiary = BroDesign.CyberCyan,
    background = BroDesign.ObsidianBg,
    surface = BroDesign.CarbonSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = BroDesign.CrispWhite,
    onSurface = BroDesign.CrispWhite
)

private val BroLightPalette = lightColorScheme(
    primary = Color(0xFF141824),
    secondary = BroDesign.HyperOrange,
    tertiary = Color(0xFF007799),
    background = Color(0xFFEAECEF),
    surface = Color(0xFFFFFFFF),
    onPrimary = BroDesign.VoltLime,
    onSecondary = Color.White,
    onBackground = Color(0xFF090A0F),
    onSurface = Color(0xFF090A0F)
)

@Composable
fun BroTheme(
    themeMode: String = "DARK",
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode.uppercase()) {
        "LIGHT" -> false
        "AUTO" -> isSystemInDarkTheme()
        else -> true
    }
    MaterialTheme(
        colorScheme = if (useDark) BroDarkPalette else BroLightPalette,
        content = content
    )
}

package com.bro.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object BroDesign {
    val Background = Color(0xFF121212)
    val Surface = Color(0xFF1E1E1E)
    val SurfaceElevated = Color(0xFF2A2A2A)
    val PrimaryAccent = Color(0xFF6200EE)
    val SecondaryAccent = Color(0xFF03DAC6)
    val CrispWhite = Color(0xFFFFFFFF)
    val MutedText = Color(0xFF9E9E9E)
    val BorderColor = Color(0xFF333333)

    val CardShape = RoundedCornerShape(16.dp)
    val ComposerShape = RoundedCornerShape(24.dp)
    val PillShape = RoundedCornerShape(50.dp)

    val HeaderStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = CrispWhite
    )
    val BodyStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = CrispWhite
    )
    val LabelStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = MutedText
    )
}

@Composable
fun MinimalCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(BroDesign.CardShape)
            .background(BroDesign.Surface)
            .border(1.dp, BroDesign.BorderColor, BroDesign.CardShape)
            .padding(16.dp),
        content = content
    )
}

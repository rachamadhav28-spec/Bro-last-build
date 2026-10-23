package com.bro.assistant.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

object BroDesign {
    // Cyber-Tactile Palette
    val ObsidianBg = Color(0xFF090A0F)
    val CarbonSurface = Color(0xFF12151E)
    val ElevatedTactile = Color(0xFF1A1F2C)
    val VoltLime = Color(0xFFCCFF00)
    val HyperOrange = Color(0xFFFF4D00)
    val CyberCyan = Color(0xFF00F0FF)
    val CrispWhite = Color(0xFFF3F5F8)
    val MutedSteel = Color(0xFF7D8597)
    val BorderGrid = Color(0xFF262C3A)
    val DangerRed = Color(0xFFFF2A54)

    // Chamfered Tactical Shapes (Cut corners instead of soft glass)
    val CardShape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp, topEnd = 4.dp, bottomStart = 4.dp)
    val ComposerShape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp, topEnd = 4.dp, bottomStart = 4.dp)
    val PillShape = CutCornerShape(6.dp)

    // Typography
    val HeaderStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 20.sp,
        letterSpacing = 1.5.sp,
        color = CrispWhite
    )
    val BodyStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = CrispWhite
    )
    val HudLabelStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        color = VoltLime
    )
}

@Composable
fun TacticalCard(
    modifier: Modifier = Modifier,
    accentColor: Color = BroDesign.BorderGrid,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(BroDesign.CardShape)
            .background(BroDesign.CarbonSurface)
            .border(1.5.dp, accentColor, BroDesign.CardShape)
            .padding(14.dp),
        content = content
    )
}

/**
 * Signal Hex Logo + Cyber-Tactile Radar Voice Core
 */
@Composable
fun CyberRadarCore(
    isActive: Boolean,
    isSpeaking: Boolean,
    size: Dp = 84.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 2400 else 8000, easing = LinearEasing)
        ),
        label = "rot"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = if (isActive || isSpeaking) 1.15f else 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking) 450 else 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val coreColor = when {
        isSpeaking -> BroDesign.HyperOrange
        isActive -> BroDesign.VoltLime
        else -> BroDesign.CyberCyan
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = (this.size.minDimension / 2f) * pulse
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            // Outer Segmented Tactical Ring
            rotate(rotation, center) {
                for (i in 0 until 6) {
                    drawArc(
                        color = coreColor.copy(alpha = 0.75f),
                        startAngle = i * 60f,
                        sweepAngle = 36f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Square)
                    )
                }
            }

            // Inner Signal Hexagon
            val hexPath = Path()
            val hexRadius = radius * 0.62f
            for (i in 0 until 6) {
                val angleRad = Math.toRadians((60.0 * i) - 30.0)
                val x = center.x + (hexRadius * cos(angleRad)).toFloat()
                val y = center.y + (hexRadius * sin(angleRad)).toFloat()
                if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
            }
            hexPath.close()

            drawPath(
                path = hexPath,
                brush = Brush.radialGradient(
                    colors = listOf(coreColor.copy(alpha = 0.35f), Color.Transparent),
                    center = center,
                    radius = hexRadius
                )
            )
            drawPath(
                path = hexPath,
                color = coreColor,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        Text(
            text = if (isSpeaking) "TX" else if (isActive) "RX" else "BRO",
            style = BroDesign.HudLabelStyle.copy(color = coreColor)
        )
    }
}

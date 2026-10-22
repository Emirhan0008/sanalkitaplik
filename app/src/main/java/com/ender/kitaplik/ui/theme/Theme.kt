package com.ender.kitaplik.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Cream = Color(0xFFFAF3E7)
val CreamDeep = Color(0xFFF1E4D1)
val InkBrown = Color(0xFF2E2620)
val Wood = Color(0xFFB4683C)
val WoodDeep = Color(0xFF8E4E2C)
val Brass = Color(0xFFD9A05B)
val Muted = Color(0xFF8A7862)

private val Colors = lightColorScheme(
    primary = Wood,
    onPrimary = Color(0xFFFFF7EC),
    secondary = Color(0xFF2F7A6B),
    background = Cream,
    onBackground = InkBrown,
    surface = Color(0xFFFFFAF2),
    onSurface = InkBrown,
    surfaceVariant = CreamDeep,
    onSurfaceVariant = Muted,
    outline = Color(0xFFD9C7AF)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 33.sp,
        lineHeight = 39.sp,
        color = InkBrown
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        color = InkBrown
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp
    )
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp)
)

@Composable
fun KitaplikTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

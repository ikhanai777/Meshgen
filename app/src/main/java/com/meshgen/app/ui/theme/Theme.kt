package com.meshgen.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Near-monochrome graphite palette with a single cyan accent. */
object MeshColors {
    val Graphite950 = Color(0xFF0B0C0E)
    val Graphite900 = Color(0xFF121418)
    val Graphite850 = Color(0xFF181B20)
    val Graphite800 = Color(0xFF1F2329)
    val Graphite700 = Color(0xFF2B3038)
    val Graphite500 = Color(0xFF5A616C)
    val Graphite300 = Color(0xFF9AA1AC)
    val Graphite100 = Color(0xFFE6E8EB)
    val Accent = Color(0xFF5CE1E6)
    val AccentDim = Color(0x335CE1E6)
    val Warning = Color(0xFFE6B85C)
}

private val colors = darkColorScheme(
    primary = MeshColors.Accent,
    onPrimary = MeshColors.Graphite950,
    secondary = MeshColors.Graphite300,
    onSecondary = MeshColors.Graphite950,
    background = MeshColors.Graphite950,
    onBackground = MeshColors.Graphite100,
    surface = MeshColors.Graphite900,
    onSurface = MeshColors.Graphite100,
    surfaceVariant = MeshColors.Graphite850,
    onSurfaceVariant = MeshColors.Graphite300,
    outline = MeshColors.Graphite700,
    outlineVariant = MeshColors.Graphite800,
)

private val typography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.sp),
)

@Composable
fun MeshGenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

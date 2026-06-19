package cli.shady.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ShadyPalette {
    val TerminalBackground = Color(0xFF07110C)
    val TerminalPanel = Color(0xFF0A1512)
    val TerminalPanelElevated = Color(0xFF10201D)
    val TerminalBorder = Color(0xFF1F3A2B)
    val TerminalBorderStrong = Color(0xFF315449)
    val Text = Color(0xFFD8E3E8)
    val MutedText = Color(0xFF9AB8A5)
    val DimText = Color(0xFF667580)
    val Success = Color(0xFF34D399)
    val Info = Color(0xFF5CB7FF)
    val Warning = Color(0xFFD6B15A)
    val Danger = Color(0xFFFF667A)
    val RowActive = Color(0xFF14242C)
    val RowHover = Color(0xFF101F1B)
    val Net = Color(0xFF45D06E)
    val Disk = Color(0xFFE3B341)
}

object ShadySpacing {
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 20.dp
    val Window = 24.dp
}

object ShadyRadius {
    val Small = 6.dp
    val Medium = 8.dp
    val Large = 8.dp
    val Pill = 999.dp
}

object ShadySizes {
    val BorderWidth = 1.dp
    val HeaderElevation = 1.dp
    val TinyGap = 2.dp
    val MainWindowWidth = 800.dp
    val MainWindowHeight = 600.dp
    val MainWindowMinWidth = 600
    val MainWindowMinHeight = 400
    val ToolWindowWidth = 980.dp
    val ToolWindowHeight = 560.dp
    val ToolWindowMinWidth = 760
    val ToolWindowMinHeight = 420
    val ResourceMetricCardHeight = 164.dp
    val ResourceSparklineHeight = 58.dp
    val ResourceSparklineStroke = 2.4.dp
    val ResourceDonutSize = 62.dp
    val ResourceDonutStroke = 7.dp
    val TerminalTabHeight = 40.dp
    val FuzzyPanelHeight = 260.dp
}

object ShadyAlpha {
    const val Badge = 0.14f
    const val Track = 0.18f
}

private val ShadyColorScheme = darkColorScheme(
    primary = Color(0xFF5DE89A),
    onPrimary = Color(0xFF00210F),
    secondary = Color(0xFF8FD6A8),
    background = Color(0xFF09130D),
    onBackground = Color(0xFFE4F1E8),
    surface = Color(0xFF0E1B13),
    onSurface = Color(0xFFE4F1E8),
    surfaceVariant = Color(0xFF14251A),
    onSurfaceVariant = Color(0xFFB7CDBD),
    outline = Color(0xFF2F4A38),
    error = ShadyPalette.Danger,
)

private val ShadyTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
    ),
)

private val ShadyShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(ShadyRadius.Small),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(ShadyRadius.Medium),
    large = androidx.compose.foundation.shape.RoundedCornerShape(ShadyRadius.Large),
)

@Composable
fun ShadyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShadyColorScheme,
        typography = ShadyTypography,
        shapes = ShadyShapes,
        content = content,
    )
}

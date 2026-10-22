package sk.alexia.os.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Os {
    val Bg = Color(0xFF05070F)
    val Violet = Color(0xFFB06BFF)
    val Cyan = Color(0xFF3DE0FF)
    val Mint = Color(0xFF4BE3A4)
    val Rose = Color(0xFFFF6B9A)
    val Amber = Color(0xFFFFC24B)
    val Text = Color(0xFFF2F4FF)
    val TextDim = Color(0xB3E3E7FF)
    val Glass = Color(0x1FFFFFFF)
    val GlassStrong = Color(0x33FFFFFF)
    val GlassBorder = Color(0x26FFFFFF)

    val Accent = Brush.linearGradient(listOf(Violet, Cyan))

    /** Tapety: dvojice farieb pre animovanú „auróru“. */
    val wallpapers = listOf(
        Triple("Aurora", listOf(Color(0xFF6A2BFF), Color(0xFF00C2FF), Color(0xFF00FFA3)), Color(0xFF060814)),
        Triple("Nebula", listOf(Color(0xFFFF3D8B), Color(0xFF8A3DFF), Color(0xFF3D6BFF)), Color(0xFF0A0614)),
        Triple("Ocean", listOf(Color(0xFF0057FF), Color(0xFF00D1C1), Color(0xFF00385F)), Color(0xFF020B14)),
        Triple("Ember", listOf(Color(0xFFFF6A00), Color(0xFFFF2D55), Color(0xFF7A1FFF)), Color(0xFF120605)),
    )
}

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Thin, fontSize = 88.sp, letterSpacing = (-2).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 28.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp),
)

@Composable
fun AlexiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Os.Cyan,
            secondary = Os.Violet,
            tertiary = Os.Mint,
            background = Os.Bg,
            surface = Color(0xFF0E1222),
            onPrimary = Color(0xFF001018),
            onBackground = Os.Text,
            onSurface = Os.Text,
            error = Os.Rose,
        ),
        typography = typography,
        content = content,
    )
}

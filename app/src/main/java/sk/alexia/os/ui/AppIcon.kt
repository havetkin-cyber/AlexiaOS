package sk.alexia.os.ui

import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import sk.alexia.os.system.LaunchableApp
import sk.alexia.os.ui.theme.Os
import kotlin.math.abs

/** Farby gradientu pre jednotný štýl ikon – odvodené od názvu balíka, aby boli stále rovnaké. */
private val palettes = listOf(
    listOf(Color(0xFF7A3DFF), Color(0xFFB06BFF)),
    listOf(Color(0xFF00A3FF), Color(0xFF3DE0FF)),
    listOf(Color(0xFF00B37A), Color(0xFF4BE3A4)),
    listOf(Color(0xFFFF3D8B), Color(0xFFFF8AB8)),
    listOf(Color(0xFFFF6A00), Color(0xFFFFC24B)),
    listOf(Color(0xFF3D6BFF), Color(0xFF8A9BFF)),
)

private class IconData(val full: ImageBitmap?, val mono: ImageBitmap?)

/**
 * Ikona skutočnej appky. V jednotnom štýle AlexiaOS sa použije monochromatická vrstva ikony
 * (Android 13+) na farebnom „squircle“; ak ju appka nemá, zobrazí sa pôvodná ikona.
 */
@Composable
fun AppIcon(app: LaunchableApp, themed: Boolean, size: Dp = 60.dp) {
    val data = remember(app.packageName, app.activity) {
        val d = app.icon
        val mono = if (Build.VERSION.SDK_INT >= 33 && d is AdaptiveIconDrawable) {
            runCatching { d.monochrome?.toBitmap(160, 160)?.asImageBitmap() }.getOrNull()
        } else null
        IconData(runCatching { d?.toBitmap(160, 160)?.asImageBitmap() }.getOrNull(), mono)
    }
    val shape = RoundedCornerShape(size * 0.3f)
    val mono = data.mono
    if (themed && mono != null) {
        val colors = palettes[abs(app.packageName.hashCode()) % palettes.size]
        Box(
            Modifier.size(size).clip(shape).background(Brush.linearGradient(colors))
                .border(1.dp, Color.White.copy(alpha = 0.18f), shape),
            contentAlignment = Alignment.Center,
        ) {
            // Monochromatická vrstva má okraje adaptívnej ikony – zväčšíme ju
            Image(mono, app.label, Modifier.size(size * 1.35f), colorFilter = ColorFilter.tint(Color.White))
        }
    } else {
        Box(Modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
            val full = data.full
            if (full != null) Image(full, app.label, Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().background(Os.Accent))
        }
    }
}

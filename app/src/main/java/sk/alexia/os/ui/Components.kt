package sk.alexia.os.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sk.alexia.os.ui.theme.Os
import kotlin.math.cos
import kotlin.math.sin

/** Animovaná tapeta – pomaly sa pohybujúce farebné hmloviny. */
@Composable
fun AuroraWallpaper(index: Int, modifier: Modifier = Modifier, dim: Float = 0f) {
    val (_, colors, base) = Os.wallpapers[index.coerceIn(0, Os.wallpapers.lastIndex)]
    val t by rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        val w = size.width
        val h = size.height
        colors.forEachIndexed { i, c ->
            val phase = t + i * 2.1f
            val center = Offset(
                w * (0.5f + 0.38f * cos(phase * (0.7f + i * 0.15f))),
                h * (0.35f + 0.3f * sin(phase * (0.5f + i * 0.2f)) + i * 0.12f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c.copy(alpha = 0.55f), c.copy(alpha = 0.12f), Color.Transparent),
                    center = center, radius = w * (0.75f + 0.1f * i),
                ),
                radius = w * (0.75f + 0.1f * i), center = center,
            )
        }
        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
    }
}

/** „Sklenená“ karta – priesvitná s jemným okrajom. */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    var m = modifier
        .clip(shape)
        .background(if (strong) Os.GlassStrong else Os.Glass)
        .border(1.dp, Os.GlassBorder, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m, content = content)
}

/** Ikona appky AlexiaOS – gradient „squircle“ so symbolom. */
@Composable
fun OsAppIcon(icon: ImageVector, colors: List<Color>, size: Dp = 60.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(colors))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun AppLabel(text: String, color: Color = Os.Text) {
    Text(
        text, color = color, style = MaterialTheme.typography.labelSmall,
        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        modifier = Modifier.width(76.dp),
    )
}

/** Hlavička vnútornej appky s tlačidlom späť. */
@Composable
fun AppScaffold(
    title: String,
    subtitle: String? = null,
    accent: List<Color> = listOf(Os.Violet, Os.Cyan),
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Os.Bg)) {
        Canvas(Modifier.fillMaxWidth().height(260.dp)) {
            drawRect(Brush.verticalGradient(listOf(accent.first().copy(alpha = 0.35f), Color.Transparent)))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Späť", tint = Os.Text) }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = Os.Text)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Os.TextDim)
                }
            }
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), content = content)
        }
    }
}

/** Štítok „zašifrované“. */
@Composable
fun EncryptedBadge(text: String = "AES-256-GCM") {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Os.Mint.copy(alpha = 0.16f))
            .border(1.dp, Os.Mint.copy(alpha = 0.4f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Os.Mint))
        Text("🔒 $text", color = Os.Mint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionTitle(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text.uppercase(), color = Os.TextDim, style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(8.dp))
}

/** Klikanie bez vlnky (pre celé plochy). */
@Composable
fun Modifier.plainClick(onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

package sk.alexia.os.apps

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.alexia.os.crypto.CryptoEngine
import sk.alexia.os.data.SecureFiles
import sk.alexia.os.ui.AppScaffold
import sk.alexia.os.ui.EncryptedBadge
import sk.alexia.os.ui.Glass
import sk.alexia.os.ui.theme.Os
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Súbory: šifrované úložisko a súkromná galéria. */
@Composable
fun FilesApp(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val files = remember { SecureFiles(ctx) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(files.list()) }
    var tab by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<SecureFiles.Entry?>(null) }
    var exporting by remember { mutableStateOf<SecureFiles.Entry?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var ok = 0
            uris.forEachIndexed { i, uri ->
                busy = "Šifrujem ${i + 1} / ${uris.size}…"
                if (withContext(Dispatchers.IO) { runCatching { files.import(uri) }.isSuccess }) ok++
            }
            entries = files.list()
            busy = null
            message = "Zašifrovaných $ok súborov. Originály v galérii môžeš teraz zmazať."
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val e = exporting
        exporting = null
        if (uri == null || e == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = "Dešifrujem ${e.name}…"
            val r = withContext(Dispatchers.IO) { runCatching { files.export(e, uri) } }
            busy = null
            message = if (r.isSuccess) "Súbor ${e.name} bol uložený dešifrovaný." else "Export zlyhal: ${r.exceptionOrNull()?.message}"
        }
    }

    selected?.let { e ->
        FileViewer(
            files, e,
            onClose = { selected = null },
            onExport = { exporting = e; exporter.launch(e.name) },
            onDelete = {
                files.delete(e); entries = files.list(); selected = null
                message = "Súbor ${e.name} bol zmazaný."
            },
        )
        return
    }

    AppScaffold("Súbory", "Šifrované úložisko a galéria", accent = listOf(Os.Rose, Os.Violet), onBack = onBack) {
        EncryptedBadge("Obálkové šifrovanie AES-256-GCM")
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { importer.launch(arrayOf("*/*")) },
            enabled = busy == null,
            colors = ButtonDefaults.buttonColors(containerColor = Os.Rose),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("+ Pridať a zašifrovať súbory") }
        busy?.let {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 8.dp).height(16.dp), color = Os.Rose, strokeWidth = 2.dp)
                Text(it, color = Os.TextDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        message?.let { Text(it, color = Os.TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }

        val photos = entries.filter { it.isImage }
        TabRow(
            selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = Os.Text,
            indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), color = Os.Rose) },
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Galéria (${photos.size})") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Všetky súbory (${entries.size})") })
        }

        if (tab == 0) {
            if (photos.isEmpty()) EmptyHint("Žiadne fotky. Pridaj súkromné fotky – v telefóne budú uložené len zašifrované.")
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(photos, key = { it.id }) { e -> Thumb(files, e) { selected = e } }
            }
        } else {
            if (entries.isEmpty()) EmptyHint("Úložisko je prázdne.")
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                contentPadding = PaddingValues(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.id }, span = { GridItemSpan(1) }) { e ->
                    Glass(Modifier.fillMaxWidth(), onClick = { selected = e }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(icon(e), style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.padding(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, color = Os.Text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${size(e.size)} • ${date(e.added)}", color = Os.TextDim, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("🔒", color = Os.Mint)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, color = Os.TextDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 24.dp))
}

@Composable
private fun Thumb(files: SecureFiles, e: SecureFiles.Entry, onClick: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, e.id) {
        value = withContext(Dispatchers.IO) { decode(files, e, 360) }
    }
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Os.Glass).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b, e.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else CircularProgressIndicator(Modifier.height(18.dp), color = Os.Rose, strokeWidth = 2.dp)
    }
}

private fun decode(files: SecureFiles, e: SecureFiles.Entry, target: Int): ImageBitmap? = runCatching {
    val bytes = files.readBytes(e) ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > target * 2) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    bytes.fill(0)
    bmp?.asImageBitmap()
}.getOrNull()

@Composable
private fun FileViewer(
    files: SecureFiles,
    e: SecureFiles.Entry,
    onClose: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var raw by remember { mutableStateOf(false) }
    val image by produceState<ImageBitmap?>(null, e.id) {
        if (e.isImage) value = withContext(Dispatchers.IO) { decode(files, e, 1600) }
    }
    val rawBytes = remember(e.id) { runCatching { files.rawPreview(e) }.getOrDefault(ByteArray(0)) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.name, color = Os.Text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${size(e.size)} • ${e.mime}", color = Os.TextDim, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onClose) { Text("Zavrieť", color = Os.Cyan) }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val img = image
                when {
                    raw -> Glass(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Takto je súbor uložený v telefóne:", color = Os.TextDim, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            Text(CryptoEngine.toHex(rawBytes, 96), color = Os.Rose, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    img != null -> Image(img, e.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    e.isImage -> CircularProgressIndicator(color = Os.Rose)
                    else -> Text("${icon(e)}\n\nNáhľad nie je k dispozícii.\nExportuj súbor a otvor ho inou appkou.",
                        color = Os.TextDim, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { raw = !raw }) { Text(if (raw) "Normálne" else "👁 Ako zlodej", color = Os.Cyan) }
                TextButton(onClick = onExport) { Text("Exportovať", color = Os.Cyan) }
                TextButton(onClick = onDelete) { Text("Zmazať", color = Os.Rose) }
            }
        }
    }
}

private fun icon(e: SecureFiles.Entry) = when {
    e.isImage -> "🖼"
    e.isVideo -> "🎬"
    e.mime.contains("pdf") -> "📄"
    e.mime.startsWith("audio/") -> "🎵"
    e.mime.contains("zip") || e.mime.contains("compressed") -> "🗜"
    else -> "📁"
}

private fun size(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%.0f kB".format(b / 1024.0)
    else -> "$b B"
}

private fun date(t: Long): String = SimpleDateFormat("d. M. yyyy", Locale.getDefault()).format(Date(t))

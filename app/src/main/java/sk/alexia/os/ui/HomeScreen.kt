package sk.alexia.os.ui

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import sk.alexia.os.data.OsStore
import sk.alexia.os.system.InstalledApps
import sk.alexia.os.system.LaunchableApp
import sk.alexia.os.ui.theme.Os
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class OsApp(val name: String, val icon: ImageVector, val colors: List<Color>, val screen: OsScreen)

private val builtIn = listOf(
    OsApp("Trezor", Icons.Outlined.Inventory2, listOf(Color(0xFF7A3DFF), Color(0xFFB06BFF)), OsScreen.VAULT),
    OsApp("Správy", Icons.Filled.Chat, listOf(Color(0xFF00A3FF), Color(0xFF3DE0FF)), OsScreen.MESSAGES),
    OsApp("Bezpečnosť", Icons.Filled.Shield, listOf(Color(0xFF00B37A), Color(0xFF4BE3A4)), OsScreen.SECURITY),
    OsApp("Nastavenia", Icons.Filled.Settings, listOf(Color(0xFF4A5068), Color(0xFF8A91B0)), OsScreen.SETTINGS),
)

/** Appky rodiny Alexia (Alexia Core chat, Shield, PDF…) – nájdu sa podľa názvu. */
private fun isAlexiaApp(app: LaunchableApp): Boolean =
    app.label.contains("alexia", ignoreCase = true) || app.packageName.contains("alexia", ignoreCase = true)

@Composable
fun HomeScreen(store: OsStore, wallpaper: Int, onOpen: (OsScreen) -> Unit, onLock: () -> Unit) {
    val ctx = LocalContext.current
    var drawer by remember { mutableStateOf(false) }
    var control by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(store.privacyMode) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val apps by produceState(emptyList<LaunchableApp>()) { value = InstalledApps.load(ctx) }
    val alexiaApps = remember(apps) { apps.filter(::isAlexiaApp) }
    val notes = remember { store.loadNotes().size }

    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    BackHandler(enabled = drawer || control) { drawer = false; control = false }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            var total = 0f
            var startY = 0f
            detectVerticalDragGestures(
                onDragStart = { startY = it.y; total = 0f },
                onDragEnd = {
                    when {
                        total < -140f -> drawer = true
                        total > 140f && startY < size.height * 0.35f -> control = true
                    }
                },
            ) { _, d -> total += d }
        }
    ) {
        OsWallpaper(store, wallpaper, dim = 0.1f)

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // Hodiny
            Spacer(Modifier.height(28.dp))
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)), color = Os.Text, fontSize = 72.sp, fontWeight = FontWeight.Thin)
            Text(slovakDate(now), color = Os.Text, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            EncryptedBadge(if (privacy) "Režim súkromia zapnutý" else "Všetky dáta šifrované")
            Spacer(Modifier.height(20.dp))

            // Widgety
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Glass(Modifier.weight(1f).height(110.dp), onClick = { onOpen(OsScreen.SECURITY) }) {
                    Column(Modifier.padding(14.dp)) {
                        Icon(Icons.Filled.Shield, null, tint = Os.Mint)
                        Spacer(Modifier.weight(1f))
                        Text("Chránené", color = Os.Text, style = MaterialTheme.typography.titleMedium)
                        Text("Šifrovanie aktívne", color = Os.TextDim, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Glass(Modifier.weight(1f).height(110.dp), onClick = { onOpen(OsScreen.VAULT) }) {
                    Column(Modifier.padding(14.dp)) {
                        Icon(Icons.Outlined.Inventory2, null, tint = Os.Violet)
                        Spacer(Modifier.weight(1f))
                        Text(if (privacy) "•••" else "$notes", color = Os.Text, style = MaterialTheme.typography.titleMedium)
                        Text("tajných poznámok", color = Os.TextDim, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Appky AlexiaOS
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                builtIn.forEach { a ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onOpen(a.screen) }) {
                        OsAppIcon(a.icon, a.colors)
                        Spacer(Modifier.height(6.dp))
                        AppLabel(a.name)
                    }
                }
            }

            // Rodina Alexia
            if (alexiaApps.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Glass(Modifier.fillMaxWidth(), radius = 28.dp) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Alexia Core", color = Os.Text, fontSize = 26.sp, fontWeight = FontWeight.Light)
                        Spacer(Modifier.height(12.dp))
                        alexiaApps.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                row.forEach { app -> RealAppTile(app, privacy) { InstalledApps.launch(ctx, app) } }
                                repeat(3 - row.size) { Spacer(Modifier.size(76.dp)) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "↑ potiahni nahor pre všetky appky • ↓ zhora pre ovládací panel",
                color = Os.TextDim, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(110.dp))
        }

        // Dok
        Glass(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth(),
            radius = 32.dp, strong = true,
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                DockIcon(Icons.Filled.Phone, listOf(Color(0xFF00B37A), Color(0xFF4BE3A4))) {
                    start(ctx, Intent(Intent.ACTION_DIAL))
                }
                DockIcon(Icons.Filled.Chat, listOf(Color(0xFF00A3FF), Color(0xFF3DE0FF))) { onOpen(OsScreen.MESSAGES) }
                DockIcon(Icons.Filled.PhotoCamera, listOf(Color(0xFF4A5068), Color(0xFF8A91B0))) {
                    start(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                }
                DockIcon(Icons.Filled.Language, listOf(Color(0xFFFF6A00), Color(0xFFFFC24B))) {
                    start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com")))
                }
                DockIcon(Icons.Filled.Apps, listOf(Color(0xFF7A3DFF), Color(0xFF3DE0FF))) { drawer = true }
            }
        }

        AnimatedVisibility(control, enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut() + slideOutVertically { -it / 3 }) {
            ControlCenter(
                privacy = privacy,
                onPrivacy = { privacy = it; store.privacyMode = it },
                onLock = { control = false; onLock() },
                onSettings = { control = false; onOpen(OsScreen.SETTINGS) },
                onClose = { control = false },
            )
        }

        AnimatedVisibility(drawer, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
            AppDrawer(apps, onClose = { drawer = false }) { InstalledApps.launch(ctx, it); drawer = false }
        }
    }
}

/** Tapeta: vstavaná animovaná alebo vlastná fotka z galérie. */
@Composable
fun OsWallpaper(store: OsStore, wallpaper: Int, dim: Float) {
    val ctx = LocalContext.current
    val custom by produceState<ImageBitmap?>(null, store.customWallpaper) {
        value = store.customWallpaper?.let { loadWallpaper(ctx, it) }
    }
    val img = custom
    if (img != null) {
        Image(img, null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f + dim)))
    } else {
        AuroraWallpaper(wallpaper, dim = dim)
    }
}

private suspend fun loadWallpaper(ctx: Context, uri: String): ImageBitmap? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val u = Uri.parse(uri)
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (opts.outHeight / sample > 2800) sample *= 2
            ctx.contentResolver.openInputStream(u)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            }?.asImageBitmap()
        }.getOrNull()
    }

@Composable
private fun RealAppTile(app: LaunchableApp, privacy: Boolean, onClick: () -> Unit) {
    val bmp = remember(app.packageName, app.activity) {
        runCatching { app.icon?.toBitmap(144, 144)?.asImageBitmap() }.getOrNull()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp, app.label, Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().background(Os.Accent))
        }
        Spacer(Modifier.height(6.dp))
        AppLabel(if (privacy) "•••" else app.label)
    }
}

@Composable
private fun DockIcon(icon: ImageVector, colors: List<Color>, onClick: () -> Unit) {
    Box(Modifier.clickable(onClick = onClick)) { OsAppIcon(icon, colors, size = 54.dp) }
}

private fun start(ctx: Context, intent: Intent) {
    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// ---------------------------------------------------------------- Ovládací panel

@Composable
private fun ControlCenter(
    privacy: Boolean,
    onPrivacy: (Boolean) -> Unit,
    onLock: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var torch by remember { mutableStateOf(false) }
    val battery = remember {
        ctx.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).plainClick(onClose)) {
        Glass(
            Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth().plainClick { },
            radius = 32.dp, strong = true,
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ovládací panel", color = Os.Text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (battery >= 0) Text("🔋 $battery %", color = Os.TextDim, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(16.dp))
                val tiles = listOf(
                    Triple("Wi-Fi", Icons.Filled.Wifi, false) to {
                        start(ctx, if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS))
                    },
                    Triple("Bluetooth", Icons.Filled.Bluetooth, false) to { start(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    Triple("Baterka", Icons.Filled.FlashlightOn, torch) to { torch = setTorch(ctx, !torch) },
                    Triple("Jas", Icons.Filled.Brightness6, false) to { start(ctx, Intent(Settings.ACTION_DISPLAY_SETTINGS)) },
                    Triple("Súkromie", if (privacy) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, privacy) to { onPrivacy(!privacy) },
                    Triple("Zamknúť", Icons.Filled.Lock, false) to onLock,
                    Triple("Nastavenia", Icons.Filled.Settings, false) to onSettings,
                )
                tiles.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (t, action) ->
                            val (label, icon, on) = t
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(58.dp).clip(RoundedCornerShape(20.dp))
                                        .background(if (on) Os.Cyan else Os.Glass)
                                        .clickable { action() },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(icon, label, tint = if (on) Color(0xFF001018) else Os.Text) }
                                Spacer(Modifier.height(4.dp))
                                Text(label, color = Os.TextDim, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private fun setTorch(ctx: Context, on: Boolean): Boolean = runCatching {
    val cm = ctx.getSystemService(CameraManager::class.java)
    val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    cm.setTorchMode(id, on)
    on
}.getOrDefault(false)

// ---------------------------------------------------------------- Zoznam appiek

@Composable
private fun AppDrawer(apps: List<LaunchableApp>, onClose: () -> Unit, onLaunch: (LaunchableApp) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    Box(Modifier.fillMaxSize().background(Color(0xF205070F))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Hľadať v ${apps.size} appkách", color = Os.TextDim) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Os.TextDim) },
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Os.Cyan, unfocusedBorderColor = Os.GlassBorder,
                    focusedTextColor = Os.Text, unfocusedTextColor = Os.Text,
                ),
            )
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(shown, key = { it.packageName + "/" + it.activity }) { app ->
                    Box(contentAlignment = Alignment.Center) { RealAppTile(app, false) { onLaunch(app) } }
                }
            }
            Text(
                "Zavrieť", color = Os.TextDim, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onClose).padding(12.dp),
            )
        }
    }
}

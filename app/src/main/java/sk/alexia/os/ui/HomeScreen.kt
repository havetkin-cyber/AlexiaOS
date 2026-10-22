package sk.alexia.os.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import sk.alexia.os.data.HomeLayout
import sk.alexia.os.data.OsStore
import sk.alexia.os.system.InstalledApps
import sk.alexia.os.system.LaunchableApp
import sk.alexia.os.system.WidgetHost
import sk.alexia.os.ui.theme.Os
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class OsApp(val name: String, val icon: ImageVector, val colors: List<Color>, val screen: OsScreen)

private val builtIn = listOf(
    OsApp("Trezor", Icons.Outlined.Inventory2, listOf(Color(0xFF7A3DFF), Color(0xFFB06BFF)), OsScreen.VAULT),
    OsApp("Súbory", Icons.Outlined.Lock, listOf(Color(0xFFFF3D8B), Color(0xFFFF8AB8)), OsScreen.FILES),
    OsApp("Správy", Icons.Filled.Chat, listOf(Color(0xFF00A3FF), Color(0xFF3DE0FF)), OsScreen.MESSAGES),
    OsApp("Bezpečnosť", Icons.Filled.Shield, listOf(Color(0xFF00B37A), Color(0xFF4BE3A4)), OsScreen.SECURITY),
    OsApp("Nastavenia", Icons.Filled.Settings, listOf(Color(0xFF4A5068), Color(0xFF8A91B0)), OsScreen.SETTINGS),
)

/** Appky rodiny Alexia (Alexia Core chat, Shield, PDF…) – nájdu sa podľa názvu. */
private fun isAlexiaApp(app: LaunchableApp): Boolean =
    app.label.contains("alexia", ignoreCase = true) || app.packageName.contains("alexia", ignoreCase = true)

fun LaunchableApp.key(): String = "$packageName/$activity"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(store: OsStore, wallpaper: Int, onOpen: (OsScreen) -> Unit, onLock: () -> Unit) {
    val ctx = LocalContext.current
    var drawer by remember { mutableStateOf(false) }
    var shade by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(store.privacyMode) }
    var layout by remember { mutableStateOf(HomeLayout.load(ctx)) }
    var actionApp by remember { mutableStateOf<LaunchableApp?>(null) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var widgetPicker by remember { mutableStateOf(false) }
    var editWidgets by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val apps by produceState(emptyList<LaunchableApp>(), refresh) { value = InstalledApps.load(ctx) }
    val byKey = remember(apps) { apps.associateBy { it.key() } }
    val alexiaApps = remember(apps) { apps.filter(::isAlexiaApp) }
    val notes = remember { store.loadNotes().size }
    val notifications by sk.alexia.os.system.AlexiaNotificationService.items.collectAsState()

    fun update(l: HomeLayout) { layout = l; HomeLayout.save(ctx, l) }

    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    BackHandler(enabled = drawer || shade || actionApp != null || openFolder != null || widgetPicker) {
        drawer = false; shade = false; actionApp = null; openFolder = null; widgetPicker = false
    }

    // ---- Pridávanie widgetu: povolenie → konfigurácia → plocha ----
    var pendingWidget by remember { mutableIntStateOf(-1) }
    val configure = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pendingWidget
        if (id != -1) {
            if (r.resultCode == Activity.RESULT_OK) update(layout.copy(widgets = layout.widgets + id)) else WidgetHost.delete(ctx, id)
        }
        pendingWidget = -1
    }
    fun finishWidget(id: Int) {
        val info = WidgetHost.info(ctx, id)
        val cfg = info?.configure
        if (cfg != null) {
            val launched = runCatching {
                configure.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(cfg).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            }.isSuccess
            if (!launched) { update(layout.copy(widgets = layout.widgets + id)); pendingWidget = -1 }
        } else {
            update(layout.copy(widgets = layout.widgets + id)); pendingWidget = -1
        }
    }
    val bind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pendingWidget
        if (id != -1) {
            if (r.resultCode == Activity.RESULT_OK) finishWidget(id) else { WidgetHost.delete(ctx, id); pendingWidget = -1 }
        }
    }
    fun addWidget(p: AppWidgetProviderInfo) {
        widgetPicker = false
        val id = WidgetHost.allocate(ctx)
        pendingWidget = id
        if (WidgetHost.manager(ctx).bindAppWidgetIdIfAllowed(id, p.provider)) {
            finishWidget(id)
        } else {
            bind.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, p.provider)
            )
        }
    }

    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                var startY = 0f
                detectVerticalDragGestures(
                    onDragStart = { startY = it.y; total = 0f },
                    onDragEnd = {
                        when {
                            total < -140f -> drawer = true
                            total > 140f && startY < size.height * 0.4f -> shade = true
                        }
                    },
                ) { _, d -> total += d }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onLock() }) }
    ) {
        OsWallpaper(store, wallpaper, dim = 0.1f)

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)), color = Os.Text, fontSize = 72.sp, fontWeight = FontWeight.Thin)
                    Text(slovakDate(now), color = Os.Text, style = MaterialTheme.typography.titleMedium)
                }
                if (notifications.isNotEmpty()) {
                    Glass(radius = 16.dp, onClick = { shade = true }) {
                        Text("🔔 ${notifications.size}", color = Os.Text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            EncryptedBadge(if (privacy) "Režim súkromia zapnutý" else "Všetky dáta šifrované")
            Spacer(Modifier.height(18.dp))

            // Widgety AlexiaOS
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Glass(Modifier.weight(1f).height(104.dp), onClick = { onOpen(OsScreen.SECURITY) }) {
                    Column(Modifier.padding(14.dp)) {
                        Icon(Icons.Filled.Shield, null, tint = Os.Mint)
                        Spacer(Modifier.weight(1f))
                        Text("Chránené", color = Os.Text, style = MaterialTheme.typography.titleMedium)
                        Text("Šifrovanie aktívne", color = Os.TextDim, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Glass(Modifier.weight(1f).height(104.dp), onClick = { onOpen(OsScreen.VAULT) }) {
                    Column(Modifier.padding(14.dp)) {
                        Icon(Icons.Outlined.Inventory2, null, tint = Os.Violet)
                        Spacer(Modifier.weight(1f))
                        Text(if (privacy) "•••" else "$notes", color = Os.Text, style = MaterialTheme.typography.titleMedium)
                        Text("tajných poznámok", color = Os.TextDim, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Systémové widgety
            layout.widgets.forEach { id ->
                Spacer(Modifier.height(12.dp))
                SystemWidget(id, editWidgets) {
                    WidgetHost.delete(ctx, id)
                    update(layout.copy(widgets = layout.widgets - id))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                if (layout.widgets.isNotEmpty()) {
                    TextButton(onClick = { editWidgets = !editWidgets }) { Text(if (editWidgets) "Hotovo" else "Upraviť widgety", color = Os.TextDim) }
                }
                TextButton(onClick = { widgetPicker = true }) { Text("+ Widget", color = Os.TextDim) }
            }

            // Appky AlexiaOS
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                builtIn.forEach { a ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onOpen(a.screen) }) {
                        OsAppIcon(a.icon, a.colors, size = 56.dp)
                        Spacer(Modifier.height(6.dp))
                        AppLabel(a.name)
                    }
                }
            }

            // Pripnuté appky a priečinky
            val pinnedApps = layout.pinned.mapNotNull { byKey[it] }
            if (pinnedApps.isNotEmpty() || layout.folders.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                val tiles: List<@Composable () -> Unit> =
                    layout.folders.map { f -> @Composable { FolderTile(f, byKey, layout.themedIcons) { openFolder = f.name } } } +
                        pinnedApps.map { app ->
                            @Composable {
                                AppTile(app, layout.themedIcons, privacy, onLong = { actionApp = app }) { InstalledApps.launch(ctx, app) }
                            }
                        }
                tiles.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { it() }
                        repeat(4 - row.size) { Spacer(Modifier.size(76.dp)) }
                    }
                }
            }

            // Rodina Alexia
            if (alexiaApps.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Glass(Modifier.fillMaxWidth(), radius = 28.dp) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Alexia Core", color = Os.Text, fontSize = 26.sp, fontWeight = FontWeight.Light)
                        Spacer(Modifier.height(12.dp))
                        alexiaApps.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                row.forEach { app ->
                                    AppTile(app, themed = false, privacy = privacy, onLong = { actionApp = app }) { InstalledApps.launch(ctx, app) }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.size(76.dp)) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "↑ všetky appky • ↓ notifikácie • 2× ťuk zamknúť • podrž appku pre možnosti",
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
                DockIcon(Icons.Filled.Phone, listOf(Color(0xFF00B37A), Color(0xFF4BE3A4))) { start(ctx, Intent(Intent.ACTION_DIAL)) }
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

        AnimatedVisibility(shade, enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut() + slideOutVertically { -it / 3 }) {
            NotificationShade(
                privacy = privacy,
                onPrivacy = { privacy = it; store.privacyMode = it },
                onLock = { shade = false; onLock() },
                onSettings = { shade = false; onOpen(OsScreen.SETTINGS) },
                onClose = { shade = false },
            )
        }

        AnimatedVisibility(drawer, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
            AppDrawer(apps, layout.themedIcons, onClose = { drawer = false }, onLong = { actionApp = it }) {
                InstalledApps.launch(ctx, it); drawer = false
            }
        }

        openFolder?.let { name ->
            val folder = layout.folders.firstOrNull { it.name == name }
            if (folder == null) openFolder = null else {
                FolderOverlay(
                    folder, byKey, layout.themedIcons,
                    onClose = { openFolder = null },
                    onLaunch = { InstalledApps.launch(ctx, it); openFolder = null },
                    onLong = { actionApp = it },
                    onDelete = { update(layout.copy(folders = layout.folders - folder)); openFolder = null },
                )
            }
        }

        actionApp?.let { app ->
            AppActionsSheet(
                app = app,
                layout = layout,
                onUpdate = { update(it) },
                onUninstalled = { refresh++ },
                onClose = { actionApp = null },
            )
        }

        if (widgetPicker) WidgetPicker(onPick = { addWidget(it) }, onClose = { widgetPicker = false })
    }
}

// ---------------------------------------------------------------- Dlaždice

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(app: LaunchableApp, themed: Boolean, privacy: Boolean, onLong: () -> Unit, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(onLongClick = onLong, onClick = onClick),
    ) {
        AppIcon(app, themed)
        Spacer(Modifier.height(6.dp))
        AppLabel(if (privacy) "•••" else app.label)
    }
}

@Composable
private fun FolderTile(folder: HomeLayout.Folder, byKey: Map<String, LaunchableApp>, themed: Boolean, onClick: () -> Unit) {
    val apps = folder.apps.mapNotNull { byKey[it] }.take(4)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Glass(Modifier.size(60.dp), radius = 18.dp, strong = true) {
            if (apps.isEmpty()) {
                Icon(Icons.Filled.Folder, null, tint = Os.Text, modifier = Modifier.align(Alignment.Center))
            } else {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    apps.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { row.forEach { AppIcon(it, themed, size = 21.dp) } }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        AppLabel(folder.name)
    }
}

@Composable
private fun SystemWidget(id: Int, editing: Boolean, onRemove: () -> Unit) {
    val ctx = LocalContext.current
    val info = remember(id) { WidgetHost.info(ctx, id) } ?: return
    val density = LocalDensity.current
    val heightDp = with(density) { maxOf(info.minHeight, 160).toDp() }
    Box(Modifier.fillMaxWidth()) {
        Glass(Modifier.fillMaxWidth().height(heightDp), radius = 24.dp) {
            AndroidView(
                factory = { c -> WidgetHost.get(c).createView(c, id, info).apply { setAppWidget(id, info) } },
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
        if (editing) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(50)).background(Os.Rose),
            ) { Icon(Icons.Filled.Close, "Odstrániť", tint = Color.White) }
        }
    }
}

@Composable
private fun DockIcon(icon: ImageVector, colors: List<Color>, onClick: () -> Unit) {
    Box(Modifier.clickable(onClick = onClick)) { OsAppIcon(icon, colors, size = 54.dp) }
}

fun start(ctx: Context, intent: Intent) {
    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
        Image(img, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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

// ---------------------------------------------------------------- Zoznam appiek

@Composable
private fun AppDrawer(
    apps: List<LaunchableApp>,
    themed: Boolean,
    onClose: () -> Unit,
    onLong: (LaunchableApp) -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
) {
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
                items(shown, key = { it.key() }) { app ->
                    Box(contentAlignment = Alignment.Center) {
                        AppTile(app, themed, false, onLong = { onLong(app) }) { onLaunch(app) }
                    }
                }
            }
            Text(
                "Zavrieť", color = Os.TextDim, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onClose).padding(12.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Priečinok

@Composable
private fun FolderOverlay(
    folder: HomeLayout.Folder,
    byKey: Map<String, LaunchableApp>,
    themed: Boolean,
    onClose: () -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
    onLong: (LaunchableApp) -> Unit,
    onDelete: () -> Unit,
) {
    val apps = folder.apps.mapNotNull { byKey[it] }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).plainClick(onClose), contentAlignment = Alignment.Center) {
        Glass(Modifier.padding(24.dp).fillMaxWidth().plainClick { }, radius = 32.dp, strong = true) {
            Column(Modifier.padding(20.dp)) {
                Text(folder.name, color = Os.Text, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(14.dp))
                if (apps.isEmpty()) Text("Priečinok je prázdny", color = Os.TextDim)
                apps.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { app -> AppTile(app, themed, false, onLong = { onLong(app) }) { onLaunch(app) } }
                        repeat(3 - row.size) { Spacer(Modifier.size(76.dp)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDelete) { Text("Zrušiť priečinok", color = Os.Rose) }
            }
        }
    }
}

// ---------------------------------------------------------------- Možnosti appky

@Composable
private fun AppActionsSheet(
    app: LaunchableApp,
    layout: HomeLayout,
    onUpdate: (HomeLayout) -> Unit,
    onUninstalled: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val key = app.key()
    var newFolder by remember { mutableStateOf("") }
    val pinned = key in layout.pinned
    val inFolder = layout.folders.firstOrNull { key in it.apps }
    val uninstall = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onUninstalled() }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).plainClick(onClose), contentAlignment = Alignment.BottomCenter) {
        Glass(
            Modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth().plainClick { },
            radius = 32.dp, strong = true,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, themed = false, size = 48.dp)
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(app.label, color = Os.Text, style = MaterialTheme.typography.titleMedium)
                        Text(app.packageName, color = Os.TextDim, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                SheetAction(if (pinned) "Odopnúť z plochy" else "📌 Pripnúť na plochu") {
                    onUpdate(layout.copy(pinned = if (pinned) layout.pinned - key else layout.pinned + key)); onClose()
                }
                if (inFolder != null) {
                    SheetAction("Odobrať z priečinka „${inFolder.name}“") {
                        onUpdate(layout.copy(folders = layout.folders.map { if (it == inFolder) it.copy(apps = it.apps - key) else it })); onClose()
                    }
                } else {
                    layout.folders.forEach { f ->
                        SheetAction("📁 Pridať do „${f.name}“") {
                            onUpdate(layout.copy(folders = layout.folders.map { if (it == f) it.copy(apps = it.apps + key) else it })); onClose()
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            newFolder, { newFolder = it }, Modifier.weight(1f), singleLine = true,
                            placeholder = { Text("Nový priečinok…", color = Os.TextDim) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Os.Cyan, unfocusedBorderColor = Os.GlassBorder,
                                focusedTextColor = Os.Text, unfocusedTextColor = Os.Text,
                            ),
                        )
                        IconButton(onClick = {
                            val name = newFolder.trim()
                            if (name.isNotEmpty() && layout.folders.none { it.name == name }) {
                                onUpdate(layout.copy(folders = layout.folders + HomeLayout.Folder(name, listOf(key)), pinned = layout.pinned - key))
                                onClose()
                            }
                        }) { Icon(Icons.Filled.Add, "Vytvoriť", tint = Os.Cyan) }
                    }
                }
                SheetAction("ℹ️ Informácie o appke") {
                    start(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))); onClose()
                }
                SheetAction("🗑 Odinštalovať", color = Os.Rose) {
                    runCatching { uninstall.launch(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))) }
                    onClose()
                }
            }
        }
    }
}

@Composable
private fun SheetAction(text: String, color: Color = Os.Text, onClick: () -> Unit) {
    Text(
        text, color = color, style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp),
    )
}

// ---------------------------------------------------------------- Výber widgetu

@Composable
private fun WidgetPicker(onPick: (AppWidgetProviderInfo) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val providers = remember { WidgetHost.providers(ctx) }
    Box(Modifier.fillMaxSize().background(Color(0xF205070F))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pridať widget", color = Os.Text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Zavrieť", tint = Os.Text) }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listItems(providers) { p ->
                    val label = remember(p) { p.loadLabel(ctx.packageManager) }
                    val app = remember(p) {
                        runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(p.provider.packageName, 0)).toString() }
                            .getOrDefault(p.provider.packageName)
                    }
                    Glass(Modifier.fillMaxWidth(), onClick = { onPick(p) }) {
                        Column(Modifier.padding(14.dp)) {
                            Text(label, color = Os.Text, style = MaterialTheme.typography.titleMedium)
                            Text(app, color = Os.TextDim, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

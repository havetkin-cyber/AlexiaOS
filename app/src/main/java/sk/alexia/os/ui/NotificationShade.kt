package sk.alexia.os.ui

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import sk.alexia.os.system.AlexiaNotificationService
import sk.alexia.os.system.OsNotification
import sk.alexia.os.ui.theme.Os
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Potiahnutie zhora: rýchle prepínače + notifikácie celého telefónu v štýle AlexiaOS. */
@Composable
fun NotificationShade(
    privacy: Boolean,
    onPrivacy: (Boolean) -> Unit,
    onLock: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var torch by remember { mutableStateOf(false) }
    var access by remember { mutableStateOf(AlexiaNotificationService.hasAccess(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = AlexiaNotificationService.hasAccess(ctx) }
    val notifications by AlexiaNotificationService.items.collectAsState()
    val battery = remember {
        ctx.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).plainClick(onClose)) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(12.dp).fillMaxSize()) {
            Glass(Modifier.fillMaxWidth().plainClick { }, radius = 32.dp, strong = true) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ovládací panel", color = Os.Text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (battery >= 0) Text("🔋 $battery %", color = Os.TextDim, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile("Wi-Fi", Icons.Filled.Wifi, false, Modifier.weight(1f)) {
                            start(ctx, if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS))
                        }
                        Tile("Bluetooth", Icons.Filled.Bluetooth, false, Modifier.weight(1f)) { start(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                        Tile("Baterka", Icons.Filled.FlashlightOn, torch, Modifier.weight(1f)) { torch = setTorch(ctx, !torch) }
                        Tile("Jas", Icons.Filled.Brightness6, false, Modifier.weight(1f)) { start(ctx, Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile("Súkromie", if (privacy) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, privacy, Modifier.weight(1f)) { onPrivacy(!privacy) }
                        Tile("Zamknúť", Icons.Filled.Lock, false, Modifier.weight(1f), onLock)
                        Tile("Nastavenia", Icons.Filled.Settings, false, Modifier.weight(1f), onSettings)
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Notifikácie", color = Os.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (notifications.any { it.clearable }) {
                    TextButton(onClick = { AlexiaNotificationService.clearAll() }) { Text("Vymazať všetky", color = Os.Cyan) }
                }
            }

            when {
                !access -> Glass(Modifier.fillMaxWidth().plainClick { }) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Povoľ AlexiaOS prístup k notifikáciám", color = Os.Text, style = MaterialTheme.typography.titleMedium)
                        Text("Notifikácie sa zobrazia tu, v štýle AlexiaOS. Nič sa neukladá ani neodosiela.", color = Os.TextDim, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { start(ctx, AlexiaNotificationService.accessIntent(ctx)) }) { Text("Povoliť", color = Os.Cyan) }
                    }
                }
                notifications.isEmpty() -> Text(
                    "Žiadne notifikácie ✨", color = Os.TextDim, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally),
                )
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(notifications, key = { it.key }) { n ->
                        NotificationCard(n, privacy, fmt.format(Date(n.time))) {
                            AlexiaNotificationService.open(n); onClose()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(n: OsNotification, privacy: Boolean, time: String, onOpen: () -> Unit) {
    Glass(Modifier.fillMaxWidth(), radius = 22.dp, strong = true, onClick = onOpen) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.appLabel, color = Os.Cyan, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text(time, color = Os.TextDim, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(2.dp))
                if (privacy) {
                    Text("🔒 Obsah skrytý (režim súkromia)", color = Os.TextDim, style = MaterialTheme.typography.bodyMedium)
                } else {
                    if (n.title.isNotBlank()) Text(n.title, color = Os.Text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (n.text.isNotBlank()) Text(n.text, color = Os.TextDim, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (n.clearable) {
                IconButton(onClick = { AlexiaNotificationService.dismiss(n.key) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, "Zrušiť", tint = Os.TextDim, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, icon: ImageVector, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(20.dp))
                .background(if (on) Os.Cyan else Os.Glass)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (on) Color(0xFF001018) else Os.Text) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Os.TextDim, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private fun setTorch(ctx: Context, on: Boolean): Boolean = runCatching {
    val cm = ctx.getSystemService(CameraManager::class.java)
    val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    cm.setTorchMode(id, on)
    on
}.getOrDefault(false)

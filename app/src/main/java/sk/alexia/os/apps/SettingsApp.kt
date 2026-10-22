package sk.alexia.os.apps

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.alexia.os.data.OsStore
import sk.alexia.os.ui.AppScaffold
import sk.alexia.os.ui.AuroraWallpaper
import sk.alexia.os.ui.Glass
import sk.alexia.os.ui.PinPad
import sk.alexia.os.ui.SectionTitle
import sk.alexia.os.ui.theme.Os

@Composable
fun SettingsApp(store: OsStore, wallpaper: Int, onWallpaper: (Int) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var changingPin by remember { mutableStateOf(false) }
    var pinMsg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(store.customWallpaper) }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            store.customWallpaper = uri.toString()
            custom = uri.toString()
        }
    }

    if (changingPin) {
        Box(Modifier.fillMaxSize()) {
            AuroraWallpaper(wallpaper, dim = 0.5f)
            PinPad(
                title = "Nový PIN", subtitle = "4 až 8 číslic", error = null, busy = busy, minLength = 4,
                onCancel = { changingPin = false },
                onSubmit = { pin ->
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.Default) { store.setPin(pin) }
                        busy = false
                        changingPin = false
                        pinMsg = "PIN zmenený ✓"
                    }
                },
            )
        }
        return
    }

    AppScaffold("Nastavenia", "AlexiaOS 0.1 • ukážková verzia", accent = listOf(Color(0xFF8A91B0), Os.Violet), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionTitle("Tapeta")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Os.wallpapers.forEachIndexed { i, (name, _, _) ->
                    val selected = custom == null && i == wallpaper
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Box(
                            Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp))
                                .border(if (selected) 2.dp else 1.dp, if (selected) Os.Cyan else Os.GlassBorder, RoundedCornerShape(16.dp))
                                .clickable {
                                    store.customWallpaper = null
                                    custom = null
                                    onWallpaper(i)
                                }
                        ) { AuroraWallpaper(i) }
                        Text(name, color = if (selected) Os.Cyan else Os.TextDim, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SettingRow(
                if (custom != null) "🖼 Vlastná tapeta je nastavená" else "🖼 Vybrať vlastnú tapetu z galérie",
                if (custom != null) "Ťukni pre zmenu fotky" else "Napríklad tvoja obľúbená fotka",
            ) { picker.launch(arrayOf("image/*")) }

            SectionTitle("Zabezpečenie")
            SettingRow("Zmeniť PIN", pinMsg ?: "PIN AlexiaOS chráni prístup k systému") { changingPin = true; pinMsg = null }
            SettingRow("Zámok telefónu a šifrovanie", "Systémové nastavenia zabezpečenia") {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }

            SectionTitle("Systém")
            SettingRow("Nastaviť AlexiaOS ako domovskú obrazovku", "Tlačidlo Domov potom otvorí AlexiaOS") {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            SettingRow("Nastavenia Androidu", "Všetky systémové nastavenia telefónu") {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }

            SectionTitle("O systéme")
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("AlexiaOS 0.1 „Aurora“", color = Os.Text, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ukážka (koncept) súkromného mobilného systému. Beží ako vrstva nad Androidom – " +
                            "rozhranie, šifrovanie a appky sú AlexiaOS, jadro a ovládače zostávajú z Androidu.",
                        color = Os.TextDim, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Glass(Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Os.Text, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = Os.TextDim, style = MaterialTheme.typography.bodySmall)
        }
    }
}

package sk.alexia.os.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import sk.alexia.os.crypto.CryptoEngine
import sk.alexia.os.data.OsStore
import sk.alexia.os.ui.AppScaffold
import sk.alexia.os.ui.EncryptedBadge
import sk.alexia.os.ui.Glass
import sk.alexia.os.ui.theme.Os
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Trezor: poznámky šifrované AES-256-GCM kľúčom z Android Keystore. */
@Composable
fun VaultApp(store: OsStore, onBack: () -> Unit) {
    var notes by remember { mutableStateOf(store.loadNotes()) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var attacker by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("d. M. HH:mm", Locale.getDefault()) }

    fun save(newList: List<OsStore.Note>) {
        notes = newList
        store.saveNotes(newList)
    }

    AppScaffold(
        "Trezor", "Poznámky šifrované v čipe telefónu",
        accent = listOf(Os.Violet, Color(0xFF7A3DFF)), onBack = onBack,
    ) {
        EncryptedBadge("AES-256-GCM • Android Keystore")
        Spacer(Modifier.height(12.dp))

        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Názov", color = Os.TextDim) }, colors = fieldColors())
                OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().height(110.dp),
                    placeholder = { Text("Tajný text, heslo, PIN…", color = Os.TextDim) }, colors = fieldColors())
                Button(
                    onClick = {
                        if (title.isNotBlank() || body.isNotBlank()) {
                            save(listOf(OsStore.Note(System.currentTimeMillis(), title.ifBlank { "Bez názvu" }, body, System.currentTimeMillis())) + notes)
                            title = ""; body = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Os.Violet),
                ) { Text("🔒 Zašifrovať a uložiť") }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${notes.size} poznámok", color = Os.TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { attacker = !attacker }) {
                Text(if (attacker) "Zobraziť normálne" else "👁 Ako to vidí zlodej", color = Os.Cyan)
            }
        }

        if (attacker) {
            val raw = remember(notes) { store.rawVault() }
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Súbor vault.alx (${raw.size} B) – bez kľúča z čipu je to len šum:", color = Os.TextDim, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (raw.isEmpty()) "(prázdny)" else CryptoEngine.toHex(raw, 160),
                        color = Os.Rose, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notes, key = { it.id }) { n ->
                    var open by remember { mutableStateOf(false) }
                    Glass(Modifier.fillMaxWidth(), onClick = { open = !open }) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(n.title, color = Os.Text, style = MaterialTheme.typography.titleMedium)
                                    Text(fmt.format(Date(n.time)), color = Os.TextDim, style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { save(notes.filterNot { it.id == n.id }) }) {
                                    Icon(Icons.Filled.Delete, "Zmazať", tint = Os.TextDim)
                                }
                            }
                            Text(
                                if (open) n.body else "•".repeat(minOf(18, n.body.length.coerceAtLeast(6))),
                                color = Os.Text, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(6.dp))
                                    .background(if (open) Color.Transparent else Os.Glass).padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Os.Cyan, unfocusedBorderColor = Os.GlassBorder,
    focusedTextColor = Os.Text, unfocusedTextColor = Os.Text, cursorColor = Os.Cyan,
)

package sk.alexia.os.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import sk.alexia.os.crypto.CryptoEngine
import sk.alexia.os.ui.AppScaffold
import sk.alexia.os.ui.EncryptedBadge
import sk.alexia.os.ui.Glass
import sk.alexia.os.ui.theme.Os

private data class Msg(val fromMe: Boolean, val text: String, val sealed: ByteArray)

/**
 * Ukážka end-to-end šifrovania: ty a Alexia máte každý vlastný pár kľúčov (ECDH P-256).
 * Spoločný kľúč si každá strana vypočíta sama, cez „sieť“ ide len zašifrovaný balík.
 */
@Composable
fun MessagesApp(onBack: () -> Unit) {
    val me = remember { CryptoEngine.newIdentity("Ty") }
    val alexia = remember { CryptoEngine.newIdentity("Alexia") }
    val myKey = remember { CryptoEngine.sharedKey(me.keyPair, alexia.keyPair.public) }
    val herKey = remember { CryptoEngine.sharedKey(alexia.keyPair, me.keyPair.public) }
    val messages = remember { mutableStateListOf<Msg>() }
    var input by remember { mutableStateOf("") }
    var showWire by remember { mutableStateOf(false) }
    var pendingReply by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()

    LaunchedEffect(Unit) {
        val hello = "Ahoj! Táto konverzácia je šifrovaná end-to-end. Ani AlexiaOS ju nevidí. 🔐"
        messages += Msg(false, hello, CryptoEngine.sealMessage(herKey, hello))
    }
    LaunchedEffect(pendingReply) {
        val text = pendingReply ?: return@LaunchedEffect
        delay(900)
        val sealed = CryptoEngine.sealMessage(herKey, replyTo(text))
        // Ty správu dešifruješ svojím vlastným výpočtom spoločného kľúča
        messages += Msg(false, CryptoEngine.openMessage(myKey, sealed), sealed)
        pendingReply = null
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex) }

    AppScaffold("Správy", "Alexia • šifrované end-to-end", accent = listOf(Os.Cyan, Os.Violet), onBack = onBack) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EncryptedBadge("ECDH P-256 + AES-256-GCM")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showWire = !showWire }) { Text(if (showWire) "Skryť sieť" else "Čo ide sieťou?", color = Os.Cyan) }
        }
        Glass(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(10.dp)) {
                Text("Odtlačok tvojho kľúča: ${me.fingerprint}", color = Os.TextDim, style = MaterialTheme.typography.labelSmall)
                Text("Odtlačok Alexie: ${alexia.fingerprint}", color = Os.TextDim, style = MaterialTheme.typography.labelSmall)
                Text(
                    if (myKey.contentEquals(herKey)) "✓ Obe strany vypočítali rovnaký kľúč – nikdy sa neposlal" else "✗ Kľúče nesedia",
                    color = Os.Mint, style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        LazyColumn(
            state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { m ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromMe) Alignment.CenterEnd else Alignment.CenterStart) {
                    Column(
                        Modifier.widthIn(max = 290.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (m.fromMe) Brush.linearGradient(listOf(Os.Violet, Os.Cyan)) else Brush.linearGradient(listOf(Os.GlassStrong, Os.Glass)))
                            .padding(12.dp)
                    ) {
                        Text(m.text, color = Os.Text, style = MaterialTheme.typography.bodyMedium)
                        if (showWire) {
                            Spacer(Modifier.height(6.dp))
                            Text("sieť: " + CryptoEngine.toHex(m.sealed, 40), color = Os.Bg.copy(alpha = 0.85f).takeIf { m.fromMe } ?: Os.Amber,
                                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Šifrovaná správa…", color = Os.TextDim) },
                shape = RoundedCornerShape(24.dp), colors = fieldColors(),
            )
            IconButton(onClick = {
                val text = input.trim()
                if (text.isNotEmpty()) {
                    // Zašifruješ svojím kľúčom, Alexia to otvorí svojím
                    val sealed = CryptoEngine.sealMessage(myKey, text)
                    messages += Msg(true, CryptoEngine.openMessage(herKey, sealed), sealed)
                    input = ""
                    pendingReply = text
                }
            }) { Icon(Icons.AutoMirrored.Filled.Send, "Poslať", tint = Os.Cyan) }
        }
    }
}

private fun replyTo(text: String): String {
    val t = text.lowercase()
    return when {
        "ahoj" in t || "čau" in t || "cau" in t -> "Ahoj! 💜 Som tu, všetko je šifrované."
        "heslo" in t || "pin" in t -> "Heslá si odlož do Trezoru – tam sú šifrované kľúčom z čipu."
        "alexia" in t -> "AlexiaOS – súkromie v jadre. Čo chceš vyskúšať?"
        "?" in t -> "Dobrá otázka! Táto správa prešla sieťou len ako náhodne vyzerajúce bajty."
        else -> "Prijaté a dešifrované ✓ (${text.length} znakov)"
    }
}

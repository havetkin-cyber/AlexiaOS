package sk.alexia.os.apps

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.alexia.os.crypto.CryptoEngine
import sk.alexia.os.data.OsStore
import sk.alexia.os.ui.AppScaffold
import sk.alexia.os.ui.Glass
import sk.alexia.os.ui.SectionTitle
import sk.alexia.os.ui.theme.Os

private data class Layer(val title: String, val ok: Boolean, val detail: String, val fix: Intent? = null)

/** Bezpečnostné centrum: vrstvy šifrovania a ochrany, každá skutočne overená v telefóne. */
@Composable
fun SecurityApp(store: OsStore, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val layers by produceState<List<Layer>?>(null) {
        value = withContext(Dispatchers.Default) { layers(ctx, store) }
    }

    AppScaffold("Bezpečnosť", "Vrstvy ochrany AlexiaOS", accent = listOf(Os.Mint, Os.Cyan), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val list = layers
            val score = if (list == null) 0 else (list.count { it.ok } * 100 / list.size)
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(170.dp)) {
                    val stroke = 14.dp.toPx()
                    val arc = Size(size.width - stroke, size.height - stroke)
                    val tl = Offset(stroke / 2, stroke / 2)
                    drawArc(Os.Glass, -90f, 360f, false, tl, arc, style = Stroke(stroke))
                    drawArc(
                        Brush.sweepGradient(listOf(Os.Mint, Os.Cyan, Os.Violet, Os.Mint)),
                        -90f, 360f * score / 100f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (list == null) "…" else "$score", color = Os.Text, fontSize = 48.sp, fontWeight = FontWeight.Light)
                    Text("skóre ochrany", color = Os.TextDim, style = MaterialTheme.typography.labelSmall)
                }
            }

            SectionTitle("Vrstvy šifrovania")
            list?.forEach { l ->
                Glass(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (l.ok) "✅" else "⚠️", fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.title, color = Os.Text, style = MaterialTheme.typography.titleMedium)
                            Text(l.detail, color = Os.TextDim, style = MaterialTheme.typography.bodySmall)
                        }
                        val fix = l.fix
                        if (!l.ok && fix != null) {
                            TextButton(onClick = { runCatching { ctx.startActivity(fix.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) {
                                Text("Opraviť", color = Os.Cyan)
                            }
                        }
                    }
                }
            }

            SectionTitle("Ako AlexiaOS chráni dáta")
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Hlavný kľúč AES-256 vzniká priamo v bezpečnostnom čipe a nikdy ho neopustí.",
                        "Trezor a nastavenia sa šifrujú AES-256-GCM, ktorý odhalí aj pokus o úpravu dát.",
                        "PIN sa neukladá – iba odtlačok PBKDF2-SHA256 so soľou a ${CryptoEngine.PBKDF2_ITERATIONS / 1000} 000 opakovaniami.",
                        "Po 5 chybných pokusoch sa zámok predlžuje, porovnávanie beží v konštantnom čase.",
                        "Správy používajú výmenu kľúčov ECDH – spoločný kľúč sa nikdy neposiela sieťou.",
                        "Pri odchode z AlexiaOS sa systém hneď zamkne.",
                    ).forEach { Text("• $it", color = Os.Text, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun layers(ctx: Context, store: OsStore): List<Layer> {
    val s = CryptoEngine.status(ctx)
    val km = ctx.getSystemService(KeyguardManager::class.java)
    val deviceLock = km?.isDeviceSecure == true
    val vaultOk = runCatching {
        val probe = "AlexiaOS".toByteArray()
        CryptoEngine.decrypt(CryptoEngine.encrypt(probe)).contentEquals(probe)
    }.getOrDefault(false)
    return listOf(
        Layer("Šifrovanie úložiska", s.storageEncrypted, s.storageDetail, Intent(Settings.ACTION_SECURITY_SETTINGS)),
        Layer("Hardvérový kľúč", s.hardwareKey, s.keyDetail),
        Layer("Šifrovanie AlexiaOS", vaultOk, if (vaultOk) "AES-256-GCM test prebehol úspešne" else "Šifrovací test zlyhal"),
        Layer("PIN AlexiaOS", store.hasPin, if (store.hasPin) "PBKDF2-SHA256, ${CryptoEngine.PBKDF2_ITERATIONS} iterácií" else "Nenastavený"),
        Layer(
            "Zámok telefónu", deviceLock,
            if (deviceLock) "Systémový zámok chráni kľúče aj po reštarte" else "Nastav PIN telefónu – bez neho je šifrovanie slabšie",
            Intent(Settings.ACTION_SECURITY_SETTINGS),
        ),
    )
}

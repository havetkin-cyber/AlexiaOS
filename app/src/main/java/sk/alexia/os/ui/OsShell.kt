package sk.alexia.os.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.alexia.os.apps.MessagesApp
import sk.alexia.os.apps.SecurityApp
import sk.alexia.os.apps.SettingsApp
import sk.alexia.os.apps.VaultApp
import sk.alexia.os.data.OsStore
import sk.alexia.os.ui.theme.Os
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OsScreen { HOME, VAULT, MESSAGES, SECURITY, SETTINGS }

private enum class Phase { BOOT, SETUP, LOCKED, UNLOCKED }

@Composable
fun AlexiaOS() {
    val ctx = LocalContext.current
    val store = remember { OsStore(ctx) }
    var phase by remember { mutableStateOf(Phase.BOOT) }
    var screen by remember { mutableStateOf(OsScreen.HOME) }
    var wallpaper by remember { mutableIntStateOf(store.wallpaper) }

    // Pri odchode z AlexiaOS sa systém zamkne
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (phase == Phase.UNLOCKED && store.hasPin) phase = Phase.LOCKED
    }

    LaunchedEffect(Unit) {
        delay(1600)
        phase = if (store.hasPin) Phase.LOCKED else Phase.SETUP
    }

    BackHandler(enabled = phase == Phase.UNLOCKED && screen != OsScreen.HOME) { screen = OsScreen.HOME }

    AnimatedContent(
        targetState = phase,
        transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(400)) },
        label = "phase",
    ) { p ->
        when (p) {
            Phase.BOOT -> BootScreen()
            Phase.SETUP -> SetupScreen(store, wallpaper) { phase = Phase.UNLOCKED }
            Phase.LOCKED -> LockScreen(store, wallpaper) { phase = Phase.UNLOCKED }
            Phase.UNLOCKED -> AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    if (targetState == OsScreen.HOME) {
                        (fadeIn(tween(250)) + scaleIn(initialScale = 1.06f)) togetherWith (fadeOut(tween(200)) + scaleOut(targetScale = 0.9f))
                    } else {
                        (fadeIn(tween(250)) + scaleIn(initialScale = 0.9f)) togetherWith (fadeOut(tween(200)) + scaleOut(targetScale = 1.06f))
                    }
                },
                label = "screen",
            ) { s ->
                val home = { screen = OsScreen.HOME }
                when (s) {
                    OsScreen.HOME -> HomeScreen(
                        store = store,
                        wallpaper = wallpaper,
                        onOpen = { screen = it },
                        onLock = { if (store.hasPin) phase = Phase.LOCKED },
                    )
                    OsScreen.VAULT -> VaultApp(store, home)
                    OsScreen.MESSAGES -> MessagesApp(home)
                    OsScreen.SECURITY -> SecurityApp(store, home)
                    OsScreen.SETTINGS -> SettingsApp(store, wallpaper, onWallpaper = {
                        wallpaper = it
                        store.wallpaper = it
                    }, onBack = home)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Boot

@Composable
private fun BootScreen() {
    Box(Modifier.fillMaxSize().background(Os.Bg), contentAlignment = Alignment.Center) {
        AuroraWallpaper(0, dim = 0.55f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(Os.Accent),
                contentAlignment = Alignment.Center,
            ) { Text("A", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light) }
            Spacer(Modifier.height(20.dp))
            Text("AlexiaOS", color = Os.Text, fontSize = 34.sp, fontWeight = FontWeight.Light, letterSpacing = 4.sp)
            Spacer(Modifier.height(6.dp))
            Text("Súkromie v jadre", color = Os.TextDim, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Inicializujem šifrovanie…",
            color = Os.TextDim, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp),
        )
    }
}

// ---------------------------------------------------------------- Setup

@Composable
private fun SetupScreen(store: OsStore, wallpaper: Int, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        AuroraWallpaper(wallpaper, dim = 0.35f)
        when (step) {
            0 -> Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Vitaj v", color = Os.TextDim, style = MaterialTheme.typography.headlineMedium)
                Text("AlexiaOS", color = Os.Text, fontSize = 48.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(24.dp))
                listOf(
                    "🔐" to "Všetky tvoje dáta sú šifrované AES-256",
                    "🧬" to "Kľúče sú uložené v bezpečnostnom čipe telefónu",
                    "💬" to "Správy so šifrovaním end-to-end",
                    "🛡️" to "Bez sledovania, bez reklám, bez cloudu",
                ).forEach { (e, t) ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e, fontSize = 22.sp)
                        Spacer(Modifier.width(14.dp))
                        Text(t, color = Os.Text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(36.dp))
                Button(
                    onClick = { step = 1 },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Os.Cyan, contentColor = Color(0xFF001018)),
                ) { Text("Začať", fontWeight = FontWeight.SemiBold) }
            }
            else -> PinPad(
                title = if (step == 1) "Vytvor si PIN" else "Zopakuj PIN",
                subtitle = "4 až 8 číslic • uloží sa len ako PBKDF2 odtlačok",
                error = error,
                busy = busy,
                minLength = 4,
                onSubmit = { pin ->
                    if (step == 1) {
                        first = pin; error = null; step = 2
                    } else if (pin != first) {
                        error = "PIN sa nezhoduje, skús znova"; first = ""; step = 1
                    } else {
                        busy = true
                        scope.launch {
                            withContext(Dispatchers.Default) { store.setPin(pin) }
                            busy = false
                            onDone()
                        }
                    }
                },
            )
        }
    }
}

// ---------------------------------------------------------------- Lock screen

@Composable
private fun LockScreen(store: OsStore, wallpaper: Int, onUnlock: () -> Unit) {
    var showPad by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lockedUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            var total = 0f
            detectVerticalDragGestures(onDragEnd = { if (total < -120f) showPad = true; total = 0f }) { _, d -> total += d }
        }
    ) {
        OsWallpaper(store, wallpaper, dim = if (showPad) 0.5f else 0.15f)

        AnimatedVisibility(!showPad, enter = fadeIn(), exit = fadeOut()) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(48.dp))
                Icon(Icons.Filled.Lock, null, tint = Os.Text, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)),
                    color = Os.Text, style = MaterialTheme.typography.displayLarge,
                )
                Text(slovakDate(now), color = Os.Text, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                EncryptedBadge("Zariadenie je zamknuté a zašifrované")
                Spacer(Modifier.weight(1f))
                Text(
                    "Potiahni nahor alebo ťukni na odomknutie",
                    color = Os.TextDim, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { showPad = true }.padding(12.dp),
                )
            }
        }

        AnimatedVisibility(
            showPad,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 },
        ) {
            val waiting = lockedUntil > now
            PinPad(
                title = "Zadaj PIN",
                subtitle = if (waiting) "Príliš veľa pokusov – počkaj ${(lockedUntil - now) / 1000 + 1} s" else "AlexiaOS",
                error = error,
                busy = busy || waiting,
                minLength = 4,
                onCancel = { showPad = false; error = null },
                onSubmit = { pin ->
                    busy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) { store.checkPin(pin) }
                        busy = false
                        if (ok) {
                            error = null
                            onUnlock()
                        } else {
                            val fails = store.failedAttempts
                            error = "Nesprávny PIN ($fails×)"
                            if (fails >= 5) lockedUntil = System.currentTimeMillis() + 30_000L * (fails - 4)
                        }
                    }
                },
            )
        }
    }
}

fun slovakDate(time: Long): String {
    val days = listOf("nedeľa", "pondelok", "utorok", "streda", "štvrtok", "piatok", "sobota")
    val months = listOf("januára", "februára", "marca", "apríla", "mája", "júna", "júla", "augusta", "septembra", "októbra", "novembra", "decembra")
    val c = java.util.Calendar.getInstance().apply { timeInMillis = time }
    return "${days[c.get(java.util.Calendar.DAY_OF_WEEK) - 1].replaceFirstChar { it.uppercase() }}, " +
        "${c.get(java.util.Calendar.DAY_OF_MONTH)}. ${months[c.get(java.util.Calendar.MONTH)]}"
}

// ---------------------------------------------------------------- PIN pad

@Composable
fun PinPad(
    title: String,
    subtitle: String,
    error: String?,
    busy: Boolean,
    minLength: Int,
    onCancel: (() -> Unit)? = null,
    onSubmit: (String) -> Unit,
) {
    var pin by remember(title) { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = Os.Text, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Os.TextDim, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(maxOf(minLength, pin.length)) { i ->
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .background(if (i < pin.length) Os.Cyan else Color.Transparent)
                        .border(1.5.dp, Os.Text.copy(alpha = 0.6f), CircleShape)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.height(24.dp)) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(20.dp), color = Os.Cyan, strokeWidth = 2.dp)
                error != null -> Text(error, color = Os.Rose, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(20.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(76.dp).clip(CircleShape)
                            .background(if (k == "OK") Os.Cyan.copy(alpha = 0.85f) else Os.Glass)
                            .border(1.dp, Os.GlassBorder, CircleShape)
                            .clickable(enabled = !busy) {
                                when (k) {
                                    "⌫" -> pin = pin.dropLast(1)
                                    "OK" -> if (pin.length >= minLength) { onSubmit(pin); pin = "" }
                                    else -> if (pin.length < 8) pin += k
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (k) {
                            "⌫" -> Icon(Icons.AutoMirrored.Filled.Backspace, "Zmazať", tint = Os.Text)
                            "OK" -> Text("OK", color = Color(0xFF001018), fontWeight = FontWeight.Bold)
                            else -> Text(k, color = Os.Text, fontSize = 28.sp, fontWeight = FontWeight.Light)
                        }
                    }
                }
            }
        }
        if (onCancel != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCancel) { Text("Zrušiť", color = Os.TextDim) }
        }
    }
}

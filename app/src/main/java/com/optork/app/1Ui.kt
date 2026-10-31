package com.optork.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val BG = Color(0xFF0A0A0C)
val SURF = Color(0xFF15151A)
val SURF2 = Color(0xFF23232B)
val ACC = Color(0xFF00E5A0)
val WARN = Color(0xFFFF5C7A)
val DIM = Color(0xFF8A8A94)
private const val ADB_CMD = "adb shell pm grant com.optork.app android.permission.WRITE_SECURE_SETTINGS"

@Composable
fun accColors() = ButtonDefaults.buttonColors(containerColor = ACC, contentColor = Color.Black)

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SURF).padding(16.dp),
        content = content
    )
}

@Composable
fun Metric(label: String, value: String, fraction: Float?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = DIM, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            Text(value, color = Color.White, fontSize = 12.sp)
        }
        if (fraction != null) {
            val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "metric")
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                color = ACC
            )
        }
    }
}

@Composable
fun BigAction(title: String, sub: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        colors = accColors()
    ) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
            Text(title, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(sub, fontSize = 11.sp)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = DIM, fontSize = 11.sp, letterSpacing = 2.sp)
}

@Composable
fun SplashScreen(status: String) {
    val inf = rememberInfiniteTransition(label = "splash")
    val pulse by inf.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )
    Column(
        Modifier.fillMaxSize().background(BG).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(150.dp).scale(pulse)
        )
        Spacer(Modifier.height(18.dp))
        Text("OPTORK", color = ACC, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 8.sp)
        Text(status, color = DIM, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        LinearProgressIndicator(modifier = Modifier.width(180.dp), color = ACC)
        Spacer(Modifier.height(28.dp))
        Text("por @eai._ldkz", color = DIM, fontSize = 11.sp)
    }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    var splash by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Iniciando...") }
    var tab by remember { mutableIntStateOf(0) }
    var optimizing by remember { mutableStateOf<Pair<Game, Boolean>?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf<DeviceProfile?>(null) }
    var cpuPct by remember { mutableIntStateOf(-1) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        status = "Lendo aplicativos do aparelho..."
        withContext(Dispatchers.IO) { AppRepo.load(ctx) }
        status = "Verificando atualizações..."
        update = withContext(Dispatchers.IO) { UpdateChecker.check(ctx) }
        status = "Pronto"
        delay(350)
        splash = false
    }

    LaunchedEffect(Unit) {
        val cpu = CpuSampler()
        while (true) {
            val pair = withContext(Dispatchers.IO) { DeviceProfiler.read(ctx) to cpu.sample() }
            profile = pair.first
            cpuPct = pair.second
            delay(2000)
        }
    }

    val opt = optimizing
    val p = profile
    if (splash) {
        SplashScreen(status)
    } else if (opt != null && p != null) {
        OptimizeScreen(
            game = opt.first,
            p = p,
            ultra = opt.second,
            onPlay = { optimizing = null },
            onFinish = {
                Optimizer.restore(ctx)
                optimizing = null
            }
        )
    } else {
        Scaffold(
            containerColor = BG,
            bottomBar = { BottomBar(tab) { tab = it } }
        ) { pad ->
            Crossfade(
                targetState = tab,
                modifier = Modifier.padding(pad).fillMaxSize()
            ) { t ->
                when (t) {
                    0 -> HomeTab(p, cpuPct, onUltra = { pickerOpen = true })
                    1 -> GamesTab(onOptimize = { optimizing = it to false })
                    2 -> RecommendTab()
                    3 -> ToolsTab()
                    else -> SupportTab(update)
                }
            }
        }
    }

    if (pickerOpen) {
        GamePicker(
            onDismiss = { pickerOpen = false },
            onPick = { g ->
                pickerOpen = false
                optimizing = g to true
            }
        )
    }
    update?.let { u -> UpdateDialog(u) { update = null } }
}

@Composable
fun BottomBar(tab: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("Início", "Jogos", "Dicas", "Ferramentas", "Suporte")
    val icons = listOf("◉", "▤", "★", "⚙", "✉")
    NavigationBar(containerColor = SURF) {
        labels.forEachIndexed { i, label ->
            NavigationBarItem(
                selected = tab == i,
                onClick = { onSelect(i) },
                icon = { Text(icons[i], fontSize = 16.sp) },
                label = { Text(label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ACC,
                    selectedTextColor = ACC,
                    unselectedIconColor = DIM,
                    unselectedTextColor = DIM,
                    indicatorColor = SURF2
                )
            )
        }
    }
}

@Composable
fun HomeTab(p: DeviceProfile?, cpu: Int, onUltra: () -> Unit) {
    val ctx = LocalContext.current
    var monitorOn by remember { mutableStateOf(Prefs.flag(ctx, "monitor")) }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("OPTORK", color = ACC, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
            Text("Otimização inteligente para jogos", color = DIM, fontSize = 13.sp)
        }
        if (p == null) {
            item { Text("Lendo seu aparelho...", color = DIM) }
        } else {
            item {
                Panel {
                    SectionTitle("SEU APARELHO")
                    Text(p.tier.label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${p.cores} núcleos  •  Tela ${p.refreshHz} Hz", color = DIM, fontSize = 12.sp)
                }
            }
            item {
                Panel {
                    SectionTitle("MONITOR AO VIVO")
                    Spacer(Modifier.height(8.dp))
                    val used = (p.ramTotalMb - p.ramFreeMb).coerceAtLeast(0L)
                    val ramFrac = used.toFloat() / p.ramTotalMb.coerceAtLeast(1L)
                    Metric("RAM", "${(ramFrac * 100).toInt()}%  ($used/${p.ramTotalMb} MB)", ramFrac)
                    Metric("CPU", if (cpu >= 0) "$cpu%" else "medindo...", if (cpu >= 0) cpu / 100f else null)
                    Metric(
                        "BATERIA",
                        "${if (p.batteryPct >= 0) "${p.batteryPct}%" else "N/D"}  •  ${"%.1f".format(p.batteryTemp)}°C",
                        if (p.batteryPct >= 0) p.batteryPct / 100f else null
                    )
                    Metric("TELA", "${p.refreshHz} Hz  •  Térmico: ${p.thermal}", null)
                }
            }
            item {
                BigAction(
                    "MODO ULTRA",
                    "Encerra apps em segundo plano, inclusive protegidos, e foca no jogo",
                    onClick = onUltra
                )
            }
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Monitor flutuante", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("RAM, CPU, bateria e temperatura sobre qualquer app", color = DIM, fontSize = 11.sp)
                        }
                        Switch(
                            checked = monitorOn,
                            onCheckedChange = { monitorOn = toggleMonitor(ctx, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GamesTab(onOptimize: (Game) -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var favs by remember { mutableStateOf(Prefs.favorites(ctx)) }
    var colMem by remember { mutableStateOf(Prefs.collections(ctx)) }
    var colSel by remember { mutableStateOf<String?>(null) }
    var folderFor by remember { mutableStateOf<Game?>(null) }
    val usage by produceState(emptyMap<String, Long>()) {
        value = withContext(Dispatchers.IO) { Usage.top(ctx, 7, 300).toMap() }
    }
    val games = AppRepo.apps.filter { it.isGame }
    val names = Prefs.collectionNames(ctx)
    val shown: List<Game> = when {
        mode == 1 -> games.filter { it.pkg in favs }
        mode == 2 -> games.filter { usage.containsKey(it.pkg) }.sortedByDescending { usage[it.pkg] ?: 0L }
        mode == 3 && colSel != null -> games.filter { "$colSel|${it.pkg}" in colMem }
        mode == 3 -> emptyList()
        else -> games
    }.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }

    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("JOGOS", color = ACC, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Todos", "Favoritos", "Mais jogados", "Coleções").forEachIndexed { i, t ->
                    FilterChip(
                        selected = mode == i,
                        onClick = {
                            mode = i
                            colSel = null
                        },
                        label = { Text(t, fontSize = 11.sp) }
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Buscar jogo") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (mode == 3 && colSel == null) {
            if (names.isEmpty()) {
                item {
                    Text(
                        "Nenhuma coleção ainda. Toque em 'Coleção' em um jogo para criar a sua.",
                        color = DIM,
                        fontSize = 13.sp
                    )
                }
            }
            items(names, key = { it }) { n ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SURF)
                        .clickable { colSel = n }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(n, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${colMem.count { it.startsWith("$n|") }} jogos", color = DIM, fontSize = 12.sp)
                }
            }
        } else {
            if (mode == 3) {
                item { TextButton(onClick = { colSel = null }) { Text("← Coleções", color = ACC) } }
            }
            if (shown.isEmpty()) {
                item { Text("Nenhum jogo nesta lista.", color = DIM, fontSize = 13.sp) }
            }
            items(shown, key = { it.pkg }) { g ->
                GameRow(
                    g = g,
                    fav = g.pkg in favs,
                    onFav = { on ->
                        favs = if (on) favs + g.pkg else favs - g.pkg
                        Prefs.save(ctx, "fav", favs)
                    },
                    onFolder = { folderFor = g },
                    onOptimize = { onOptimize(g) }
                )
            }
        }
    }

    folderFor?.let { g ->
        CollectionDialog(g) {
            folderFor = null
            colMem = Prefs.collections(ctx)
        }
    }
}

@Composable
fun GameRow(g: Game, fav: Boolean, onFav: (Boolean) -> Unit, onFolder: () -> Unit, onOptimize: () -> Unit) {
    val img = remember(g.pkg) { g.icon.asImageBitmap() }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SURF).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(img, null, Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(g.label, color = Color.White, maxLines = 1, fontWeight = FontWeight.SemiBold)
            Row {
                TextButton(onClick = { onFav(!fav) }) {
                    Text(if (fav) "★ Favorito" else "☆ Favoritar", color = if (fav) ACC else DIM, fontSize = 11.sp)
                }
                TextButton(onClick = onFolder) { Text("Coleção", color = DIM, fontSize = 11.sp) }
            }
        }
        Button(onClick = onOptimize, colors = accColors()) {
            Text("OTIMIZAR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
fun CollectionDialog(g: Game, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var names by remember { mutableStateOf(Prefs.collectionNames(ctx)) }
    var mem by remember { mutableStateOf(Prefs.collections(ctx)) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        title = { Text("Coleções de ${g.label}") },
        text = {
            Column {
                names.forEach { n ->
                    val key = "$n|${g.pkg}"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n, modifier = Modifier.weight(1f))
                        Switch(
                            checked = key in mem,
                            onCheckedChange = { on ->
                                mem = if (on) mem + key else mem - key
                                Prefs.save(ctx, "col", mem)
                            }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text("Nova coleção") },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        val n = newName.trim()
                        if (n.isNotEmpty() && !n.contains("|")) {
                            names = (names + n).distinct().sorted()
                            mem = mem + "$n|${g.pkg}"
                            Prefs.save(ctx, "col", mem)
                            newName = ""
                        }
                    }) { Text("CRIAR") }
                }
            }
        }
    )
}

@Composable
fun GamePicker(onDismiss: () -> Unit, onPick: (Game) -> Unit) {
    val games = AppRepo.apps.filter { it.isGame }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text("Modo Ultra: escolha o jogo") },
        text = {
            Column {
                Text(
                    "Atenção: encerra todos os apps em segundo plano, inclusive os protegidos. Apps abertos na tela não são fechados pelo Android.",
                    fontSize = 12.sp,
                    color = WARN
                )
                Spacer(Modifier.height(8.dp))
                if (games.isEmpty()) {
                    Text("Nenhum jogo detectado.", color = DIM)
                }
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(games, key = { it.pkg }) { g ->
                        Text(
                            g.label,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(g) }.padding(vertical = 12.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun RecommendTab() {
    val ctx = LocalContext.current
    val recs by produceState(emptyList<Pair<Rec, String>>()) {
        value = withContext(Dispatchers.IO) { Recommender.recommend(ctx) }
    }
    val granted = remember { Usage.granted(ctx) }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("DICAS DE JOGOS", color = ACC, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        }
        item {
            Text(
                if (granted) "Baseado nos seus jogos mais jogados nos últimos 7 dias e nos seus favoritos."
                else "Ative o acesso ao uso de apps para recomendações baseadas no que você mais joga.",
                color = DIM,
                fontSize = 13.sp
            )
        }
        if (!granted) {
            item {
                OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                    Text("ATIVAR USO DE APPS")
                }
            }
        }
        items(recs, key = { it.first.pkg }) { pair ->
            val r = pair.first
            Panel {
                Text(r.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(r.genre, color = ACC, fontSize = 12.sp)
                Text(pair.second, color = DIM, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${r.pkg}"))
                        )
                    },
                    colors = accColors()
                ) { Text("VER NA LOJA", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun ToolsTab() {
    val ctx = LocalContext.current
    var pro by remember { mutableStateOf(Prefs.flag(ctx, "pro")) }
    var dnd by remember { mutableStateOf(isDndGranted(ctx)) }
    var callRole by remember { mutableStateOf(isCallScreeningHeld(ctx)) }
    var blocked by remember { mutableStateOf(Prefs.blocked(ctx)) }
    var hiddenBlock by remember { mutableStateOf(Prefs.flag(ctx, "block_hidden")) }
    var newNumber by remember { mutableStateOf("") }
    var canWrite by remember { mutableStateOf(Anim.canWrite(ctx)) }
    var animReduced by remember { mutableStateOf(Prefs.get(ctx).contains("anim_saved")) }
    var protectedSet by remember { mutableStateOf(Prefs.protected(ctx)) }
    var killMsg by remember { mutableStateOf("") }
    var usageOk by remember { mutableStateOf(Usage.granted(ctx)) }
    var fps by remember { mutableFloatStateOf(0f) }
    var testing by remember { mutableStateOf(false) }
    val counter = remember { FpsCounter { fps = it } }
    DisposableEffect(testing) {
        if (testing) counter.start()
        onDispose { counter.stop() }
    }
    val usage by produceState(emptyList<Pair<String, Long>>()) {
        value = withContext(Dispatchers.IO) { Usage.top(ctx, 1, 10) }
    }
    val labelOf = AppRepo.apps.associate { it.pkg to it.label }
    val userApps = AppRepo.apps.filter { !it.isSystem }

    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("FERRAMENTAS", color = ACC, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp) }

        item {
            Panel {
                SectionTitle("MODO PRO GAME")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Silencia ligações, WhatsApp, Instagram e demais notificações enquanto o jogo roda.",
                        color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f)
                    )
                    Switch(checked = pro, onCheckedChange = {
                        pro = it
                        Prefs.setFlag(ctx, "pro", it)
                    })
                }
                if (!dnd) {
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }, colors = accColors()) {
                            Text("CONCEDER", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { dnd = isDndGranted(ctx) }) { Text("VERIFICAR") }
                    }
                }
            }
        }

        item {
            Panel {
                SectionTitle("BLOQUEIO DE SPAM (LIGAÇÕES)")
                Text(
                    if (callRole) "Bloqueio ativo: o OPTORK filtra as ligações."
                    else "Ative para o OPTORK filtrar ligações de números bloqueados.",
                    color = Color.White, fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                if (!callRole) {
                    Button(onClick = {
                        requestCallScreening(ctx)
                        callRole = isCallScreeningHeld(ctx)
                    }, colors = accColors()) { Text("ATIVAR BLOQUEIO", fontWeight = FontWeight.Bold) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bloquear números ocultos", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = hiddenBlock, onCheckedChange = {
                        hiddenBlock = it
                        Prefs.setFlag(ctx, "block_hidden", it)
                    })
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newNumber,
                        onValueChange = { newNumber = it },
                        singleLine = true,
                        label = { Text("Número (com DDD)") },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        val n = newNumber.filter { it.isDigit() }
                        if (n.length >= 8) {
                            blocked = blocked + n
                            Prefs.save(ctx, "blocked", blocked)
                            newNumber = ""
                        }
                    }) { Text("ADD", color = ACC, fontWeight = FontWeight.Bold) }
                }
                blocked.sorted().forEach { n ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            blocked = blocked - n
                            Prefs.save(ctx, "blocked", blocked)
                        }) { Text("REMOVER", color = WARN, fontSize = 11.sp) }
                    }
                }
            }
        }

        item {
            Panel {
                SectionTitle("APPS QUE MAIS USARAM HOJE")
                if (!usageOk) {
                    Text("Permita o acesso ao uso de apps para ver quais consomem mais.", color = DIM, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                        Text("PERMITIR")
                    }
                    TextButton(onClick = { usageOk = Usage.granted(ctx) }) { Text("VERIFICAR", color = DIM) }
                } else {
                    val rows = usage.filter { it.first != ctx.packageName }.take(8)
                    if (rows.isEmpty()) Text("Sem dados de uso ainda.", color = DIM, fontSize = 12.sp)
                    rows.forEach { (pkg, ms) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(labelOf[pkg] ?: pkg, color = Color.White, fontSize = 13.sp, maxLines = 1)
                                Text("${ms / 60000} min hoje", color = DIM, fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                Optimizer.killOne(ctx, pkg)
                                killMsg = "Encerrado: ${labelOf[pkg] ?: pkg}"
                            }) { Text("ENCERRAR", color = ACC, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    if (killMsg.isNotEmpty()) Text(killMsg, color = DIM, fontSize = 11.sp)
                }
            }
        }

        item {
            Panel {
                SectionTitle("ANIMAÇÕES DO SISTEMA")
                if (canWrite) {
                    Text(
                        if (animReduced) "Animações reduzidas. Restaure quando terminar."
                        else "Permissão concedida. Reduza para o sistema responder mais rápido.",
                        color = Color.White, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(onClick = {
                            Optimizer.reduceAnimations(ctx, 0.5f)
                            animReduced = Prefs.get(ctx).contains("anim_saved")
                        }, colors = accColors()) { Text("REDUZIR", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            Optimizer.restoreAnimations(ctx)
                            animReduced = false
                        }) { Text("RESTAURAR") }
                    }
                } else {
                    Text(
                        "Precisa de uma permissão única pelo computador (USB). Veja o guia.",
                        color = Color.White, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(ADB_CMD, color = ACC, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(onClick = {
                            ctx.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("adb", ADB_CMD))
                        }, colors = accColors()) { Text("COPIAR", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { canWrite = Anim.canWrite(ctx) }) { Text("VERIFICAR") }
                    }
                }
            }
        }

        item {
            Panel {
                SectionTitle("TESTE DE FPS (TAXA DA TELA)")
                Text(
                    if (testing) "%.0f FPS".format(fps) else "Mede quadros por segundo. Mexa a tela enquanto testa.",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { testing = !testing }, colors = accColors()) {
                    Text(if (testing) "PARAR" else "INICIAR", fontWeight = FontWeight.Bold)
                }
            }
        }

        item { SectionTitle("APPS PROTEGIDOS (não são encerrados)") }
        items(userApps, key = { it.pkg }) { a ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SURF).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(a.label, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                Switch(
                    checked = a.pkg in protectedSet,
                    onCheckedChange = { on ->
                        protectedSet = if (on) protectedSet + a.pkg else protectedSet - a.pkg
                        Prefs.save(ctx, "protected", protectedSet)
                    }
                )
            }
        }
    }
}

@Composable
fun SupportTab(update: UpdateInfo?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ver = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0" }
    var type by remember { mutableStateOf("Sugestão") }
    var message by remember { mutableStateOf("") }
    var checkMsg by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("SUPORTE", color = ACC, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp) }

        item {
            Panel {
                Text("OPTORK v$ver", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Criado por @eai._ldkz", color = DIM, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row {
                    Button(
                        onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CREATOR_IG))) },
                        colors = accColors()
                    ) { Text("INSTAGRAM", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        scope.launch {
                            checking = true
                            checkMsg = when {
                                !UpdateChecker.isConfigured() -> "Atualizações não configuradas (veja Engine.kt)"
                                else -> {
                                    val u = withContext(Dispatchers.IO) { UpdateChecker.check(ctx) }
                                    if (u != null) "Nova versão ${u.version} disponível" else "Você está na versão mais recente"
                                }
                            }
                            checking = false
                        }
                    }) { Text(if (checking) "VERIFICANDO..." else "ATUALIZAÇÕES") }
                }
                if (checkMsg.isNotEmpty()) Text(checkMsg, color = DIM, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                if (update != null) Text("Atualização ${update.version} disponível", color = ACC, fontSize = 12.sp)
            }
        }

        item {
            Panel {
                SectionTitle("ENVIAR SUGESTÃO, ERRO OU MELHORIA")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Erro", "Sugestão", "Melhoria", "Mudança").forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t, fontSize = 11.sp) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Descreva aqui") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val body = message.trim()
                        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))
                            .putExtra(Intent.EXTRA_SUBJECT, "[OPTORK][$type] v$ver")
                            .putExtra(
                                Intent.EXTRA_TEXT,
                                "$body\n\n— Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
                            )
                        ctx.startActivity(i)
                    },
                    enabled = message.isNotBlank(),
                    colors = accColors()
                ) { Text("ENVIAR PARA $SUPPORT_EMAIL", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun UpdateDialog(u: UpdateInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Atualização disponível") },
        text = { Text("A versão ${u.version} do OPTORK já está disponível. Quer baixar agora?") },
        confirmButton = {
            TextButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)))
                onDismiss()
            }) { Text("BAIXAR") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Depois") } }
    )
}

@Composable
fun OptimizeScreen(game: Game, p: DeviceProfile, ultra: Boolean, onPlay: () -> Unit, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    var prog by remember { mutableStateOf(Progress("Iniciando", 0f)) }
    LaunchedEffect(game.pkg) {
        Optimizer.run(ctx, game, p, ultra).collect { prog = it }
    }
    val res = prog.result
    val img = remember(game.pkg) { game.icon.asImageBitmap() }
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )

    Column(
        Modifier.fillMaxSize().background(BG).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(img, null, Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(12.dp))
        Text(game.label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(24.dp))
        if (res == null) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(130.dp)) {
                CircularProgressIndicator(
                    progress = { prog.pct },
                    modifier = Modifier.size(130.dp),
                    color = ACC,
                    strokeWidth = 6.dp
                )
                Text("${(prog.pct * 100).toInt()}%", color = ACC, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                if (ultra) "Modo Ultra: aplicando correções" else "Aplicando correções e otimizações",
                color = ACC, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(prog.step + "…", color = DIM, fontSize = 13.sp)
        } else {
            Text("OTIMIZADO", color = ACC, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Spacer(Modifier.height(12.dp))
            Panel {
                Text("RAM liberada: ${res.ramFreedMb} MB", color = Color.White, fontSize = 13.sp)
                Text("Apps encerrados: ${res.appsClosed}", color = Color.White, fontSize = 13.sp)
                Text("Cache limpo: ${res.cacheMb} MB", color = Color.White, fontSize = 13.sp)
                Text("Modo foco: ${res.focusMode}", color = Color.White, fontSize = 13.sp)
                Text(
                    "Animações reduzidas: ${if (res.anim) "sim" else "sem permissão"}",
                    color = Color.White, fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Panel {
                SectionTitle("AJUSTES RECOMENDADOS NO JOGO")
                Spacer(Modifier.height(6.dp))
                res.tips.forEach {
                    Text("• $it", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    ctx.packageManager.getLaunchIntentForPackage(game.pkg)?.let { ctx.startActivity(it) }
                    onPlay()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = accColors()
            ) { Text("JOGAR AGORA", fontWeight = FontWeight.Black) }
            TextButton(onClick = onFinish) { Text("Finalizar e restaurar", color = DIM) }
        }
    }
}

package com.optork.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.align
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private const val ADB_CMD = "adb shell pm grant com.optork.app android.permission.WRITE_SECURE_SETTINGS"
private const val PURPLE = 0xFF6E5BFF

// ---------- Base visual ----------

fun Modifier.glass(radius: Dp = 26.dp, fill: Color = GLASS): Modifier =
    this.clip(RoundedCornerShape(radius)).background(fill).border(1.dp, STROKE, RoundedCornerShape(radius))

@Composable
fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glass().padding(18.dp), content = content)
}

@Composable
fun Eyebrow(text: String) {
    Text(text.uppercase(), color = DIM, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun AmbientBackground() {
    val inf = rememberInfiniteTransition(label = "ambient")
    val drift by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift"
    )
    Box(Modifier.fillMaxSize().background(BG)) {
        Box(
            Modifier
                .offset(x = (-90 + 70 * drift).dp, y = (-80 + 40 * drift).dp)
                .size(300.dp)
                .blur(120.dp)
                .background(ACC.copy(alpha = 0.20f), CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (70 - 50 * drift).dp, y = (60 - 30 * drift).dp)
                .size(340.dp)
                .blur(130.dp)
                .background(Color(PURPLE).copy(alpha = 0.22f), CircleShape)
        )
    }
}

@Composable
fun PressButton(text: String, modifier: Modifier = Modifier, primary: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
    val bg by animateColorAsState(if (primary) WHITE else GLASS_HI, label = "pressbg")
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, STROKE, RoundedCornerShape(50))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (primary) BG else WHITE,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) WHITE else GLASS, label = "pill")
    val fg by animateColorAsState(if (selected) BG else WHITE, label = "pillfg")
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, STROKE, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun optSwitch() = SwitchDefaults.colors(
    checkedThumbColor = BG,
    checkedTrackColor = WHITE,
    uncheckedThumbColor = DIM,
    uncheckedTrackColor = GLASS_HI,
    uncheckedBorderColor = Color.Transparent
)

@Composable
fun Spark(values: List<Float>, color: Color = WHITE) {
    Canvas(Modifier.fillMaxWidth().height(42.dp)) {
        if (values.size < 2) return@Canvas
        val path = Path()
        val stepX = size.width / (values.size - 1)
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height * (1f - v.coerceIn(0f, 1f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color,
            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun StatTile(label: String, value: String, sub: String, history: List<Float>, modifier: Modifier = Modifier) {
    Column(modifier.glass(24.dp).padding(16.dp)) {
        Eyebrow(label)
        Spacer(Modifier.height(8.dp))
        Text(value, color = WHITE, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(sub, color = DIM, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Spark(history, ACC)
    }
}

// ---------- Splash ----------

@Composable
fun SplashScreen(status: String) {
    val inf = rememberInfiniteTransition(label = "splash")
    val breathe by inf.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    val enter by animateFloatAsState(if (show) 1f else 0f, tween(900), label = "enter")

    Box(Modifier.fillMaxSize().background(BG), contentAlignment = Alignment.Center) {
        Box(Modifier.size(280.dp).blur(100.dp).background(ACC.copy(alpha = 0.22f), CircleShape))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                alpha = enter
                scaleX = 0.8f + 0.2f * enter
                scaleY = 0.8f + 0.2f * enter
            }
        ) {
            Box(
                Modifier
                    .size(132.dp)
                    .graphicsLayer {
                        scaleX = breathe
                        scaleY = breathe
                    }
                    .clip(RoundedCornerShape(34.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp)
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "OPTORK",
                color = WHITE,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 10.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(status, color = DIM, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(modifier = Modifier.width(180.dp), color = WHITE, trackColor = GLASS_HI)
            Spacer(Modifier.height(30.dp))
            Text("por @eai._ldkz", color = DIM, fontSize = 11.sp, letterSpacing = 1.sp)
        }
    }
}

// ---------- Root ----------

private fun pushHist(list: MutableList<Float>, v: Float) {
    list.add(v)
    if (list.size > 30) list.removeAt(0)
}

private fun DeviceProfile.ramFrac(): Float =
    (ramTotalMb - ramFreeMb).toFloat() / ramTotalMb.coerceAtLeast(1L)

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
    val ramH = remember { mutableStateListOf<Float>() }
    val cpuH = remember { mutableStateListOf<Float>() }
    val batH = remember { mutableStateListOf<Float>() }

    LaunchedEffect(Unit) {
        status = "Lendo aplicativos do aparelho..."
        withContext(Dispatchers.IO) { AppRepo.load(ctx) }
        status = "Verificando atualizações..."
        update = withContext(Dispatchers.IO) { UpdateChecker.check(ctx) }
        status = "Pronto"
        delay(450)
        splash = false
    }

    LaunchedEffect(Unit) {
        val cpu = CpuSampler()
        while (true) {
            val pair = withContext(Dispatchers.IO) { DeviceProfiler.read(ctx) to cpu.sample() }
            val pr = pair.first
            profile = pr
            cpuPct = pair.second
            pushHist(ramH, pr.ramFrac())
            if (pair.second >= 0) pushHist(cpuH, pair.second / 100f)
            if (pr.batteryPct >= 0) pushHist(batH, pr.batteryPct / 100f)
            delay(2000)
        }
    }

    val opt = optimizing
    val p = profile
    Box(Modifier.fillMaxSize()) {
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
            AmbientBackground()
            Crossfade(
                targetState = tab,
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                label = "tabs"
            ) { t ->
                when (t) {
                    0 -> HomeTab(p, cpuPct, ramH.toList(), cpuH.toList(), batH.toList(), onUltra = { pickerOpen = true })
                    1 -> GamesTab(onOptimize = { optimizing = it to false })
                    2 -> RecommendTab()
                    3 -> ToolsTab()
                    else -> SupportTab(update)
                }
            }
            FloatingNav(
                tab = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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
fun FloatingNav(tab: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf("⌂" to "Início", "▦" to "Jogos", "✦" to "Dicas", "⚙" to "Ferramentas", "✉" to "Suporte")
    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(40.dp))
            .background(Color(0xE8101015))
            .border(1.dp, STROKE, RoundedCornerShape(40.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, pair ->
            NavItem(pair.first, pair.second, selected = tab == i) { onSelect(i) }
        }
    }
}

@Composable
fun NavItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) WHITE else Color.Transparent, label = "navbg")
    val fg by animateColorAsState(if (selected) BG else DIM, label = "navfg")
    Column(
        Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, color = fg, fontSize = 17.sp)
        if (selected) Text(label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 12 -> "Bom dia"
        h < 18 -> "Boa tarde"
        else -> "Boa noite"
    }
}

// ---------- Início ----------

@Composable
fun HomeTab(
    p: DeviceProfile?,
    cpu: Int,
    ramH: List<Float>,
    cpuH: List<Float>,
    batH: List<Float>,
    onUltra: () -> Unit
) {
    val ctx = LocalContext.current
    var monitorOn by remember { mutableStateOf(Prefs.flag(ctx, "monitor")) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Eyebrow(greeting() + ", jogador")
            Spacer(Modifier.height(6.dp))
            Text(
                "Seu aparelho\nestá pronto.",
                color = WHITE,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 40.sp
            )
        }
        item {
            Pill(p?.tier?.label ?: "Lendo aparelho...", selected = true) {}
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "RAM",
                    if (p == null) "--" else "${(p.ramFrac() * 100).toInt()}%",
                    if (p == null) "" else "${p.ramTotalMb - p.ramFreeMb} de ${p.ramTotalMb} MB",
                    ramH,
                    Modifier.weight(1f)
                )
                StatTile(
                    "CPU",
                    if (cpu >= 0) "$cpu%" else "--",
                    "${p?.cores ?: 0} núcleos",
                    cpuH,
                    Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "BATERIA",
                    if (p == null || p.batteryPct < 0) "--" else "${p.batteryPct}%",
                    if (p == null) "" else "${"%.1f".format(p.batteryTemp)}°C",
                    batH,
                    Modifier.weight(1f)
                )
                StatTile(
                    "TELA",
                    if (p == null) "--" else "${p.refreshHz}Hz",
                    if (p == null) "" else "Térmico: ${p.thermal}",
                    ramH.map { 1f - it },
                    Modifier.weight(1f)
                )
            }
        }
        item {
            Glass {
                Eyebrow("Modo Ultra")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Encerra os apps em segundo plano, inclusive os protegidos, e deixa o jogo com toda a memória.",
                    color = WHITE,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(16.dp))
                PressButton("OTIMIZAR AGORA", modifier = Modifier.fillMaxWidth(), onClick = onUltra)
            }
        }
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Monitor flutuante", color = WHITE, fontWeight = FontWeight.SemiBold)
                        Text("RAM, CPU, bateria e temperatura sobre qualquer app", color = DIM, fontSize = 11.sp)
                    }
                    Switch(
                        checked = monitorOn,
                        onCheckedChange = { monitorOn = toggleMonitor(ctx, it) },
                        colors = optSwitch()
                    )
                }
            }
        }
    }
}

// ---------- Jogos ----------

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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Eyebrow("Biblioteca")
            Spacer(Modifier.height(4.dp))
            Text("Jogos", color = WHITE, fontSize = 34.sp, fontWeight = FontWeight.Black)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todos", "Favoritos", "Mais jogados", "Coleções").forEachIndexed { i, t ->
                    Pill(t, selected = mode == i) {
                        mode = i
                        colSel = null
                    }
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
                    Modifier
                        .fillMaxWidth()
                        .glass()
                        .clickable { colSel = n }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(n, color = WHITE, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
                GameCard(
                    g = g,
                    fav = g.pkg in favs,
                    minutes = usage[g.pkg]?.let { it / 60000 },
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
fun GameCard(
    g: Game,
    fav: Boolean,
    minutes: Long?,
    onFav: (Boolean) -> Unit,
    onFolder: () -> Unit,
    onOptimize: () -> Unit
) {
    val img = remember(g.pkg) { g.icon.asImageBitmap() }
    Row(
        Modifier.fillMaxWidth().glass().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(img, null, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(g.label, color = WHITE, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (minutes != null) "$minutes min nos últimos 7 dias" else "Jogo instalado",
                color = DIM,
                fontSize = 11.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (fav) "★" else "☆",
                    color = if (fav) ACC else DIM,
                    fontSize = 17.sp,
                    modifier = Modifier.clickable { onFav(!fav) }.padding(end = 16.dp, top = 4.dp)
                )
                Text(
                    "Coleção",
                    color = DIM,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onFolder() }.padding(top = 6.dp)
                )
            }
        }
        PressButton("OTIMIZAR", primary = true, onClick = onOptimize)
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
        containerColor = SURF,
        titleContentColor = WHITE,
        textContentColor = WHITE,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = ACC) } },
        title = { Text("Coleções de ${g.label}") },
        text = {
            Column {
                for (n in names) {
                    val key = "$n|${g.pkg}"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n, modifier = Modifier.weight(1f))
                        Switch(
                            checked = key in mem,
                            onCheckedChange = { on ->
                                mem = if (on) mem + key else mem - key
                                Prefs.save(ctx, "col", mem)
                            },
                            colors = optSwitch()
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
                    }) { Text("CRIAR", color = ACC) }
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
        containerColor = SURF,
        titleContentColor = WHITE,
        textContentColor = WHITE,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = DIM) } },
        title = { Text("Modo Ultra: escolha o jogo") },
        text = {
            Column {
                Text(
                    "Encerra todos os apps em segundo plano, inclusive os protegidos. Apps abertos na tela não são fechados pelo Android.",
                    fontSize = 12.sp,
                    color = WARN
                )
                Spacer(Modifier.height(10.dp))
                if (games.isEmpty()) Text("Nenhum jogo detectado.", color = DIM)
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(games, key = { it.pkg }) { g ->
                        Text(
                            g.label,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(g) }.padding(vertical = 13.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    )
}

// ---------- Dicas ----------

@Composable
fun RecommendTab() {
    val ctx = LocalContext.current
    val recs by produceState(emptyList<Pair<Rec, String>>()) {
        value = withContext(Dispatchers.IO) { Recommender.recommend(ctx) }
    }
    val granted = remember { Usage.granted(ctx) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Eyebrow("Descoberta")
            Spacer(Modifier.height(4.dp))
            Text("Dicas", color = WHITE, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(
                if (granted) "Baseado no que você mais joga nos últimos 7 dias e nos seus favoritos."
                else "Ative o acesso ao uso de apps para recomendações baseadas no que você joga.",
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
            Glass {
                Pill(r.genre, selected = false) {}
                Spacer(Modifier.height(10.dp))
                Text(r.name, color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text(pair.second, color = DIM, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                PressButton("VER NA LOJA", primary = false) {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${r.pkg}"))
                    )
                }
            }
        }
    }
}

// ---------- Ferramentas ----------

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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Eyebrow("Controle")
            Spacer(Modifier.height(4.dp))
            Text("Ferramentas", color = WHITE, fontSize = 34.sp, fontWeight = FontWeight.Black)
        }

        item {
            Glass {
                Eyebrow("Modo Pro Game")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Silencia ligações, WhatsApp, Instagram e notificações enquanto o jogo roda.",
                        color = WHITE, fontSize = 13.sp, modifier = Modifier.weight(1f)
                    )
                    Switch(checked = pro, onCheckedChange = {
                        pro = it
                        Prefs.setFlag(ctx, "pro", it)
                    }, colors = optSwitch())
                }
                if (!dnd) {
                    Spacer(Modifier.height(10.dp))
                    Row {
                        PressButton("CONCEDER", primary = true) {
                            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        }
                        Spacer(Modifier.width(8.dp))
                        PressButton("VERIFICAR", primary = false) { dnd = isDndGranted(ctx) }
                    }
                }
            }
        }

        item {
            Glass {
                Eyebrow("Bloqueio de spam")
                Spacer(Modifier.height(8.dp))
                Text(
                    if (callRole) "Ativo: o OPTORK filtra as ligações."
                    else "Ative para filtrar ligações de números bloqueados.",
                    color = WHITE, fontSize = 13.sp
                )
                if (!callRole) {
                    Spacer(Modifier.height(10.dp))
                    PressButton("ATIVAR BLOQUEIO") {
                        requestCallScreening(ctx)
                        callRole = isCallScreeningHeld(ctx)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bloquear números ocultos", color = WHITE, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = hiddenBlock, onCheckedChange = {
                        hiddenBlock = it
                        Prefs.setFlag(ctx, "block_hidden", it)
                    }, colors = optSwitch())
                }
                Spacer(Modifier.height(10.dp))
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
                for (n in blocked.sorted()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n, color = WHITE, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            blocked = blocked - n
                            Prefs.save(ctx, "blocked", blocked)
                        }) { Text("REMOVER", color = WARN, fontSize = 11.sp) }
                    }
                }
            }
        }

        item {
            Glass {
                Eyebrow("Apps que mais usaram hoje")
                Spacer(Modifier.height(8.dp))
                if (!usageOk) {
                    Text("Permita o acesso ao uso de apps para ver os consumidores.", color = DIM, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        PressButton("PERMITIR") { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                        Spacer(Modifier.width(8.dp))
                        PressButton("VERIFICAR", primary = false) { usageOk = Usage.granted(ctx) }
                    }
                } else {
                    val rows = usage.filter { it.first != ctx.packageName }.take(8)
                    if (rows.isEmpty()) Text("Sem dados de uso ainda.", color = DIM, fontSize = 12.sp)
                    for ((pkg, ms) in rows) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(labelOf[pkg] ?: pkg, color = WHITE, fontSize = 13.sp, maxLines = 1)
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
            Glass {
                Eyebrow("Animações do sistema")
                Spacer(Modifier.height(8.dp))
                if (canWrite) {
                    Text(
                        if (animReduced) "Animações reduzidas. Restaure quando terminar."
                        else "Permissão concedida. Reduza para o sistema responder mais rápido.",
                        color = WHITE, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        PressButton("REDUZIR") {
                            Optimizer.reduceAnimations(ctx, 0.5f)
                            animReduced = Prefs.get(ctx).contains("anim_saved")
                        }
                        Spacer(Modifier.width(8.dp))
                        PressButton("RESTAURAR", primary = false) {
                            Optimizer.restoreAnimations(ctx)
                            animReduced = false
                        }
                    }
                } else {
                    Text(
                        "Precisa de uma permissão única pelo computador (USB). Veja o guia de instalação.",
                        color = WHITE, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(ADB_CMD, color = ACC, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        PressButton("COPIAR") {
                            ctx.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("adb", ADB_CMD))
                        }
                        Spacer(Modifier.width(8.dp))
                        PressButton("VERIFICAR", primary = false) { canWrite = Anim.canWrite(ctx) }
                    }
                }
            }
        }

        item {
            Glass {
                Eyebrow("Taxa da tela")
                Spacer(Modifier.height(8.dp))
                Text(
                    if (testing) "%.0f FPS".format(fps) else "Mede quadros por segundo. Mexa a tela durante o teste.",
                    color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                PressButton(if (testing) "PARAR" else "INICIAR") { testing = !testing }
            }
        }

        item { Eyebrow("Apps protegidos (não são encerrados)") }
        items(userApps, key = { it.pkg }) { a ->
            Row(
                Modifier.fillMaxWidth().glass(20.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(a.label, color = WHITE, maxLines = 1, modifier = Modifier.weight(1f))
                Switch(
                    checked = a.pkg in protectedSet,
                    onCheckedChange = { on ->
                        protectedSet = if (on) protectedSet + a.pkg else protectedSet - a.pkg
                        Prefs.save(ctx, "protected", protectedSet)
                    },
                    colors = optSwitch()
                )
            }
        }
    }
}

// ---------- Suporte ----------

@Composable
fun SupportTab(update: UpdateInfo?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ver = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0" }
    var type by remember { mutableStateOf("Sugestão") }
    var message by remember { mutableStateOf("") }
    var checkMsg by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Eyebrow("Suporte")
            Spacer(Modifier.height(4.dp))
            Text("Fale com a gente", color = WHITE, fontSize = 34.sp, fontWeight = FontWeight.Black)
        }

        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("@eai._ldkz", color = WHITE, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("Criador do OPTORK · v$ver", color = DIM, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row {
                    PressButton("INSTAGRAM") {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CREATOR_IG)))
                    }
                    Spacer(Modifier.width(8.dp))
                    PressButton("ATUALIZAÇÕES", primary = false) {
                        scope.launch {
                            checking = true
                            checkMsg = if (!UpdateChecker.isConfigured()) {
                                "Atualizações não configuradas (veja Engine.kt)"
                            } else {
                                val u = withContext(Dispatchers.IO) { UpdateChecker.check(ctx) }
                                if (u != null) "Nova versão ${u.version} disponível" else "Você está na versão mais recente"
                            }
                            checking = false
                        }
                    }
                }
                if (checking) Text("Verificando...", color = DIM, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                if (checkMsg.isNotEmpty()) Text(checkMsg, color = ACC, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                if (update != null) Text("Atualização ${update.version} disponível", color = ACC, fontSize = 12.sp)
            }
        }

        item {
            Glass {
                Eyebrow("Enviar erro, sugestão ou melhoria")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Erro", "Sugestão", "Melhoria", "Mudança").forEach { t ->
                        Pill(t, selected = type == t) { type = t }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Descreva aqui") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                PressButton("ENVIAR", modifier = Modifier.fillMaxWidth()) {
                    if (message.isNotBlank()) {
                        val body = message.trim()
                        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))
                            .putExtra(Intent.EXTRA_SUBJECT, "[OPTORK][$type] v$ver")
                            .putExtra(
                                Intent.EXTRA_TEXT,
                                "$body\n\n— Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
                            )
                        ctx.startActivity(i)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Vai para $SUPPORT_EMAIL", color = DIM, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun UpdateDialog(u: UpdateInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SURF,
        titleContentColor = WHITE,
        textContentColor = WHITE,
        title = { Text("Atualização disponível") },
        text = { Text("A versão ${u.version} do OPTORK já está disponível. Quer baixar agora?") },
        confirmButton = {
            TextButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)))
                onDismiss()
            }) { Text("BAIXAR", color = ACC) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Depois", color = DIM) } }
    )
}

// ---------- Otimização ----------

@Composable
fun OptimizeScreen(game: Game, p: DeviceProfile, ultra: Boolean, onPlay: () -> Unit, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    var prog by remember { mutableStateOf(Progress("Iniciando", 0f)) }
    LaunchedEffect(game.pkg) {
        Optimizer.run(ctx, game, p, ultra).collect { prog = it }
    }
    val res = prog.result
    val img = remember(game.pkg) { game.icon.asImageBitmap() }
    val pct by animateFloatAsState(prog.pct, tween(500), label = "pct")
    val inf = rememberInfiniteTransition(label = "opt")
    val glow by inf.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(img, null, Modifier.size(78.dp).clip(RoundedCornerShape(22.dp)))
            Spacer(Modifier.height(12.dp))
            Text(game.label, color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(28.dp))
            if (res == null) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                    Box(Modifier.size(180.dp).blur(40.dp).background(ACC.copy(alpha = glow), CircleShape))
                    CircularProgressIndicator(
                        progress = { pct },
                        modifier = Modifier.size(180.dp),
                        color = WHITE,
                        trackColor = GLASS_HI,
                        strokeWidth = 6.dp
                    )
                    Text(
                        "${(pct * 100).toInt()}%",
                        color = WHITE,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    if (ultra) "Modo Ultra em andamento" else "Aplicando correções e otimizações",
                    color = WHITE,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(prog.step + "…", color = DIM, fontSize = 13.sp, textAlign = TextAlign.Center)
            } else {
                ResultPanel(res, ultra, onPlay, onFinish, ctx, game.pkg)
            }
        }
    }
}

@Composable
fun ResultPanel(res: Result, ultra: Boolean, onPlay: () -> Unit, onFinish: () -> Unit, ctx: Context, pkg: String) {
    val ram by animateIntAsState(res.ramFreedMb.toInt(), tween(900), label = "ram")
    val apps by animateIntAsState(res.appsClosed, tween(900), label = "apps")
    val cache by animateIntAsState(res.cacheMb.toInt(), tween(900), label = "cache")
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 6 }
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("OTIMIZADO", color = WHITE, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("RAM LIBERADA", "$ram MB", Modifier.weight(1f))
                StatBox("APPS ENCERRADOS", "$apps", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("CACHE LIMPO", "$cache MB", Modifier.weight(1f))
                StatBox("ANIMAÇÕES", if (res.anim) "Reduzidas" else "Sem permissão", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Glass {
                Eyebrow("Modo foco")
                Spacer(Modifier.height(4.dp))
                Text(res.focusMode, color = WHITE, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            Glass {
                Eyebrow("Ajustes recomendados no jogo")
                Spacer(Modifier.height(8.dp))
                for (tip in res.tips) {
                    Text("•  $tip", color = WHITE, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            PressButton("JOGAR AGORA", modifier = Modifier.fillMaxWidth()) {
                ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it) }
                onPlay()
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onFinish) { Text("Finalizar e restaurar", color = DIM) }
        }
    }
}

@Composable
fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.glass(22.dp).padding(16.dp)) {
        Eyebrow(label)
        Spacer(Modifier.height(6.dp))
        Text(value, color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

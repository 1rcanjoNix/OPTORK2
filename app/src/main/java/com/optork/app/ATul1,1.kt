package com.optork.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

private val BG = Color(0xFF0A0A0C)
private val SURF = Color(0xFF15151A)
private val SURF2 = Color(0xFF23232B)
private val ACC = Color(0xFF00E5A0)
private val DIM = Color(0xFF8A8A94)
private const val ADB_CMD = "adb shell pm grant com.optork.app android.permission.WRITE_SECURE_SETTINGS"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = ACC,
                    onPrimary = Color.Black,
                    background = BG,
                    surface = SURF,
                    onSurface = Color.White,
                    onBackground = Color.White
                )
            ) { App() }
        }
    }

    override fun onResume() {
        super.onResume()
        Optimizer.restoreIfActive(this)
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var profile by remember { mutableStateOf(DeviceProfiler.read(ctx)) }
    var tab by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf<Game?>(null) }

    val g = current
    if (g != null) {
        OptimizeScreen(
            game = g,
            p = profile,
            onPlay = { current = null },
            onFinish = {
                Optimizer.restore(ctx)
                current = null
                profile = DeviceProfiler.read(ctx)
            }
        )
    } else {
        Scaffold(
            containerColor = BG,
            bottomBar = { BottomBar(tab) { tab = it } }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> PanelTab(profile) { profile = DeviceProfiler.read(ctx) }
                    1 -> GamesTab { current = it }
                    else -> ToolsTab()
                }
            }
        }
    }
}

@Composable
fun BottomBar(tab: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("Painel", "Jogos", "Ferramentas")
    val icons = listOf("◉", "▤", "⚙")
    NavigationBar(containerColor = SURF) {
        labels.forEachIndexed { i, label ->
            NavigationBarItem(
                selected = tab == i,
                onClick = { onSelect(i) },
                icon = { Text(icons[i], fontSize = 18.sp) },
                label = { Text(label, fontSize = 11.sp) },
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
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SURF).padding(16.dp),
        content = content
    )
}

@Composable
fun accBtnColors() = ButtonDefaults.buttonColors(containerColor = ACC, contentColor = Color.Black)

@Composable
fun PanelTab(p: DeviceProfile, onRefresh: () -> Unit) {
    val used = (p.ramTotalMb - p.ramFreeMb).coerceAtLeast(0)
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("OPTORK", color = ACC, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
            Text("Otimização inteligente para jogos", color = DIM, fontSize = 13.sp)
        }
        item {
            Panel {
                Text("SEU APARELHO", color = DIM, fontSize = 11.sp, letterSpacing = 2.sp)
                Text(p.tier.label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("RAM em uso: $used / ${p.ramTotalMb} MB", color = DIM, fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = { used.toFloat() / p.ramTotalMb.coerceAtLeast(1L) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = ACC
                )
                Text("Núcleos: ${p.cores}  •  Tela: ${p.refreshHz} Hz", color = DIM, fontSize = 12.sp)
                Text(
                    "Bateria: ${if (p.batteryPct >= 0) "${p.batteryPct}%" else "N/D"}  •  Temp: ${"%.1f".format(p.batteryTemp)}°C",
                    color = DIM, fontSize = 12.sp
                )
                Text(
                    "Térmico: ${p.thermal}  •  Economia de bateria: ${if (p.powerSave) "ativa" else "desligada"}",
                    color = DIM, fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRefresh, colors = accBtnColors()) {
                    Text("ATUALIZAR PERFIL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        item {
            Panel {
                Text("RECOMENDAÇÕES PARA O SEU APARELHO", color = DIM, fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(6.dp))
                Optimizer.tips(p).forEach {
                    Text("• $it", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
fun GamesTab(onOptimize: (Game) -> Unit) {
    val ctx = LocalContext.current
    var showAll by remember { mutableStateOf(false) }
    val list = remember(showAll) { GameDetector.find(ctx, showAll) }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("JOGOS", color = ACC, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showAll) "Mostrando todos os apps" else "Mostrando jogos detectados",
                    color = DIM, fontSize = 12.sp, modifier = Modifier.weight(1f)
                )
                Switch(checked = showAll, onCheckedChange = { showAll = it })
            }
            Spacer(Modifier.height(8.dp))
        }
        if (list.isEmpty()) {
            item {
                Text(
                    "Nenhum item encontrado. Ative 'Mostrando todos' para escolher um app manualmente.",
                    color = DIM, fontSize = 13.sp
                )
            }
        }
        items(list, key = { it.pkg }) { g ->
            val bmp = remember(g.pkg) { g.icon.toBitmap(96, 96).asImageBitmap() }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SURF).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(bmp, null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                Text(
                    g.label, color = Color.White, maxLines = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                )
                Button(onClick = { onOptimize(g) }, colors = accBtnColors()) {
                    Text("OTIMIZAR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ToolsTab() {
    val ctx = LocalContext.current
    var fps by remember { mutableFloatStateOf(0f) }
    var testing by remember { mutableStateOf(false) }
    val counter = remember { FpsCounter { fps = it } }
    DisposableEffect(testing) {
        if (testing) counter.start()
        onDispose { counter.stop() }
    }

    val apps = remember { GameDetector.find(ctx, all = true) }
    var protectedSet by remember { mutableStateOf(Prefs.protected(ctx)) }
    var dnd by remember { mutableStateOf(isDndGranted(ctx)) }
    var canWrite by remember { mutableStateOf(Anim.canWrite(ctx)) }
    var animReduced by remember { mutableStateOf(Prefs.get(ctx).contains("anim_saved")) }
    var msg by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("FERRAMENTAS", color = ACC, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        }
        item {
            Panel {
                Text("TESTE DE FPS", color = DIM, fontSize = 11.sp, letterSpacing = 2.sp)
                Text(
                    if (testing) "%.0f FPS".format(fps) else "Mede a fluidez da tela agora",
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { testing = !testing }, colors = accBtnColors()) {
                    Text(if (testing) "PARAR" else "INICIAR", fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Panel {
                Text("LIMPAR MEMÓRIA", color = DIM, fontSize = 11.sp, letterSpacing = 2.sp)
                Text(
                    if (msg.isEmpty()) "Encerra apps em segundo plano que não estão protegidos." else msg,
                    color = Color.White, fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val n = Optimizer.clearRam(ctx)
                    msg = "$n apps em segundo plano encerrados"
                }, colors = accBtnColors()) { Text("LIMPAR AGORA", fontWeight = FontWeight.Bold) }
            }
        }
        item {
            Panel {
                Text("MODO FOCO (SEM NOTIFICAÇÕES)", color = DIM, fontSize = 11.sp, letterSpacing = 2.sp)
                Text(
                    if (dnd) "Permissão concedida." else "Silencia notificações durante a otimização.",
                    color = Color.White, fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }, colors = accBtnColors()) {
                        Text(if (dnd) "GERENCIAR" else "CONCEDER", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { dnd = isDndGranted(ctx) }) { Text("VERIFICAR") }
                }
            }
        }
        item {
            Panel {
                Text("ANIMAÇÕES DO SISTEMA", color = DIM, fontSize = 11.sp, letterSpacing = 2.sp)
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
                        }, colors = accBtnColors()) { Text("REDUZIR", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            Optimizer.restoreAnimations(ctx)
                            animReduced = false
                        }) { Text("RESTAURAR") }
                    }
                } else {
                    Text(
                        "Precisa de uma permissão única pelo computador (USB). Veja o passo a passo no guia.",
                        color = Color.White, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(ADB_CMD, color = ACC, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(onClick = {
                            val cb = ctx.getSystemService(ClipboardManager::class.java)
                            cb.setPrimaryClip(ClipData.newPlainText("adb", ADB_CMD))
                        }, colors = accBtnColors()) { Text("COPIAR", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { canWrite = Anim.canWrite(ctx) }) { Text("VERIFICAR") }
                    }
                }
            }
        }
        item {
            Text("APPS PROTEGIDOS (nunca encerrados)", color = DIM, fontSize = 11.sp, letterSpacing = 2.sp)
        }
        items(apps, key = { it.pkg }) { a ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SURF).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(a.label, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                Switch(
                    checked = a.pkg in protectedSet,
                    onCheckedChange = { on ->
                        protectedSet = if (on) protectedSet + a.pkg else protectedSet - a.pkg
                        Prefs.setProtected(ctx, protectedSet)
                    }
                )
            }
        }
    }
}

@Composable
fun OptimizeScreen(game: Game, p: DeviceProfile, onPlay: () -> Unit, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    var prog by remember { mutableStateOf(Progress("Iniciando", 0f)) }
    LaunchedEffect(game.pkg) {
        Optimizer.run(ctx, game, p).collect { prog = it }
    }
    val res = prog.result
    val bmp = remember(game.pkg) { game.icon.toBitmap(160, 160).asImageBitmap() }

    Column(
        Modifier.fillMaxSize().background(BG).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(bmp, null, Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(14.dp))
        Text(game.label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(28.dp))
        if (res == null) {
            Text("Aplicando correções e otimizações", color = ACC, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { prog.pct },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = ACC
            )
            Spacer(Modifier.height(10.dp))
            Text(prog.step + "…", color = DIM, fontSize = 13.sp)
        } else {
            Text("OTIMIZADO", color = ACC, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Spacer(Modifier.height(12.dp))
            Panel {
                Text("RAM liberada: ${res.ramFreedMb} MB", color = Color.White, fontSize = 13.sp)
                Text("Apps encerrados: ${res.appsClosed}", color = Color.White, fontSize = 13.sp)
                Text("Cache limpo: ${res.cacheFreedMb} MB", color = Color.White, fontSize = 13.sp)
                Text("Modo foco: ${if (res.focus) "ativo" else "sem permissão"}", color = Color.White, fontSize = 13.sp)
                Text(
                    "Animações reduzidas: ${if (res.anim) "sim" else "sem permissão"}",
                    color = Color.White, fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Panel {
                Text("AJUSTES RECOMENDADOS NO JOGO", color = DIM, fontSize = 11.sp, letterSpacing = 2.sp)
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
                colors = accBtnColors()
            ) { Text("JOGAR AGORA", fontWeight = FontWeight.Black) }
            TextButton(onClick = onFinish) { Text("Finalizar e restaurar", color = DIM) }
        }
    }
}

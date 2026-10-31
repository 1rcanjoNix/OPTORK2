package com.optork.app

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.Choreographer
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// ====== CONFIGURAÇÃO ======
// Troque SEU_USUARIO/OPTORK pelo seu usuário e nome de repositório do GitHub (ex: joao/OPTORK)
const val GITHUB_REPO = "SEU_USUARIO/OPTORK"
const val SUPPORT_EMAIL = "danielmartinsrosa001@gmail.com"
const val CREATOR_IG = "https://www.instagram.com/eai._ldkz/"

private val ANIM_KEYS = listOf(
    Settings.Global.WINDOW_ANIMATION_SCALE,
    Settings.Global.TRANSITION_ANIMATION_SCALE,
    Settings.Global.ANIMATOR_DURATION_SCALE
)

enum class Tier(val label: String) {
    LOW("ECONÔMICO"), MID("EQUILIBRADO"), HIGH("DESEMPENHO MÁXIMO")
}

data class DeviceProfile(
    val ramTotalMb: Long,
    val ramFreeMb: Long,
    val cores: Int,
    val tier: Tier,
    val thermal: String,
    val batteryTemp: Float,
    val batteryPct: Int,
    val powerSave: Boolean,
    val refreshHz: Int
)

data class Game(
    val pkg: String,
    val label: String,
    val icon: Bitmap,
    val isGame: Boolean,
    val isSystem: Boolean
)

data class Result(
    val ramFreedMb: Long,
    val appsClosed: Int,
    val cacheMb: Long,
    val focusMode: String,
    val anim: Boolean,
    val tips: List<String>
)

data class Progress(val step: String, val pct: Float, val result: Result? = null)
data class UpdateInfo(val version: String, val url: String)
data class Rec(val pkg: String, val name: String, val genre: String)

object Prefs {
    fun get(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("optork", Context.MODE_PRIVATE)

    fun save(ctx: Context, key: String, value: Set<String>) {
        get(ctx).edit().putStringSet(key, value).apply()
    }

    fun strset(ctx: Context, key: String): Set<String> =
        get(ctx).getStringSet(key, emptySet())?.toSet() ?: emptySet()

    fun protected(ctx: Context) = strset(ctx, "protected")
    fun favorites(ctx: Context) = strset(ctx, "fav")
    fun blocked(ctx: Context) = strset(ctx, "blocked")
    fun collections(ctx: Context) = strset(ctx, "col")

    fun collectionNames(ctx: Context): List<String> =
        collections(ctx).map { it.substringBefore("|") }.distinct().sorted()

    fun flag(ctx: Context, key: String): Boolean = get(ctx).getBoolean(key, false)

    fun setFlag(ctx: Context, key: String, value: Boolean) {
        get(ctx).edit().putBoolean(key, value).apply()
    }
}

fun isDndGranted(ctx: Context): Boolean =
    ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

fun isCallScreeningHeld(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < 29) return false
    val rm = ctx.getSystemService(RoleManager::class.java)
    return rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}

fun requestCallScreening(ctx: Context) {
    if (Build.VERSION.SDK_INT < 29) return
    val rm = ctx.getSystemService(RoleManager::class.java)
    if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
        ctx.startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    }
}

fun toggleMonitor(ctx: Context, on: Boolean): Boolean {
    if (on && !Settings.canDrawOverlays(ctx)) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return false
    }
    val svc = Intent(ctx, MonitorService::class.java)
    if (on) ContextCompat.startForegroundService(ctx, svc) else ctx.stopService(svc)
    Prefs.setFlag(ctx, "monitor", on)
    return on
}

object Anim {
    fun canWrite(ctx: Context): Boolean =
        ctx.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED
}

object DeviceProfiler {
    @Suppress("DEPRECATION")
    fun read(ctx: Context): DeviceProfile {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalMb = mi.totalMem / 1048576
        val cores = Runtime.getRuntime().availableProcessors()
        val tier = when {
            totalMb >= 7000 && cores >= 8 -> Tier.HIGH
            totalMb >= 4500 -> Tier.MID
            else -> Tier.LOW
        }
        val pm = ctx.getSystemService(PowerManager::class.java)
        val thermal = if (Build.VERSION.SDK_INT >= 29) thermalName(pm.currentThermalStatus) else "N/D"
        val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = (bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val pct = if (level >= 0) level * 100 / scale else -1
        val hz = ctx.getSystemService(WindowManager::class.java).defaultDisplay.refreshRate.toInt()
        return DeviceProfile(
            ramTotalMb = totalMb,
            ramFreeMb = mi.availMem / 1048576,
            cores = cores,
            tier = tier,
            thermal = thermal,
            batteryTemp = temp,
            batteryPct = pct,
            powerSave = pm.isPowerSaveMode,
            refreshHz = hz
        )
    }

    private fun thermalName(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Normal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Leve"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderado"
        else -> "Crítico"
    }
}

class CpuSampler {
    private var initialized = false
    private var lastIdle = 0L
    private var lastTotal = 0L

    fun sample(): Int = try {
        val line = File("/proc/stat").useLines { it.first() }
        val v = line.split(Regex("\\s+")).drop(1).filter { it.isNotEmpty() }.map { it.toLong() }
        val idle = v[3] + v.getOrElse(4) { 0L }
        val total = v.sum()
        val result = if (!initialized) -1 else {
            val dIdle = idle - lastIdle
            val dTotal = total - lastTotal
            if (dTotal <= 0L) -1 else ((dTotal - dIdle) * 100 / dTotal).toInt()
        }
        initialized = true
        lastIdle = idle
        lastTotal = total
        result
    } catch (e: Exception) {
        -1
    }
}

object AppRepo {
    @Volatile
    var apps: List<Game> = emptyList()

    fun load(ctx: Context): List<Game> {
        val pm = ctx.packageManager
        val list = pm.getInstalledApplications(0)
            .filter { it.packageName != ctx.packageName }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { a ->
                Game(
                    pkg = a.packageName,
                    label = a.loadLabel(pm).toString(),
                    icon = a.loadIcon(pm).toBitmap(72, 72),
                    isGame = isGame(a),
                    isSystem = (a.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedBy { it.label.lowercase() }
        apps = list
        return list
    }

    private fun isGame(a: ApplicationInfo): Boolean =
        (a.flags and ApplicationInfo.FLAG_IS_GAME) != 0 ||
            (Build.VERSION.SDK_INT >= 26 && a.category == ApplicationInfo.CATEGORY_GAME)
}

object Usage {
    @Suppress("DEPRECATION")
    fun granted(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun top(ctx: Context, days: Int, limit: Int): List<Pair<String, Long>> {
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val end = System.currentTimeMillis()
        val start = end - days * 86_400_000L
        return usm.queryAndAggregateUsageStats(start, end).values
            .filter { it.totalTimeInForeground > 0 && it.packageName != null }
            .map { it.packageName to it.totalTimeInForeground }
            .sortedByDescending { it.second }
            .take(limit)
    }
}

object Catalog {
    val games = listOf(
        Rec("com.dts.freefireth", "Free Fire", "Battle Royale"),
        Rec("com.tencent.ig", "PUBG Mobile", "Battle Royale"),
        Rec("com.activision.callofduty.shooter", "Call of Duty: Mobile", "Tiro"),
        Rec("com.supercell.brawlstars", "Brawl Stars", "Ação"),
        Rec("com.supercell.clashofclans", "Clash of Clans", "Estratégia"),
        Rec("com.supercell.clashroyale", "Clash Royale", "Estratégia"),
        Rec("com.mojang.minecraftpe", "Minecraft", "Sandbox"),
        Rec("com.roblox.client", "Roblox", "Sandbox"),
        Rec("com.innersloth.spacemafia", "Among Us", "Social"),
        Rec("com.kiloo.subwaysurf", "Subway Surfers", "Casual"),
        Rec("com.king.candycrushsaga", "Candy Crush Saga", "Casual"),
        Rec("com.kitkagames.fallbuddies", "Stumble Guys", "Casual"),
        Rec("com.miHoYo.GenshinImpact", "Genshin Impact", "RPG"),
        Rec("com.HoYoverse.hkrpgoversea", "Honkai: Star Rail", "RPG"),
        Rec("com.riotgames.league.wildrift", "Wild Rift", "MOBA"),
        Rec("com.gameloft.android.ANMP.GloftA9HM", "Asphalt 9", "Corrida"),
        Rec("com.ea.games.r3_row", "Real Racing 3", "Corrida"),
        Rec("com.ea.gp.fifamobile", "EA Sports FC Mobile", "Esporte"),
        Rec("jp.konami.pesam", "eFootball", "Esporte")
    )
}

object Recommender {
    fun recommend(ctx: Context): List<Pair<Rec, String>> {
        val installed = AppRepo.apps.map { it.pkg }.toSet()
        val usage = Usage.top(ctx, 7, 300).toMap()
        val favs = Prefs.favorites(ctx)
        val genreScore = mutableMapOf<String, Long>()
        Catalog.games.filter { it.pkg in installed }.forEach { r ->
            val w = (usage[r.pkg] ?: 0L) + (if (r.pkg in favs) 3_600_000L else 0L)
            genreScore[r.genre] = (genreScore[r.genre] ?: 0L) + w
        }
        return Catalog.games
            .filter { it.pkg !in installed }
            .map { r ->
                val s = genreScore[r.genre] ?: 0L
                val why = if (s > 0) "Combina com os gêneros que você mais joga (${r.genre})" else "Popular, ainda não instalado"
                r to why
            }
            .sortedByDescending { genreScore[it.first.genre] ?: 0L }
    }
}

class FpsCounter(private val onUpdate: (Float) -> Unit) {
    private var frames = 0
    private var windowStart = 0L
    private var running = false

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (windowStart == 0L) windowStart = frameTimeNanos
            frames++
            val elapsed = frameTimeNanos - windowStart
            if (elapsed >= 1_000_000_000L) {
                onUpdate(frames * 1_000_000_000f / elapsed)
                frames = 0
                windowStart = frameTimeNanos
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        running = true
        frames = 0
        windowStart = 0L
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(callback)
    }
}

object UpdateChecker {
    fun isConfigured(): Boolean = !GITHUB_REPO.startsWith("SEU_")

    fun check(ctx: Context): UpdateInfo? {
        if (!isConfigured()) return null
        return try {
            val c = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
                .openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 6000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) return null
            val json = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name").removePrefix("v")
            val url = json.optString("html_url")
            val current = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
            if (isNewer(tag, current)) UpdateInfo(tag, url) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun isNewer(a: String, b: String): Boolean {
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }
            val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }
}

object Optimizer {
    private fun availMb(ctx: Context): Long {
        val mi = ActivityManager.MemoryInfo()
        ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        return mi.availMem / 1048576
    }

    /** Encerra apps em segundo plano. Modo normal respeita a lista de protegidos. */
    fun killBackground(ctx: Context, exclude: String?, ignoreProtected: Boolean): Int {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val base = if (ignoreProtected) emptySet() else Prefs.protected(ctx)
        val keep = base + setOfNotNull(ctx.packageName, exclude)
        var n = 0
        ctx.packageManager.getInstalledApplications(0)
            .filter { it.packageName !in keep && (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .forEach {
                am.killBackgroundProcesses(it.packageName)
                n++
            }
        return n
    }

    fun killOne(ctx: Context, pkg: String) {
        ctx.getSystemService(ActivityManager::class.java).killBackgroundProcesses(pkg)
    }

    fun clearCache(ctx: Context): Long {
        var bytes = 0L
        listOfNotNull(ctx.cacheDir, ctx.externalCacheDir).forEach { dir ->
            dir.listFiles()?.forEach { f: File ->
                bytes += f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                f.deleteRecursively()
            }
        }
        return bytes / 1048576
    }

    fun setFocus(ctx: Context, pro: Boolean): String {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return "sem permissão"
        if (!Prefs.flag(ctx, "focus_on")) {
            Prefs.get(ctx).edit()
                .putInt("prev_filter", nm.currentInterruptionFilter)
                .putBoolean("focus_on", true)
                .apply()
        }
        return if (pro) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            "Pro Game (ligações e notificações silenciadas)"
        } else {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            "Prioridade"
        }
    }

    fun reduceAnimations(ctx: Context, scale: Float): Boolean {
        if (!Anim.canWrite(ctx)) return false
        val prefs = Prefs.get(ctx)
        if (!prefs.contains("anim_saved")) {
            val saved = ANIM_KEYS.joinToString("|") {
                Settings.Global.getFloat(ctx.contentResolver, it, 1f).toString()
            }
            prefs.edit().putString("anim_saved", saved).apply()
        }
        return try {
            ANIM_KEYS.forEach { Settings.Global.putFloat(ctx.contentResolver, it, scale) }
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun restoreAnimations(ctx: Context) {
        val prefs = Prefs.get(ctx)
        val saved = prefs.getString("anim_saved", null) ?: return
        val values = saved.split("|").map { it.toFloatOrNull() ?: 1f }
        try {
            ANIM_KEYS.zip(values).forEach { (k, v) -> Settings.Global.putFloat(ctx.contentResolver, k, v) }
        } catch (e: SecurityException) {
            // sem permissão: nada a restaurar
        }
        prefs.edit().remove("anim_saved").apply()
    }

    fun restoreFocus(ctx: Context) {
        val prefs = Prefs.get(ctx)
        if (!prefs.getBoolean("focus_on", false)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(prefs.getInt("prev_filter", NotificationManager.INTERRUPTION_FILTER_ALL))
        }
        prefs.edit().putBoolean("focus_on", false).apply()
    }

    fun restore(ctx: Context) {
        restoreAnimations(ctx)
        restoreFocus(ctx)
        Prefs.setFlag(ctx, "active", false)
    }

    fun restoreIfActive(ctx: Context) {
        if (Prefs.flag(ctx, "active")) restore(ctx)
    }

    fun tips(p: DeviceProfile, ultra: Boolean): List<String> = buildList {
        if (ultra) add("Modo Ultra ativo: foque no jogo e evite abrir outros apps")
        when (p.tier) {
            Tier.LOW -> {
                add("Qualidade gráfica: baixa")
                add("FPS: 30 (estável, aquece menos)")
                add("Sombras, reflexos e pós-processamento: desligados")
            }
            Tier.MID -> {
                add("Qualidade gráfica: média")
                add("FPS: 60")
                add("Sombras baixas; antialiasing desligado")
            }
            Tier.HIGH -> {
                add("Qualidade gráfica: alta ou ultra")
                add(if (p.refreshHz >= 90) "FPS: máximo (tela de ${p.refreshHz} Hz)" else "FPS: 60")
                add("Resolução: máxima")
            }
        }
        if (p.thermal != "Normal" && p.thermal != "N/D") {
            add("Aparelho aquecido: faça pausas para evitar queda de FPS")
        }
        if (p.powerSave) add("Economia de bateria ativa: desligue para liberar desempenho")
        if (p.batteryPct in 0..20) add("Bateria baixa: o sistema reduz o desempenho automaticamente")
    }

    fun run(ctx: Context, game: Game, p: DeviceProfile, ultra: Boolean): Flow<Progress> = flow {
        val pro = ultra || Prefs.flag(ctx, "pro")

        emit(Progress("Analisando o aparelho", 0.08f))
        delay(500)

        emit(
            Progress(
                if (ultra) "Modo Ultra: encerrando apps em segundo plano" else "Encerrando apps em segundo plano",
                0.25f
            )
        )
        val before = availMb(ctx)
        val closed = killBackground(ctx, game.pkg, ignoreProtected = ultra)
        delay(900)
        val ram = (availMb(ctx) - before).coerceAtLeast(0L)

        emit(Progress("Limpando cache do OPTORK", 0.45f))
        val cache = clearCache(ctx)
        delay(400)

        emit(Progress(if (pro) "Ativando Pro Game (sem ligações e notificações)" else "Ativando modo foco", 0.6f))
        val focusMode = setFocus(ctx, pro)
        delay(400)

        emit(Progress("Ajustando animações", 0.8f))
        val anim = reduceAnimations(ctx, if (ultra || p.tier == Tier.LOW) 0f else 0.5f)
        delay(400)

        emit(Progress("Aplicando perfil ${p.tier.label}", 0.95f))
        delay(400)
        Prefs.setFlag(ctx, "active", true)
        emit(Progress("Concluído", 1f, Result(ram, closed, cache, focusMode, anim, tips(p, ultra))))
    }.flowOn(Dispatchers.Default)
}

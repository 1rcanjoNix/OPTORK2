package com.optork.app

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Choreographer
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

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

data class Game(val pkg: String, val label: String, val icon: Drawable)

data class Result(
    val ramFreedMb: Long,
    val appsClosed: Int,
    val cacheFreedMb: Long,
    val focus: Boolean,
    val anim: Boolean,
    val tips: List<String>
)

data class Progress(val step: String, val pct: Float, val result: Result? = null)

private val ANIM_KEYS = listOf(
    Settings.Global.WINDOW_ANIMATION_SCALE,
    Settings.Global.TRANSITION_ANIMATION_SCALE,
    Settings.Global.ANIMATOR_DURATION_SCALE
)

object Prefs {
    fun get(ctx: Context) = ctx.getSharedPreferences("optork", Context.MODE_PRIVATE)

    fun protected(ctx: Context): Set<String> =
        get(ctx).getStringSet("protected", emptySet())?.toSet() ?: emptySet()

    fun setProtected(ctx: Context, s: Set<String>) {
        get(ctx).edit().putStringSet("protected", s).apply()
    }
}

object Anim {
    fun canWrite(ctx: Context): Boolean =
        ctx.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED
}

fun isDndGranted(ctx: Context): Boolean =
    ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

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

object GameDetector {
    fun find(ctx: Context, all: Boolean = false): List<Game> {
        val pm = ctx.packageManager
        return pm.getInstalledApplications(0)
            .filter { it.packageName != ctx.packageName }
            .filter { if (all) (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 else isGame(it) }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { Game(it.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .sortedBy { it.label.lowercase() }
    }

    private fun isGame(a: ApplicationInfo): Boolean =
        (a.flags and ApplicationInfo.FLAG_IS_GAME) != 0 ||
            (Build.VERSION.SDK_INT >= 26 && a.category == ApplicationInfo.CATEGORY_GAME)
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

object Optimizer {
    private fun availMb(ctx: Context): Long {
        val mi = ActivityManager.MemoryInfo()
        ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        return mi.availMem / 1048576
    }

    /** Encerra apps em segundo plano, exceto protegidos, o próprio OPTORK e o jogo alvo. */
    fun clearRam(ctx: Context, exclude: String? = null): Int {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val keep = Prefs.protected(ctx) + setOfNotNull(ctx.packageName, exclude)
        var n = 0
        ctx.packageManager.getInstalledApplications(0)
            .filter { it.packageName !in keep && (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .forEach {
                am.killBackgroundProcesses(it.packageName)
                n++
            }
        return n
    }

    /** Limpa o cache do próprio OPTORK. */
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

    fun enableFocus(ctx: Context): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return false
        val prefs = Prefs.get(ctx)
        if (!prefs.getBoolean("focus_on", false)) {
            prefs.edit()
                .putInt("prev_filter", nm.currentInterruptionFilter)
                .putBoolean("focus_on", true)
                .apply()
        }
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        return true
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
            // sem permissão, nada a restaurar
        }
        prefs.edit().remove("anim_saved").apply()
    }

    fun restoreFocus(ctx: Context) {
        val prefs = Prefs.get(ctx)
        if (!prefs.getBoolean("focus_on", false)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(
                prefs.getInt("prev_filter", NotificationManager.INTERRUPTION_FILTER_ALL)
            )
        }
        prefs.edit().putBoolean("focus_on", false).apply()
    }

    fun restore(ctx: Context) {
        restoreAnimations(ctx)
        restoreFocus(ctx)
        Prefs.get(ctx).edit().putBoolean("active", false).apply()
    }

    fun restoreIfActive(ctx: Context) {
        if (Prefs.get(ctx).getBoolean("active", false)) restore(ctx)
    }

    fun tips(p: DeviceProfile): List<String> = buildList {
        when (p.tier) {
            Tier.LOW -> {
                add("Qualidade gráfica: baixa")
                add("FPS: 30 (estável e sem aquecer)")
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
        if (p.powerSave) add("Economia de bateria ativa: desligue para liberar o desempenho")
        if (p.batteryPct in 0..20) add("Bateria baixa: o sistema reduz o desempenho automaticamente")
    }

    fun run(ctx: Context, game: Game, p: DeviceProfile): Flow<Progress> = flow {
        emit(Progress("Analisando o aparelho", 0.08f))
        delay(500)

        emit(Progress("Encerrando apps em segundo plano", 0.25f))
        val before = availMb(ctx)
        val closed = clearRam(ctx, game.pkg)
        delay(900)
        val ram = (availMb(ctx) - before).coerceAtLeast(0)

        emit(Progress("Limpando cache do OPTORK", 0.45f))
        val cache = clearCache(ctx)
        delay(400)

        emit(Progress("Ativando modo foco", 0.6f))
        val focus = enableFocus(ctx)
        delay(400)

        emit(Progress("Ajustando animações", 0.8f))
        val anim = reduceAnimations(ctx, if (p.tier == Tier.LOW) 0f else 0.5f)
        delay(400)

        emit(Progress("Aplicando perfil ${p.tier.label}", 0.95f))
        delay(400)
        Prefs.get(ctx).edit().putBoolean("active", true).apply()
        emit(Progress("Concluído", 1f, Result(ram, closed, cache, focus, anim, tips(p))))
    }.flowOn(Dispatchers.Default)
}

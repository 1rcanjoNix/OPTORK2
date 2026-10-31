package com.optork.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class MonitorService : Service() {
    private lateinit var wm: WindowManager
    private var view: TextView? = null
    private val cpu = CpuSampler()
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startInForeground()

        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(24, 12, 24, 12)
            background = GradientDrawable().apply {
                setColor(0xCC0A0A0C.toInt())
                cornerRadius = 40f
                setStroke(3, 0xFF00E5A0.toInt())
            }
        }
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 120
        }
        wm.addView(tv, lp)
        view = tv
        handler.post(tick)
    }

    private fun update() {
        val p = DeviceProfiler.read(this)
        val used = if (p.ramTotalMb > 0) (p.ramTotalMb - p.ramFreeMb) * 100 / p.ramTotalMb else 0L
        val c = cpu.sample()
        val bat = if (p.batteryPct >= 0) "${p.batteryPct}%" else "--"
        view?.text = "RAM $used%  CPU ${if (c >= 0) "$c%" else "--"}  BAT $bat ${"%.0f".format(p.batteryTemp)}°C  ${p.refreshHz}Hz"
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("optork_mon", "Monitor OPTORK", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n: Notification = Notification.Builder(this, "optork_mon")
            .setContentTitle("OPTORK monitor ativo")
            .setContentText("Desative em Início > Monitor flutuante")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        view?.let { wm.removeView(it) }
        view = null
        super.onDestroy()
    }
}

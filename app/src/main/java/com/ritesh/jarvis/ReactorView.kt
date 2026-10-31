package com.ritesh.jarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** JARVIS ka arc-reactor. Ghoomta rehta hai. mode: 0 = sun raha hai, 1 = bol raha hai, 2 = mic band. */
class ReactorView(ctx: Context) : View(ctx) {

    var mode: Int = 0
        set(v) { field = v; invalidate() }

    private var t = 0f
    private var last = 0L
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val tri = Path()

    private val tick = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
            t += dt
            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        last = 0L
        postOnAnimation(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private fun main(): Int = when (mode) { 1 -> C.AMBER; 2 -> C.MUTED; else -> C.CYAN }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        val d = resources.displayMetrics.density
        val col = main()
        val speed = if (mode == 2) 0.15f else 1f
        val beat = if (mode == 1) 9f else 3f
        val pulse = 1f + 0.04f * sin(t * beat)

        // chamak (glow)
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r * 0.8f, intArrayOf((col and 0x00FFFFFF) or 0x66000000, (col and 0x00FFFFFF) or 0x1A000000, 0x00000000), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 0.8f, p)
        p.shader = null

        // bahar ke 60 nishan, dheere ghoomte
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = col
        c.save()
        c.rotate(t * 6f * speed, cx, cy)
        for (i in 0 until 60) {
            val long = i % 5 == 0
            p.strokeWidth = (if (long) 1.6f else 1f) * d
            p.alpha = if (long) 255 else 150
            c.save()
            c.rotate(i * 6f, cx, cy)
            c.drawLine(cx, cy - r + 2f * d, cx, cy - r + (if (long) 11f else 6f) * d, p)
            c.restore()
        }
        c.restore()
        p.alpha = 255

        // 3 moti chaap, ulti disha mein
        p.strokeWidth = 6f * d
        p.strokeCap = Paint.Cap.BUTT
        rect.set(cx - r * 0.76f, cy - r * 0.76f, cx + r * 0.76f, cy + r * 0.76f)
        c.save()
        c.rotate(-t * 28f * speed, cx, cy)
        for (k in 0 until 3) c.drawArc(rect, k * 120f + 10f, 86f, false, p)
        c.restore()

        // patli ring jo seedhi ghoomti hai
        p.strokeWidth = 1.5f * d
        p.alpha = 190
        rect.set(cx - r * 0.62f, cy - r * 0.62f, cx + r * 0.62f, cy + r * 0.62f)
        c.save()
        c.rotate(t * 45f * speed, cx, cy)
        for (k in 0 until 6) c.drawArc(rect, k * 60f, 40f, false, p)
        c.restore()
        p.alpha = 255

        // beech ka gola aur tikona
        val core = r * 0.42f * pulse
        p.style = Paint.Style.FILL
        p.color = C.SURFACE
        c.drawCircle(cx, cy, core, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * d
        p.color = col
        c.drawCircle(cx, cy, core, p)

        val tr = core * 0.62f
        tri.reset()
        for (i in 0 until 3) {
            val a = (-90.0 + 120.0 * i) * PI / 180.0
            val x = cx + tr * cos(a).toFloat()
            val y = cy + tr * sin(a).toFloat()
            if (i == 0) tri.moveTo(x, y) else tri.lineTo(x, y)
        }
        tri.close()
        p.style = Paint.Style.FILL
        p.color = (col and 0x00FFFFFF) or 0x33000000
        c.drawPath(tri, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.8f * d
        p.color = col
        c.drawPath(tri, p)

        // sabse andar chamakta bindu
        p.style = Paint.Style.FILL
        p.color = 0xFFE6FDFF.toInt()
        c.drawCircle(cx, cy, core * 0.16f, p)
    }
}

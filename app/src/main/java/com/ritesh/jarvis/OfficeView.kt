package com.ritesh.jarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * JARVIS ka ofis. Agent apni seat par rehte hain. Jab koi asli kaam aata hai, Manager us agent ko bulata hai,
 * agent seedha Manager ke paas jaata hai, diary mein kaam leta hai, wapas seat par aakar kaam karta hai.
 * Idhar-udhar bekaar ghumna nahi hota. Jo dikhta hai wo sirf asli kaam se chalta hai.
 */
class OfficeView(ctx: Context) : View(ctx) {

    private enum class S { IDLE, TO_MGR, AT_MGR, TO_WORK, WORKING }

    private class W(val agent: Agent, val hx: Float, val hy: Float, val idx: Int) {
        var x = hx
        var y = hy
        var tx = hx
        var ty = hy
        var s = S.IDLE
        var t = 0f
        var diary = false
        var job = ""
        var ok = true
        var phase = 0f
        var moving = false
        val queue = ArrayList<Pair<String, Boolean>>()
    }

    private val walkers = ArrayList<W>()
    private var mgrPulse = 0f
    private var mgrBubble = ""
    private var mgrBubbleT = 0f
    private var clock = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var last = 0L

    private val mgrX = 0.5f
    private val mgrY = 0.1f

    init {
        for ((i, a) in Agents.list.withIndex()) {
            val row = i / 4
            val col = i % 4
            walkers.add(W(a, 0.125f + col * 0.25f, 0.44f + row * 0.165f, i))
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
            update(dt)
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

    /** Asli kaam aaya. Manager us agent ko bulayega. agentId "manager" ho to sirf Manager bolta hai. */
    fun assign(agentId: String, text: String, ok: Boolean) {
        mgrPulse = 1f
        val w = walkers.firstOrNull { it.agent.id == agentId && it.agent.live }
        if (w == null) {
            mgrBubble = text
            mgrBubbleT = 4f
        } else {
            w.queue.add(Pair(text, ok))
        }
    }

    /** Emergency stop: sab ruk jaye, sab apni seat par. */
    fun stopAll() {
        for (w in walkers) {
            w.queue.clear()
            w.diary = false
            w.s = S.IDLE
            w.tx = w.hx
            w.ty = w.hy
        }
        mgrBubble = "सब रुक गया"
        mgrBubbleT = 3f
    }

    private fun update(dt: Float) {
        clock += dt
        if (mgrPulse > 0f) mgrPulse = (mgrPulse - dt * 0.6f).coerceAtLeast(0f)
        if (mgrBubbleT > 0f) mgrBubbleT -= dt

        for (w in walkers) {
            val dx = w.tx - w.x
            val dy = w.ty - w.y
            val d = hypot(dx, dy)
            w.moving = d > 0.006f
            if (w.moving) {
                val step = min(d, 0.3f * dt)
                w.x += dx / d * step
                w.y += dy / d * step
                w.phase += dt * 12f
            }

            when (w.s) {
                S.IDLE -> {
                    if (w.queue.isNotEmpty()) {
                        w.s = S.TO_MGR
                        // Manager ke saamne line mein khade hone ki jagah
                        w.tx = mgrX + ((w.idx % 8) - 3.5f) * 0.058f
                        w.ty = mgrY + 0.17f + (w.idx / 8) * 0.045f
                    }
                }
                S.TO_MGR -> if (!w.moving) { w.s = S.AT_MGR; w.t = 1.4f }
                S.AT_MGR -> {
                    w.t -= dt
                    if (w.t <= 0f && w.queue.isNotEmpty()) {
                        val job = w.queue.removeAt(0)
                        w.job = job.first
                        w.ok = job.second
                        w.diary = true
                        w.s = S.TO_WORK
                        w.tx = w.hx
                        w.ty = w.hy
                    }
                }
                S.TO_WORK -> if (!w.moving) { w.s = S.WORKING; w.t = 4f }
                S.WORKING -> {
                    w.t -= dt
                    if (w.t <= 0f) { w.diary = false; w.s = S.IDLE }
                }
            }
        }
    }

    private fun clip(s: String, n: Int): String = if (s.length <= n) s else s.take(n - 1) + "…"

    private fun text(c: Canvas, s: String, x: Float, y: Float, sizeSp: Float, color: Int, bold: Boolean = false, mono: Boolean = false) {
        tp.color = color
        tp.textSize = sizeSp * resources.displayMetrics.scaledDensity
        tp.textAlign = Paint.Align.CENTER
        tp.isFakeBoldText = bold
        tp.typeface = if (mono) Typeface.MONOSPACE else Typeface.DEFAULT
        c.drawText(s, x, y, tp)
    }

    private fun bubble(c: Canvas, s: String, cx: Float, bottom: Float, color: Int) {
        val d = resources.displayMetrics.density
        val label = clip(s, 24)
        tp.textSize = 11f * resources.displayMetrics.scaledDensity
        tp.typeface = Typeface.DEFAULT
        val w = tp.measureText(label) + 24f
        val h = 30f * d
        var l = cx - w / 2
        if (l < 4f) l = 4f
        if (l + w > width - 4f) l = width - 4f - w
        rect.set(l, bottom - h, l + w, bottom)
        p.style = Paint.Style.FILL
        p.color = C.SURFACE
        c.drawRoundRect(rect, h / 2, h / 2, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * d
        p.color = color
        c.drawRoundRect(rect, h / 2, h / 2, p)
        text(c, label, l + w / 2, bottom - h / 2 + 4f * d, 11f, C.TEXT)
    }

    private fun person(c: Canvas, px: Float, py: Float, u: Float, color: Int, phase: Float, moving: Boolean, diary: Boolean, bob: Float) {
        val d = resources.displayMetrics.density
        val y = py + bob
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * d
        p.strokeCap = Paint.Cap.ROUND
        p.color = color
        val sw = if (moving) sin(phase) * 0.35f * u else 0f
        c.drawLine(px - 0.2f * u, y, px - 0.2f * u + sw, y + 0.8f * u, p)
        c.drawLine(px + 0.2f * u, y, px + 0.2f * u - sw, y + 0.8f * u, p)
        p.style = Paint.Style.FILL
        p.color = C.SURFACE2
        rect.set(px - 0.5f * u, y - 1.1f * u, px + 0.5f * u, y)
        c.drawRoundRect(rect, 0.25f * u, 0.25f * u, p)
        p.style = Paint.Style.STROKE
        p.color = color
        c.drawRoundRect(rect, 0.25f * u, 0.25f * u, p)
        p.style = Paint.Style.FILL
        p.color = C.SURFACE2
        c.drawCircle(px, y - 1.6f * u, 0.5f * u, p)
        p.style = Paint.Style.STROKE
        p.color = color
        c.drawCircle(px, y - 1.6f * u, 0.5f * u, p)
        if (diary) {
            p.style = Paint.Style.FILL
            p.color = C.AMBER
            rect.set(px + 0.45f * u, y - 0.95f * u, px + 1.05f * u, y - 0.35f * u)
            c.drawRoundRect(rect, 0.06f * u, 0.06f * u, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1f * d
            p.color = C.BG
            c.drawLine(px + 0.55f * u, y - 0.8f * u, px + 0.95f * u, y - 0.8f * u, p)
            c.drawLine(px + 0.55f * u, y - 0.65f * u, px + 0.95f * u, y - 0.65f * u, p)
        }
    }

    private fun brackets(c: Canvas, w: Float, h: Float, d: Float) {
        val len = 22f * d
        val m = 10f * d
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * d
        p.strokeCap = Paint.Cap.SQUARE
        p.color = C.CYAN
        c.drawLine(m, m, m + len, m, p); c.drawLine(m, m, m, m + len, p)
        c.drawLine(w - m, m, w - m - len, m, p); c.drawLine(w - m, m, w - m, m + len, p)
        c.drawLine(m, h - m, m + len, h - m, p); c.drawLine(m, h - m, m, h - m - len, p)
        c.drawLine(w - m, h - m, w - m - len, h - m, p); c.drawLine(w - m, h - m, w - m, h - m - len, p)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = resources.displayMetrics.density
        val u = w * 0.04f

        p.style = Paint.Style.FILL
        p.color = C.SURFACE
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, 12f * d, 12f * d, p)

        // jaal (grid)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = C.BORDER
        var gx = w / 8
        while (gx < w) { c.drawLine(gx, 0f, gx, h, p); gx += w / 8 }
        var gy = h / 12
        while (gy < h) { c.drawLine(0f, gy, w, gy, p); gy += h / 12 }

        // ghoomti hui scan line
        val sy = ((clock * 0.12f) % 1f) * h
        p.color = C.CYAN
        p.alpha = 70
        p.strokeWidth = 2f * d
        c.drawLine(0f, sy, w, sy, p)
        p.alpha = 255

        brackets(c, w, h, d)
        text(c, "JARVIS OPS · LIVE", w / 2, 22f * d, 10f, C.CYAN, true, true)

        // Manager ki seat
        val mx = mgrX * w
        val my = mgrY * h + 14f * d
        p.style = Paint.Style.FILL
        p.color = C.SURFACE2
        rect.set(mx - 3f * u, my + 0.2f * u, mx + 3f * u, my + 1.6f * u)
        c.drawRoundRect(rect, 8f * d, 8f * d, p)
        if (mgrPulse > 0f) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f * d
            p.color = C.AMBER
            p.alpha = (255 * mgrPulse).toInt()
            c.drawCircle(mx, my - 1.3f * u, u * (1.2f + (1f - mgrPulse) * 1.8f), p)
            p.alpha = 255
        }
        person(c, mx, my, u * 1.1f, C.AMBER, 0f, false, false, 0f)
        text(c, "Manager", mx, my + 3.1f * u, 11f, C.AMBER, true)
        if (mgrBubbleT > 0f) bubble(c, mgrBubble, mx, my - 3.0f * u, C.AMBER)

        // agent ki seatein
        for (k in walkers) {
            val dx = k.hx * w
            val dy = k.hy * h
            p.style = Paint.Style.FILL
            p.color = C.SURFACE2
            rect.set(dx - 1.5f * u, dy + 0.5f * u, dx + 1.5f * u, dy + 1.35f * u)
            c.drawRoundRect(rect, 5f * d, 5f * d, p)
            text(c, clip(k.agent.name.replace(" एजेंट", ""), 9), dx, dy + 2.55f * u, 9f, if (k.agent.live) C.MUTED else C.BORDER)
        }

        // log
        for (k in walkers) {
            val live = k.agent.live
            val color = if (live) C.CYAN else C.BORDER
            val bob = if (!k.moving && k.s == S.IDLE && live) sin(clock * 2f + k.idx) * 0.05f * u else 0f
            person(c, k.x * w, k.y * h, u, color, k.phase, k.moving, k.diary, bob)
            if (!live) text(c, "जल्द", k.x * w, k.y * h - 2.4f * u, 9f, C.MUTED)
            if (k.s == S.WORKING) {
                bubble(c, k.job, k.x * w, k.y * h - 2.4f * u, if (k.ok) C.GREEN else C.RED)
            } else if (k.s == S.AT_MGR) {
                text(c, "काम ले रहा है", k.x * w, k.y * h - 2.4f * u, 9f, C.AMBER)
            }
        }
    }
}

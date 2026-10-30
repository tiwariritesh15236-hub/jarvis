package com.ritesh.jarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * JARVIS ka ofis. Agent idhar-udhar ghoomte hain. Jab koi asli kaam aata hai,
 * Manager agent ko bulata hai, agent diary lekar aata hai, kaam leta hai aur apni jagah jaakar karta hai.
 * Yahan jo dikhta hai wo sirf asli kaam (assign) se chalta hai, nakli kaam nahi banta.
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
        var wander = 2f + Math.random().toFloat() * 6f
        val queue = ArrayList<Pair<String, Boolean>>()
        var moving = false
    }

    private val walkers = ArrayList<W>()
    private var mgrPulse = 0f
    private var mgrBubble = ""
    private var mgrBubbleT = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var last = 0L

    private val mgrX = 0.5f
    private val mgrY = 0.12f

    init {
        val n = Agents.list.size
        for (i in 0 until n) {
            val row = i / 4
            val col = i % 4
            walkers.add(W(Agents.list[i], 0.14f + col * 0.24f, 0.56f + row * 0.26f, i))
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

    /** Asli kaam aaya: Manager is agent ko bulayega. agentId "manager" ho to sirf Manager bolta hai. */
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

    /** Emergency stop: sab ruk jaye, sab apni jagah. */
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
        if (mgrPulse > 0f) mgrPulse = (mgrPulse - dt * 0.6f).coerceAtLeast(0f)
        if (mgrBubbleT > 0f) mgrBubbleT -= dt

        for (w in walkers) {
            // chalna
            val dx = w.tx - w.x
            val dy = w.ty - w.y
            val d = hypot(dx, dy)
            w.moving = d > 0.006f
            if (w.moving) {
                val step = min(d, 0.22f * dt)
                w.x += dx / d * step
                w.y += dy / d * step
                w.phase += dt * 11f
            }

            when (w.s) {
                S.IDLE -> {
                    if (w.queue.isNotEmpty()) {
                        w.s = S.TO_MGR
                        w.tx = mgrX + (w.idx - 3.5f) * 0.05f
                        w.ty = mgrY + 0.2f
                    } else if (w.agent.live) {
                        w.wander -= dt
                        if (w.wander <= 0f) {
                            w.wander = 3f + Math.random().toFloat() * 6f
                            w.tx = (w.hx + (Math.random().toFloat() - 0.5f) * 0.16f).coerceIn(0.06f, 0.94f)
                            w.ty = (w.hy + (Math.random().toFloat() - 0.5f) * 0.14f).coerceIn(0.4f, 0.95f)
                        }
                    }
                }
                S.TO_MGR -> if (!w.moving) { w.s = S.AT_MGR; w.t = 1.4f }
                S.AT_MGR -> {
                    w.t -= dt
                    if (w.t <= 0f) {
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

    private fun text(c: Canvas, s: String, x: Float, y: Float, sizeSp: Float, color: Int, bold: Boolean = false) {
        tp.color = color
        tp.textSize = sizeSp * resources.displayMetrics.scaledDensity
        tp.textAlign = Paint.Align.CENTER
        tp.isFakeBoldText = bold
        c.drawText(s, x, y, tp)
    }

    private fun bubble(c: Canvas, s: String, cx: Float, bottom: Float, color: Int) {
        val label = clip(s, 24)
        tp.textSize = 11f * resources.displayMetrics.scaledDensity
        val w = tp.measureText(label) + 24f
        val h = 30f * resources.displayMetrics.density
        var l = cx - w / 2
        if (l < 4f) l = 4f
        if (l + w > width - 4f) l = width - 4f - w
        rect.set(l, bottom - h, l + w, bottom)
        p.style = Paint.Style.FILL
        p.color = C.SURFACE
        c.drawRoundRect(rect, h / 2, h / 2, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * resources.displayMetrics.density
        p.color = color
        c.drawRoundRect(rect, h / 2, h / 2, p)
        text(c, label, l + w / 2, bottom - h / 2 + 4f * resources.displayMetrics.density, 11f, C.TEXT)
    }

    private fun person(c: Canvas, px: Float, py: Float, u: Float, color: Int, phase: Float, moving: Boolean, diary: Boolean) {
        val d = resources.displayMetrics.density
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * d
        p.strokeCap = Paint.Cap.ROUND
        p.color = color
        val sw = if (moving) sin(phase) * 0.35f * u else 0f
        c.drawLine(px - 0.2f * u, py, px - 0.2f * u + sw, py + 0.8f * u, p)
        c.drawLine(px + 0.2f * u, py, px + 0.2f * u - sw, py + 0.8f * u, p)
        p.style = Paint.Style.FILL
        p.color = C.SURFACE2
        rect.set(px - 0.5f * u, py - 1.1f * u, px + 0.5f * u, py)
        c.drawRoundRect(rect, 0.25f * u, 0.25f * u, p)
        p.style = Paint.Style.STROKE
        p.color = color
        c.drawRoundRect(rect, 0.25f * u, 0.25f * u, p)
        p.style = Paint.Style.FILL
        p.color = C.SURFACE2
        c.drawCircle(px, py - 1.6f * u, 0.5f * u, p)
        p.style = Paint.Style.STROKE
        p.color = color
        c.drawCircle(px, py - 1.6f * u, 0.5f * u, p)
        if (diary) {
            p.style = Paint.Style.FILL
            p.color = C.AMBER
            rect.set(px + 0.45f * u, py - 0.95f * u, px + 1.05f * u, py - 0.35f * u)
            c.drawRoundRect(rect, 0.06f * u, 0.06f * u, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1f * d
            p.color = C.BG
            c.drawLine(px + 0.55f * u, py - 0.8f * u, px + 0.95f * u, py - 0.8f * u, p)
            c.drawLine(px + 0.55f * u, py - 0.65f * u, px + 0.95f * u, py - 0.65f * u, p)
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = resources.displayMetrics.density
        val u = w * 0.045f

        p.style = Paint.Style.FILL
        p.color = C.SURFACE
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, 20f * d, 20f * d, p)

        // faint floor lines
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = C.BORDER
        var gx = w / 8
        while (gx < w) { c.drawLine(gx, 0f, gx, h, p); gx += w / 8 }

        // Manager desk
        val mx = mgrX * w
        val my = mgrY * h
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
        person(c, mx, my, u * 1.1f, C.AMBER, 0f, false, false)
        text(c, "Manager", mx, my + 3.1f * u, 11f, C.AMBER, true)
        if (mgrBubbleT > 0f) bubble(c, mgrBubble, mx, my - 3.4f * u, C.AMBER)

        // desks
        for (k in walkers) {
            val dx = k.hx * w
            val dy = k.hy * h
            p.style = Paint.Style.FILL
            p.color = C.SURFACE2
            rect.set(dx - 1.6f * u, dy + 0.5f * u, dx + 1.6f * u, dy + 1.4f * u)
            c.drawRoundRect(rect, 6f * d, 6f * d, p)
            text(c, clip(k.agent.name.replace(" एजेंट", ""), 10), dx, dy + 2.6f * u, 10f, if (k.agent.live) C.MUTED else C.BORDER)
        }

        // people
        for (k in walkers) {
            val live = k.agent.live
            val color = if (live) C.CYAN else C.BORDER
            person(c, k.x * w, k.y * h, u, color, k.phase, k.moving, k.diary)
            if (!live) text(c, "जल्द", k.x * w, k.y * h - 2.6f * u, 10f, C.MUTED)
            if (k.s == S.WORKING) {
                bubble(c, k.job, k.x * w, k.y * h - 2.6f * u, if (k.ok) C.GREEN else C.RED)
            } else if (k.s == S.AT_MGR) {
                text(c, "काम लेते हुए…", k.x * w, k.y * h - 2.6f * u, 10f, C.AMBER)
            }
        }
    }
}

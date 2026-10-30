package com.ritesh.jarvis

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/** Mic wala gola. Sunte samay dhadakta hai. */
class OrbView(ctx: Context) : View(ctx) {
    var listening: Boolean = false
        set(v) {
            field = v
            if (v) {
                if (!anim.isStarted) anim.start()
            } else {
                anim.cancel()
                pulse = 0f
                invalidate()
            }
        }

    private var pulse = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1300
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            pulse = it.animatedValue as Float
            invalidate()
        }
    }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        val d = resources.displayMetrics.density

        // bahar ki patli ring
        p.style = Paint.Style.STROKE
        p.strokeWidth = d
        p.color = C.BORDER
        c.drawCircle(cx, cy, r - d, p)

        // beech ki ring (sunte samay faili hui)
        p.strokeWidth = 1.5f * d
        p.color = C.CYAN
        if (listening) {
            p.alpha = (200 * (1f - pulse)).toInt()
            c.drawCircle(cx, cy, r * (0.70f + 0.28f * pulse), p)
        } else {
            p.alpha = 100
            c.drawCircle(cx, cy, r * 0.76f, p)
        }

        // andar ka gola
        p.style = Paint.Style.FILL
        p.color = C.SURFACE
        c.drawCircle(cx, cy, r * 0.52f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * d
        p.color = C.CYAN
        c.drawCircle(cx, cy, r * 0.52f, p)

        // mic ka nishan
        val u = r * 0.5f
        p.strokeWidth = u * 0.09f
        p.strokeCap = Paint.Cap.ROUND
        c.drawRoundRect(RectF(cx - 0.2f * u, cy - 0.62f * u, cx + 0.2f * u, cy + 0.08f * u), 0.2f * u, 0.2f * u, p)
        c.drawArc(RectF(cx - 0.42f * u, cy - 0.36f * u, cx + 0.42f * u, cy + 0.46f * u), 0f, 180f, false, p)
        c.drawLine(cx, cy + 0.46f * u, cx, cy + 0.7f * u, p)
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }
}

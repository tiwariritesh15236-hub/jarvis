package com.ritesh.jarvis

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** JARVIS ke rang (design wale). */
object C {
    val BG = Color.parseColor("#070C14")
    val NAV = Color.parseColor("#0A121C")
    val SURFACE = Color.parseColor("#0E1622")
    val SURFACE2 = Color.parseColor("#131E2E")
    val BORDER = Color.parseColor("#1F2E42")
    val TEXT = Color.parseColor("#E8F1FA")
    val MUTED = Color.parseColor("#93A7BD")
    val CYAN = Color.parseColor("#38D6F2")
    val AMBER = Color.parseColor("#FFB547")
    val GREEN = Color.parseColor("#5EE6A8")
    val RED = Color.parseColor("#FF6B6B")
    val ON_CYAN = Color.parseColor("#04202A")
    val ON_RED = Color.parseColor("#1A0505")
}

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.shape(fill: Int, stroke: Int = 0, radiusDp: Int = 14, strokeDp: Int = 1): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != 0) setStroke(dp(strokeDp), stroke)
    }

fun Context.label(text: String, sizeSp: Float = 15f, color: Int = C.TEXT, bold: Boolean = false, mono: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        if (mono) setTypeface(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        else if (bold) setTypeface(typeface, Typeface.BOLD)
    }

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun Context.card(stroke: Int = C.BORDER, fill: Int = C.SURFACE): LinearLayout = vbox().apply {
    background = shape(fill, stroke, 16)
    setPadding(dp(16), dp(14), dp(16), dp(14))
}

fun Context.dot(color: Int, sizeDp: Int = 8): View = View(this).apply {
    background = GradientDrawable().apply {
        setShape(GradientDrawable.OVAL)
        setColor(color)
    }
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
}

fun lp(w: Int, h: Int, weight: Float = 0f): LinearLayout.LayoutParams = LinearLayout.LayoutParams(w, h, weight)

fun Context.lpm(w: Int, h: Int, top: Int = 0, bottom: Int = 0, left: Int = 0, weight: Float = 0f): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(w, h, weight).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
        leftMargin = dp(left)
    }

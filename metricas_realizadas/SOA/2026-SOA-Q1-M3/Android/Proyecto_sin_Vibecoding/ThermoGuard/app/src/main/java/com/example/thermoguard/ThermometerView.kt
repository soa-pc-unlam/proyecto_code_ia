package com.example.thermoguard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat

/**
 * VISTA: ThermometerView
 * DESCRIPCIÓN: Componente gráfico para visualizar temperatura de forma no interactiva.
 */
class ThermometerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, def: Int = 0) : View(context, attrs, def) {

    private val min = Constants.FRIO_MIN
    private val max = Constants.CALIENTE_MAX
    private var currentT = 0f
    private var color = ContextCompat.getColor(context, R.color.primary)
    private var anim: ValueAnimator? = null

    private val pTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.thermometer_track) }
    private val pMerc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pGlass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(30, 255, 255, 255) }
    private val pTick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.thermometer_tick); strokeWidth = 3f }
    private val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.thermometer_label) }
    private val pKnob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.deep_ocean_text) }
    private val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f }

    /** Actualiza la temperatura mostrada con una animación suave. */
    fun updateData(t: Float, c: Int) {
        this.color = c
        val target = t.coerceIn(min, max)
        anim?.cancel()
        anim = ValueAnimator.ofFloat(currentT, target).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener { currentT = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val tw = w * 0.15f; val br = tw * 1.3f; val cx = w * 0.45f
        val top = h * 0.05f + tw/2; val bcy = h - br - 10f; val bot = bcy - br * 0.5f; val uh = bot - top

        canvas.drawRoundRect(RectF(cx - tw/2, top - tw/2, cx + tw/2, bot), tw/2, tw/2, pTrack)
        canvas.drawCircle(cx, bcy, br, pTrack)

        pMerc.color = color
        val mt = bot - ((currentT - min) / (max - min)) * uh
        canvas.drawRoundRect(RectF(cx - tw/2 + (tw * 0.2f), mt, cx + tw/2 - (tw * 0.2f), bot), tw/2, tw/2, pMerc)
        canvas.drawCircle(cx, bcy, br * 0.8f, pMerc)

        canvas.drawRoundRect(RectF(cx - tw/4, top, cx - tw/8, bot - 10f), 5f, 5f, pGlass)
        pLabel.textSize = h * 0.03f
        for (t in min.toInt()..max.toInt() step 10) {
            val ty = bot - ((t - min) / (max - min)) * uh
            canvas.drawLine(cx + tw * 0.6f, ty, cx + tw, ty, pTick)
            canvas.drawText("$t°", cx + tw * 1.2f, ty + pLabel.textSize * 0.3f, pLabel)
        }

        pRing.color = color
        val ky = mt.coerceIn(top, bot)
        canvas.drawCircle(cx, ky, tw * 0.35f, pKnob)
        canvas.drawCircle(cx, ky, tw * 0.35f, pRing)
    }
}

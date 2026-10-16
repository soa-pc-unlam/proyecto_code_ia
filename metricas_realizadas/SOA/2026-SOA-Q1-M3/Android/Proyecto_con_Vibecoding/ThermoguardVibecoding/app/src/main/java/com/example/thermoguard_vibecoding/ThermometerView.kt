package com.example.thermoguard_vibecoding

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class ThermometerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var temperature: Float = 20f
    private var animatedTemp: Float = 20f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()
    
    private val mercuryColor get() = when {
        animatedTemp <= Constants.TEMP_COLD_MAX -> ContextCompat.getColor(context, R.color.status_blue)
        animatedTemp <= Constants.TEMP_MEDIUM_MAX -> ContextCompat.getColor(context, R.color.status_green)
        else -> ContextCompat.getColor(context, R.color.status_red)
    }

    fun setTemperature(temp: Float) {
        val animator = ValueAnimator.ofFloat(animatedTemp, temp)
        animator.duration = 800
        animator.addUpdateListener {
            animatedTemp = it.animatedValue as Float
            invalidate()
        }
        animator.start()
        temperature = temp
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2
        val tubeWidth = w * 0.2f
        val bulbRadius = w * 0.25f
        
        // Draw background tube
        paint.color = ContextCompat.getColor(context, R.color.thermometer_background)
        rectF.set(centerX - tubeWidth / 2, 20f, centerX + tubeWidth / 2, h - bulbRadius)
        canvas.drawRoundRect(rectF, tubeWidth / 2, tubeWidth / 2, paint)
        canvas.drawCircle(centerX, h - bulbRadius, bulbRadius, paint)

        // Draw Mercury
        paint.color = mercuryColor
        val maxTemp = Constants.TEMP_HOT_MAX
        val minTemp = Constants.TEMP_COLD_MIN
        val levelHeight = (h - bulbRadius - 20f) * ((animatedTemp - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)
        val currentTop = h - bulbRadius - levelHeight
        
        rectF.set(centerX - tubeWidth / 2, currentTop, centerX + tubeWidth / 2, h - bulbRadius)
        canvas.drawRect(rectF, paint)
        canvas.drawCircle(centerX, h - bulbRadius, bulbRadius, paint)

        // Draw scale
        paint.color = Color.BLACK
        paint.textSize = 30f
        val steps = 5
        for (i in 0..steps) {
            val tempVal = i * (maxTemp / steps)
            val y = h - bulbRadius - (h - bulbRadius - 20f) * (i / steps.toFloat())
            canvas.drawLine(centerX + tubeWidth / 2, y, centerX + tubeWidth / 2 + 20f, y, paint)
            canvas.drawText("${tempVal.toInt()}°", centerX + tubeWidth / 2 + 30f, y + 10f, paint)
        }
    }
}
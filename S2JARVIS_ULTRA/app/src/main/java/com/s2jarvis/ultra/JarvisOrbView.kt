package com.s2jarvis.ultra

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min
import kotlin.math.sin

/** Siri-inspired animated spectrum orb, drawn natively without external assets. */
class JarvisOrbView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var active = false
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3600L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    fun setActive(value: Boolean) {
        active = value
        if (value && !animator.isStarted) animator.start()
        invalidate()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (!animator.isStarted) animator.start() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val base = min(width, height) * 0.285f
        val wave = sin(phase * Math.PI * 2).toFloat()
        val pulse = if (active) 1f + 0.10f * wave else 1f + 0.018f * wave
        val r = base * pulse
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy, r * 2.05f,
            intArrayOf(0x557E6BFF, 0x334C8EFF, 0x22EC62B7, 0x0035BFFF), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 2.05f, paint)

        // Soft spectrum aura layers create the fluid Siri-like colour bloom.
        val shift = if (active) wave * r * .16f else 0f
        paint.shader = RadialGradient(cx - r * .35f + shift, cy - r * .25f, r * 1.5f,
            intArrayOf(0xFFFFF7FF.toInt(), 0xFFB7A8FF.toInt(), 0xFF6C7DFF.toInt(), 0xFFEC65C8.toInt(), 0xFF342D79.toInt()),
            floatArrayOf(0f, .20f, .48f, .76f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)

        paint.shader = RadialGradient(cx + r * .35f, cy + r * .34f, r * 1.15f,
            intArrayOf(0x00FFFFFF, 0x6679E7FF, 0x448F65FF, 0x00FF5CC8), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * .98f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (active) 2.4f else 1.3f
        paint.color = 0x66DCCBFF
        canvas.drawCircle(cx, cy, r * (1.20f + .035f * wave), paint)
        paint.strokeWidth = 1f
        paint.color = 0x3399D9FF
        canvas.drawCircle(cx, cy, r * (1.38f - .025f * wave), paint)
        paint.style = Paint.Style.FILL
    }
}

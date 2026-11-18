package com.chronos.app.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.chronos.app.theme.CyberThemeId
import com.chronos.app.theme.ResolvedCyberPalette
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Non-interactive PS5-inspired cinematic particle layer rendering drifting Golden Sands motes,
 * mystic temporal wisps, and directional spark bursts upon tactical button presses.
 */
class SandsParticleVfxView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class AmbientParticle(
        var xNorm: Float,
        var yNorm: Float,
        var speedY: Float,
        var driftX: Float,
        var radiusDp: Float,
        var alphaBase: Int,
        var colorSlot: Int,
        var phase: Float
    )

    private data class BurstSpark(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        var maxLife: Float,
        var radiusPx: Float,
        var color: Int
    )

    private var palette: ResolvedCyberPalette = CyberThemeId.PRINCE_OF_PERSIA_SANDS.resolve(true)
    private var lastFrameMs: Long = System.currentTimeMillis()
    private val burstSparks = mutableListOf<BurstSpark>()
    private val random = Random(2026)

    private val ambientParticles = List(26) { index ->
        AmbientParticle(
            xNorm = random.nextFloat(),
            yNorm = random.nextFloat(),
            speedY = 0.012f + random.nextFloat() * 0.024f,
            driftX = (random.nextFloat() - 0.5f) * 0.014f,
            radiusDp = 1.4f + random.nextFloat() * 2.4f,
            alphaBase = 75 + random.nextInt(115),
            colorSlot = index % 3,
            phase = random.nextFloat() * 6.28318f
        )
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val sparkGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val frameAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            stepSimulation()
            invalidate()
        }
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrameMs = System.currentTimeMillis()
        if (!frameAnimator.isStarted) {
            frameAnimator.start()
        }
    }

    override fun onDetachedFromWindow() {
        frameAnimator.cancel()
        super.onDetachedFromWindow()
    }

    fun applyPalette(newPalette: ResolvedCyberPalette) {
        palette = newPalette
        invalidate()
    }

    fun triggerBurstAt(anchorView: View, overridePrimary: Int? = null, overrideAccent: Int? = null) {
        if (width <= 0 || height <= 0) return
        val selfLoc = IntArray(2)
        val targetLoc = IntArray(2)
        getLocationOnScreen(selfLoc)
        anchorView.getLocationOnScreen(targetLoc)

        val cx = (targetLoc[0] - selfLoc[0]) + anchorView.width * 0.5f
        val cy = (targetLoc[1] - selfLoc[1]) + anchorView.height * 0.5f
        val density = resources.displayMetrics.density

        val cPrimary = overridePrimary ?: palette.primaryNeon
        val cSecondary = overrideAccent ?: palette.secondaryNeon
        val cTertiary = palette.tertiaryNeon

        val count = 16
        repeat(count) { i ->
            val angle = (i.toFloat() / count.toFloat()) * 6.28318f + (random.nextFloat() - 0.5f) * 0.35f
            val speed = (85f + random.nextFloat() * 145f) * density
            val color = when (i % 3) {
                0 -> cPrimary
                1 -> cSecondary
                else -> cTertiary
            }
            burstSparks.add(
                BurstSpark(
                    x = cx,
                    y = cy,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    life = 0f,
                    maxLife = 0.42f + random.nextFloat() * 0.25f,
                    radiusPx = (2.2f + random.nextFloat() * 2.4f) * density,
                    color = color
                )
            )
        }
    }

    private fun stepSimulation() {
        val now = System.currentTimeMillis()
        val dt = ((now - lastFrameMs) / 1000f).coerceIn(0.001f, 0.05f)
        lastFrameMs = now

        ambientParticles.forEach { p ->
            p.phase += dt * 1.9f
            p.yNorm -= p.speedY * dt
            p.xNorm += (p.driftX + sin(p.phase) * 0.008f) * dt
            if (p.yNorm < -0.04f) {
                p.yNorm = 1.04f
                p.xNorm = random.nextFloat()
            }
            if (p.xNorm < -0.02f) p.xNorm = 1.02f
            if (p.xNorm > 1.02f) p.xNorm = -0.02f
        }

        val iterator = burstSparks.iterator()
        while (iterator.hasNext()) {
            val s = iterator.next()
            s.life += dt
            if (s.life >= s.maxLife) {
                iterator.remove()
            } else {
                s.x += s.vx * dt
                s.y += s.vy * dt
                s.vx *= 0.92f
                s.vy *= 0.92f
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val density = resources.displayMetrics.density
        val isPrince = palette.themeId == CyberThemeId.PRINCE_OF_PERSIA_SANDS
        val intensityFactor = when {
            isPrince && palette.isDark -> 1.0f
            isPrince -> 0.72f
            palette.isDark -> 0.62f
            else -> 0.40f
        }

        ambientParticles.forEach { p ->
            val px = p.xNorm * w
            val py = p.yNorm * h
            val baseColor = when (p.colorSlot) {
                0 -> palette.primaryNeon
                1 -> palette.secondaryNeon
                else -> palette.tertiaryNeon
            }
            val shimmer = 0.55f + 0.45f * sin(p.phase)
            val alpha = (p.alphaBase * shimmer * intensityFactor).toInt().coerceIn(0, 220)
            val r = p.radiusDp * density

            sparkGlowPaint.shader = RadialGradient(
                px,
                py,
                r * 2.8f,
                intArrayOf(
                    Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),
                    Color.TRANSPARENT
                ),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(px, py, r * 2.8f, sparkGlowPaint)

            particlePaint.color = Color.argb(
                (alpha * 1.15f).toInt().coerceAtMost(255),
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor)
            )
            canvas.drawCircle(px, py, r * 0.75f, particlePaint)
        }

        burstSparks.forEach { s ->
            val progress = (s.life / s.maxLife).coerceIn(0f, 1f)
            val alpha = ((1f - progress) * 245f).toInt().coerceIn(0, 245)
            val glowR = s.radiusPx * (2.6f - progress)

            sparkGlowPaint.shader = RadialGradient(
                s.x,
                s.y,
                glowR.coerceAtLeast(1f),
                intArrayOf(
                    Color.argb(alpha, 255, 255, 255),
                    Color.argb((alpha * 0.85f).toInt(), Color.red(s.color), Color.green(s.color), Color.blue(s.color)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(s.x, s.y, glowR, sparkGlowPaint)
        }
    }
}

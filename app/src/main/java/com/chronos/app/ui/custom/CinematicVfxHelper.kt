package com.chronos.app.ui.custom

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.chronos.app.R
import com.chronos.app.theme.CyberThemeId
import com.chronos.app.theme.ResolvedCyberPalette
import com.google.android.material.card.MaterialCardView

/**
 * Provides HarmonyOS 7 specular glass depth + high-depth diffused colored shadows +
 * PS5-inspired cinematic glow, golden-sand / mystic-blue / critical-crimson border pulses,
 * and kinetic press micro-animations.
 */
object CinematicVfxHelper {

    fun attachCardCinematicPulse(
        card: MaterialCardView,
        palette: ResolvedCyberPalette,
        baseStrokeColor: Int,
        elevationDp: Float = 12f
    ) {
        val existing = card.getTag(R.id.tag_cinematic_animator) as? ValueAnimator
        existing?.cancel()

        val density = card.resources.displayMetrics.density
        card.cardElevation = elevationDp * density

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val shadowTint = Color.argb(
                if (palette.isDark) 135 else 80,
                Color.red(baseStrokeColor),
                Color.green(baseStrokeColor),
                Color.blue(baseStrokeColor)
            )
            card.outlineAmbientShadowColor = shadowTint
            card.outlineSpotShadowColor = shadowTint
        }

        if (palette.themeId != CyberThemeId.PRINCE_OF_PERSIA_SANDS) {
            card.strokeColor = baseStrokeColor
            card.setTag(R.id.tag_cinematic_animator, null)
            return
        }

        val gold = palette.primaryNeon
        val mysticBlue = palette.secondaryNeon
        val criticalCrimson = palette.tertiaryNeon
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                val t = va.animatedValue as Float
                val phaseColor = if (t < 0.7f) {
                    blendColors(gold, mysticBlue, (t / 0.7f) * 0.65f)
                } else {
                    blendColors(mysticBlue, criticalCrimson, ((t - 0.7f) / 0.3f) * 0.45f)
                }
                card.strokeColor = phaseColor
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val dynamicShadow = Color.argb(
                        if (palette.isDark) 150 else 85,
                        Color.red(phaseColor),
                        Color.green(phaseColor),
                        Color.blue(phaseColor)
                    )
                    card.outlineSpotShadowColor = dynamicShadow
                }
            }
            start()
        }
        card.setTag(R.id.tag_cinematic_animator, animator)
    }

    fun applyAmbientBlurEffectIfSupported(backdropView: View, enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backdropView.setRenderEffect(
                if (enabled) {
                    RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP)
                } else {
                    null
                }
            )
        }
    }

    fun playTacticalPressBurst(target: View, particleOverlay: SandsParticleVfxView? = null) {
        val scaleDownX = ObjectAnimator.ofFloat(target, View.SCALE_X, 1f, 0.955f, 1.018f, 1f)
        val scaleDownY = ObjectAnimator.ofFloat(target, View.SCALE_Y, 1f, 0.955f, 1.018f, 1f)
        AnimatorSet().apply {
            playTogether(scaleDownX, scaleDownY)
            duration = 230L
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        particleOverlay?.triggerBurstAt(target)
    }

    fun animateSmoothTextTransition(vararg views: View, onMidpointSwap: () -> Unit) {
        views.forEach { it.animate().cancel() }
        val fadeOuts = views.map { view ->
            ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 0.15f).apply {
                duration = 140L
            }
        }
        val outSet = AnimatorSet().apply { playTogether(fadeOuts) }
        outSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onMidpointSwap()
                views.forEach { v ->
                    v.translationY = 8f
                    v.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(220L)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
            }
        })
        outSet.start()
    }

    private fun blendColors(from: Int, to: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val a = (Color.alpha(from) * inv + Color.alpha(to) * ratio).toInt().coerceIn(0, 255)
        val r = (Color.red(from) * inv + Color.red(to) * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(from) * inv + Color.green(to) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(from) * inv + Color.blue(to) * ratio).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }
}

package com.chronos.app.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat
import com.chronos.app.R
import com.chronos.app.data.local.HabitCycleState
import com.chronos.app.domain.ChronosTimeEngine
import com.chronos.app.domain.DayPhase
import com.chronos.app.domain.ProjectedTimelineNode
import com.chronos.app.feedback.CyberFeedbackManager
import com.chronos.app.theme.CyberThemeId
import com.chronos.app.theme.ResolvedCyberPalette
import kotlin.math.hypot

class LaserTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class RenderedNodeHit(
        val node: ProjectedTimelineNode,
        val cx: Float,
        val cy: Float
    )

    private var themePalette: ResolvedCyberPalette = CyberThemeId.CYBERPUNK_CLASSIC.resolve(true)
    private var dayProgress: Float = 0.5f
    private var dayPhase: DayPhase = DayPhase.NIGHT
    private var projectedNodes: List<ProjectedTimelineNode> = emptyList()
    private val renderedHits = mutableListOf<RenderedNodeHit>()
    private var pulsePhase: Float = 0f

    private var downX = 0f
    private var downY = 0f

    var onNodeSelected: ((ProjectedTimelineNode) -> Unit)? = null

    private val monoTypeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val trackRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = monoTypeface
    }

    private val laserGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val laserCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val whiteHotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val flarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val durationLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val nodeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val nodeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val nodePillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val nodePillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.1f
    }

    private val nodeNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = monoTypeface
    }

    private val trackRect = RectF()
    private val laserRect = RectF()
    private val pillRect = RectF()

    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulsePhase = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!pulseAnimator.isStarted) {
            pulseAnimator.start()
        }
    }

    override fun onDetachedFromWindow() {
        pulseAnimator.cancel()
        super.onDetachedFromWindow()
    }

    fun updateTimeline(
        progress: Float,
        phase: DayPhase,
        nodes: List<ProjectedTimelineNode>,
        palette: ResolvedCyberPalette
    ) {
        dayProgress = progress.coerceIn(0f, 1f)
        dayPhase = phase
        projectedNodes = nodes
        themePalette = palette
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val density = resources.displayMetrics.density
        val padH = 22f * density
        val trackLeft = padH
        val trackRight = w - padH
        val trackWidth = (trackRight - trackLeft).coerceAtLeast(1f)

        // Centered 24h laser track to give balanced space above and below for alternating nodes
        val laserY = h * 0.50f
        val trackHeight = 9f * density

        // 1. Frosted Glass Capsule Track + Specular Rim
        trackPaint.color = themePalette.surfaceElevatedColor
        trackRimPaint.color = themePalette.subtleBorderColor
        trackRect.set(trackLeft, laserY - trackHeight / 2f, trackRight, laserY + trackHeight / 2f)
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, trackPaint)
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, trackRimPaint)

        // 2. Hour Markers (00h, 06h, 12h, 18h, 24h) neatly tucked on the laser baseline
        labelPaint.textSize = 8.5f * density
        labelPaint.color = themePalette.textMuted
        tickPaint.color = themePalette.textMuted
        val markers = listOf(0f to "00h", 0.25f to "06h", 0.5f to "12h", 0.75f to "18h", 1f to "24h")
        markers.forEach { (frac, label) ->
            val x = trackLeft + trackWidth * frac
            canvas.drawLine(x, laserY - 7f * density, x, laserY + 7f * density, tickPaint)
            canvas.drawText(label, x, laserY + 17f * density, labelPaint)
        }

        // 3. Phase-Dynamic Luminous Laser Beam
        val (phaseStart, phaseEnd) = ChronosTimeEngine.laserColorsForPhase(dayPhase, themePalette)
        val tipX = trackLeft + trackWidth * dayProgress.coerceAtLeast(0.015f)

        val glowAlphaEnd = if (themePalette.isDark) 150 else 95
        val glowAlphaStart = if (themePalette.isDark) 55 else 35
        val glowHalfH = (10f + 3.5f * pulsePhase) * density
        laserGlowPaint.shader = LinearGradient(
            trackLeft,
            laserY,
            tipX,
            laserY,
            intArrayOf(
                Color.argb(glowAlphaStart, Color.red(phaseStart), Color.green(phaseStart), Color.blue(phaseStart)),
                Color.argb(glowAlphaEnd, Color.red(phaseEnd), Color.green(phaseEnd), Color.blue(phaseEnd))
            ),
            null,
            Shader.TileMode.CLAMP
        )
        laserRect.set(trackLeft, laserY - glowHalfH, tipX, laserY + glowHalfH)
        canvas.drawRoundRect(laserRect, glowHalfH, glowHalfH, laserGlowPaint)

        laserCorePaint.shader = LinearGradient(
            trackLeft,
            laserY,
            tipX,
            laserY,
            phaseStart,
            phaseEnd,
            Shader.TileMode.CLAMP
        )
        laserRect.set(trackLeft, laserY - trackHeight / 2f, tipX, laserY + trackHeight / 2f)
        canvas.drawRoundRect(laserRect, trackHeight / 2f, trackHeight / 2f, laserCorePaint)

        whiteHotPaint.alpha = if (themePalette.isDark) (185 + (65 * pulsePhase)).toInt() else 210
        val filamentHalfH = 1.4f * density
        laserRect.set(trackLeft + 3f * density, laserY - filamentHalfH, tipX, laserY + filamentHalfH)
        canvas.drawRoundRect(laserRect, filamentHalfH, filamentHalfH, whiteHotPaint)

        val flareRadius = (16f + 5.5f * pulsePhase) * density
        flarePaint.shader = RadialGradient(
            tipX,
            laserY,
            flareRadius,
            intArrayOf(
                Color.WHITE,
                Color.argb(if (themePalette.isDark) 215 else 175, Color.red(phaseEnd), Color.green(phaseEnd), Color.blue(phaseEnd)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.36f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(tipX, laserY, flareRadius, flarePaint)

        // 4. Alternating Top / Bottom Distribution for Projected Chronological Nodes + Guide Lines
        renderedHits.clear()
        val topNodeY = h * 0.25f
        val bottomNodeY = h * 0.77f
        nodeNamePaint.textSize = 9.0f * density
        var lastTopRightX = -Float.MAX_VALUE
        var lastBottomRightX = -Float.MAX_VALUE

        projectedNodes.forEachIndexed { index, node ->
            val isAbove = index % 2 == 0
            val nodeY = if (isAbove) topNodeY else bottomNodeY

            val startX = trackLeft + trackWidth * node.startFraction
            val endX = (trackLeft + trackWidth * (node.startFraction + node.durationFraction))
                .coerceAtLeast(startX + 14f * density)
                .coerceAtMost(trackRight)

            val nodeColor = when {
                node.timer.parsedHabitState() == HabitCycleState.COMPLETED -> themePalette.tertiaryNeon
                node.isCurrentlyActiveWindow -> themePalette.secondaryNeon
                else -> themePalette.primaryNeon
            }

            // Luminous capsule duration line along node's alternating lane
            durationLinePaint.color = Color.argb(
                if (themePalette.isDark) 52 else 40,
                Color.red(nodeColor),
                Color.green(nodeColor),
                Color.blue(nodeColor)
            )
            durationLinePaint.strokeWidth = 6f * density
            canvas.drawLine(startX, nodeY, endX, nodeY, durationLinePaint)

            durationLinePaint.color = Color.argb(225, Color.red(nodeColor), Color.green(nodeColor), Color.blue(nodeColor))
            durationLinePaint.strokeWidth = 2.6f * density
            canvas.drawLine(startX, nodeY, endX, nodeY, durationLinePaint)

            // Vertical guide line connecting alternating node to the 24h laser bar
            durationLinePaint.color = Color.argb(150, Color.red(nodeColor), Color.green(nodeColor), Color.blue(nodeColor))
            durationLinePaint.strokeWidth = 1.4f * density
            if (isAbove) {
                canvas.drawLine(startX, nodeY + 6f * density, startX, laserY - 6f * density, durationLinePaint)
            } else {
                canvas.drawLine(startX, laserY + 6f * density, startX, nodeY - 6f * density, durationLinePaint)
            }

            // Node halo + dot
            val haloR = if (node.isCurrentlyActiveWindow) (13f + 4f * pulsePhase) * density else 10f * density
            flarePaint.shader = RadialGradient(
                startX,
                nodeY,
                haloR,
                intArrayOf(
                    Color.argb(if (themePalette.isDark) 170 else 115, Color.red(nodeColor), Color.green(nodeColor), Color.blue(nodeColor)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(startX, nodeY, haloR, flarePaint)

            val dotR = 5.5f * density
            nodeDotPaint.color = nodeColor
            canvas.drawCircle(startX, nodeY, dotR, nodeDotPaint)

            nodeRingPaint.color = if (themePalette.isDark) Color.WHITE else themePalette.surfaceColor
            nodeRingPaint.strokeWidth = 1.6f * density
            canvas.drawCircle(startX, nodeY, dotR, nodeRingPaint)

            // Alternating label pill (above topNodeY when isAbove, below bottomNodeY when !isAbove)
            val shortName = if (node.timer.name.length > 10) {
                node.timer.name.take(9) + "…"
            } else {
                node.timer.name
            }
            val timeTag = ChronosTimeEngine.formatMinuteOfDay(node.activationMinuteOfDay)
            val labelText = "$shortName · $timeTag"
            val textWidth = nodeNamePaint.measureText(labelText)
            val pillHalfW = (textWidth / 2f) + 6f * density
            var pillCenterX = startX.coerceIn(trackLeft + pillHalfW, trackRight - pillHalfW)

            val pillHalfH = 8.5f * density
            var pillCenterY = if (isAbove) {
                (nodeY - 17f * density).coerceAtLeast(11f * density)
            } else {
                (nodeY + 17f * density).coerceAtMost(h - 11f * density)
            }

            // Anti-overlap stagger if another pill on the same alternating side is too close horizontally
            val minGap = 4f * density
            if (isAbove) {
                if (pillCenterX - pillHalfW < lastTopRightX + minGap) {
                    val shifted = (lastTopRightX + minGap + pillHalfW)
                    if (shifted + pillHalfW <= trackRight) {
                        pillCenterX = shifted
                    } else {
                        pillCenterY = (pillCenterY - 12f * density).coerceAtLeast(9f * density)
                    }
                }
                lastTopRightX = pillCenterX + pillHalfW
            } else {
                if (pillCenterX - pillHalfW < lastBottomRightX + minGap) {
                    val shifted = (lastBottomRightX + minGap + pillHalfW)
                    if (shifted + pillHalfW <= trackRight) {
                        pillCenterX = shifted
                    } else {
                        pillCenterY = (pillCenterY + 12f * density).coerceAtMost(h - 9f * density)
                    }
                }
                lastBottomRightX = pillCenterX + pillHalfW
            }

            pillRect.set(
                pillCenterX - pillHalfW,
                pillCenterY - pillHalfH,
                pillCenterX + pillHalfW,
                pillCenterY + pillHalfH
            )

            // Connect pill to node center when shifted horizontally to preserve crystal-clear association
            val pillAttachY = if (isAbove) pillRect.bottom else pillRect.top
            val nodeEdgeY = if (isAbove) nodeY - dotR else nodeY + dotR
            durationLinePaint.color = Color.argb(125, Color.red(nodeColor), Color.green(nodeColor), Color.blue(nodeColor))
            durationLinePaint.strokeWidth = 1.1f * density
            canvas.drawLine(startX, nodeEdgeY, pillCenterX, pillAttachY, durationLinePaint)

            nodePillPaint.color = themePalette.surfaceColor
            nodePillStrokePaint.color = Color.argb(155, Color.red(nodeColor), Color.green(nodeColor), Color.blue(nodeColor))
            canvas.drawRoundRect(pillRect, pillHalfH, pillHalfH, nodePillPaint)
            canvas.drawRoundRect(pillRect, pillHalfH, pillHalfH, nodePillStrokePaint)

            val fm = nodeNamePaint.fontMetrics
            val textBaseline = pillCenterY - (fm.ascent + fm.descent) / 2f
            nodeNamePaint.color = themePalette.textPrimary
            canvas.drawText(labelText, pillCenterX, textBaseline, nodeNamePaint)

            if (node.isCurrentlyActiveWindow && themePalette.themeId == CyberThemeId.PRINCE_OF_PERSIA_SANDS) {
                val sparkOrbit = (9f + 3f * pulsePhase) * density
                nodeDotPaint.color = themePalette.primaryNeon
                canvas.drawCircle(startX - sparkOrbit, nodeY - 2f * density, 1.5f * density, nodeDotPaint)
                canvas.drawCircle(startX + sparkOrbit, nodeY + 2f * density, 1.5f * density, nodeDotPaint)
            }

            renderedHits.add(RenderedNodeHit(node, startX, nodeY))
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val distMoved = hypot(event.x - downX, event.y - downY)
                val touchRadius = 28f * resources.displayMetrics.density
                if (distMoved <= touchRadius) {
                    val hit = renderedHits.minByOrNull { hit ->
                        hypot(event.x - hit.cx, event.y - hit.cy)
                    }
                    if (hit != null && hypot(event.x - hit.cx, event.y - hit.cy) <= 34f * resources.displayMetrics.density) {
                        CyberFeedbackManager.getInstance(context).playBeepProgress()
                        performClick()
                        onNodeSelected?.invoke(hit.node)
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}

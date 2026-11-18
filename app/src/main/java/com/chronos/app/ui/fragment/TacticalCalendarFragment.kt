package com.chronos.app.ui.fragment

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chronos.app.R
import com.chronos.app.data.local.HabitCycleState
import com.chronos.app.data.local.PerformanceCategory
import com.chronos.app.databinding.FragmentTacticalCalendarBinding
import com.chronos.app.domain.CalendarDayCell
import com.chronos.app.domain.NeuralStatsCalculator
import com.chronos.app.domain.TacticalCalendarSnapshot
import com.chronos.app.feedback.CyberFeedbackManager
import com.chronos.app.theme.ResolvedCyberPalette
import com.chronos.app.ui.custom.CinematicVfxHelper
import com.chronos.app.ui.custom.NeuralPulseChartView
import com.chronos.app.ui.viewmodel.ChronosUiState
import com.chronos.app.ui.viewmodel.ChronosViewModel
import kotlinx.coroutines.launch

class TacticalCalendarFragment : Fragment() {

    private var _binding: FragmentTacticalCalendarBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ChronosViewModel
    private var lastPaletteApplied: ResolvedCyberPalette? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTacticalCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[ChronosViewModel::class.java]

        buildWeekdayHeaders()

        binding.btnPrevMonth.setOnClickListener {
            CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
            viewModel.shiftCalendarMonth(-1)
        }

        binding.btnNextMonth.setOnClickListener {
            CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
            viewModel.shiftCalendarMonth(1)
        }

        binding.btnCalendarToday.setOnClickListener {
            CyberFeedbackManager.getInstance(requireContext()).playBeepProgress()
            CinematicVfxHelper.playTacticalPressBurst(it)
            viewModel.selectCalendarDate(NeuralStatsCalculator.formatDateKey())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderCalendarState(state)
                }
            }
        }
    }

    private fun buildWeekdayHeaders() {
        val row = binding.calendarWeekdayRow
        row.removeAllViews()
        val mono = ResourcesCompat.getFont(requireContext(), R.font.jetbrains_mono)
        val labels = listOf("L", "M", "X", "J", "V", "S", "D")
        labels.forEach { day ->
            val tv = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.CENTER
                text = day
                typeface = mono
                textSize = 10.5f
            }
            row.addView(tv)
        }
    }

    private fun renderCalendarState(state: ChronosUiState) {
        val palette = state.palette
        val snapshot = state.tacticalCalendar

        if (lastPaletteApplied != palette) {
            lastPaletteApplied = palette
            applyPalette(palette)
        }

        binding.tvCalendarMonthTitle.text = snapshot.monthTitle
        renderMonthGrid(snapshot, palette)
        renderWeeklyBreakdown(snapshot, palette)
        renderSelectedDayAudit(snapshot, palette)
    }

    private fun renderMonthGrid(snapshot: TacticalCalendarSnapshot, palette: ResolvedCyberPalette) {
        val grid = binding.calendarGridContainer
        grid.removeAllViews()
        val density = resources.displayMetrics.density
        val mono = ResourcesCompat.getFont(requireContext(), R.font.jetbrains_mono)

        snapshot.dayCells.chunked(7).forEach { weekCells ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (4f * density).toInt()
                }
            }

            weekCells.forEach { cell ->
                row.addView(buildDayCellView(cell, cell.dateKey == snapshot.selectedDateKey, palette, mono, density))
            }
            grid.addView(row)
        }
    }

    private fun buildDayCellView(
        cell: CalendarDayCell,
        isSelected: Boolean,
        palette: ResolvedCyberPalette,
        mono: android.graphics.Typeface?,
        density: Float
    ): View {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = (48f * density).toInt()
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (2f * density).toInt()
                marginEnd = (2f * density).toInt()
            }
            setPadding(
                (2f * density).toInt(),
                (5f * density).toInt(),
                (2f * density).toInt(),
                (5f * density).toInt()
            )
            alpha = if (cell.isInDisplayedMonth) 1f else 0.32f
            isClickable = true
            isFocusable = true
            contentDescription = "Día ${cell.dayOfMonth}, cumplimiento ${cell.overallScorePercent} por ciento"

            val bg = GradientDrawable().apply {
                cornerRadius = 12f * density
                setColor(
                    when {
                        isSelected -> Color.argb(
                            if (palette.isDark) 52 else 36,
                            Color.red(palette.primaryNeon),
                            Color.green(palette.primaryNeon),
                            Color.blue(palette.primaryNeon)
                        )
                        cell.isToday -> palette.capsuleFillColor
                        else -> Color.TRANSPARENT
                    }
                )
                if (isSelected || cell.isToday) {
                    setStroke(
                        (1.2f * density).toInt(),
                        if (isSelected) palette.primaryNeon else palette.secondaryNeon
                    )
                }
            }
            background = bg

            setOnClickListener {
                CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
                viewModel.selectCalendarDate(cell.dateKey)
            }
        }

        val tvDay = TextView(requireContext()).apply {
            text = cell.dayOfMonth.toString()
            typeface = mono
            textSize = 12f
            setTextColor(
                when {
                    isSelected -> palette.primaryNeon
                    cell.isToday -> palette.secondaryNeon
                    else -> palette.textPrimary
                }
            )
            if (isSelected || cell.isToday) {
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
        }

        // Mini dimension compliance dots
        val dotsRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (3f * density).toInt()
            }
        }

        PerformanceCategory.entries.forEach { dim ->
            val active = cell.activeDimensionSet.contains(dim)
            val dot = View(requireContext()).apply {
                val dotSize = (4.5f * density).toInt()
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    marginStart = (1.5f * density).toInt()
                    marginEnd = (1.5f * density).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(
                        if (active) {
                            NeuralPulseChartView.colorForCategory(dim, palette)
                        } else if (cell.incompleteCount > 0 && dim == PerformanceCategory.PHYSICAL) {
                            palette.tertiaryNeon
                        } else {
                            Color.argb(40, Color.red(palette.textMuted), Color.green(palette.textMuted), Color.blue(palette.textMuted))
                        }
                    )
                }
            }
            dotsRow.addView(dot)
        }

        container.addView(tvDay)
        container.addView(dotsRow)
        return container
    }

    private fun renderWeeklyBreakdown(snapshot: TacticalCalendarSnapshot, palette: ResolvedCyberPalette) {
        binding.tvWeeklyComplianceBadge.text = "${snapshot.weeklyCompliancePercent}% SEMANA"
        binding.tvWeeklyComplianceBadge.setTextColor(palette.primaryNeon)
        binding.tvWeeklyComplianceBadge.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)

        val container = binding.weeklyDimensionAuditContainer
        container.removeAllViews()
        val density = resources.displayMetrics.density
        val mono = ResourcesCompat.getFont(requireContext(), R.font.jetbrains_mono)
        val orbitron = ResourcesCompat.getFont(requireContext(), R.font.orbitron)

        snapshot.weeklyDimensionBreakdown.forEach { audit ->
            val catColor = NeuralPulseChartView.colorForCategory(audit.category, palette)
            val rowWrapper = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (10f * density).toInt()
                }
            }

            val topRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val tvLabel = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = audit.category.title.uppercase()
                typeface = orbitron
                textSize = 11.5f
                setTextColor(palette.textPrimary)
            }

            val focusStr = NeuralStatsCalculator.formatMinutesCompact(audit.focusMinutes)
            val tvSummary = TextView(requireContext()).apply {
                text = "$focusStr · ${audit.completedHabits}✔/${audit.incompleteHabits}✕ · ${audit.compliancePercent}%"
                typeface = mono
                textSize = 10.5f
                setTextColor(catColor)
            }

            topRow.addView(tvLabel)
            topRow.addView(tvSummary)

            val bar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (5f * density).toInt()
                ).apply {
                    topMargin = (4f * density).toInt()
                }
                max = 100
                progress = audit.compliancePercent
                progressTintList = ColorStateList.valueOf(catColor)
                progressBackgroundTintList = ColorStateList.valueOf(palette.surfaceElevatedColor)
            }

            rowWrapper.addView(topRow)
            rowWrapper.addView(bar)
            container.addView(rowWrapper)
        }
    }

    private fun renderSelectedDayAudit(snapshot: TacticalCalendarSnapshot, palette: ResolvedCyberPalette) {
        val dayAudit = snapshot.selectedDayAudit
        binding.tvSelectedDayTitle.text = "AUDITORÍA: ${dayAudit.formattedDateLabel.uppercase()}"
        binding.tvSelectedDayTitle.setTextColor(palette.textPrimary)

        val focusCompact = NeuralStatsCalculator.formatMinutesCompact(dayAudit.totalFocusMinutes)
        val completed = dayAudit.habitAudits.count { it.parsedHabitState() == HabitCycleState.COMPLETED }
        val incomplete = dayAudit.habitAudits.count { it.parsedHabitState() == HabitCycleState.INCOMPLETE }
        binding.tvSelectedDaySummary.text = "$focusCompact enfoque · $completed completados · $incomplete incompletos"
        binding.tvSelectedDaySummary.setTextColor(palette.textSecondary)

        binding.tvSelectedDayScore.text = "${dayAudit.overallCompliancePercent}%"
        binding.tvSelectedDayScore.setTextColor(palette.secondaryNeon)
        binding.tvSelectedDayScore.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)

        val listContainer = binding.dayHabitAuditListContainer
        listContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val mono = ResourcesCompat.getFont(requireContext(), R.font.jetbrains_mono)
        val rajdhani = ResourcesCompat.getFont(requireContext(), R.font.rajdhani)

        if (dayAudit.habitAudits.isEmpty() && dayAudit.totalFocusMinutes <= 0f) {
            val emptyTv = TextView(requireContext()).apply {
                text = "Sin registros de hábitos o enfoque en esta fecha.\nMarca nodos en el sistema para auditar tu progreso."
                typeface = rajdhani
                textSize = 13.5f
                gravity = Gravity.CENTER
                setPadding(0, (12f * density).toInt(), 0, (6f * density).toInt())
                setTextColor(palette.textMuted)
            }
            listContainer.addView(emptyTv)
            return
        }

        // Dimension pills summary for selected day
        dayAudit.dimensions.filter { it.focusMinutes > 0f || it.completedHabits > 0 || it.incompleteHabits > 0 }
            .forEach { dimAudit ->
                val catColor = NeuralPulseChartView.colorForCategory(dimAudit.category, palette)
                val dimTv = TextView(requireContext()).apply {
                    text = "[${dimAudit.category.title.uppercase()}] ${NeuralStatsCalculator.formatMinutesCompact(dimAudit.focusMinutes)} · ${dimAudit.completedHabits} completado(s)"
                    typeface = mono
                    textSize = 10.5f
                    setTextColor(catColor)
                    setPadding(0, 0, 0, (4f * density).toInt())
                }
                listContainer.addView(dimTv)
            }

        dayAudit.habitAudits.forEach { audit ->
            val hState = audit.parsedHabitState()
            val (iconRes, stateTint, statusTag) = when (hState) {
                HabitCycleState.COMPLETED -> Triple(R.drawable.ic_habit_check, palette.primaryNeon, "COMPLETADO")
                HabitCycleState.INCOMPLETE -> Triple(R.drawable.ic_habit_incomplete, palette.tertiaryNeon, "INCOMPLETO")
                HabitCycleState.NONE -> Triple(R.drawable.ic_habit_none, palette.textMuted, "SIN MARCAR")
            }

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    (10f * density).toInt(),
                    (8f * density).toInt(),
                    (10f * density).toInt(),
                    (8f * density).toInt()
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (6f * density).toInt()
                }
                background = GradientDrawable().apply {
                    cornerRadius = 12f * density
                    setColor(palette.backgroundColor)
                    setStroke((1f * density).toInt(), palette.subtleBorderColor)
                }
            }

            val iv = ImageView(requireContext()).apply {
                val sz = (18f * density).toInt()
                layoutParams = LinearLayout.LayoutParams(sz, sz)
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(stateTint)
                contentDescription = statusTag
            }

            val tvName = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (10f * density).toInt()
                }
                text = "${audit.timerName} · ${audit.parsedDimension().title.uppercase()}"
                typeface = rajdhani
                textSize = 14f
                setTextColor(palette.textPrimary)
            }

            val tvTag = TextView(requireContext()).apply {
                text = statusTag
                typeface = mono
                textSize = 9.5f
                setTextColor(stateTint)
            }

            row.addView(iv)
            row.addView(tvName)
            row.addView(tvTag)
            listContainer.addView(row)
        }
    }

    private fun applyPalette(palette: ResolvedCyberPalette) {
        binding.tvCalendarTag.setTextColor(palette.primaryNeon)
        binding.tvCalendarTag.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        binding.tvCalendarTitle.setTextColor(palette.textPrimary)
        binding.tvCalendarSubtitle.setTextColor(palette.textSecondary)

        binding.btnCalendarToday.setTextColor(palette.primaryNeon)
        binding.btnCalendarToday.strokeColor = ColorStateList.valueOf(palette.primaryNeon)
        binding.btnPrevMonth.imageTintList = ColorStateList.valueOf(palette.primaryNeon)
        binding.btnNextMonth.imageTintList = ColorStateList.valueOf(palette.primaryNeon)
        binding.tvCalendarMonthTitle.setTextColor(palette.textPrimary)

        for (i in 0 until binding.calendarWeekdayRow.childCount) {
            (binding.calendarWeekdayRow.getChildAt(i) as? TextView)?.setTextColor(palette.textMuted)
        }

        binding.cardCalendarGrid.setCardBackgroundColor(palette.glassSurfaceColor)
        CinematicVfxHelper.attachCardCinematicPulse(binding.cardCalendarGrid, palette, palette.rimHighlightColor)

        binding.cardWeeklyAudit.setCardBackgroundColor(palette.glassSurfaceColor)
        binding.cardWeeklyAudit.strokeColor = palette.subtleBorderColor
        binding.tvWeeklyAuditHeader.setTextColor(palette.textPrimary)

        binding.cardDayDetailAudit.setCardBackgroundColor(palette.glassSurfaceColor)
        CinematicVfxHelper.attachCardCinematicPulse(binding.cardDayDetailAudit, palette, palette.rimHighlightColor)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

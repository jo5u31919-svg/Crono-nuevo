package com.chronos.app.domain

import com.chronos.app.data.local.CustomTimerEntity
import com.chronos.app.data.local.DailyHabitAuditEntity
import com.chronos.app.data.local.DailyNeuralStatEntity
import com.chronos.app.data.local.HabitCycleState
import com.chronos.app.data.local.PerformanceCategory
import java.util.Calendar
import java.util.Locale

data class DimensionComplianceAudit(
    val category: PerformanceCategory,
    val focusMinutes: Float,
    val completedHabits: Int,
    val incompleteHabits: Int,
    val scheduledHabits: Int
) {
    val compliancePercent: Int
        get() {
            val totalTracked = completedHabits + incompleteHabits
            return when {
                totalTracked > 0 -> ((completedHabits.toFloat() / totalTracked.toFloat()) * 100f).toInt().coerceIn(0, 100)
                focusMinutes > 0f -> 100
                else -> 0
            }
        }
}

data class CalendarDayCell(
    val dateKey: String,
    val dayOfMonth: Int,
    val isInDisplayedMonth: Boolean,
    val isToday: Boolean,
    val totalFocusMinutes: Float,
    val completedCount: Int,
    val incompleteCount: Int,
    val activeDimensionSet: Set<PerformanceCategory>,
    val overallScorePercent: Int
)

data class CalendarDayDetailAudit(
    val dateKey: String,
    val formattedDateLabel: String,
    val totalFocusMinutes: Float,
    val overallCompliancePercent: Int,
    val dimensions: List<DimensionComplianceAudit>,
    val habitAudits: List<DailyHabitAuditEntity>
)

data class TacticalCalendarSnapshot(
    val year: Int,
    val monthZeroBased: Int,
    val monthTitle: String,
    val dayCells: List<CalendarDayCell>,
    val selectedDateKey: String,
    val selectedDayAudit: CalendarDayDetailAudit,
    val weeklyCompliancePercent: Int,
    val weeklyDimensionBreakdown: List<DimensionComplianceAudit>
)

object TacticalCalendarCalculator {

    private val spanishMonths = listOf(
        "ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO",
        "JULIO", "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE"
    )

    private val spanishShortDays = listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")

    fun computeMonthSnapshot(
        year: Int,
        monthZeroBased: Int,
        selectedDateKey: String,
        persistedStats: List<DailyNeuralStatEntity>,
        persistedAudits: List<DailyHabitAuditEntity>,
        customTimers: List<CustomTimerEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): TacticalCalendarSnapshot {
        val todayKey = NeuralStatsCalculator.formatDateKey(nowMillis)
        val statsByDate = persistedStats.groupBy { it.dateKey }
        val auditsByDate = persistedAudits.groupBy { it.dateKey }

        val monthStart = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Monday-first grid offset (0..6)
        val leadingEmptyDays = when (monthStart.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            else -> 6
        }

        val gridCursor = (monthStart.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -leadingEmptyDays)
        }

        val totalCells = 42
        val cells = ArrayList<CalendarDayCell>(totalCells)
        repeat(totalCells) {
            val key = NeuralStatsCalculator.formatDateKey(gridCursor)
            val inMonth = gridCursor.get(Calendar.MONTH) == monthZeroBased &&
                gridCursor.get(Calendar.YEAR) == year
            val dayStats = statsByDate[key].orEmpty()
            val dayAudits = effectiveAuditsForDate(key, todayKey, auditsByDate[key].orEmpty(), customTimers)

            val focusMinutes = dayStats.sumOf { it.focusSeconds } / 60f
            val completedCount = dayAudits.count { it.parsedHabitState() == HabitCycleState.COMPLETED }
            val incompleteCount = dayAudits.count { it.parsedHabitState() == HabitCycleState.INCOMPLETE }

            val activeDims = mutableSetOf<PerformanceCategory>()
            dayStats.filter { it.focusSeconds > 0L }.forEach {
                activeDims.add(PerformanceCategory.fromName(it.categoryName))
            }
            dayAudits.filter { it.parsedHabitState() == HabitCycleState.COMPLETED }.forEach {
                activeDims.add(it.parsedDimension())
            }

            val score = computeDayScore(focusMinutes, completedCount, incompleteCount)
            cells.add(
                CalendarDayCell(
                    dateKey = key,
                    dayOfMonth = gridCursor.get(Calendar.DAY_OF_MONTH),
                    isInDisplayedMonth = inMonth,
                    isToday = key == todayKey,
                    totalFocusMinutes = focusMinutes,
                    completedCount = completedCount,
                    incompleteCount = incompleteCount,
                    activeDimensionSet = activeDims,
                    overallScorePercent = score
                )
            )
            gridCursor.add(Calendar.DAY_OF_YEAR, 1)
        }

        val resolvedSelectedKey = selectedDateKey.ifBlank { todayKey }
        val selectedDayAudit = buildDayAudit(
            dateKey = resolvedSelectedKey,
            todayKey = todayKey,
            dayStats = statsByDate[resolvedSelectedKey].orEmpty(),
            rawDayAudits = auditsByDate[resolvedSelectedKey].orEmpty(),
            customTimers = customTimers
        )

        // Weekly compliance across the 7-day Monday..Sunday week containing selectedDateKey
        val weekDates = buildWeekDateKeys(resolvedSelectedKey)
        val weeklyBreakdown = PerformanceCategory.entries.map { category ->
            var weekSec = 0L
            var weekCompleted = 0
            var weekIncomplete = 0
            var weekScheduled = 0
            weekDates.forEach { dKey ->
                val dStats = statsByDate[dKey].orEmpty()
                weekSec += dStats.filter { it.categoryName == category.name }.sumOf { it.focusSeconds }

                val dAudits = effectiveAuditsForDate(dKey, todayKey, auditsByDate[dKey].orEmpty(), customTimers)
                    .filter { it.parsedDimension() == category }
                weekCompleted += dAudits.count { it.parsedHabitState() == HabitCycleState.COMPLETED }
                weekIncomplete += dAudits.count { it.parsedHabitState() == HabitCycleState.INCOMPLETE }
                if (dKey == todayKey) {
                    val dayIdx = mondayIndexFromDateKey(dKey)
                    weekScheduled += customTimers.count {
                        it.parsedDimension() == category && it.isDayActive(dayIdx)
                    }
                }
            }
            DimensionComplianceAudit(
                category = category,
                focusMinutes = weekSec / 60f,
                completedHabits = weekCompleted,
                incompleteHabits = weekIncomplete,
                scheduledHabits = weekScheduled
            )
        }

        val weekTotalCompleted = weeklyBreakdown.sumOf { it.completedHabits }
        val weekTotalIncomplete = weeklyBreakdown.sumOf { it.incompleteHabits }
        val weekTotalFocus = weeklyBreakdown.sumOf { it.focusMinutes.toDouble() }.toFloat()
        val weeklyCompliance = computeDayScore(weekTotalFocus, weekTotalCompleted, weekTotalIncomplete)

        val monthTitle = "${spanishMonths[monthZeroBased.coerceIn(0, 11)]} $year"

        return TacticalCalendarSnapshot(
            year = year,
            monthZeroBased = monthZeroBased,
            monthTitle = monthTitle,
            dayCells = cells,
            selectedDateKey = resolvedSelectedKey,
            selectedDayAudit = selectedDayAudit,
            weeklyCompliancePercent = weeklyCompliance,
            weeklyDimensionBreakdown = weeklyBreakdown
        )
    }

    private fun effectiveAuditsForDate(
        dateKey: String,
        todayKey: String,
        persistedAudits: List<DailyHabitAuditEntity>,
        customTimers: List<CustomTimerEntity>
    ): List<DailyHabitAuditEntity> {
        if (dateKey != todayKey) return persistedAudits
        val byTimerId = persistedAudits.associateBy { it.timerId }.toMutableMap()
        val todayIndex = mondayIndexFromDateKey(dateKey)
        customTimers.filter { it.isDayActive(todayIndex) }.forEach { timer ->
            if (!byTimerId.containsKey(timer.id) && timer.parsedHabitState() != HabitCycleState.NONE) {
                byTimerId[timer.id] = DailyHabitAuditEntity(
                    dateKey = dateKey,
                    timerId = timer.id,
                    dimensionName = timer.parsedDimension().name,
                    timerName = timer.name,
                    habitState = timer.habitState,
                    updatedAtMs = timer.lastTickEpochMs
                )
            }
        }
        return byTimerId.values.sortedBy { it.timerId }
    }

    private fun buildDayAudit(
        dateKey: String,
        todayKey: String,
        dayStats: List<DailyNeuralStatEntity>,
        rawDayAudits: List<DailyHabitAuditEntity>,
        customTimers: List<CustomTimerEntity>
    ): CalendarDayDetailAudit {
        val audits = effectiveAuditsForDate(dateKey, todayKey, rawDayAudits, customTimers)
        val dayIndex = mondayIndexFromDateKey(dateKey)

        val dimensions = PerformanceCategory.entries.map { category ->
            val focusSec = dayStats.filter { it.categoryName == category.name }.sumOf { it.focusSeconds }
            val catAudits = audits.filter { it.parsedDimension() == category }
            val scheduled = if (dateKey == todayKey) {
                customTimers.count { it.parsedDimension() == category && it.isDayActive(dayIndex) }
            } else {
                catAudits.size
            }
            DimensionComplianceAudit(
                category = category,
                focusMinutes = focusSec / 60f,
                completedHabits = catAudits.count { it.parsedHabitState() == HabitCycleState.COMPLETED },
                incompleteHabits = catAudits.count { it.parsedHabitState() == HabitCycleState.INCOMPLETE },
                scheduledHabits = scheduled
            )
        }

        val totalFocus = dayStats.sumOf { it.focusSeconds } / 60f
        val totalCompleted = audits.count { it.parsedHabitState() == HabitCycleState.COMPLETED }
        val totalIncomplete = audits.count { it.parsedHabitState() == HabitCycleState.INCOMPLETE }

        return CalendarDayDetailAudit(
            dateKey = dateKey,
            formattedDateLabel = formatPrettyDate(dateKey),
            totalFocusMinutes = totalFocus,
            overallCompliancePercent = computeDayScore(totalFocus, totalCompleted, totalIncomplete),
            dimensions = dimensions,
            habitAudits = audits
        )
    }

    private fun computeDayScore(
        focusMinutes: Float,
        completedHabits: Int,
        incompleteHabits: Int
    ): Int {
        val totalHabits = completedHabits + incompleteHabits
        return when {
            totalHabits > 0 -> ((completedHabits.toFloat() / totalHabits.toFloat()) * 100f).toInt().coerceIn(0, 100)
            focusMinutes >= 30f -> 100
            focusMinutes > 0f -> ((focusMinutes / 30f) * 100f).toInt().coerceIn(15, 99)
            else -> 0
        }
    }

    private fun buildWeekDateKeys(anchorDateKey: String): List<String> {
        val cal = parseDateKey(anchorDateKey)
        val offset = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            else -> 6
        }
        cal.add(Calendar.DAY_OF_YEAR, -offset)
        val keys = ArrayList<String>(7)
        repeat(7) {
            keys.add(NeuralStatsCalculator.formatDateKey(cal))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return keys
    }

    private fun mondayIndexFromDateKey(dateKey: String): Int {
        val cal = parseDateKey(dateKey)
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            else -> 6
        }
    }

    private fun formatPrettyDate(dateKey: String): String {
        val cal = parseDateKey(dateKey)
        val dow = spanishShortDays[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val dom = cal.get(Calendar.DAY_OF_MONTH)
        val month = spanishMonths[cal.get(Calendar.MONTH)].lowercase(Locale.ROOT)
            .replaceFirstChar { it.titlecase(Locale.ROOT) }
        return "$dow $dom $month"
    }

    fun parseDateKey(dateKey: String): Calendar {
        val parts = dateKey.split("-")
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (parts.size == 3) {
            val y = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            val d = parts[2].toIntOrNull()
            if (y != null && m != null && d != null) {
                cal.set(Calendar.YEAR, y)
                cal.set(Calendar.MONTH, (m - 1).coerceIn(0, 11))
                cal.set(Calendar.DAY_OF_MONTH, d.coerceIn(1, 31))
            }
        }
        return cal
    }
}

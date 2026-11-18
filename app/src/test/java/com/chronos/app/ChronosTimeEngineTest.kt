package com.chronos.app

import com.chronos.app.data.local.CustomTimerEntity
import com.chronos.app.data.local.DailyHabitAuditEntity
import com.chronos.app.data.local.DailyNeuralStatEntity
import com.chronos.app.data.local.DashboardSection
import com.chronos.app.data.local.HabitCycleState
import com.chronos.app.data.local.PerformanceCategory
import com.chronos.app.data.local.ResetPeriod
import com.chronos.app.domain.ChronosTimeEngine
import com.chronos.app.domain.DayPhase
import com.chronos.app.domain.NeuralStatsCalculator
import com.chronos.app.domain.ProjectedTimelineNode
import com.chronos.app.domain.StatsPeriod
import com.chronos.app.domain.TacticalCalendarCalculator
import com.chronos.app.theme.CyberThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ChronosTimeEngineTest {

    @Test
    fun `formatDurationHms formats hours minutes seconds properly`() {
        assertEquals("01:05:09", ChronosTimeEngine.formatDurationHms(3909L))
        assertEquals("00:00:00", ChronosTimeEngine.formatDurationHms(0L))
    }

    @Test
    fun `currentDayPhase matches hour of day`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 30)
        }
        assertEquals(DayPhase.AFTERNOON, ChronosTimeEngine.currentDayPhase(cal.timeInMillis))
    }

    @Test
    fun `projectTimersOnDayBar sorts nodes chronologically`() {
        val t1 = CustomTimerEntity(
            id = 1L,
            name = "Tarde",
            startMinuteOfDay = 15 * 60,
            durationMinutes = 30,
            activeDaysMask = 0b1111111,
            resetPeriod = ResetPeriod.DAILY.name
        )
        val t2 = CustomTimerEntity(
            id = 2L,
            name = "Mañana",
            startMinuteOfDay = 8 * 60,
            durationMinutes = 45,
            activeDaysMask = 0b1111111,
            resetPeriod = ResetPeriod.DAILY.name
        )
        val projected = ChronosTimeEngine.projectTimersOnDayBar(listOf(t1, t2))
        assertEquals(2, projected.size)
        assertEquals("Mañana", projected[0].timer.name)
        assertEquals("Tarde", projected[1].timer.name)
        assertTrue(projected[0].startFraction < projected[1].startFraction)
    }

    @Test
    fun `formatNodeActivationStatus enforces strict inactive and active labels`() {
        val timer = CustomTimerEntity(
            id = 5L,
            name = "Lectura",
            startMinuteOfDay = 7 * 60 + 30,
            durationMinutes = 30,
            activeDaysMask = 0b1111111,
            resetPeriod = ResetPeriod.DAILY.name
        )
        val inactiveNode = ProjectedTimelineNode(
            timer = timer,
            startFraction = 0.3f,
            durationFraction = 0.02f,
            isCurrentlyActiveWindow = false,
            liveRemainingSeconds = 1800L,
            activationMinuteOfDay = 7 * 60 + 30
        )
        val activeNode = inactiveNode.copy(isCurrentlyActiveWindow = true)

        assertEquals("> SE ACTIVA EN: 07:30", ChronosTimeEngine.formatNodeActivationStatus(inactiveNode))
        assertEquals("> CONTEO ACTIVADO", ChronosTimeEngine.formatNodeActivationStatus(activeNode))
    }

    @Test
    fun `habit cycle state transitions cleanly across 3 states`() {
        var state = HabitCycleState.NONE
        state = state.next()
        assertEquals(HabitCycleState.COMPLETED, state)
        state = state.next()
        assertEquals(HabitCycleState.INCOMPLETE, state)
        state = state.next()
        assertEquals(HabitCycleState.NONE, state)
    }

    @Test
    fun `theme resolves distinct Day and Night palettes including Prince of Persia`() {
        val night = CyberThemeId.CYBERPUNK_CLASSIC.resolve(isDark = true)
        val day = CyberThemeId.CYBERPUNK_CLASSIC.resolve(isDark = false)

        assertTrue(night.isDark)
        assertEquals(0xFF0A0A12.toInt(), night.backgroundColor)
        assertEquals(0xFF00F0FF.toInt(), night.primaryNeon)
        assertEquals(0xFFFF0055.toInt(), night.secondaryNeon)

        assertFalse(day.isDark)
        assertEquals(0xFFF2F4F8.toInt(), day.backgroundColor)
        assertEquals(0xFF1A1C23.toInt(), day.textPrimary)
        assertEquals(0xFF007ACC.toInt(), day.primaryNeon)
        assertEquals(0xFFD60044.toInt(), day.secondaryNeon)

        val popNight = CyberThemeId.PRINCE_OF_PERSIA_SANDS.resolve(isDark = true)
        val popDay = CyberThemeId.PRINCE_OF_PERSIA_SANDS.resolve(isDark = false)
        assertEquals(0xFFE6B84D.toInt(), popNight.primaryNeon)
        assertEquals(0xFF2997FF.toInt(), popNight.secondaryNeon)
        assertEquals(0xFFFF1E56.toInt(), popNight.tertiaryNeon)
        assertFalse(popDay.isDark)
        assertEquals(0xFFF7F3EA.toInt(), popDay.backgroundColor)
    }

    @Test
    fun `dashboard section order parse and serialize preserves all sections`() {
        val custom = listOf(
            DashboardSection.PERFORMANCE_DIMENSIONS,
            DashboardSection.DAY_RESET_LASER,
            DashboardSection.MOTIVATIONAL_QUOTES,
            DashboardSection.OVERCLOCK_LAUNCHER,
            DashboardSection.CUSTOM_NODES
        )
        val serialized = DashboardSection.serializeOrder(custom)
        val parsed = DashboardSection.parseOrder(serialized)
        assertEquals(custom, parsed)

        val partialParsed = DashboardSection.parseOrder("dimensions,unknown")
        assertEquals(DashboardSection.entries.size, partialParsed.size)
        assertEquals(DashboardSection.PERFORMANCE_DIMENSIONS, partialParsed.first())
    }

    @Test
    fun `neural stats calculator computes dominant category totals and active streak`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 17, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayKey = NeuralStatsCalculator.formatDateKey(cal)
        val yesterdayCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayKey = NeuralStatsCalculator.formatDateKey(yesterdayCal)

        val stats = listOf(
            DailyNeuralStatEntity(
                dateKey = yesterdayKey,
                categoryName = PerformanceCategory.MENTAL.name,
                focusSeconds = 1800L,
                sessionsCount = 1
            ),
            DailyNeuralStatEntity(
                dateKey = todayKey,
                categoryName = PerformanceCategory.PHYSICAL.name,
                focusSeconds = 3600L,
                sessionsCount = 2
            ),
            DailyNeuralStatEntity(
                dateKey = todayKey,
                categoryName = PerformanceCategory.MENTAL.name,
                focusSeconds = 600L,
                sessionsCount = 1
            )
        )

        val summary = NeuralStatsCalculator.computeStats(
            period = StatsPeriod.LAST_7_DAYS,
            persistedStats = stats,
            performanceTimers = emptyList(),
            nowMillis = cal.timeInMillis
        )

        assertEquals(7, summary.days.size)
        assertEquals(100f, summary.totalFocusMinutes, 0.01f)
        assertEquals(PerformanceCategory.PHYSICAL, summary.dominantCategory)
        assertEquals(2, summary.activeStreakDays)
        assertTrue(summary.hasRecordedActivity)
    }

    @Test
    fun `tactical calendar calculator audits daily and weekly 4-dimension compliance`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayKey = NeuralStatsCalculator.formatDateKey(cal)
        val audits = listOf(
            DailyHabitAuditEntity(
                dateKey = todayKey,
                timerId = 1L,
                dimensionName = PerformanceCategory.PHYSICAL.name,
                timerName = "Sprint",
                habitState = HabitCycleState.COMPLETED.code
            ),
            DailyHabitAuditEntity(
                dateKey = todayKey,
                timerId = 2L,
                dimensionName = PerformanceCategory.OCCUPATIONAL.name,
                timerName = "Deep Code",
                habitState = HabitCycleState.INCOMPLETE.code
            )
        )

        val snapshot = TacticalCalendarCalculator.computeMonthSnapshot(
            year = 2026,
            monthZeroBased = Calendar.NOVEMBER,
            selectedDateKey = todayKey,
            persistedStats = emptyList(),
            persistedAudits = audits,
            customTimers = emptyList(),
            nowMillis = cal.timeInMillis
        )

        assertEquals(42, snapshot.dayCells.size)
        assertEquals(50, snapshot.selectedDayAudit.overallCompliancePercent)
        assertEquals(50, snapshot.weeklyCompliancePercent)
        assertEquals(4, snapshot.weeklyDimensionBreakdown.size)
    }

    @Test
    fun `motivational quote rotation interval is prolonged for calm reading`() {
        assertEquals(180_000L, com.chronos.app.ui.viewmodel.ChronosViewModel.QUOTE_ROTATION_INTERVAL_MS)
        assertTrue(com.chronos.app.ui.viewmodel.ChronosViewModel.QUOTE_ROTATION_INTERVAL_MS >= 120_000L)
    }
}

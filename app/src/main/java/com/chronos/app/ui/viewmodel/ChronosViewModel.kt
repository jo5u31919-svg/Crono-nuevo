package com.chronos.app.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.ChronosApplication
import com.chronos.app.data.local.CustomTimerEntity
import com.chronos.app.data.local.DailyHabitAuditEntity
import com.chronos.app.data.local.DailyNeuralStatEntity
import com.chronos.app.data.local.DashboardSection
import com.chronos.app.data.local.FeedbackSettings
import com.chronos.app.data.local.OverclockSummary
import com.chronos.app.data.local.OverlaySettings
import com.chronos.app.data.local.PerformanceCategory
import com.chronos.app.data.local.PerformanceTimerEntity
import com.chronos.app.data.local.ResetPeriod
import com.chronos.app.data.repository.MotivationalQuote
import com.chronos.app.domain.ChronosTimeEngine
import com.chronos.app.domain.DayPhase
import com.chronos.app.domain.NeuralStatsCalculator
import com.chronos.app.domain.NeuralStatsSummary
import com.chronos.app.domain.ProjectedTimelineNode
import com.chronos.app.domain.StatsPeriod
import com.chronos.app.domain.TacticalCalendarCalculator
import com.chronos.app.domain.TacticalCalendarSnapshot
import com.chronos.app.theme.CyberThemeId
import com.chronos.app.theme.ResolvedCyberPalette
import com.chronos.app.theme.ThemeAppearanceMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class PerformanceCardUiModel(
    val category: PerformanceCategory,
    val entity: PerformanceTimerEntity,
    val liveRemainingSeconds: Long,
    val progressFraction: Float,
    val anchoredNodes: List<ProjectedTimelineNode> = emptyList()
)

data class ChronosUiState(
    val nowMillis: Long = System.currentTimeMillis(),
    val dayResetFormatted: String = "00:00:00",
    val dayProgressFraction: Float = 0f,
    val dayPhase: DayPhase = DayPhase.NIGHT,
    val currentQuote: MotivationalQuote = MotivationalQuote("", "", ""),
    val quoteRotationProgress: Float = 0f,
    val performanceCards: List<PerformanceCardUiModel> = emptyList(),
    val projectedNodes: List<ProjectedTimelineNode> = emptyList(),
    val allCustomTimers: List<CustomTimerEntity> = emptyList(),
    val themeId: CyberThemeId = CyberThemeId.PRINCE_OF_PERSIA_SANDS,
    val appearanceMode: ThemeAppearanceMode = ThemeAppearanceMode.NIGHT,
    val palette: ResolvedCyberPalette = CyberThemeId.PRINCE_OF_PERSIA_SANDS.resolve(true),
    val overlaySettings: OverlaySettings = OverlaySettings(),
    val feedbackSettings: FeedbackSettings = FeedbackSettings(),
    val overclockSummary: OverclockSummary = OverclockSummary(),
    val dashboardOrder: List<DashboardSection> = DashboardSection.defaultOrder,
    val statsPeriod: StatsPeriod = StatsPeriod.THIS_WEEK,
    val highlightedStatsCategory: PerformanceCategory? = null,
    val neuralStats: NeuralStatsSummary = NeuralStatsCalculator.computeStats(
        period = StatsPeriod.THIS_WEEK,
        persistedStats = emptyList(),
        performanceTimers = emptyList()
    ),
    val tacticalCalendar: TacticalCalendarSnapshot = TacticalCalendarCalculator.computeMonthSnapshot(
        year = Calendar.getInstance().get(Calendar.YEAR),
        monthZeroBased = Calendar.getInstance().get(Calendar.MONTH),
        selectedDateKey = NeuralStatsCalculator.formatDateKey(),
        persistedStats = emptyList(),
        persistedAudits = emptyList(),
        customTimers = emptyList()
    )
)

class ChronosViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ChronosApplication
    private val repository = app.repository
    private val themeManager = app.themeManager
    private val settingsPrefs = app.settingsPrefs
    private val quotes = repository.getMotivationalQuotes()

    private val manualQuoteOffset = MutableStateFlow(0)
    private val quoteRotationAnchorMs = MutableStateFlow(System.currentTimeMillis())
    private val statsPeriodFlow = MutableStateFlow(StatsPeriod.THIS_WEEK)
    private val highlightedStatsCategoryFlow = MutableStateFlow<PerformanceCategory?>(null)

    private val initialCal = Calendar.getInstance()
    private val calendarYearMonthFlow = MutableStateFlow(
        initialCal.get(Calendar.YEAR) to initialCal.get(Calendar.MONTH)
    )
    private val selectedCalendarDateFlow = MutableStateFlow(
        NeuralStatsCalculator.formatDateKey(initialCal)
    )

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000L)
        }
    }

    private data class ThemeAndSettingsBundle(
        val themeId: CyberThemeId,
        val appearanceMode: ThemeAppearanceMode,
        val palette: ResolvedCyberPalette,
        val overlay: OverlaySettings,
        val feedback: FeedbackSettings,
        val quoteOffset: Int,
        val dashboardOrder: List<DashboardSection>
    )

    private data class StatsAndCalendarBundle(
        val statsList: List<DailyNeuralStatEntity>,
        val habitAudits: List<DailyHabitAuditEntity>,
        val period: StatsPeriod,
        val highlightedCategory: PerformanceCategory?,
        val yearMonth: Pair<Int, Int>,
        val selectedDateKey: String
    )

    private val themeBundleFlow = combine(
        themeManager.currentTheme,
        themeManager.appearanceMode,
        themeManager.resolvedPalette,
        settingsPrefs.overlaySettings,
        settingsPrefs.feedbackSettings,
        manualQuoteOffset,
        settingsPrefs.dashboardOrder
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        ThemeAndSettingsBundle(
            themeId = values[0] as CyberThemeId,
            appearanceMode = values[1] as ThemeAppearanceMode,
            palette = values[2] as ResolvedCyberPalette,
            overlay = values[3] as OverlaySettings,
            feedback = values[4] as FeedbackSettings,
            quoteOffset = values[5] as Int,
            dashboardOrder = values[6] as List<DashboardSection>
        )
    }

    private val auditsFlow = repository.dailyHabitAuditsFlow ?: flowOf(emptyList())

    private val statsAndCalendarFlow = combine(
        repository.dailyNeuralStatsFlow,
        auditsFlow,
        statsPeriodFlow,
        highlightedStatsCategoryFlow,
        calendarYearMonthFlow,
        selectedCalendarDateFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        StatsAndCalendarBundle(
            statsList = values[0] as List<DailyNeuralStatEntity>,
            habitAudits = values[1] as List<DailyHabitAuditEntity>,
            period = values[2] as StatsPeriod,
            highlightedCategory = values[3] as PerformanceCategory?,
            yearMonth = values[4] as Pair<Int, Int>,
            selectedDateKey = values[5] as String
        )
    }

    val uiState: StateFlow<ChronosUiState> = combine(
        tickerFlow,
        repository.performanceTimersFlow,
        repository.customTimersFlow,
        themeBundleFlow,
        statsAndCalendarFlow,
        settingsPrefs.overclockSummary
    ) { values ->
        val now = values[0] as Long
        @Suppress("UNCHECKED_CAST")
        val perfList = values[1] as List<PerformanceTimerEntity>
        @Suppress("UNCHECKED_CAST")
        val customList = values[2] as List<CustomTimerEntity>
        val themeBundle = values[3] as ThemeAndSettingsBundle
        val statsBundle = values[4] as StatsAndCalendarBundle
        val overclockSummary = values[5] as OverclockSummary

        val millisLeft = ChronosTimeEngine.millisUntilDayReset(now)
        val dayResetFormatted = ChronosTimeEngine.formatDurationHms(millisLeft / 1000L)
        val dayFrac = ChronosTimeEngine.dayProgressFraction(now)
        val phase = ChronosTimeEngine.currentDayPhase(now)

        val elapsedSinceAnchor = (now - quoteRotationAnchorMs.value).coerceAtLeast(0L)
        val autoSteps = (elapsedSinceAnchor / QUOTE_ROTATION_INTERVAL_MS).toInt()
        val autoIndex = (autoSteps + themeBundle.quoteOffset).let { idx ->
            ((idx % quotes.size) + quotes.size) % quotes.size
        }
        val quoteProgress = ((elapsedSinceAnchor % QUOTE_ROTATION_INTERVAL_MS).toFloat() / QUOTE_ROTATION_INTERVAL_MS.toFloat())
            .coerceIn(0f, 1f)

        val projected = ChronosTimeEngine.projectTimersOnDayBar(customList, now)
        val anchoredByDimension = projected.groupBy { it.timer.parsedDimension() }

        val perfMap = perfList.associateBy { it.categoryName }
        val perfModels = PerformanceCategory.entries.map { cat ->
            val entity = perfMap[cat.name] ?: PerformanceTimerEntity(
                categoryName = cat.name,
                totalSeconds = cat.defaultDurationMinutes * 60L,
                remainingSeconds = cat.defaultDurationMinutes * 60L
            )
            val remaining = ChronosTimeEngine.computeLivePerformanceRemaining(entity, now)
            val frac = if (entity.totalSeconds > 0L) {
                (remaining.toFloat() / entity.totalSeconds.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            PerformanceCardUiModel(
                category = cat,
                entity = entity,
                liveRemainingSeconds = remaining,
                progressFraction = frac,
                anchoredNodes = anchoredByDimension[cat].orEmpty()
            )
        }

        val neuralStats = NeuralStatsCalculator.computeStats(
            period = statsBundle.period,
            persistedStats = statsBundle.statsList,
            performanceTimers = perfList,
            nowMillis = now
        )

        val tacticalCalendar = TacticalCalendarCalculator.computeMonthSnapshot(
            year = statsBundle.yearMonth.first,
            monthZeroBased = statsBundle.yearMonth.second,
            selectedDateKey = statsBundle.selectedDateKey,
            persistedStats = statsBundle.statsList,
            persistedAudits = statsBundle.habitAudits,
            customTimers = customList,
            nowMillis = now
        )

        ChronosUiState(
            nowMillis = now,
            dayResetFormatted = dayResetFormatted,
            dayProgressFraction = dayFrac,
            dayPhase = phase,
            currentQuote = quotes[autoIndex],
            quoteRotationProgress = quoteProgress,
            performanceCards = perfModels,
            projectedNodes = projected,
            allCustomTimers = customList,
            themeId = themeBundle.themeId,
            appearanceMode = themeBundle.appearanceMode,
            palette = themeBundle.palette,
            overlaySettings = themeBundle.overlay,
            feedbackSettings = themeBundle.feedback,
            overclockSummary = overclockSummary,
            dashboardOrder = themeBundle.dashboardOrder,
            statsPeriod = statsBundle.period,
            highlightedStatsCategory = statsBundle.highlightedCategory,
            neuralStats = neuralStats,
            tacticalCalendar = tacticalCalendar
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ChronosUiState(
            currentQuote = quotes.first(),
            themeId = themeManager.currentTheme.value,
            appearanceMode = themeManager.appearanceMode.value,
            palette = themeManager.resolvedPalette.value,
            dashboardOrder = settingsPrefs.dashboardOrder.value
        )
    )

    fun rotateQuote(delta: Int = 1) {
        val elapsedSteps = ((System.currentTimeMillis() - quoteRotationAnchorMs.value).coerceAtLeast(0L) / QUOTE_ROTATION_INTERVAL_MS).toInt()
        manualQuoteOffset.value = manualQuoteOffset.value + elapsedSteps + delta
        quoteRotationAnchorMs.value = System.currentTimeMillis()
    }

    fun selectStatsPeriod(period: StatsPeriod) {
        statsPeriodFlow.value = period
    }

    fun selectHighlightedStatsCategory(category: PerformanceCategory?) {
        highlightedStatsCategoryFlow.value = category
    }

    fun shiftCalendarMonth(monthDelta: Int) {
        val (y, m) = calendarYearMonthFlow.value
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, monthDelta)
        }
        calendarYearMonthFlow.value = cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
    }

    fun selectCalendarDate(dateKey: String) {
        selectedCalendarDateFlow.value = dateKey
        val cal = TacticalCalendarCalculator.parseDateKey(dateKey)
        calendarYearMonthFlow.value = cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
    }

    fun togglePerformanceTimer(category: PerformanceCategory) {
        viewModelScope.launch {
            repository.togglePerformanceTimer(category)
        }
    }

    fun resetPerformanceTimer(category: PerformanceCategory) {
        viewModelScope.launch {
            repository.resetPerformanceTimer(category)
        }
    }

    fun updatePerformanceDuration(category: PerformanceCategory, durationMinutes: Int) {
        viewModelScope.launch {
            repository.updatePerformanceDuration(category, durationMinutes)
        }
    }

    fun saveCustomTimer(
        id: Long = 0L,
        name: String,
        startMinuteOfDay: Int,
        durationMinutes: Int,
        activeDaysMask: Int,
        resetPeriod: ResetPeriod,
        privateNotes: String,
        dimension: PerformanceCategory = PerformanceCategory.MENTAL
    ) {
        viewModelScope.launch {
            repository.createOrUpdateCustomTimer(
                id = id,
                name = name,
                startMinuteOfDay = startMinuteOfDay,
                durationMinutes = durationMinutes,
                activeDaysMask = activeDaysMask,
                resetPeriod = resetPeriod,
                privateNotes = privateNotes,
                dimension = dimension
            )
        }
    }

    fun cycleCustomTimerHabitState(id: Long) {
        viewModelScope.launch {
            repository.cycleCustomTimerHabitState(id)
        }
    }

    fun renameCustomTimer(id: Long, newName: String) {
        viewModelScope.launch {
            repository.renameCustomTimer(id, newName)
        }
    }

    fun updatePrivateNotes(id: Long, notes: String) {
        viewModelScope.launch {
            repository.updatePrivateNotes(id, notes)
        }
    }

    fun toggleCustomTimerRunning(id: Long) {
        viewModelScope.launch {
            repository.toggleCustomTimerRunning(id)
        }
    }

    fun deleteCustomTimer(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomTimer(id)
        }
    }

    fun selectTheme(theme: CyberThemeId) {
        themeManager.setTheme(theme)
    }

    fun selectAppearanceMode(mode: ThemeAppearanceMode) {
        themeManager.setAppearanceMode(mode)
    }

    fun refreshAppearance(context: Context) {
        themeManager.refreshResolvedPalette(context)
    }

    fun updateOverlaySettings(transform: (OverlaySettings) -> OverlaySettings) {
        settingsPrefs.updateSettings(transform)
    }

    fun updateFeedbackSettings(transform: (FeedbackSettings) -> FeedbackSettings) {
        settingsPrefs.updateFeedbackSettings(transform)
    }

    fun moveDashboardSection(section: DashboardSection, delta: Int) {
        settingsPrefs.moveDashboardSection(section, delta)
    }

    fun resetDashboardOrder() {
        settingsPrefs.resetDashboardOrder()
    }

    companion object {
        const val QUOTE_ROTATION_INTERVAL_MS = 180_000L
    }
}

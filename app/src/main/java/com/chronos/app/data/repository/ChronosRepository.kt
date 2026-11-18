package com.chronos.app.data.repository

import com.chronos.app.data.local.ChronosDatabase
import com.chronos.app.data.local.CustomTimerDao
import com.chronos.app.data.local.CustomTimerEntity
import com.chronos.app.data.local.DailyHabitAuditDao
import com.chronos.app.data.local.DailyHabitAuditEntity
import com.chronos.app.data.local.DailyNeuralStatDao
import com.chronos.app.data.local.DailyNeuralStatEntity
import com.chronos.app.data.local.HabitCycleState
import com.chronos.app.data.local.PerformanceCategory
import com.chronos.app.data.local.PerformanceTimerDao
import com.chronos.app.data.local.PerformanceTimerEntity
import com.chronos.app.data.local.ResetPeriod
import com.chronos.app.domain.ChronosTimeEngine
import com.chronos.app.domain.NeuralStatsCalculator
import kotlinx.coroutines.flow.Flow

data class MotivationalQuote(
    val text: String,
    val author: String,
    val role: String
)

class ChronosRepository(
    private val customTimerDao: CustomTimerDao,
    private val performanceTimerDao: PerformanceTimerDao,
    private val dailyNeuralStatDao: DailyNeuralStatDao,
    private val dailyHabitAuditDao: DailyHabitAuditDao? = null
) {
    val customTimersFlow: Flow<List<CustomTimerEntity>> = customTimerDao.observeAll()
    val performanceTimersFlow: Flow<List<PerformanceTimerEntity>> = performanceTimerDao.observeAll()
    val dailyNeuralStatsFlow: Flow<List<DailyNeuralStatEntity>> = dailyNeuralStatDao.observeAll()
    val dailyHabitAuditsFlow: Flow<List<DailyHabitAuditEntity>>? = dailyHabitAuditDao?.observeAll()

    suspend fun ensureSeeded() {
        ChronosDatabase.seedPerformanceTimers(performanceTimerDao)
    }

    suspend fun getCustomTimersSnapshot(): List<CustomTimerEntity> =
        customTimerDao.getAllSnapshot()

    suspend fun getPerformanceTimersSnapshot(): List<PerformanceTimerEntity> {
        ensureSeeded()
        return performanceTimerDao.getAllSnapshot()
    }

    suspend fun getCustomTimerById(id: Long): CustomTimerEntity? =
        customTimerDao.getById(id)

    suspend fun createOrUpdateCustomTimer(
        id: Long = 0L,
        name: String,
        startMinuteOfDay: Int,
        durationMinutes: Int,
        activeDaysMask: Int,
        resetPeriod: ResetPeriod,
        privateNotes: String,
        dimension: PerformanceCategory = PerformanceCategory.MENTAL
    ) {
        val now = System.currentTimeMillis()
        val durationSec = durationMinutes.coerceAtLeast(1) * 60L
        if (id == 0L) {
            customTimerDao.insert(
                CustomTimerEntity(
                    name = name.trim(),
                    startMinuteOfDay = startMinuteOfDay.coerceIn(0, 1439),
                    durationMinutes = durationMinutes.coerceAtLeast(1),
                    activeDaysMask = activeDaysMask,
                    resetPeriod = resetPeriod.name,
                    privateNotes = privateNotes.trim(),
                    isRunning = false,
                    remainingSeconds = durationSec,
                    lastTickEpochMs = now,
                    dimensionName = dimension.name,
                    habitState = HabitCycleState.NONE.code
                )
            )
        } else {
            val existing = customTimerDao.getById(id)
            if (existing != null) {
                customTimerDao.update(
                    existing.copy(
                        name = name.trim(),
                        startMinuteOfDay = startMinuteOfDay.coerceIn(0, 1439),
                        durationMinutes = durationMinutes.coerceAtLeast(1),
                        activeDaysMask = activeDaysMask,
                        resetPeriod = resetPeriod.name,
                        privateNotes = privateNotes.trim(),
                        remainingSeconds = durationSec,
                        isRunning = false,
                        lastTickEpochMs = now,
                        dimensionName = dimension.name
                    )
                )
            }
        }
    }

    suspend fun cycleCustomTimerHabitState(id: Long): HabitCycleState? {
        val existing = customTimerDao.getById(id) ?: return null
        val prevState = existing.parsedHabitState()
        val nextState = prevState.next()
        val now = System.currentTimeMillis()
        val dateKey = NeuralStatsCalculator.formatDateKey(now)

        customTimerDao.update(existing.copy(habitState = nextState.code))

        val auditDao = dailyHabitAuditDao
        if (auditDao != null) {
            if (nextState == HabitCycleState.NONE) {
                auditDao.deleteForDateAndTimer(dateKey, existing.id)
            } else {
                auditDao.upsert(
                    DailyHabitAuditEntity(
                        dateKey = dateKey,
                        timerId = existing.id,
                        dimensionName = existing.parsedDimension().name,
                        timerName = existing.name,
                        habitState = nextState.code,
                        updatedAtMs = now
                    )
                )
            }
        }

        val creditSec = existing.durationMinutes.coerceAtLeast(1) * 60L
        if (prevState != HabitCycleState.COMPLETED && nextState == HabitCycleState.COMPLETED) {
            dailyNeuralStatDao.adjustFocusDelta(
                dateKey = dateKey,
                categoryName = existing.parsedDimension().name,
                deltaSeconds = creditSec,
                deltaSessions = 1,
                nowMs = now
            )
        } else if (prevState == HabitCycleState.COMPLETED && nextState != HabitCycleState.COMPLETED) {
            dailyNeuralStatDao.adjustFocusDelta(
                dateKey = dateKey,
                categoryName = existing.parsedDimension().name,
                deltaSeconds = -creditSec,
                deltaSessions = -1,
                nowMs = now
            )
        }
        return nextState
    }

    suspend fun renameCustomTimer(id: Long, newName: String) {
        val existing = customTimerDao.getById(id) ?: return
        if (newName.isBlank()) return
        customTimerDao.update(existing.copy(name = newName.trim()))
    }

    suspend fun updatePrivateNotes(id: Long, notes: String) {
        val existing = customTimerDao.getById(id) ?: return
        customTimerDao.update(existing.copy(privateNotes = notes.trim()))
    }

    suspend fun toggleCustomTimerRunning(id: Long) {
        val existing = customTimerDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        if (existing.isRunning) {
            val currentRemaining = ChronosTimeEngine.computeLiveRemainingSeconds(
                existing.remainingSeconds,
                true,
                existing.lastTickEpochMs,
                now
            )
            customTimerDao.update(
                existing.copy(
                    isRunning = false,
                    remainingSeconds = currentRemaining,
                    lastTickEpochMs = now
                )
            )
        } else {
            val resetIfFinished = if (existing.remainingSeconds <= 0L) {
                existing.durationMinutes * 60L
            } else {
                existing.remainingSeconds
            }
            customTimerDao.update(
                existing.copy(
                    isRunning = true,
                    remainingSeconds = resetIfFinished,
                    lastTickEpochMs = now
                )
            )
        }
    }

    suspend fun deleteCustomTimer(id: Long) {
        customTimerDao.deleteById(id)
        dailyHabitAuditDao?.deleteAllForTimer(id)
    }

    suspend fun togglePerformanceTimer(category: PerformanceCategory) {
        ensureSeeded()
        val existing = performanceTimerDao.getByCategory(category.name) ?: return
        val now = System.currentTimeMillis()
        if (existing.isRunning) {
            val elapsed = ((now - existing.lastTickEpochMs) / 1000L)
                .coerceIn(0L, existing.remainingSeconds.coerceAtLeast(0L))
            val remaining = ChronosTimeEngine.computeLivePerformanceRemaining(existing, now)
            val completedDelta = if (remaining == 0L && elapsed > 0L) 1 else 0
            if (elapsed > 0L) {
                dailyNeuralStatDao.addFocusSeconds(
                    dateKey = NeuralStatsCalculator.formatDateKey(now),
                    categoryName = category.name,
                    deltaSeconds = elapsed,
                    incrementSession = completedDelta,
                    nowMs = now
                )
            }
            performanceTimerDao.update(
                existing.copy(
                    isRunning = false,
                    remainingSeconds = remaining,
                    lastTickEpochMs = now,
                    completedSessions = existing.completedSessions + completedDelta
                )
            )
        } else {
            val baseRemaining = if (existing.remainingSeconds <= 0L) {
                existing.totalSeconds
            } else {
                existing.remainingSeconds
            }
            performanceTimerDao.update(
                existing.copy(
                    isRunning = true,
                    remainingSeconds = baseRemaining,
                    lastTickEpochMs = now
                )
            )
        }
    }

    suspend fun resetPerformanceTimer(category: PerformanceCategory) {
        ensureSeeded()
        val existing = performanceTimerDao.getByCategory(category.name) ?: return
        val now = System.currentTimeMillis()
        if (existing.isRunning) {
            val elapsed = ((now - existing.lastTickEpochMs) / 1000L)
                .coerceIn(0L, existing.remainingSeconds.coerceAtLeast(0L))
            if (elapsed > 0L) {
                dailyNeuralStatDao.addFocusSeconds(
                    dateKey = NeuralStatsCalculator.formatDateKey(now),
                    categoryName = category.name,
                    deltaSeconds = elapsed,
                    incrementSession = 0,
                    nowMs = now
                )
            }
        }
        performanceTimerDao.update(
            existing.copy(
                isRunning = false,
                remainingSeconds = existing.totalSeconds,
                lastTickEpochMs = now
            )
        )
    }

    suspend fun updatePerformanceDuration(category: PerformanceCategory, durationMinutes: Int) {
        ensureSeeded()
        val existing = performanceTimerDao.getByCategory(category.name) ?: return
        val secs = durationMinutes.coerceIn(1, 360) * 60L
        performanceTimerDao.update(
            existing.copy(
                totalSeconds = secs,
                remainingSeconds = secs,
                isRunning = false,
                lastTickEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun completeOverclockSession(
        durationMinutes: Int,
        pauseCount: Int
    ): OverclockReward {
        ensureSeeded()
        val safeMinutes = durationMinutes.coerceAtLeast(1)
        val totalSeconds = safeMinutes * 60L
        val now = System.currentTimeMillis()
        val dateKey = NeuralStatsCalculator.formatDateKey(now)

        val stabilityMultiplier = (1f - (pauseCount.coerceAtLeast(0) * 0.10f)).coerceAtLeast(0.50f)
        val totalPoints = ((safeMinutes * 4) * stabilityMultiplier).toInt().coerceAtLeast(4)

        val weights = mapOf(
            PerformanceCategory.MENTAL to 0.35f,
            PerformanceCategory.OCCUPATIONAL to 0.30f,
            PerformanceCategory.PHYSICAL to 0.20f,
            PerformanceCategory.EXPRESSIVE to 0.15f
        )

        val categoryPoints = linkedMapOf<PerformanceCategory, Int>()
        var allocatedSeconds = 0L
        val categories = PerformanceCategory.entries
        categories.forEachIndexed { index, category ->
            val w = weights[category] ?: 0.25f
            val pts = (totalPoints * w).toInt().coerceAtLeast(1)
            categoryPoints[category] = pts

            val secForCategory = if (index == categories.lastIndex) {
                (totalSeconds - allocatedSeconds).coerceAtLeast(1L)
            } else {
                (totalSeconds * w).toLong().coerceAtLeast(1L)
            }
            allocatedSeconds += secForCategory

            dailyNeuralStatDao.addFocusSeconds(
                dateKey = dateKey,
                categoryName = category.name,
                deltaSeconds = secForCategory,
                incrementSession = 1,
                nowMs = now
            )

            performanceTimerDao.getByCategory(category.name)?.let { existing ->
                performanceTimerDao.update(
                    existing.copy(completedSessions = existing.completedSessions + 1)
                )
            }
        }

        return OverclockReward(
            totalPoints = categoryPoints.values.sum(),
            stabilityPercent = (stabilityMultiplier * 100f).toInt(),
            categoryPoints = categoryPoints
        )
    }

    fun getMotivationalQuotes(): List<MotivationalQuote> = listOf(
        MotivationalQuote(
            text = "“Tienes poder sobre tu mente, no sobre los acontecimientos externos. Date cuenta de esto y encontrarás la fuerza.”",
            author = "Marco Aurelio",
            role = "Emperador romano y filósofo estoico"
        ),
        MotivationalQuote(
            text = "“No es que tengamos poco tiempo, sino que perdemos mucho. La vida es suficientemente larga si se invierte bien.”",
            author = "Lucio Anneo Séneca",
            role = "Filósofo y estadista"
        ),
        MotivationalQuote(
            text = "“No hagas nada que sea inútil; domina tu espíritu hoy para conquistar la batalla de mañana.”",
            author = "Miyamoto Musashi",
            role = "Espadachín y estratega japonés"
        ),
        MotivationalQuote(
            text = "“Odié cada minuto de entrenamiento, pero me dije: no te rindas; sufre ahora y vive el resto de tu vida como un campeón.”",
            author = "Muhammad Ali",
            role = "Campeón mundial de peso pesado"
        ),
        MotivationalQuote(
            text = "“No temas al fracaso, sino a apuntar demasiado bajo. En los grandes intentos, hasta caer resulta glorioso.”",
            author = "Bruce Lee",
            role = "Artista marcial y pensador"
        ),
        MotivationalQuote(
            text = "“Nada en la vida debe ser temido, solamente comprendido. Ahora es el momento de comprender más para temer menos.”",
            author = "Marie Curie",
            role = "Física y doble premio Nobel"
        ),
        MotivationalQuote(
            text = "“El presente es de ellos; el futuro, por el que realmente trabajé, es mío.”",
            author = "Nikola Tesla",
            role = "Inventor e ingeniero eléctrico"
        ),
        MotivationalQuote(
            text = "“La simplicidad es la máxima sofisticación; la obstinada constancia convierte la disciplina en arte.”",
            author = "Leonardo da Vinci",
            role = "Polímata del Renacimiento"
        ),
        MotivationalQuote(
            text = "“Somos lo que hacemos repetidamente. La excelencia, entonces, no es un acto sino un hábito.”",
            author = "Aristóteles",
            role = "Filósofo griego"
        ),
        MotivationalQuote(
            text = "“¿Cuánto tiempo vas a esperar antes de exigirte lo mejor a ti mismo y ejecutar tu propio estándar?”",
            author = "Epicteto",
            role = "Filósofo estoico"
        ),
        MotivationalQuote(
            text = "“El éxito no es definitivo, el fracaso no es fatal: lo que cuenta es el coraje de continuar.”",
            author = "Winston Churchill",
            role = "Primer Ministro y premio Nobel de Literatura"
        ),
        MotivationalQuote(
            text = "“Siempre parece imposible hasta que se hace.”",
            author = "Nelson Mandela",
            role = "Líder y premio Nobel de la Paz"
        )
    )
}

data class OverclockReward(
    val totalPoints: Int,
    val stabilityPercent: Int,
    val categoryPoints: Map<PerformanceCategory, Int>
)

package com.chronos.app.data.local

import androidx.room.Entity

@Entity(
    tableName = "daily_habit_audits",
    primaryKeys = ["dateKey", "timerId"]
)
data class DailyHabitAuditEntity(
    val dateKey: String,
    val timerId: Long,
    val dimensionName: String,
    val timerName: String,
    val habitState: Int,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    fun parsedDimension(): PerformanceCategory = PerformanceCategory.fromName(dimensionName)

    fun parsedHabitState(): HabitCycleState = HabitCycleState.fromCode(habitState)
}

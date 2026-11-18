package com.chronos.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyHabitAuditDao {
    @Query("SELECT * FROM daily_habit_audits ORDER BY dateKey DESC, timerId ASC")
    fun observeAll(): Flow<List<DailyHabitAuditEntity>>

    @Query("SELECT * FROM daily_habit_audits WHERE dateKey = :dateKey ORDER BY timerId ASC")
    suspend fun getForDate(dateKey: String): List<DailyHabitAuditEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(audit: DailyHabitAuditEntity)

    @Query("DELETE FROM daily_habit_audits WHERE dateKey = :dateKey AND timerId = :timerId")
    suspend fun deleteForDateAndTimer(dateKey: String, timerId: Long)

    @Query("DELETE FROM daily_habit_audits WHERE timerId = :timerId")
    suspend fun deleteAllForTimer(timerId: Long)
}

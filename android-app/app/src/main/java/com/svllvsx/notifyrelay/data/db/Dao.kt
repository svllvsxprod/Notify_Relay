package com.svllvsx.notifyrelay.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(event: EventEntity): Long

    @Query("SELECT * FROM events WHERE status = 'pending' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPending(limit: Int): List<EventEntity>

    @Query("UPDATE events SET status = 'sending', attempts = attempts + 1 WHERE id IN (:ids)")
    suspend fun markSending(ids: List<String>)

    @Query("UPDATE events SET status = 'sent', lastError = NULL WHERE eventId IN (:eventIds)")
    suspend fun markSentByEventIds(eventIds: List<String>)

    @Query("UPDATE events SET status = 'pending', lastError = :error WHERE eventId IN (:eventIds)")
    suspend fun markPendingWithError(eventIds: List<String>, error: String)

    @Query("UPDATE events SET status = 'failed', lastError = :error WHERE eventId IN (:eventIds)")
    suspend fun markFailed(eventIds: List<String>, error: String)

    @Query("SELECT COUNT(*) FROM events WHERE status = :status")
    fun countByStatus(status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM events WHERE status = 'sent' AND createdAt >= :since")
    fun countSentSince(since: Long): Flow<Int>

    @Query("SELECT * FROM events WHERE createdAt >= :since ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(since: Long, limit: Int): Flow<List<EventEntity>>

    @Query("DELETE FROM events WHERE status = 'sent' AND createdAt < :timestamp")
    suspend fun deleteSentOlderThan(timestamp: Long)
}

@Dao
interface SelectedAppDao {
    @Upsert
    suspend fun upsert(app: SelectedAppEntity)

    @Query("UPDATE selected_apps SET enabled = :enabled, updatedAt = :updatedAt WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM selected_apps ORDER BY appLabel")
    fun observeSelectedApps(): Flow<List<SelectedAppEntity>>

    @Query("SELECT enabled FROM selected_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun isEnabled(packageName: String): Boolean?
}

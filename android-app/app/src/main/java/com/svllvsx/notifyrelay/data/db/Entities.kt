package com.svllvsx.notifyrelay.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    indices = [Index(value = ["eventId"], unique = true), Index(value = ["idempotencyKey"], unique = true), Index(value = ["status"])],
)
data class EventEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val idempotencyKey: String,
    val type: String,
    val packageName: String?,
    val appLabel: String?,
    val sender: String?,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val timestamp: Long,
    val createdAt: Long,
    val status: String,
    val attempts: Int,
    val lastError: String?,
)

@Entity(tableName = "selected_apps")
data class SelectedAppEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val enabled: Boolean,
    val updatedAt: Long,
)

object EventStatus {
    const val PENDING = "pending"
    const val SENDING = "sending"
    const val SENT = "sent"
    const val FAILED = "failed"
}

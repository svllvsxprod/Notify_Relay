package com.svllvsx.notifyrelay.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [EventEntity::class, SelectedAppEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun selectedAppDao(): SelectedAppDao
}

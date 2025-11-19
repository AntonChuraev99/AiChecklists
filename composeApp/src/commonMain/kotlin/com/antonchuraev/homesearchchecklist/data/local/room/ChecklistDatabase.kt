package com.antonchuraev.homesearchchecklist.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ChecklistEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(ChecklistItemConverters::class)
abstract class ChecklistDatabase : RoomDatabase() {
    abstract fun checklistDao(): ChecklistDao
}



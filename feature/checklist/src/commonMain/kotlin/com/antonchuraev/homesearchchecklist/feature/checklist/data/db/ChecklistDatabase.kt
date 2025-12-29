package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [ChecklistEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(ChecklistItemConverters::class)
@ConstructedBy(ChecklistDatabaseConstructor::class)
abstract class ChecklistDatabase : RoomDatabase() {
    abstract fun checklistDao(): ChecklistDao

    companion object {
        fun getRoomDatabase(
            builder: Builder<ChecklistDatabase>
        ): ChecklistDatabase {
            return builder
                .fallbackToDestructiveMigration(dropAllTables = false)
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object ChecklistDatabaseConstructor : RoomDatabaseConstructor<ChecklistDatabase>{
    override fun initialize(): ChecklistDatabase
}


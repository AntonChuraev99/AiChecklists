package com.antonchuraev.homesearchchecklist.feature.checklist.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [ChecklistEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(ChecklistItemConverters::class)
public abstract class ChecklistDatabase : RoomDatabase() {
    public abstract fun checklistDao(): ChecklistDao

    public companion object {

        public fun getRoomDatabase(
            builder: Builder<ChecklistDatabase>
        ): ChecklistDatabase {
            return builder
                .fallbackToDestructiveMigration(dropAllTables = false)
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
    }

}


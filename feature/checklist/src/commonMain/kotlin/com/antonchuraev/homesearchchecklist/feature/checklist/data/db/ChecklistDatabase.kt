package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE checklists ADD COLUMN reminderAt INTEGER DEFAULT NULL")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE checklists ADD COLUMN separateCompleted INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE checklists ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        // Preserve existing order (was ORDER BY id DESC) by assigning positions
        connection.execSQL(
            """
            UPDATE checklists SET position = (
                SELECT COUNT(*) FROM checklists AS c2 WHERE c2.id > checklists.id
            )
            """.trimIndent()
        )
    }
}

@Database(
    entities = [ChecklistEntity::class, ChecklistFillEntity::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(ChecklistItemConverters::class)
@ConstructedBy(ChecklistDatabaseConstructor::class)
abstract class ChecklistDatabase : RoomDatabase() {
    abstract fun checklistDao(): ChecklistDao
    abstract fun checklistFillDao(): ChecklistFillDao

    companion object {
        fun getRoomDatabase(
            builder: Builder<ChecklistDatabase>
        ): ChecklistDatabase {
            return builder
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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


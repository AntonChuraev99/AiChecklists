package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.TypeConverters
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers

val MIGRATION_3_4 = object : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE checklists ADD COLUMN reminderAt INTEGER DEFAULT NULL")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE checklists ADD COLUMN separateCompleted INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
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

val MIGRATION_6_7 = object : Migration(6, 7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE checklists ADD COLUMN autoDeleteCompleted INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE checklists ADD COLUMN repeatRule TEXT DEFAULT NULL")
        connection.execSQL("ALTER TABLE checklists ADD COLUMN repeatOccurrenceCount INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Add new columns for independent repeat schedule
        connection.execSQL("ALTER TABLE checklists ADD COLUMN repeatTimeOfDayMinutes INTEGER DEFAULT NULL")
        connection.execSQL("ALTER TABLE checklists ADD COLUMN repeatNextAt INTEGER DEFAULT NULL")
        // Reset existing repeat data (not on prod, safe to clear)
        connection.execSQL("UPDATE checklists SET repeatRule = NULL, repeatOccurrenceCount = 0")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Add viewMode column; existing rows default to 'Standard' (flat list behavior)
        connection.execSQL("ALTER TABLE checklists ADD COLUMN viewMode TEXT NOT NULL DEFAULT 'Standard'")
    }
}

// No-op: ChecklistFillItem.attachments lives inside the JSON-encoded `items` column.
// Room's TypeConverter is opaque — the SQL schema is unchanged. Version bump is required
// so Room skips its hash-mismatch check for installs that already have version 10.
val MIGRATION_10_11 = object : Migration(10, 11) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // no SQL changes — attachments field added to JSON blob only
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ai_chat_history` (
                `id` TEXT NOT NULL,
                `role` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `costCredits` INTEGER NOT NULL DEFAULT 0,
                `routedLayer` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

@Database(
    entities = [ChecklistEntity::class, ChecklistFillEntity::class, ChatHistoryEntry::class],
    version = 12,
    exportSchema = true
)
@TypeConverters(ChecklistItemConverters::class, ReminderConverters::class)
@ConstructedBy(ChecklistDatabaseConstructor::class)
abstract class ChecklistDatabase : RoomDatabase() {
    abstract fun checklistDao(): ChecklistDao
    abstract fun checklistFillDao(): ChecklistFillDao
    abstract fun chatHistoryDao(): ChatHistoryDao

    companion object {
        fun getRoomDatabase(
            builder: Builder<ChecklistDatabase>
        ): ChecklistDatabase {
            return builder
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                .fallbackToDestructiveMigration(dropAllTables = false)
                .setQueryCoroutineContext(Dispatchers.Default)
                .build()
        }
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object ChecklistDatabaseConstructor : RoomDatabaseConstructor<ChecklistDatabase> {
    override fun initialize(): ChecklistDatabase
}

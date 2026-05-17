package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ChatHistoryEntry)

    @Query("SELECT * FROM ai_chat_history ORDER BY timestamp ASC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ChatHistoryEntry>>

    @Query("DELETE FROM ai_chat_history")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM ai_chat_history")
    suspend fun count(): Int
}

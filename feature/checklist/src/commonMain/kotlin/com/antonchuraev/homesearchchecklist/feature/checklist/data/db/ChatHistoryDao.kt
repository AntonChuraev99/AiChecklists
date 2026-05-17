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

    // Take the *latest* :limit rows (DESC LIMIT) and re-order them ASC so the
    // chat feed gets oldest→newest. Without the subquery, plain ORDER BY ASC LIMIT
    // returns the OLDEST N rows — which silently hides recent messages once the
    // user crosses the limit. See bug: messages disappearing after re-entering chat.
    @Query(
        """
        SELECT * FROM (
            SELECT * FROM ai_chat_history
            ORDER BY timestamp DESC
            LIMIT :limit
        )
        ORDER BY timestamp ASC
        """
    )
    fun observeRecent(limit: Int): Flow<List<ChatHistoryEntry>>

    @Query("DELETE FROM ai_chat_history")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM ai_chat_history")
    suspend fun count(): Int
}

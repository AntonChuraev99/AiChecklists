package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room.TypeConverter
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ReminderConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun repeatRuleFromString(value: String?): ReminderRepeatRule? {
        if (value.isNullOrEmpty()) return null
        return try {
            json.decodeFromString(ReminderRepeatRule.serializer(), value)
        } catch (e: SerializationException) {
            // Corrupted JSON: treat as "no rule", log to Crashlytics in production
            null
        }
    }

    @TypeConverter
    fun repeatRuleToString(rule: ReminderRepeatRule?): String? {
        if (rule == null) return null
        return json.encodeToString(ReminderRepeatRule.serializer(), rule)
    }
}

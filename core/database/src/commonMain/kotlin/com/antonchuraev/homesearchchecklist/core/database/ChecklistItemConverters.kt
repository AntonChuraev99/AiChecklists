package com.antonchuraev.homesearchchecklist.core.database

import androidx.room.TypeConverter
import com.antonchuraev.homesearchchecklist.core.common.api.ChecklistItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class ChecklistItemConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromString(value: String): List<ChecklistItem> {
        if (value.isEmpty()) return emptyList()
        return json.decodeFromString(ListSerializer(ChecklistItem.serializer()), value)
    }

    @TypeConverter
    fun toString(items: List<ChecklistItem>): String {
        if (items.isEmpty()) return ""
        return json.encodeToString(ListSerializer(ChecklistItem.serializer()), items)
    }
}


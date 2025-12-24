package com.antonchuraev.homesearchchecklist.feature.checklist.data.local

import androidx.room.TypeConverter
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
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


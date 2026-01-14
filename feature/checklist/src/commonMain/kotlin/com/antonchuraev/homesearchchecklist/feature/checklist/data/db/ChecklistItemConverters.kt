package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room.TypeConverter
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
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

    @TypeConverter
    fun fillItemsFromString(value: String): List<ChecklistFillItem> {
        if (value.isEmpty()) return emptyList()
        return json.decodeFromString(ListSerializer(ChecklistFillItem.serializer()), value)
    }

    @TypeConverter
    fun fillItemsToString(items: List<ChecklistFillItem>): String {
        if (items.isEmpty()) return ""
        return json.encodeToString(ListSerializer(ChecklistFillItem.serializer()), items)
    }
}


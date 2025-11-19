package com.antonchuraev.homesearchchecklist.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Checklist(
    val id: Long = 0L,
    val name: String,
    val items: List<ChecklistItem>
)

@Serializable
data class ChecklistItem(
    val text: String,
    val checked: Boolean
)
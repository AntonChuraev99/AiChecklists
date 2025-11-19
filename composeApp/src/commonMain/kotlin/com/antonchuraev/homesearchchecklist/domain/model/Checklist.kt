package com.antonchuraev.homesearchchecklist.domain.model

data class Checklist(
    val name: String,
    val items: List<ChecklistItem>
)

data class ChecklistItem(
    val text: String,
    val checked: Boolean
)
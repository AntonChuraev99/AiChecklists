package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

import kotlinx.serialization.Serializable

/**
 * Display mode for a checklist.
 * Standard = flat list (default behavior).
 * Weekly = items grouped by weekday (ISO 1=Mon..7=Sun).
 */
@Serializable
enum class ChecklistViewMode { Standard, Weekly }

package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun formatExpirationDate(timestamp: Long): String {
    return try {
        val date = Date(timestamp)
        val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        formatter.format(date)
    } catch (e: Exception) {
        ""
    }
}

package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import java.text.SimpleDateFormat
import java.util.Calendar
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

actual fun getTrialEndDateFormatted(daysFromNow: Int): String {
    return try {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, daysFromNow)
        val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        formatter.format(calendar.time)
    } catch (e: Exception) {
        ""
    }
}

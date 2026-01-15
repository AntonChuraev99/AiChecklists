package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

actual fun formatExpirationDate(timestamp: Long): String {
    return try {
        val date = NSDate.dateWithTimeIntervalSince1970(timestamp / 1000.0)
        val formatter = NSDateFormatter()
        formatter.dateFormat = "MMM d, yyyy"
        formatter.stringFromDate(date)
    } catch (e: Exception) {
        ""
    }
}

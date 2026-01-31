package com.antonchuraev.homesearchchecklist.core.common.api

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

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

actual fun getTrialEndDateFormatted(daysFromNow: Int): String {
    return try {
        val secondsInDay = 24 * 60 * 60.0
        val date = NSDate.dateWithTimeIntervalSinceNow(daysFromNow * secondsInDay)
        val formatter = NSDateFormatter()
        formatter.dateFormat = "MMM d, yyyy"
        formatter.stringFromDate(date)
    } catch (e: Exception) {
        ""
    }
}
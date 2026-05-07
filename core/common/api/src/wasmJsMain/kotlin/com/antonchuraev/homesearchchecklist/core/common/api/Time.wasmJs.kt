package com.antonchuraev.homesearchchecklist.core.common.api

private val MONTH_NAMES = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

actual fun currentTimeMillis(): Long = currentTimeMillisJs().toLong()

actual fun formatExpirationDate(timestamp: Long): String {
    return try {
        val dateMs = timestamp.toDouble()
        val month = getMonthJs(dateMs)
        val day = getDayOfMonthJs(dateMs)
        val year = getFullYearJs(dateMs)
        "${MONTH_NAMES.getOrElse(month) { "?" }} $day, $year"
    } catch (e: Throwable) {
        ""
    }
}

actual fun getTrialEndDateFormatted(daysFromNow: Int): String {
    return try {
        val futureMs = currentTimeMillisJs() + daysFromNow.toDouble() * 86_400_000.0
        val month = getMonthJs(futureMs)
        val day = getDayOfMonthJs(futureMs)
        val year = getFullYearJs(futureMs)
        "${MONTH_NAMES.getOrElse(month) { "?" }} $day, $year"
    } catch (e: Throwable) {
        ""
    }
}

@JsFun("() => Date.now()")
private external fun currentTimeMillisJs(): Double

@JsFun("(ms) => new Date(ms).getMonth()")
private external fun getMonthJs(ms: Double): Int

@JsFun("(ms) => new Date(ms).getDate()")
private external fun getDayOfMonthJs(ms: Double): Int

@JsFun("(ms) => new Date(ms).getFullYear()")
private external fun getFullYearJs(ms: Double): Int

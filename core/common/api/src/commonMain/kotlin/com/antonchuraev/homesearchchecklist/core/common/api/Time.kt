package com.antonchuraev.homesearchchecklist.core.common.api

expect fun currentTimeMillis(): Long

expect fun formatExpirationDate(timestamp: Long): String

expect fun getTrialEndDateFormatted(daysFromNow: Int): String

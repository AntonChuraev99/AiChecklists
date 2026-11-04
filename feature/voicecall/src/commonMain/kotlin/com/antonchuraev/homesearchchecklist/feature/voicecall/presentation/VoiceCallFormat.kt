package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation

/**
 * Call clock `m:ss` (`59:59`, `0:07`), or `h:mm:ss` from one hour on. Negative input reads as 0.
 * Digits are ASCII on purpose: the timer pill uses tabular figures and must not reflow per locale.
 */
fun formatCallClock(totalSec: Int): String {
    val sec = totalSec.coerceAtLeast(0)
    val hours = sec / SECONDS_PER_HOUR
    val minutes = (sec % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = sec % SECONDS_PER_MINUTE
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$ss" else "$minutes:$ss"
}

/** Whole minutes for the limit banner, rounded UP and never below 1 (59 s left reads "1 minute"). */
fun minutesLeftRoundedUp(remainingSec: Int): Int =
    ((remainingSec.coerceAtLeast(0) + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE).coerceAtLeast(1)

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

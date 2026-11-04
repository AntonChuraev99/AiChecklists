package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.voice_call_duration_minutes
import aichecklists.core.designsystem.generated.resources.voice_call_duration_seconds
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.pluralStringResource

/**
 * Minutes and seconds of [totalSec] as words, for TalkBack ("1 minute 48 seconds"): a screen reader
 * reads the visual clock "1:48" digit by digit. Zero parts are left out; 0 reads "0 seconds".
 */
@Composable
internal fun spokenDuration(totalSec: Int): String {
    val parts = durationParts(totalSec)
    val minutes = parts.minutes?.let { pluralStringResource(Res.plurals.voice_call_duration_minutes, it, it) }
    val seconds = parts.seconds?.let { pluralStringResource(Res.plurals.voice_call_duration_seconds, it, it) }
    return listOfNotNull(minutes, seconds).joinToString(" ")
}

/** Which parts [spokenDuration] says: `null` = left out. Pure, for tests. */
internal data class DurationParts(val minutes: Int?, val seconds: Int?)

internal fun durationParts(totalSec: Int): DurationParts {
    val sec = totalSec.coerceAtLeast(0)
    val minutes = sec / SECONDS_PER_MINUTE
    val seconds = sec % SECONDS_PER_MINUTE
    return DurationParts(
        minutes = minutes.takeIf { it > 0 },
        seconds = seconds.takeIf { it > 0 || minutes == 0 },
    )
}

private const val SECONDS_PER_MINUTE = 60

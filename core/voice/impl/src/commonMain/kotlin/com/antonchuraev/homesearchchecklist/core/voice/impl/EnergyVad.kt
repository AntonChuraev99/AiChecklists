package com.antonchuraev.homesearchchecklist.core.voice.impl

import com.antonchuraev.homesearchchecklist.core.voice.api.VadConfig
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Energy VAD of voice barge-in: reports a start of speech once the input stays above [VadConfig.thresholdDbfs] for
 * [VadConfig.minSpeechMs]. Frames are PCM16 of [VadConfig.frameMs] each.
 *
 * One frame below the threshold inside a run is tolerated (a consonant gap); two end the run. After a report the
 * detector stays quiet until the run ends, so one phrase is one barge-in. Not thread-safe: one reader loop owns it.
 */
class EnergyVad(private val config: VadConfig) {
    private var runMs = 0L
    private var quietFrames = 0
    private var reported = false

    /** Feeds one frame of [length] samples; `true` exactly once per run of speech, when it reaches the minimum. */
    fun offer(frame: ShortArray, length: Int = frame.size): Boolean = offerLevel(dbfs(frame, length))

    /** [offer] for a frame whose level is already known (tests, other sources). */
    fun offerLevel(levelDbfs: Float): Boolean {
        if (levelDbfs >= config.thresholdDbfs) {
            quietFrames = 0
            runMs += config.frameMs
        } else {
            quietFrames++
            if (quietFrames > MAX_GAP_FRAMES) {
                runMs = 0
                reported = false
            }
            return false
        }
        if (!reported && runMs >= config.minSpeechMs) {
            reported = true
            return true
        }
        return false
    }

    fun reset() {
        runMs = 0
        quietFrames = 0
        reported = false
    }

    companion object {
        /** Level of silence (and of an empty frame). */
        const val SILENCE_DBFS = -96f

        private const val MAX_GAP_FRAMES = 1
        private const val FULL_SCALE = 32768.0

        /** RMS level of the first [length] samples in dBFS (`0` = full scale). */
        fun dbfs(frame: ShortArray, length: Int = frame.size): Float {
            val n = length.coerceAtMost(frame.size)
            if (n <= 0) return SILENCE_DBFS
            var sum = 0.0
            for (i in 0 until n) {
                val s = frame[i] / FULL_SCALE
                sum += s * s
            }
            val rms = sqrt(sum / n)
            if (rms <= 0.0) return SILENCE_DBFS
            return (20 * log10(rms)).toFloat().coerceAtLeast(SILENCE_DBFS)
        }
    }
}

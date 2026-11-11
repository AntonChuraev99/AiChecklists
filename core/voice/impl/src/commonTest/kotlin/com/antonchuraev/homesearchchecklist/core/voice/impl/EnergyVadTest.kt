package com.antonchuraev.homesearchchecklist.core.voice.impl

import com.antonchuraev.homesearchchecklist.core.voice.api.VadConfig
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class EnergyVadTest {

    private val config = VadConfig(thresholdDbfs = -38f, minSpeechMs = 200, frameMs = 20)
    private val loud = -20f
    private val quiet = -60f

    /** Frame indices (0-based) at which the VAD reports a start of speech. */
    private fun reports(levels: List<Float>): List<Int> {
        val vad = EnergyVad(config)
        return levels.mapIndexedNotNull { i, level -> i.takeIf { vad.offerLevel(level) } }
    }

    private fun run(frames: Int, level: Float) = List(frames) { level }

    @Test
    fun reportsOnceSpeechLastedTheMinimumOncePerRun() {
        val cases = listOf(
            "9 loud frames (180 ms) are too short" to (run(9, loud) to emptyList()),
            "10 loud frames (200 ms) report on the 10th" to (run(10, loud) to listOf(9)),
            "a long run reports once" to (run(40, loud) to listOf(9)),
            "silence never reports" to (run(50, quiet) to emptyList()),
            "the threshold itself counts as speech" to (run(10, -38f) to listOf(9)),
            "one quiet frame inside a run is a gap" to ((run(5, loud) + quiet + run(5, loud)) to listOf(10)),
            "two quiet frames end the run" to ((run(5, loud) + run(2, quiet) + run(9, loud)) to emptyList()),
            "a new run after silence reports again" to ((run(10, loud) + run(3, quiet) + run(10, loud)) to listOf(9, 22)),
        )
        cases.forEach { (name, case) -> assertEquals(case.second, reports(case.first), name) }
    }

    @Test
    fun resetForgetsTheRun() {
        val vad = EnergyVad(config)
        repeat(9) { vad.offerLevel(loud) }
        vad.reset()
        assertEquals(false, vad.offerLevel(loud))
    }

    @Test
    fun dbfsOfPcm16Frames() {
        assertEquals(EnergyVad.SILENCE_DBFS, EnergyVad.dbfs(ShortArray(320)), 0.01f)
        assertEquals(EnergyVad.SILENCE_DBFS, EnergyVad.dbfs(ShortArray(0)), 0.01f)
        // A full-scale square wave is 0 dBFS; a full-scale sine is about -3 dBFS.
        val square = ShortArray(320) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        assertEquals(0f, EnergyVad.dbfs(square), 0.01f)
        val sine = ShortArray(320) { (sin(2 * PI * it / 32) * Short.MAX_VALUE).toInt().toShort() }
        assertEquals(-3.01f, EnergyVad.dbfs(sine), 0.05f)
        // Only the first `length` samples count.
        val half = ShortArray(320) { if (it < 160) 0 else Short.MAX_VALUE }
        assertEquals(EnergyVad.SILENCE_DBFS, EnergyVad.dbfs(half, 160), 0.01f)
    }

    @Test
    fun offerMeasuresTheFrame() {
        val vad = EnergyVad(config)
        val speech = ShortArray(320) { (sin(2 * PI * it / 32) * 3_000).toInt().toShort() } // about -24 dBFS
        val results = List(10) { vad.offer(speech) }
        assertEquals(listOf(false, false, false, false, false, false, false, false, false, true), results)
    }
}

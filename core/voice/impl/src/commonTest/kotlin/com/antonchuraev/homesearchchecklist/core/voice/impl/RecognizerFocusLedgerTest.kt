package com.antonchuraev.homesearchchecklist.core.voice.impl

import com.antonchuraev.homesearchchecklist.core.voice.api.FocusChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecognizerFocusLedgerTest {

    @Test
    fun transientLossOfTheLentRecognizerIsNotAnInterruption() {
        val ledger = RecognizerFocusLedger()
        ledger.lend()
        assertNull(ledger.onSystemChange(FocusChange.LossTransient))
    }

    @Test
    fun transientLossWithoutRecognizerIsReported() {
        val ledger = RecognizerFocusLedger()
        assertEquals(FocusChange.LossTransient, ledger.onSystemChange(FocusChange.LossTransient))
    }

    @Test
    fun lateLossAfterCloseIsClaimedOnceThenTheNextOneIsAnInterruption() {
        val ledger = RecognizerFocusLedger()
        ledger.lend()
        assertNull(ledger.close(reacquire = { true }))
        // The recognizer service took the focus only after the client closed it.
        assertNull(ledger.onSystemChange(FocusChange.LossTransient))
        assertEquals(FocusChange.LossTransient, ledger.onSystemChange(FocusChange.LossTransient))
    }

    @Test
    fun permanentLossIsAlwaysReported() {
        val ledger = RecognizerFocusLedger()
        ledger.lend()
        assertEquals(FocusChange.Loss, ledger.onSystemChange(FocusChange.Loss))
    }

    @Test
    fun closeReacquiresTheFocusLostWhileLentAndReportsARefusal() {
        val granted = RecognizerFocusLedger()
        granted.lend()
        granted.onSystemChange(FocusChange.LossTransient)
        var asked = 0
        assertNull(granted.close(reacquire = { asked++; true }))
        assertEquals(1, asked)

        val refused = RecognizerFocusLedger()
        refused.lend()
        refused.onSystemChange(FocusChange.LossTransient)
        assertEquals(FocusChange.LossTransient, refused.close(reacquire = { false }))
    }

    @Test
    fun nestedRecognizersReacquireOnlyWhenTheLastOneCloses() {
        val ledger = RecognizerFocusLedger()
        ledger.lend()
        ledger.lend()
        ledger.onSystemChange(FocusChange.LossTransient)
        var asked = 0
        assertNull(ledger.close(reacquire = { asked++; true }))
        assertEquals(0, asked)
        assertNull(ledger.close(reacquire = { asked++; true }))
        assertEquals(1, asked)
    }

    @Test
    fun gainOrReleaseClearsWhatIsOwedBack() {
        val gained = RecognizerFocusLedger()
        gained.lend()
        gained.onSystemChange(FocusChange.LossTransient)
        gained.onSystemChange(FocusChange.Gain)
        var asked = 0
        assertNull(gained.close(reacquire = { asked++; true }))
        assertEquals(0, asked)

        val released = RecognizerFocusLedger()
        released.lend()
        released.onSystemChange(FocusChange.LossTransient)
        released.released()
        assertNull(released.close(reacquire = { asked++; true }))
        assertEquals(0, asked)
    }
}

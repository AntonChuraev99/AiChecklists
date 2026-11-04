package com.antonchuraev.homesearchchecklist.core.voice.impl

import com.antonchuraev.homesearchchecklist.core.voice.api.AudioFocusController
import com.antonchuraev.homesearchchecklist.core.voice.api.FocusChange

/**
 * The bookkeeping behind [AudioFocusController.lendToRecognizer], shared by the Android controller and the test fake so
 * both classify focus changes the same way. Not thread-safe: the owner calls it from one thread (the main thread on
 * Android, where AudioManager delivers focus changes by default).
 *
 * Why it exists: the platform `SpeechRecognizer` service takes `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` for every utterance,
 * and the system tells the call `AUDIOFOCUS_LOSS_TRANSIENT`. Treated as an interruption, the call paused its own
 * microphone at every start of listening.
 *
 * The recognizer service requests the focus asynchronously: a recognizer cancelled or failed right after it was opened
 * still takes the focus tens of milliseconds after the client closed it, then abandons it. So a lent recognizer stays
 * "unclaimed" until the one transient loss it causes arrives, closed or not. Only one: the next transient loss is an
 * interruption again.
 */
class RecognizerFocusLedger {

    /** Recognizers opened and not yet closed (a restart may open the next one before the previous one is closed). */
    private var lent = 0

    /**
     * A recognizer was lent the focus and its transient loss has not arrived yet. A flag, not a count: a recognizer that
     * never asks for the focus must not make the call deaf to more than one interruption.
     */
    private var unclaimed = false

    /** A transient loss was taken as the recognizer's and the focus has not come back since. */
    private var lostWhileLent = false

    fun lend() {
        lent++
        unclaimed = true
    }

    /** A focus change from the system -> what the controller reports, `null` = the recognizer's own, not reported. */
    fun onSystemChange(change: FocusChange): FocusChange? = when (change) {
        FocusChange.LossTransient -> if (lent > 0 || unclaimed) {
            unclaimed = false
            lostWhileLent = true
            null
        } else {
            change
        }
        // A permanent loss drops the request from the system's focus stack: nothing comes back by itself.
        FocusChange.Loss -> {
            unclaimed = false
            lostWhileLent = false
            change
        }
        FocusChange.Gain -> {
            lostWhileLent = false
            change
        }
    }

    /**
     * The last open recognizer closed with the focus still away: [reacquire] asks for it again. Returns the change to
     * report: [FocusChange.LossTransient] when the system refused (the focus is locked, a phone call), else `null`.
     */
    fun close(reacquire: () -> Boolean): FocusChange? {
        if (lent > 0) lent--
        if (lent > 0 || !lostWhileLent) return null
        lostWhileLent = false
        return if (reacquire()) null else FocusChange.LossTransient
    }

    /** The request was abandoned: nothing is owed back to it. */
    fun released() {
        unclaimed = false
        lostWhileLent = false
    }
}

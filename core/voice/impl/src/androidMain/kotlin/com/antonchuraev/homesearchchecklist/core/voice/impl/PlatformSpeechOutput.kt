package com.antonchuraev.homesearchchecklist.core.voice.impl

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechLocale
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechOutput
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechOutputEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechOutputStatus
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechRate
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechSegmentOut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * Platform `TextToSpeech`: `USAGE_ASSISTANT` / `CONTENT_TYPE_SPEECH`, every segment queued with `QUEUE_ADD` in its own
 * language, `onRangeStart` -> [SpeechOutputEvent.Range] (spoken characters) for the caption and the cut point, a level
 * envelope for the orb from the synthesized PCM (`onAudioAvailable`) or, when the engine gives none, from the word
 * ranges. `onRangeStart` exists from API 26: below it the caption gets no Range and the cut point is the utterance
 * start.
 *
 * One instance per call (Koin `factory`); [release] shuts the engine down, a later [prepare] binds a new one.
 * Callbacks come on a binder thread: the tables below are concurrent, events go through a buffered flow.
 */
internal class PlatformSpeechOutput(
    private val context: Context,
    private val logger: AppLogger,
) : SpeechOutput {

    @Volatile private var tts: TextToSpeech? = null

    private val _events = MutableSharedFlow<SpeechOutputEvent>(extraBufferCapacity = EVENT_BUFFER)
    override val events: Flow<SpeechOutputEvent> = _events

    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level.asStateFlow()

    /** Engine id of one segment -> its utterance and where the segment starts in the utterance text. */
    private val segments = ConcurrentHashMap<String, Segment>()

    override suspend fun prepare(locales: Set<SpeechLocale>): SpeechOutputStatus = withContext(Dispatchers.Main.immediate) {
        val engine = tts ?: bind() ?: return@withContext SpeechOutputStatus.Unavailable
        val missing = locales.filterTo(mutableSetOf()) { locale ->
            when (engine.isLanguageAvailable(Locale.forLanguageTag(locale.tag))) {
                TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> true
                else -> false
            }
        }
        if (missing.isEmpty()) SpeechOutputStatus.Ready else SpeechOutputStatus.MissingVoice(missing)
    }

    private suspend fun bind(): TextToSpeech? {
        var created: TextToSpeech? = null
        val ok = withTimeoutOrNull(BIND_TIMEOUT) {
            suspendCancellableCoroutine { continuation ->
                created = TextToSpeech(context) { status ->
                    if (continuation.isActive) continuation.resume(status == TextToSpeech.SUCCESS)
                }
            }
        } ?: false
        val engine = created
        if (!ok || engine == null) {
            logger.warning(TAG, "TextToSpeech did not initialize (ok=$ok): speech output unavailable")
            engine?.shutdown()
            return null
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        engine.setOnUtteranceProgressListener(listener)
        tts = engine
        return engine
    }

    override fun speak(utteranceId: String, segments: List<SpeechSegmentOut>, rate: SpeechRate) {
        val engine = tts
        if (engine == null) {
            val message = "speak before prepare: $utteranceId"
            logger.error(TAG, message, IllegalStateException(message))
            _events.tryEmit(SpeechOutputEvent.Error(utteranceId, TextToSpeech.ERROR))
            return
        }
        val offsets = SpokenText.segmentOffsets(segments.map { it.text })
        engine.setSpeechRate(rate.factor)
        segments.forEachIndexed { index, segment ->
            val id = "$utteranceId|$index"
            this.segments[id] = Segment(utteranceId, offsets[index], first = index == 0, last = index == segments.lastIndex)
            // Language and rate are captured per request at enqueue time.
            engine.setLanguage(Locale.forLanguageTag(segment.locale.tag))
            val result = engine.speak(segment.text, TextToSpeech.QUEUE_ADD, null, id)
            if (result != TextToSpeech.SUCCESS) {
                this.segments.remove(id)
                logger.warning(TAG, "TTS speak refused ($result) on $utteranceId: utterance ends with Error")
                _events.tryEmit(SpeechOutputEvent.Error(utteranceId, result))
                return
            }
        }
    }

    override fun stop() {
        tts?.stop()
        segments.clear()
        _level.value = 0f
    }

    override fun release() {
        tts?.let {
            it.stop()
            it.shutdown()
        }
        tts = null
        segments.clear()
        _level.value = 0f
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            val segment = segments[utteranceId] ?: return
            if (segment.first) _events.tryEmit(SpeechOutputEvent.Started(segment.utteranceId))
        }

        override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
            val segment = segments[utteranceId] ?: return
            _events.tryEmit(SpeechOutputEvent.Range(segment.utteranceId, segment.offset + end))
            // Fallback envelope for engines without onAudioAvailable: each word is a pulse.
            if (_level.value < WORD_PULSE) _level.value = WORD_PULSE
        }

        override fun onAudioAvailable(utteranceId: String, audio: ByteArray) {
            if (segments[utteranceId] == null) return
            val samples = ShortArray(audio.size / 2)
            ByteBuffer.wrap(audio).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
            val db = EnergyVad.dbfs(samples)
            val normalized = ((db - LEVEL_FLOOR_DBFS) / -LEVEL_FLOOR_DBFS).coerceIn(0f, 1f)
            _level.value = maxOf(normalized, _level.value * DECAY)
        }

        override fun onDone(utteranceId: String) {
            val segment = segments.remove(utteranceId) ?: return
            if (segment.last) {
                _level.value = 0f
                _events.tryEmit(SpeechOutputEvent.Done(segment.utteranceId))
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) = onError(utteranceId, TextToSpeech.ERROR)

        override fun onError(utteranceId: String, errorCode: Int) {
            val segment = segments.remove(utteranceId) ?: return
            logger.warning(TAG, "TTS error $errorCode on ${segment.utteranceId}: rest of the utterance dropped")
            // The rest of this utterance is dropped so one Error ends it.
            segments.entries.removeIf { it.value.utteranceId == segment.utteranceId }
            _level.value = 0f
            _events.tryEmit(SpeechOutputEvent.Error(segment.utteranceId, errorCode))
        }
    }

    private class Segment(val utteranceId: String, val offset: Int, val first: Boolean, val last: Boolean)

    private companion object {
        const val TAG = "VoiceSpeechOutput"
        const val EVENT_BUFFER = 256
        const val WORD_PULSE = 0.45f
        const val DECAY = 0.8f
        const val LEVEL_FLOOR_DBFS = -60f
        val BIND_TIMEOUT = 5.seconds
    }
}

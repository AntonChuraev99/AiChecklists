package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.FilePickerType
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.InputDataType
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.toInputDataType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * WHICH materials open their system picker on screen entry.
 *
 * Every assertion pins the WHOLE set, never one member. An extra material auto-opening is as much a
 * defect as a missing one, and the two failure modes look nothing alike to the user: a missing one
 * costs a tap, while an extra one hijacks the screen (VOICE would start recording, RAW_TEXT would
 * throw a file dialog over a text field the user was about to type into).
 */
class EntryPickerPolicyTest {

    @Test
    fun onlyTheTwoFileMaterialsOpenAPickerOnEntry() {
        assertEquals(
            listOf(InputDataType.PHOTO, InputDataType.PDF),
            InputDataType.entries.filter { it.opensPickerOnEntry() },
            "Only PHOTO and PDF land on a screen whose whole editor is a button that opens the " +
                "system picker. WEB_LINK/RAW_TEXT land on a text field, VOICE would start recording " +
                "the microphone, and TEXT_FILE is not a door — nothing navigates straight to it.",
        )
    }

    @Test
    fun onlyThePhotoAndPdfDoorsOpenAPicker_theOtherThreeLandOnTheirEditor() {
        assertEquals(
            listOf(AnalyzeInputKind.PHOTO, AnalyzeInputKind.PDF),
            AnalyzeInputKind.entries.filter { it.toInputDataType().opensPickerOnEntry() },
            "Read through the door enum the navigation route actually carries: entering on " +
                "WEB_LINK, RAW_TEXT or VOICE must leave the user in their editor, not in a file dialog.",
        )
    }

    @Test
    fun everyAutoOpeningMaterialOpensItsOwnDialog_notTheTextFallback() {
        val dialogs = InputDataType.entries
            .filter { it.opensPickerOnEntry() }
            .associateWith { it.toFilePickerType() }

        assertEquals(
            mapOf(
                InputDataType.PHOTO to FilePickerType.IMAGE,
                InputDataType.PDF to FilePickerType.PDF,
            ),
            dialogs,
            "Auto-opening the WRONG dialog is worse than not opening one: the user taps Photo and " +
                "gets a document browser with no images in it, and the screen looks broken rather " +
                "than merely unhelpful.",
        )
    }

    /**
     * The WHOLE mapping, non-file materials included — because the answer for those is `null` and the
     * defect it replaced was a plausible wrong one.
     *
     * `FilePickerType.TEXT` for WEB_LINK / RAW_TEXT / VOICE compiled, read as deliberate, and would
     * open a text-file chooser for a voice recording the first time a call site forgot the
     * `opensPickerOnEntry()` gate. Pinned as a map so an added material fails here instead of
     * inheriting a silent default.
     */
    @Test
    fun onlyFileMaterialsMapToAPicker_theOtherThreeMapToNothing() {
        assertEquals(
            mapOf(
                InputDataType.PHOTO to FilePickerType.IMAGE,
                InputDataType.PDF to FilePickerType.PDF,
                InputDataType.TEXT_FILE to FilePickerType.TEXT,
                InputDataType.WEB_LINK to null,
                InputDataType.RAW_TEXT to null,
                InputDataType.VOICE to null,
            ),
            InputDataType.entries.associateWith { it.toFilePickerType() },
        )
    }

    // ── What the screen OPENS on ──────────────────────────────────────────────────────────────────

    /**
     * The full precedence table of [resolveEntryMaterial], every door x prefilled/not.
     *
     * Pinned whole rather than per-case because the two halves fail in opposite, both-bad ways: lose
     * the prefill rule and a PHOTO door throws a file dialog over text the user can no longer see;
     * lose the door rule and the v2 capture dock's four pills all land on the source picker again,
     * which is the dead end this work removed.
     */
    @Test
    fun theScreenOpensOnTheDoorsMaterial_unlessTextWasPrefilled() {
        val doors: List<AnalyzeInputKind?> = listOf(null) + AnalyzeInputKind.entries

        assertEquals(
            doors.associateWith { it?.toInputDataType() },
            doors.associateWith { resolveEntryMaterial(initialText = null, initialInputKind = it) },
            "With nothing prefilled the door alone decides, and `null` (no door) must stay null — " +
                "that is the ordinary Templates -> \"Create with AI\" entry, which has to land on the " +
                "source picker.",
        )
        assertEquals(
            doors.associateWith { InputDataType.RAW_TEXT },
            doors.associateWith {
                resolveEntryMaterial(initialText = "buy milk", initialInputKind = it)
            },
            "Shared/selected text IS raw text and is already in the field. It outranks EVERY door, " +
                "including PHOTO — otherwise the photo picker opens on top of text that is now hidden.",
        )
    }

    /** Blank is not text: a whitespace-only extra must not hijack the door the user actually tapped. */
    @Test
    fun blankPrefillDoesNotCountAsText() {
        assertEquals(
            InputDataType.PHOTO,
            resolveEntryMaterial(initialText = "   ", initialInputKind = AnalyzeInputKind.PHOTO),
        )
        assertEquals(
            InputDataType.PHOTO,
            resolveEntryMaterial(initialText = "", initialInputKind = AnalyzeInputKind.PHOTO),
        )
    }
}

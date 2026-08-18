package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.analyze_source_change
import aichecklists.core.designsystem.generated.resources.analyze_source_link_short
import aichecklists.core.designsystem.generated.resources.analyze_source_pdf
import aichecklists.core.designsystem.generated.resources.analyze_source_photo
import aichecklists.core.designsystem.generated.resources.analyze_source_text_file_short
import aichecklists.core.designsystem.generated.resources.analyze_source_text_short
import aichecklists.core.designsystem.generated.resources.analyze_source_voice
import com.antonchuraev.homesearchchecklist.desingsystem.components.SourcePill
import com.antonchuraev.homesearchchecklist.desingsystem.components.SourcePillEntry
import com.antonchuraev.homesearchchecklist.desingsystem.components.SourcePillGrid
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.InputDataType
import org.jetbrains.compose.resources.stringResource

/**
 * The six materials, in the order they are offered.
 *
 * SHORT labels throughout ("File", "Link", "Text"), and that is a layout requirement rather than a
 * copy preference: the pills are equal-width, so the WIDEST label alone decides how many fit abreast.
 * Recorded with the long forms ("Text File", "Web Link", "Paste Text") the block measured 158dp at
 * 320dp/EN and 325dp at 320dp/RU × fontScale 1.5 — i.e. the compaction this screen exists for would
 * have failed exactly where the screen is most cramped. The long forms are not merely unread here —
 * they were deleted from all three locales with this redesign, so there is nothing left to pick by
 * mistake.
 *
 * Icons name FORMATS (`Image`, `PictureAsPdf`, `Description`, `Link`, `TextFields`, `Mic`) while the
 * capture dock's four doors name ACTIONS (`PhotoCamera`, `AttachFile`, …). The divergence is on
 * purpose: choosing between six formats needs the formats to be told apart, whereas the dock is
 * offering four things to do. Unifying the two sets is an owner call and would move the dock's
 * goldens, so it is not made here.
 */
@Composable
internal fun analyzeSourceEntries(): List<SourcePillEntry<InputDataType>> = listOf(
    SourcePillEntry(
        InputDataType.PHOTO,
        Icons.Outlined.Image,
        stringResource(Res.string.analyze_source_photo),
    ),
    SourcePillEntry(
        InputDataType.PDF,
        Icons.Outlined.PictureAsPdf,
        stringResource(Res.string.analyze_source_pdf),
    ),
    SourcePillEntry(
        InputDataType.TEXT_FILE,
        Icons.Outlined.Description,
        stringResource(Res.string.analyze_source_text_file_short),
    ),
    SourcePillEntry(
        InputDataType.WEB_LINK,
        Icons.Outlined.Link,
        stringResource(Res.string.analyze_source_link_short),
    ),
    SourcePillEntry(
        InputDataType.RAW_TEXT,
        Icons.Outlined.TextFields,
        stringResource(Res.string.analyze_source_text_short),
    ),
    SourcePillEntry(
        InputDataType.VOICE,
        Icons.Outlined.Mic,
        stringResource(Res.string.analyze_source_voice),
    ),
)

/**
 * "What am I handing the AI" — six pills while the answer is open, one pill once it is settled.
 *
 * ## What this replaced and why
 * Six full-width cards, 2 per row, ~332dp tall. On a 640dp phone that is the entire first screen:
 * the owner's report was "сверху огромные карточки и только ниже выбор вложения" — the editor for the
 * material you just chose started BELOW the fold, so every entry cost a scroll to reach the one
 * control the screen exists for. Six 48dp pills over two rows measure ~104dp, and the editor lands in
 * the first screen at every supported size.
 *
 * ## Two shapes, one block
 * - **[expanded]** — the measured grid, with the current material filled. The grid does not hide once
 *   a material is chosen; it collapses, and the editor under it stays put either way.
 * - **collapsed** — a single content-width pill carrying the current material and a chevron. This is
 *   the shape a user who came through a door ("Photo" in the capture dock) sees: they have already
 *   answered the question, so the screen does not ask it again — it only offers to change the answer.
 *   The price the owner accepted is that changing material from a door costs two taps rather than one.
 *
 * The block is never empty: with nothing selected it renders the grid regardless of [expanded], so a
 * stale `false` can never leave the screen with no way to choose anything.
 *
 * @param onExpandedChange UI-only. Opening the grid is not a material change and deliberately sends
 *   no intent — the ViewModel learns about a change only when one actually happens.
 * @param onTypeSelected fired only when the tapped material DIFFERS from the current one. Re-tapping
 *   the material you are already on is "never mind": it closes the grid and changes nothing, which
 *   also means it cannot discard the file you already picked (see the payload reset in
 *   [AnalyzeViewModel]).
 */
@Composable
internal fun AnalyzeSourcePicker(
    selectedType: InputDataType?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTypeSelected: (InputDataType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = analyzeSourceEntries()
    val active = selectedType?.let { type -> entries.firstOrNull { it.value == type } }

    // tween, not spring: `Motion.kt` prescribes the bezier equivalent for `animateContentSize`
    // because an unsettled spring re-measures the layout every frame — and this block lives inside a
    // LazyColumn, which would re-measure with it. `spatialDefault` rather than `spatialFast`: the
    // distance is 104↔48dp, well past the 48dp the fast token is scoped to.
    val sizeSpec: FiniteAnimationSpec<IntSize> =
        if (LocalReducedMotion.current) snap() else AppMotion.spatialDefaultTween()

    Box(modifier = modifier.animateContentSize(sizeSpec)) {
        if (active == null || expanded) {
            SourcePillGrid(
                entries = entries,
                onSelect = { type ->
                    if (type != selectedType) onTypeSelected(type)
                    onExpandedChange(false)
                },
                selected = selectedType,
            )
        } else {
            // The ROW spans the width; the PILL inside it does not. That is the whole point of the
            // wrapper: a start-aligned capsule that hugs its own content, with the rest of the line
            // left empty. A pill stretched to the full width would read as the screen's primary
            // button and compete with the real CTA below it.
            Row(modifier = Modifier.fillMaxWidth()) {
                SourcePill(
                    entry = active,
                    // The value is ignored on purpose — this pill's job is "open the grid", and the
                    // material it shows is the one already chosen.
                    onSelect = { onExpandedChange(true) },
                    // Quiet, not filled: this is not a selected item in a set of six, it is a
                    // control. Filled `primary` here was recorded and rejected — it reads as the
                    // screen's main action.
                    //
                    // `null`, not `false`: it renders identically, and it keeps `selected` out of the
                    // semantics — a control that was never part of a set must not be announced "not
                    // selected". See [SourcePill]'s `selected` param.
                    selected = null,
                    trailing = Icons.Outlined.ExpandMore,
                    // The visible word "Photo" does not say that tapping changes the source; the
                    // chevron says it only to people who can see it.
                    contentDescription = stringResource(Res.string.analyze_source_change, active.label),
                )
            }
        }
    }
}

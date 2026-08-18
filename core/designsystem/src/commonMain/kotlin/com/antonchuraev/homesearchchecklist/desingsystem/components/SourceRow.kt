package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.analyze_source_link_short
import aichecklists.core.designsystem.generated.resources.analyze_source_pdf
import aichecklists.core.designsystem.generated.resources.analyze_source_photo
import aichecklists.core.designsystem.generated.resources.analyze_source_voice
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * One pill in a [SourcePillGrid]: the value it stands for, its icon and its visible label.
 *
 * Generic in [T] because the two grids in the app choose from two different vocabularies — the entry
 * row picks an [AnalyzeInputKind] (a `core:common` door), the Analyze screen picks its own
 * `InputDataType` (a `feature:analyze` domain model) — and a design-system component may not import
 * a feature's model. Flat primitives plus an [ImageVector], nothing else: the moment this holds a
 * resource id or a feature enum the dependency direction inverts.
 *
 * The icon is decorative — the visible [label] right next to it is what a screen reader announces.
 */
data class SourcePillEntry<T>(val value: T, val icon: ImageVector, val label: String)

/**
 * [SourceRow] under the heading that says what the four pills are FOR.
 *
 * ## Why the heading is not optional decoration
 * Four bare pills reading "Photo · PDF · Link · Voice" directly under a task input are ambiguous in
 * the worst possible way: they read as "attach one of these to the task you are typing", which is a
 * DIFFERENT offer the app already serves (item attachments). The words are the only thing that turns
 * them into "hand me this instead and I will build a whole checklist" — the Play listing's own
 * promise. The Inbox's in-list door was shipped with a heading for exactly this reason; the capture
 * dock's copy of the row was not, and the owner reported it (2026-08-17).
 *
 * ## One component, two headings
 * The two hosts pass DIFFERENT titles and both are correct:
 *  - on the Inbox page the row is the screen's own promise → "Turn content into a checklist";
 *  - inside the capture dock it is the alternative to the task already being typed → "Or create a
 *    checklist from:".
 *
 * So the title is a parameter, not a resource read in here. What is shared — and what this component
 * exists to keep shared — is the TYPOGRAPHY and the gap between heading and pills. Two hand-rolled
 * `Column { Text; SourceRow }` blocks in two features is precisely how one of them ends up at
 * `labelMedium` with 4dp after the next round of tuning.
 *
 * The gap ABOVE the heading belongs to the HOST: on the Inbox it separates the block from the
 * add-task row above it (SpacingLg), in the dock it comes out of the input field's own vertical
 * padding. A section cannot know what it is standing under.
 *
 * @param title the heading. Must come from `strings.xml` — see the project's no-hardcoded-strings
 *   rule; it is a `String` rather than a resource id because this module is where the resources live
 *   and both hosts already resolve their own copy.
 * @param onSelect forwarded verbatim to [SourceRow]; the HOST still owns the `ai_entry_tapped` emit,
 *   because neither this component nor the row knows which surface it was mounted on.
 */
@Composable
fun SourceRowSection(
    title: String,
    onSelect: (AnalyzeInputKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            // `onSurface`, like the pills' own labels: this section is mounted on the page AND on the
            // bottom chrome, and those two are the same content role in both themes — see
            // AppChatColors, which only re-plans FILLS across planes, never ink.
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = AppDimens.SpacingSm),
        )
        SourceRow(onSelect = onSelect)
    }
}

/**
 * The "turn content into a checklist" entry row: four named materials, each opening the Analyze
 * flow with that source ALREADY chosen.
 *
 * Named by MATERIAL (Photo / PDF / Web Link / Voice) rather than by mechanism ("Analyze", "AI"),
 * and every label is visible rather than hidden behind an overflow or a "+". Both choices are
 * load-bearing: this row exists because the v2 shell shipped with no route to Analyze at all while
 * Analyze accounts for **half** of all checklist creators, and because the three materials that
 * were only reachable through a generic picker recorded no usage whatsoever.
 *
 * ## Layout contract
 * - Fills its parent's width and divides it into four EQUAL pills — the four materials are peers,
 *   so no one of them gets a wider tap target than the others.
 * - Steps down 4 abreast → 2 abreast → one column, and **every step is MEASURED, not a width
 *   threshold.** A dp constant would have been calibrated against English: "Photo / PDF / Link /
 *   Voice" fits 360dp, "Web Link" alone does not, and the RU/HI translations are longer again — so
 *   a hardcoded breakpoint silently truncates or wraps mid-word in exactly the locales nobody
 *   screenshots. Measuring the real pills at the real text scale handles font scale, translation
 *   length and window width with one rule and no constants to re-tune.
 *
 *   ⚠️ EVERY rung needs its own check. The 2×2 was once an unchecked `else`, and at 200dp ×
 *   fontScale 2.0 in RU it was picked while two pills did not in fact fit: "Photo" wrapped to two
 *   lines (72dp) next to a one-line "PDF" (48dp) — two peers at two different heights, which is the
 *   defect this row's equal-width rule exists to avoid in the first place.
 * - It never drops a label and never truncates one. The labels ARE the affordance: a generic
 *   "Analyze" door recorded **zero** photo/pdf/voice analyses in 30 days against 46 for raw text.
 * - Pills are [AppDimens.MinTouchTarget] as a MINIMUM, never a fixed height. `Modifier.height()`
 *   pins min AND max, which silently defeats `minimumInteractiveComponentSize()` and clips glyph
 *   descenders once the text outgrows the box — the exact defect the 38dp preset chips shipped.
 *
 * A single shared component rather than one copy per host: the capture dock and the Inbox empty
 * state show the same four doors, and two copies would let them drift into two different answers
 * to "what can I hand the AI".
 *
 * @param onSelect the material the user picked. The host is responsible for emitting
 *   `ai_entry_tapped` with its own [com.antonchuraev.homesearchchecklist.core.common.api.AiEntrySource]
 *   before navigating — this component reports WHAT was tapped, never WHERE, because it does not
 *   know which surface it was mounted on.
 */
@Composable
fun SourceRow(
    onSelect: (AnalyzeInputKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    SourcePillGrid(
        entries = listOf(
            SourcePillEntry(
                AnalyzeInputKind.PHOTO,
                Icons.Outlined.PhotoCamera,
                stringResource(Res.string.analyze_source_photo),
            ),
            SourcePillEntry(
                AnalyzeInputKind.PDF,
                Icons.Outlined.AttachFile,
                stringResource(Res.string.analyze_source_pdf),
            ),
            SourcePillEntry(
                AnalyzeInputKind.WEB_LINK,
                Icons.Outlined.Link,
                // The SHORT form ("Link"). The pills are equal width, so the widest label alone
                // decides whether four fit abreast on a 360dp window — and "Web Link", the long form
                // this replaced, measured just over the line and dropped the whole row to a 2x2 grid.
                stringResource(Res.string.analyze_source_link_short),
            ),
            SourcePillEntry(
                AnalyzeInputKind.VOICE,
                Icons.Outlined.Mic,
                stringResource(Res.string.analyze_source_voice),
            ),
        ),
        onSelect = onSelect,
        modifier = modifier,
        // The four doors are peers, not a choice with a current answer: this row OPENS Analyze, it
        // does not record what you last opened. `null` keeps every pill idle.
        selected = null,
    )
}

/**
 * The measured grid behind [SourceRow] and behind the Analyze screen's source picker: N equal pills,
 * arranged in as few rows as they fit in, with an optional current selection.
 *
 * ## Why this is one component and not two
 * The two call sites differ in what a tap MEANS — the entry row opens a flow, the Analyze picker
 * changes the screen's current material — and in nothing else. They share the pill, the equal-column
 * rule, the measured ladder, the probe and its `clearAndSetSemantics` trap, the 48dp minimum and the
 * "never truncate a material name" contract. A second, thinner copy for Analyze would re-open every
 * defect this file already closed, starting with the wrapped label beside an unwrapped one.
 *
 * ## Layout contract
 * - Fills its parent's width and divides it into EQUAL columns. Equal because the entries are peers:
 *   no one material may get a wider tap target than another.
 * - Steps down through [sourcePillRungs] — the fewest columns that still fits the entries into 1, 2,
 *   3 … rows — and **every rung is MEASURED, not a width threshold.** A dp constant would have been
 *   calibrated against English: "Photo / PDF / Link / Voice" fits 360dp, "Web Link" alone does not,
 *   and the RU/HI translations are longer again — so a hardcoded breakpoint silently truncates or
 *   wraps mid-word in exactly the locales nobody screenshots.
 *
 *   ⚠️ EVERY rung needs its own check. The 2×2 was once an unchecked `else`, and at 200dp ×
 *   fontScale 2.0 in RU it was picked while two pills did not in fact fit: "Photo" wrapped to two
 *   lines (72dp) next to a one-line "PDF" (48dp) — two peers at two different heights, which is the
 *   defect this grid's equal-width rule exists to avoid in the first place.
 * - Column width is COMPUTED, not `Modifier.weight(1f)`. Weight only produces equal pills while the
 *   rung divides the entry count exactly: with six entries and a rung of four, the two pills of the
 *   last row would each stretch to half the window — twice their peers directly above them. A width
 *   computed once from the constraints and reused by every row leaves a ragged row with an empty
 *   tail instead, which is the only harmless way for a row to be short. For a rung that DOES divide
 *   the count the arithmetic reproduces `weight(1f)` down to the pixel, remainder distribution
 *   included, so the four-door goldens do not move.
 * - It never drops a label and never truncates one. The labels ARE the affordance: a generic
 *   "Analyze" door recorded **zero** photo/pdf/voice analyses in 30 days against 46 for raw text.
 *
 * @param selected the entry value currently chosen, or `null` when this grid is a set of doors
 *   rather than a choice. Selection changes FILL and BORDER only — never padding, weight or glyphs —
 *   because the column width was measured from an idle pill and a selected pill that grew would no
 *   longer fit the column measured for it.
 */
@Composable
fun <T> SourcePillGrid(
    entries: List<SourcePillEntry<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    selected: T? = null,
) {
    SubcomposeLayout(modifier = modifier.fillMaxWidth()) { constraints ->
        val gapPx = AppDimens.SpacingSm.roundToPx()

        // Probe pass: measure the pills at their NATURAL width (unbounded constraints) to learn how
        // much the widest label actually needs, here, in this locale, at this font scale. N equal
        // pills only fit if the WIDEST one fits N times over — equal widths mean the widest label
        // sets the column, so probing the average would round down into a truncation.
        val widestPill = subcompose(SourceRowSlot.Probe) {
            entries.forEach {
                // `clearAndSetSemantics {}` is NOT cosmetic here. A subcomposed slot still
                // contributes to the semantics tree even though these copies are measured and never
                // placed, so without it every label exists TWICE: a screen reader announces eight
                // doors, and `onNodeWithText("Photo")` finds two nodes. Caught by
                // `sourceRow_exposesEachDoorExactlyOnce` — which is why that test exists.
                //
                // Probed IDLE even when something is selected: the selected style is fill-and-border
                // only, so an idle probe measures the selected pill correctly too, and a probe that
                // followed the selection would re-measure the whole grid on every tap.
                SourcePill(entry = it, onSelect = {}, modifier = Modifier.clearAndSetSemantics {})
            }
        }.maxOf { it.measure(Constraints()).width }

        // Every rung is MEASURED against the same rule — "a column must be at least as wide as the
        // widest pill needs" — so no arrangement is ever chosen that forces a label to wrap.
        //
        // Because each rung guarantees `columnWidth >= widestPill`, equal-height row-mates fall out
        // of the layout rather than being enforced afterwards; the `CenterVertically` below is the
        // cheap guard for the one case no rule can prevent — a single pill wider than the window.
        //
        // ⚠️ An UNBOUNDED width short-circuits to the widest rung instead of running the arithmetic.
        // `constraints.maxWidth` is `Constraints.Infinity` (Int.MAX_VALUE) there — a horizontally
        // scrollable parent, an unbounded `SubcomposeLayout`, or an intrinsic-measurement pass — and
        // both `widestPill * rung` and `maxWidth - gapPx * (perRow - 1)` overflow to a NEGATIVE Int,
        // which fails every fit check and silently collapses a four-door row to a single column. With
        // infinite room the answer is the top of the ladder by definition, so the branch is the true
        // answer and not a fallback. `fillMaxWidth` makes this unreachable from either host today; it
        // is one hoist into a scrollable row away from being reachable.
        val perRow = if (constraints.hasBoundedWidth) {
            sourcePillRungs(entries.size).firstOrNull { rung ->
                widestPill * rung + gapPx * (rung - 1) <= constraints.maxWidth
            } ?: 1
        } else {
            sourcePillRungs(entries.size).first()
        }

        val columns = if (constraints.hasBoundedWidth) {
            sourcePillColumnWidths(
                available = constraints.maxWidth - gapPx * (perRow - 1),
                perRow = perRow,
            )
        } else {
            // Nothing to divide: every column is exactly the widest pill, and the row ends where the
            // content does.
            IntArray(perRow) { widestPill }
        }

        val content = subcompose(SourceRowSlot.Content) {
            // One Column for every rung, not a branch per shape: with a single row the Column
            // measures identically to the bare Row it replaced, and a single code path cannot grow
            // an arrangement that skips the fit check.
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
                entries.chunked(perRow).forEach { group ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        group.forEachIndexed { column, entry ->
                            SourcePill(
                                entry = entry,
                                onSelect = onSelect,
                                // Same column widths in EVERY row, so a short last row keeps its
                                // pills the size of their peers above and simply ends early.
                                modifier = Modifier.width(columns[column].toDp()),
                                // `null` when this grid has no current answer, so its pills are not
                                // announced "not selected" — see [SourcePill]'s `selected` param. A
                                // grid WITH an answer reports true/false for every pill.
                                selected = selected?.let { entry.value == it },
                            )
                        }
                    }
                }
            }
        }.first().measure(constraints)

        layout(content.width, content.height) { content.place(0, 0) }
    }
}

/**
 * The arrangements a grid of [count] pills is allowed to take, widest first.
 *
 * "The fewest columns that still fits everything into 1 row, then 2, then 3 …". For four entries
 * that is `4, 2, 1` — byte-for-byte the ladder [SourceRow] shipped with — and for six it is
 * `6, 3, 2, 1`.
 *
 * Derived rather than listed as divisors, because divisors alone break on a prime count: the only
 * divisors of five are 5 and 1, so dropping one material would send the grid from "five abreast"
 * straight to a 328dp-tall single column with nothing in between. This rule yields `5, 3, 2, 1`
 * there, and the ragged rows it produces are safe precisely because the column width is computed
 * rather than weighted.
 */
internal fun sourcePillRungs(count: Int): List<Int> {
    if (count <= 1) return listOf(1)
    // rows = 1..count; the columns each row count needs is ceil(count / rows). Distinct, descending.
    return (1..count).map { rows -> (count + rows - 1) / rows }.distinct()
}

/**
 * Splits [available] px into [perRow] columns that tile it exactly.
 *
 * Reproduces what `Row` does for `Modifier.weight(1f)` children — round the even share, then hand
 * the leftover pixels one at a time to the leading columns — so replacing weights with explicit
 * widths cannot shift an existing golden by a pixel. What it adds is that the SAME widths are then
 * reused by a short final row, which weights cannot express.
 */
internal fun sourcePillColumnWidths(available: Int, perRow: Int): IntArray {
    if (perRow <= 1) return intArrayOf(available.coerceAtLeast(0))
    val base = (available.toFloat() / perRow).roundToInt()
    var remainder = available - base * perRow
    return IntArray(perRow) {
        val unit = when {
            remainder > 0 -> 1
            remainder < 0 -> -1
            else -> 0
        }
        remainder -= unit
        (base + unit).coerceAtLeast(0)
    }
}

private enum class SourceRowSlot { Probe, Content }

/**
 * One capsule: icon, label, and optionally a trailing affordance glyph.
 *
 * ## Selection is spelled in three channels, none of them hue
 * 1. **Lightness inversion** — a dark capsule among near-white ones in light, a light one among dark
 *    ones in dark. For a deuteranope that is the strongest channel available.
 * 2. **The border disappears.** The outline is present on every idle pill and absent on the selected
 *    one, so the shape's edge changes, not just its colour.
 * 3. **`semantics { selected }`** — without it TalkBack announces six identical buttons. This is the
 *    same gap that was closed in `SelectablePromptChipItem`, and the fix is copied from there.
 *
 * ⛔ Nothing about [selected] may change GEOMETRY — not a check glyph, not `FontWeight`, not
 * padding. [SourcePillGrid] measures its columns from an idle probe; a pill that grew on selection
 * would stop fitting the column measured for it, and tapping "Link" would drop its label to a second
 * line. Fill and border only, deliberately.
 *
 * @param selected three states, not two. `true` / `false` mean "a member of a selectable set, and it
 *   is / is not the current answer"; **`null` means the pill is not a member of a set at all** — a
 *   door that opens a flow, or the collapsed "change source" control. Only `true` and `false` write
 *   `semantics { selected }`; `null` writes nothing, because a `selected = false` on a door makes
 *   TalkBack announce "not selected" about a thing that was never selectable, which is a statement
 *   about state where there is no state. It renders identically to `false`.
 * @param trailing an affordance glyph after the label — today only the chevron on the Analyze
 *   screen's collapsed control, which is the one place a pill means "tap to change this" rather than
 *   "tap to choose this".
 * @param contentDescription REPLACES the accessible name. `Modifier.clickable` merges this capsule's
 *   descendants, so the label is part of the capsule's own node rather than a second one — set a
 *   description and a screen reader reads it INSTEAD of the label, not after it. Leave `null` in a
 *   grid: there the visible label is already the right name and a description would only be a second
 *   place to keep the same noun. The collapsed control is the exception — "Photo" alone does not say
 *   that tapping it changes the source.
 */
@Composable
fun <T> SourcePill(
    entry: SourcePillEntry<T>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    trailing: ImageVector? = null,
    contentDescription: String? = null,
) {
    // Aliased because inside `semantics { }` the names `selected` and `contentDescription` resolve to
    // the semantics properties being assigned, not to these parameters. The compiler stays silent
    // about it and the state simply never reaches TalkBack.
    val isSelected = selected
    val accessibleName = contentDescription
    // `null` renders exactly like `false` — see the [selected] KDoc. Only the SEMANTICS differ.
    val isFilled = selected == true
    val labelColor = if (isFilled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val iconTint = if (isFilled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        // Plane-relative, and outlined as well as filled. This row's two hosts sit on DIFFERENT
        // surfaces — the quick-capture dock paints `AppSurface.bottomChrome()`, the Inbox empty state
        // is the bare page — and a fixed `surfaceContainer` is only ever right on one of them: it
        // measured ΔL* ≈ 2 off the dark chrome, so the four pills dissolved into the dock in the one
        // place they matter most (this row is the v2 shell's ONLY route into Analyze).
        //
        // The outline is new with the fill. `surfaceContainer` carried the row alone because it was a
        // step DOWN from the light page; `raised()` is a step UP, and on the near-white page that step
        // is +1.7 — invisible on its own. Every tappable edge in this system takes the same firm
        // `controlOutline`, which is also what makes these four read as peers of the chat's chips
        // rather than as a fifth kind of button. See AppChatColors.
        //
        // ## The argument this replaced, and why it does not reproduce
        // The pills used to be filled and NOT outlined, on the reasoning that "an outline reads as a
        // decorative frame around the input above it rather than as four things you can press" — and
        // the host that reasoning was written for is exactly this one: `QuickCaptureDock` puts an
        // `AddItemInputField` (an `OutlinedTextField`, same `outline` role, same 1dp) directly above
        // the row. Re-checked on `SourceRowScreenshotTest.dock_withSources_360dp_light/dark` at 3x,
        // it does not happen, and the reason is the fill that arrived WITH the outline: the field is a
        // wide EMPTY box carried by its ring alone, each pill is a small capsule whose `#FFFFFF` fill
        // is +12.2 off the `#DEDCD6` chrome (+5.9 in dark) and is the dominant channel. Different
        // shape, different scale, different fill — nothing in the frame closes into one enclosing
        // frame. Softening these to `contentOutline` would drop the ring to 1.04 : 1 on the light
        // chrome and leave the v2 shell's ONLY route into Analyze as four fill-only capsules, which
        // is a weaker control bought against a reading the recorded frames do not show.
        // A selected pill fills solid `primary` with no border at all — the same two-channel active
        // state the chat's chips use, so no new visual dialect appears for this one grid.
        color = if (isFilled) MaterialTheme.colorScheme.primary else AppChatColors.raised(),
        border = if (isFilled) {
            null
        } else {
            BorderStroke(AppDimens.DividerThickness, AppChatColors.controlOutline())
        },
        shape = RoundedCornerShape(percent = 50),
        modifier = modifier
            // MIN height, not a fixed one — see the layout contract in [SourcePillGrid]'s KDoc.
            .heightIn(min = AppDimens.MinTouchTarget)
            // role = Button so the accessible node is announced as pressable. It also MERGES this
            // capsule's descendants, which is what makes the icon decorative and the label part of
            // this one node — in a grid that label is the accessible name and no contentDescription
            // is needed.
            .clickable(role = Role.Button) { onSelect(entry.value) }
            .semantics {
                // Only when this pill really is a member of a selectable set. A door written as
                // `selected = false` is announced "not selected", which is a claim about state on a
                // thing that has none — see the [selected] KDoc.
                if (isSelected != null) this.selected = isSelected
                if (accessibleName != null) this.contentDescription = accessibleName
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = AppDimens.SpacingSm, vertical = AppDimens.SpacingXs),
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.width(SourceIconSize).heightIn(min = SourceIconSize),
            )
            Spacer(Modifier.width(AppDimens.SpacingXs))
            Text(
                text = entry.label,
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
                textAlign = TextAlign.Center,
                // No maxLines/ellipsis: a truncated material name defeats the whole point of the
                // row. If a label cannot fit, the WRAP threshold above is what must move.
                //
                // No semantics handling here, and that is measured rather than assumed: `Modifier
                // .clickable` sets `MergeDescendants = true`, so this Text does NOT become a second
                // accessible node — it merges into the capsule, which then carries `Text = '[Photo]'`
                // alongside any [contentDescription], and the description simply wins. Dumped from
                // the real tree on 2026-08-17 (one node per pill, 74x48px, `MergeDescendants = 'true'`)
                // after a review reported a double announcement here. Clearing this Text would take
                // the label out of the capsule's accessible node and out of every `onNodeWithText`
                // lookup for no gain.
            )
            if (trailing != null) {
                Spacer(Modifier.width(AppDimens.SpacingXs))
                Icon(
                    imageVector = trailing,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.width(SourceIconSize).heightIn(min = SourceIconSize),
                )
            }
        }
    }
}

private val SourceIconSize = 18.dp

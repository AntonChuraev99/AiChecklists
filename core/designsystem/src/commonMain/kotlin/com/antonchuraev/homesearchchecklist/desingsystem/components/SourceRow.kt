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

/** Icon is decorative — the visible label right next to it is what a screen reader announces. */
private data class SourceEntry(val kind: AnalyzeInputKind, val icon: ImageVector, val label: String)

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
    val entries = listOf(
        SourceEntry(
            AnalyzeInputKind.PHOTO,
            Icons.Outlined.PhotoCamera,
            stringResource(Res.string.analyze_source_photo),
        ),
        SourceEntry(
            AnalyzeInputKind.PDF,
            Icons.Outlined.AttachFile,
            stringResource(Res.string.analyze_source_pdf),
        ),
        SourceEntry(
            AnalyzeInputKind.WEB_LINK,
            Icons.Outlined.Link,
            // The SHORT form ("Link"), not analyze_source_link ("Web Link"): the pills are equal
            // width, so the widest label alone decides whether four fit abreast on a 360dp window —
            // and "Web Link" measured just over the line, dropping the whole row to a 2x2 grid.
            stringResource(Res.string.analyze_source_link_short),
        ),
        SourceEntry(
            AnalyzeInputKind.VOICE,
            Icons.Outlined.Mic,
            stringResource(Res.string.analyze_source_voice),
        ),
    )

    SubcomposeLayout(modifier = modifier.fillMaxWidth()) { constraints ->
        val gapPx = AppDimens.SpacingSm.roundToPx()

        // Probe pass: measure the pills at their NATURAL width (unbounded constraints) to learn how
        // much the widest label actually needs, here, in this locale, at this font scale. Four equal
        // pills only fit if the WIDEST one fits four times over — equal widths mean the widest label
        // sets the column, so probing the average would round down into a truncation.
        val widestPill = subcompose(SourceRowSlot.Probe) {
            entries.forEach {
                // `clearAndSetSemantics {}` is NOT cosmetic here. A subcomposed slot still
                // contributes to the semantics tree even though these copies are measured and never
                // placed, so without it every label exists TWICE: a screen reader announces eight
                // doors, and `onNodeWithText("Photo")` finds two nodes. Caught by
                // `sourceRow_exposesEachDoorExactlyOnce` — which is why that test exists.
                SourcePill(entry = it, onSelect = {}, modifier = Modifier.clearAndSetSemantics {})
            }
        }.maxOf { it.measure(Constraints()).width }

        // Every rung is MEASURED against the same rule — "a column must be at least as wide as the
        // widest pill needs" — so no arrangement is ever chosen that forces a label to wrap. The 2×2
        // used to be an unchecked `else`, which is precisely how a wrapped two-line "Photo" ended up
        // beside a one-line "PDF" at 200dp × fontScale 2.0 (SourceRowFitTest pins the measurement).
        //
        // Because each rung guarantees `columnWidth >= widestPill`, equal-height row-mates fall out
        // of the layout rather than being enforced afterwards; the `CenterVertically` below is the
        // cheap guard for the one case no rule can prevent — a single pill wider than the window.
        val perRow = when {
            widestPill * 4 + gapPx * 3 <= constraints.maxWidth -> 4
            widestPill * 2 + gapPx <= constraints.maxWidth -> 2
            else -> 1
        }

        val content = subcompose(SourceRowSlot.Content) {
            // One Column for all three rungs, not a branch per shape: with `perRow = 4` the Column
            // holds a single Row and measures identically to the bare Row it replaced, and a single
            // code path cannot grow a fourth arrangement that skips the fit check.
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
                entries.chunked(perRow).forEach { group ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        group.forEach { entry ->
                            SourcePill(entry, onSelect, Modifier.weight(1f))
                        }
                    }
                }
            }
        }.first().measure(constraints)

        layout(content.width, content.height) { content.place(0, 0) }
    }
}

private enum class SourceRowSlot { Probe, Content }

@Composable
private fun SourcePill(
    entry: SourceEntry,
    onSelect: (AnalyzeInputKind) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        color = AppChatColors.raised(),
        border = BorderStroke(AppDimens.DividerThickness, AppChatColors.controlOutline()),
        shape = RoundedCornerShape(percent = 50),
        modifier = modifier
            // MIN height, not a fixed one — see the layout contract in [SourceRow]'s KDoc.
            .heightIn(min = AppDimens.MinTouchTarget)
            // role = Button so the accessible node is announced as pressable. No contentDescription
            // is set anywhere: the pill's visible Text IS its accessible name, and adding one would
            // make a screen reader announce the same noun twice.
            .clickable(role = Role.Button) { onSelect(entry.kind) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = AppDimens.SpacingSm, vertical = AppDimens.SpacingXs),
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(SourceIconSize).heightIn(min = SourceIconSize),
            )
            Spacer(Modifier.width(AppDimens.SpacingXs))
            Text(
                text = entry.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                // No maxLines/ellipsis: a truncated material name defeats the whole point of the
                // row. If a label cannot fit, the WRAP threshold above is what must move.
            )
        }
    }
}

private val SourceIconSize = 18.dp

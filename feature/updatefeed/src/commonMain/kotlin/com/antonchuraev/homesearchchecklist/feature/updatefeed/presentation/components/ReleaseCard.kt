package com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.UpdatePostAction
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.VersionReleaseGroup

/**
 * A collapsible release card in the Google Play release-notes style.
 *
 * One card = one app version. The header shows "Version X.Y" with a chevron indicator and is
 * always tappable to expand/collapse the body. Default state: **expanded**.
 *
 * The body contains:
 * 1. Optional [VersionReleaseGroup.storeDescription] — ditto Google Play release notes text,
 *    with emoji and newlines rendered natively.
 * 2. A [HorizontalDivider] separating the store description from feature rows (when both exist).
 * 3. [FeatureItem] rows for each post in the group, separated by thin dividers.
 *
 * When [VersionReleaseGroup.posts] is empty (bug-fix-only release), only the store description
 * is rendered — no feature rows, no empty-state placeholder.
 *
 * Expanded state survives scroll-recycling inside LazyColumn because `rememberSaveable` stores
 * per-key state via the list's SaveableStateHolder; also survives process death as a bonus.
 */
@Composable
internal fun ReleaseCard(
    release: VersionReleaseGroup,
    onActionClick: (postId: String, action: UpdatePostAction) -> Unit,
    modifier: Modifier = Modifier,
    lockedActionDeepLinks: Set<String> = emptySet()
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val headerContentDescription = "Version ${release.version} release notes, " +
        if (expanded) "expanded, tap to collapse" else "collapsed, tap to expand"
    val interactionSource = remember { MutableInteractionSource() }

    // Tap target covers the whole card (including CardPadding) via a clickable lifted onto the
    // outer modifier. `indication = null` removes the ripple per product decision — the chevron
    // rotation is the sole affordance.
    AppCard(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { expanded = !expanded }
            )
            .semantics { contentDescription = headerContentDescription }
    ) {
        // No spacedBy on the outer Column — header-to-body gap lives INSIDE AnimatedVisibility
        // (as padding on its inner Column) so the 12dp space collapses together with the body
        // instead of snapping to zero once the exit animation completes.
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Version ${release.version}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = if (expanded) 180f else 0f
                    }
                )
            }

            // Body — animated visibility driven by expanded state
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = AppDimens.SpacingMd),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
                ) {
                    // Google Play store description (release notes text)
                    if (release.storeDescription != null) {
                        Text(
                            text = release.storeDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Divider only when there are feature rows below
                        if (release.posts.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }

                    // Feature items separated by dividers
                    release.posts.forEachIndexed { index, post ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                        FeatureItem(
                            post = post,
                            lockedActionDeepLinks = lockedActionDeepLinks,
                            onActionClick = { action -> onActionClick(post.id, action) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

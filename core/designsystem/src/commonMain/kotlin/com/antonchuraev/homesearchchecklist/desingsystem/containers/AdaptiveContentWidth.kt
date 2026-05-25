package com.antonchuraev.homesearchchecklist.desingsystem.containers

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Constrains content to a max width and centers it inside parent. Use on
 * scrolling content of screens shown on desktop / tablet landscape — without
 * this, text columns stretch across 1920px viewport and become unreadable.
 *
 * Defaults: 720dp suits single-pane (one main column of body text + cards).
 * Pass 1200dp for list-detail content where two stacked panes share viewport.
 *
 * Use `.adaptiveContentWidth()` on the outermost Column/LazyColumn modifier.
 * Internally implemented as `widthIn(max = X)` — Compose will center this on
 * single-pane layouts because parent (NavDisplay or PermanentDrawer-wrapped Box)
 * doesn't apply additional alignment.
 */
fun Modifier.adaptiveContentWidth(maxWidthDp: Int = 720) =
    this.widthIn(max = maxWidthDp.dp)

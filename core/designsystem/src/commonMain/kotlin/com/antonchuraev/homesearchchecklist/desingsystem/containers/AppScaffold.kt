package com.antonchuraev.homesearchchecklist.desingsystem.containers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import org.jetbrains.compose.resources.stringResource

/**
 * App-level scaffold wrapper that centralises TopAppBar configuration, system-inset
 * handling, and adaptive TopAppBar type selection.
 *
 * @param scrollBehavior When non-null, the TopAppBar will collapse on scroll using
 *   [TopAppBarDefaults.exitUntilCollapsedScrollBehavior]. On Compact, a
 *   [CenterAlignedTopAppBar] is used; on Medium/Expanded, a [MediumTopAppBar] (denser,
 *   larger title area — better information density on tablet/desktop). The nested scroll
 *   connection is automatically applied to the Box content area so callers only need to
 *   pass the LazyColumn/Column with no extra modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String? = null,
    onBackButtonClick: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    content: @Composable () -> Unit
) {
    val windowSizeClass = rememberAppWindowSizeClass()
    val isCompact = windowSizeClass == AppWindowSizeClass.Compact

    val resolvedNavigationIcon: @Composable () -> Unit = {
        when {
            navigationIcon != null -> navigationIcon()
            onBackButtonClick != null -> {
                IconButton(onClick = onBackButtonClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = stringResource(Res.string.back),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    val topBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = snackbarHost,
        topBar = {
            if (title != null || onBackButtonClick != null || navigationIcon != null) {
                if (isCompact) {
                    CenterAlignedTopAppBar(
                        title = {
                            title?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        },
                        navigationIcon = resolvedNavigationIcon,
                        colors = topBarColors,
                        actions = { actions() },
                        scrollBehavior = scrollBehavior,
                    )
                } else {
                    MediumTopAppBar(
                        title = {
                            title?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        },
                        navigationIcon = resolvedNavigationIcon,
                        colors = TopAppBarDefaults.mediumTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        actions = { actions() },
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        },
        bottomBar = bottomBar
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .then(
                    if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    else Modifier
                )
        ) {
            content.invoke()
        }
    }
}

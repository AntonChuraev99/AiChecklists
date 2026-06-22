package com.antonchuraev.homesearchchecklist.feature.debug.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingsScreen(
    viewModel: OnboardingsViewModel = koinViewModel()
) {
    AppScaffold(
        title = stringResource(Res.string.debug_onboardings_title),
        onBackButtonClick = { viewModel.sendIntent(OnboardingsIntent.OnBack) }
    ) {
        // Column + verticalScroll instead of LazyColumn — same reasoning as ScreenCatalogScreen:
        // Compose Testing's performScrollTo() needs all nodes present in the semantic tree.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            // ── Variants ──────────────────────────────────────────────────
            SectionHeader(stringResource(Res.string.debug_onboardings_section_variants))

            CatalogItem(
                testTag = "onboardings_variant_interactive",
                title = stringResource(Res.string.debug_onboardings_interactive_title),
                subtitle = stringResource(Res.string.debug_onboardings_interactive_desc),
                isDisabled = false,
                onClick = { viewModel.sendIntent(OnboardingsIntent.LaunchVariant(OnboardingVariant.Interactive)) }
            )

            CatalogItem(
                testTag = "onboardings_variant_slides",
                title = stringResource(Res.string.debug_onboardings_slides_title),
                subtitle = stringResource(Res.string.debug_onboardings_slides_desc),
                isDisabled = false,
                onClick = { viewModel.sendIntent(OnboardingsIntent.LaunchVariant(OnboardingVariant.Slides)) }
            )

            CatalogItem(
                testTag = "onboardings_variant_ai_welcome",
                title = stringResource(Res.string.debug_onboardings_ai_welcome_title),
                subtitle = stringResource(Res.string.debug_onboardings_ai_welcome_desc),
                isDisabled = false,
                onClick = { viewModel.sendIntent(OnboardingsIntent.LaunchVariant(OnboardingVariant.AiWelcome)) }
            )
        }
    }
}

package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.analyze_error_analysis_failed
import aichecklists.core.designsystem.generated.resources.analyze_error_checklist_gone
import aichecklists.core.designsystem.generated.resources.analyze_error_invalid_input
import aichecklists.core.designsystem.generated.resources.analyze_error_input_too_large
import aichecklists.core.designsystem.generated.resources.analyze_error_input_unreadable
import aichecklists.core.designsystem.generated.resources.analyze_error_interrupted
import aichecklists.core.designsystem.generated.resources.analyze_error_network
import aichecklists.core.designsystem.generated.resources.analyze_error_nothing_found
import aichecklists.core.designsystem.generated.resources.analyze_error_server
import aichecklists.core.designsystem.generated.resources.analyze_error_user_not_ready
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobFailure
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * The ONE mapping of an analysis failure to the text the user reads (DESIGN_SPEC G3) — used by the
 * Analyze screen's Failed phase and by the Android "couldn't create a checklist" notification, so the
 * two never say different things. Transport markers (`http_*`, `network`) are never shown raw;
 * credits / daily limit keep the server's own actionable message.
 *
 * [resolve] defaults to Compose Resources' `getString`; tests pass a resolver because resources do not
 * load on a plain JVM host.
 */
suspend fun AnalysisJobFailure.userFacingMessage(
    resolve: suspend (StringResource) -> String = { getString(it) },
): String = when (reason) {
    AiFailureReason.NOTHING_FOUND -> resolve(Res.string.analyze_error_nothing_found)
    AiFailureReason.INPUT_TOO_LARGE -> resolve(Res.string.analyze_error_input_too_large)
    AiFailureReason.INPUT_UNREADABLE -> resolve(Res.string.analyze_error_input_unreadable)
    AiFailureReason.INTERRUPTED -> resolve(Res.string.analyze_error_interrupted)
    AiFailureReason.NETWORK, AiFailureReason.TIMEOUT -> resolve(Res.string.analyze_error_network)
    AiFailureReason.SERVER_5XX, AiFailureReason.AUTH_403 -> resolve(Res.string.analyze_error_server)
    AiFailureReason.USER_NOT_READY -> resolve(Res.string.analyze_error_user_not_ready)
    AiFailureReason.TARGET_MISSING -> resolve(Res.string.analyze_error_checklist_gone)
    AiFailureReason.INVALID_INPUT -> resolve(Res.string.analyze_error_invalid_input)
    AiFailureReason.CREDIT_GATE,
    AiFailureReason.DAILY_LIMIT,
    AiFailureReason.INPUT_TOO_LONG,
    AiFailureReason.CANCELLED,
    AiFailureReason.UNKNOWN,
    -> serverMessage?.takeIf { it.isNotBlank() && !it.startsWith("http_") }
        ?: resolve(Res.string.analyze_error_analysis_failed)
}

/** Buttons of the Failed phase for this failure (DESIGN_SPEC G1). */
val AnalysisJobFailure.failedCta: FailedCta
    get() = when (reason) {
        AiFailureReason.CREDIT_GATE,
        AiFailureReason.DAILY_LIMIT,
        AiFailureReason.CANCELLED,
        AiFailureReason.TARGET_MISSING,
        -> FailedCta.Dismiss
        AiFailureReason.INVALID_INPUT,
        AiFailureReason.NOTHING_FOUND,
        AiFailureReason.INPUT_TOO_LARGE,
        AiFailureReason.INPUT_UNREADABLE,
        AiFailureReason.INTERRUPTED,
        AiFailureReason.INPUT_TOO_LONG,
        -> FailedCta.ChangeInput
        AiFailureReason.NETWORK,
        AiFailureReason.TIMEOUT,
        AiFailureReason.SERVER_5XX,
        AiFailureReason.AUTH_403,
        AiFailureReason.USER_NOT_READY,
        AiFailureReason.UNKNOWN,
        -> FailedCta.Retry
    }

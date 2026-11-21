package com.antonchuraev.homesearchchecklist.feature.analyze.domain.model

/**
 * Analyze material that is already in memory — what a background job sends after reading its
 * captured payload (the picker's path may be gone by then). [AnalyzeInputData] is the path-based
 * twin used by the synchronous callers (AI-chat attachments, onboarding).
 */
sealed interface PreparedAnalyzeInput {
    data class Text(val text: String) : PreparedAnalyzeInput

    data class Url(val url: String) : PreparedAnalyzeInput

    /** JPEG after the job's downscale, or the original bytes when the image could not be decoded. */
    class Image(val bytes: ByteArray) : PreparedAnalyzeInput

    class Pdf(val bytes: ByteArray, val fileName: String) : PreparedAnalyzeInput

    class Audio(val bytes: ByteArray) : PreparedAnalyzeInput
}

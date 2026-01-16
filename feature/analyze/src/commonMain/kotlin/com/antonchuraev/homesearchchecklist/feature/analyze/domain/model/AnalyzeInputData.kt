package com.antonchuraev.homesearchchecklist.feature.analyze.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents different types of input data that can be analyzed by AI
 * to automatically fill checklist items.
 */
@Serializable
sealed interface AnalyzeInputData {

    /**
     * Photo input - user takes or selects a photo of property listing
     * @param filePath Local file path to the image
     * @param mimeType Image MIME type (e.g., "image/jpeg", "image/png")
     */
    @Serializable
    data class Photo(
        val filePath: String,
        val mimeType: String = "image/jpeg"
    ) : AnalyzeInputData

    /**
     * PDF document input - user selects a PDF file with property details
     * @param filePath Local file path to the PDF
     * @param fileName Original file name
     */
    @Serializable
    data class PdfDocument(
        val filePath: String,
        val fileName: String
    ) : AnalyzeInputData

    /**
     * Text file input - user selects a text file with property description
     * @param filePath Local file path to the text file
     * @param content Extracted text content (optional, can be loaded lazily)
     */
    @Serializable
    data class TextFile(
        val filePath: String,
        val content: String? = null
    ) : AnalyzeInputData

    /**
     * URL link input - user provides a link to property listing website
     * @param url The URL to the property listing
     */
    @Serializable
    data class WebLink(
        val url: String
    ) : AnalyzeInputData

    /**
     * Raw text input - user manually enters text description
     * @param text The raw text entered by user
     */
    @Serializable
    data class RawText(
        val text: String
    ) : AnalyzeInputData

    /**
     * Audio input - user records voice message for analysis
     * @param filePath Local file path to the audio file
     * @param mimeType Audio MIME type (e.g., "audio/m4a", "audio/mp4")
     */
    @Serializable
    data class Audio(
        val filePath: String,
        val mimeType: String = "audio/m4a"
    ) : AnalyzeInputData
}

/**
 * Enum representing the type of input for UI selection
 */
enum class InputDataType {
    PHOTO,
    PDF,
    TEXT_FILE,
    WEB_LINK,
    RAW_TEXT,
    VOICE
}

package com.antonchuraev.homesearchchecklist.feature.analyze.data.analyzer

import com.antonchuraev.homesearchchecklist.feature.analyze.data.config.ApiKeyProvider
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.analyzer.AiAnalyzer
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import dev.shreyaspatil.ai.client.generativeai.GenerativeModel
import dev.shreyaspatil.ai.client.generativeai.type.PlatformImage
import dev.shreyaspatil.ai.client.generativeai.type.content
import kotlin.reflect.KClass

/**
 * Implementation of AiAnalyzer using Google Gemini API.
 * Analyzes various input types and extracts checklist items.
 */
class GeminiAiAnalyzer(
    private val apiKeyProvider: ApiKeyProvider
) : AiAnalyzer {

    private val apiKey: String by lazy { apiKeyProvider.getGeminiApiKey() }

    private val textModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey
        )
    }

    private val visionModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey
        )
    }

    override suspend fun analyze(
        inputData: AnalyzeInputData,
        targetChecklist: Checklist?
    ): Result<AnalyzeResult> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Gemini API key not configured"))
        }

        return try {
            val response = when (inputData) {
                is AnalyzeInputData.RawText -> analyzeText(inputData.text, targetChecklist)
                is AnalyzeInputData.WebLink -> analyzeWebLink(inputData.url, targetChecklist)
                is AnalyzeInputData.Photo -> analyzePhoto(inputData, targetChecklist)
                is AnalyzeInputData.PdfDocument -> analyzePdf(inputData, targetChecklist)
                is AnalyzeInputData.TextFile -> analyzeTextFile(inputData, targetChecklist)
                is AnalyzeInputData.Audio -> analyzeAudio(inputData, targetChecklist)
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isAvailable(): Boolean {
        return apiKey.isNotBlank()
    }

    override fun getSupportedInputTypes(): Set<KClass<out AnalyzeInputData>> = setOf(
        AnalyzeInputData.Photo::class,
        AnalyzeInputData.PdfDocument::class,
        AnalyzeInputData.TextFile::class,
        AnalyzeInputData.WebLink::class,
        AnalyzeInputData.RawText::class,
        AnalyzeInputData.Audio::class
    )

    private suspend fun analyzeText(text: String, targetChecklist: Checklist?): AnalyzeResult {
        val prompt = buildPrompt(
            contentDescription = "следующий текст",
            content = text,
            targetChecklist = targetChecklist
        )

        val response = textModel.generateContent(prompt)
        return parseResponse(response.text ?: "")
    }

    private suspend fun analyzeWebLink(url: String, targetChecklist: Checklist?): AnalyzeResult {
        val prompt = buildPrompt(
            contentDescription = "контент по ссылке: $url",
            content = "Проанализируй веб-страницу по адресу: $url",
            targetChecklist = targetChecklist
        )

        val response = textModel.generateContent(prompt)
        return parseResponse(response.text ?: "")
    }

    private suspend fun analyzePhoto(photo: AnalyzeInputData.Photo, targetChecklist: Checklist?): AnalyzeResult {
        val imageBytes = readFileBytes(photo.filePath)
        if (imageBytes == null) {
            return AnalyzeResult(
                suggestedItems = emptyList(),
                confidence = 0f,
                summary = "Не удалось прочитать изображение",
                warnings = listOf("Файл не найден или недоступен: ${photo.filePath}")
            )
        }

        val contextPrompt = if (targetChecklist != null) {
            "Контекст: существующий чек-лист '${targetChecklist.name}' с пунктами: ${targetChecklist.items.joinToString { it.text }}"
        } else ""

        val inputContent = content {
            image(PlatformImage(imageBytes))
            text("""
                Look at this image carefully. Based on what you see in the image, create a practical checklist.

                If the image contains text (a document, recipe, instructions, etc.), extract actionable items from that text.
                If the image shows objects or a scene (e.g. a fridge with food, a room, a suitcase), create a checklist relevant to what you see (e.g. grocery list, inspection checklist, packing list).

                $contextPrompt

                Rules:
                1. Each item must be specific and actionable
                2. Respond in English
                3. Format: each item on a new line, starting with "- "
                4. Only the list of items, no extra text before or after
                5. Maximum 10 items

                Example format:
                - Buy milk
                - Check expiration dates
            """.trimIndent())
        }

        val response = visionModel.generateContent(inputContent)
        return parseResponse(response.text ?: "")
    }

    private suspend fun analyzePdf(pdf: AnalyzeInputData.PdfDocument, targetChecklist: Checklist?): AnalyzeResult {
        // For PDF, we'll extract text-based analysis
        // Note: Full PDF parsing would require additional libraries
        val prompt = buildPrompt(
            contentDescription = "PDF документ '${pdf.fileName}'",
            content = "Создай чек-лист на основе типичного содержимого документа типа '${pdf.fileName}'",
            targetChecklist = targetChecklist
        )

        val response = textModel.generateContent(prompt)
        return parseResponse(response.text ?: "",
            warnings = listOf("PDF анализ ограничен. Для полного анализа используйте текстовый ввод."))
    }

    private suspend fun analyzeTextFile(textFile: AnalyzeInputData.TextFile, targetChecklist: Checklist?): AnalyzeResult {
        val content = textFile.content ?: readFileText(textFile.filePath) ?: ""
        if (content.isBlank()) {
            return AnalyzeResult(
                suggestedItems = emptyList(),
                confidence = 0f,
                summary = "Файл пуст или не удалось прочитать",
                warnings = listOf("Не удалось прочитать содержимое файла")
            )
        }
        return analyzeText(content, targetChecklist)
    }

    private suspend fun analyzeAudio(audio: AnalyzeInputData.Audio, targetChecklist: Checklist?): AnalyzeResult {
        val audioBytes = readFileBytes(audio.filePath)
        if (audioBytes == null) {
            return AnalyzeResult(
                suggestedItems = emptyList(),
                confidence = 0f,
                summary = "Не удалось прочитать аудиофайл",
                warnings = listOf("Файл не найден или недоступен: ${audio.filePath}")
            )
        }

        val contextPrompt = if (targetChecklist != null) {
            "Контекст: существующий чек-лист '${targetChecklist.name}' с пунктами: ${targetChecklist.items.joinToString { it.text }}"
        } else ""

        val inputContent = content {
            blob(audio.mimeType, audioBytes)
            text("""
                Прослушай эту голосовую запись и создай список пунктов для проверки (чек-лист) на основе сказанного.

                $contextPrompt

                Требования:
                1. Внимательно прослушай запись и извлеки все упомянутые задачи, пункты или действия
                2. Каждый пункт должен быть конкретным и проверяемым
                3. Пункты должны быть на русском языке
                4. Формат ответа - каждый пункт с новой строки, начиная с "- "
                5. Только список пунктов, без дополнительного текста
                6. Максимум 15 пунктов

                Пример формата:
                - Проверить состояние стен
                - Осмотреть окна
            """.trimIndent())
        }

        val response = visionModel.generateContent(inputContent)
        return parseResponse(response.text ?: "")
    }

    private fun buildPrompt(
        contentDescription: String,
        content: String,
        targetChecklist: Checklist?
    ): String {
        val contextPart = if (targetChecklist != null) {
            """
            Контекст: существующий чек-лист '${targetChecklist.name}' с пунктами:
            ${targetChecklist.items.joinToString("\n") { "- ${it.text}" }}

            Создай дополнительные пункты, которые дополнят существующий список.
            """.trimIndent()
        } else {
            "Создай новый чек-лист."
        }

        val weeklyPart = if (targetChecklist?.viewMode == ChecklistViewMode.Weekly) {
            """

            ВАЖНО: Это еженедельный чек-лист (Weekly mode). Распредели пункты по дням недели (Понедельник-Воскресенье).
            После каждого пункта добавляй тег дня в формате [day:N], где N — номер дня (1=Понедельник, 7=Воскресенье).
            Пример: - Сделать зарядку [day:1]
            """.trimIndent()
        } else {
            ""
        }

        return """
            Проанализируй $contentDescription и создай список пунктов для проверки (чек-лист).

            Контент для анализа:
            $content

            $contextPart
            $weeklyPart

            Требования:
            1. Каждый пункт должен быть конкретным и проверяемым
            2. Пункты должны быть на русском языке
            3. Формат ответа - каждый пункт с новой строки, начиная с "- "
            4. Только список пунктов, без дополнительного текста в начале или конце
            5. Не нумеруй пункты, используй только "- "
            6. Максимум 10 пунктов

            Пример формата ответа:
            - Проверить первый пункт
            - Проверить второй пункт
        """.trimIndent()
    }

    /**
     * Parses the Gemini response into a list of ChecklistItems.
     * Supports optional [day:N] suffix for weekly mode items (N=1..7, ISO weekday).
     * If the AI omits the day tag, weekday defaults to null (item appears in "unassigned" state).
     */
    private fun parseResponse(responseText: String, warnings: List<String> = emptyList()): AnalyzeResult {
        val dayTagRegex = Regex("""\[day:([1-7])]""")
        val items = responseText
            .lines()
            .map { it.trim() }
            .filter { it.startsWith("-") || it.startsWith("•") || it.startsWith("*") }
            .map { line ->
                val rawText = line
                    .removePrefix("-")
                    .removePrefix("•")
                    .removePrefix("*")
                    .trim()
                // Extract optional [day:N] tag
                val dayMatch = dayTagRegex.find(rawText)
                val weekday = dayMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                val text = rawText.replace(dayTagRegex, "").trim()
                ChecklistItem(text = text, checked = false, weekday = weekday)
            }
            .filter { it.text.isNotBlank() }
            .take(15) // Safety limit

        val confidence = when {
            items.size >= 5 -> 0.9f
            items.size >= 3 -> 0.75f
            items.size >= 1 -> 0.5f
            else -> 0.1f
        }

        val summary = when {
            items.isEmpty() -> "Не удалось извлечь пункты из ответа AI"
            items.size == 1 -> "Найден 1 пункт для проверки"
            items.size in 2..4 -> "Найдено ${items.size} пункта для проверки"
            else -> "Найдено ${items.size} пунктов для проверки"
        }

        return AnalyzeResult(
            suggestedItems = items,
            confidence = confidence,
            summary = summary,
            warnings = warnings
        )
    }

    private fun readFileBytes(filePath: String): ByteArray? {
        return com.antonchuraev.homesearchchecklist.feature.analyze.data.util.FileReader.readBytes(filePath)
    }

    private fun readFileText(filePath: String): String? {
        return com.antonchuraev.homesearchchecklist.feature.analyze.data.util.FileReader.readText(filePath)
    }
}

package com.antonchuraev.homesearchchecklist.feature.analyze.data.analyzer

import com.antonchuraev.homesearchchecklist.feature.analyze.domain.analyzer.AiAnalyzer
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import kotlin.reflect.KClass
import kotlinx.coroutines.delay

/**
 * Stub implementation of AiAnalyzer for development and testing.
 * Returns mock data simulating AI analysis results.
 *
 * Production implementation: [GeminiAiAnalyzer]
 */
class StubAiAnalyzer : AiAnalyzer {

    override suspend fun analyze(
        inputData: AnalyzeInputData,
        targetChecklist: Checklist?
    ): Result<AnalyzeResult> {
        // Simulate network/processing delay
        delay(2000)

        val suggestedItems = generateMockItems(inputData)
        val summary = generateMockSummary(inputData)

        return Result.success(
            AnalyzeResult(
                suggestedItems = suggestedItems,
                confidence = 0.85f,
                summary = summary,
                warnings = listOf("Это тестовые данные. AI анализ будет реализован позже.")
            )
        )
    }

    override suspend fun isAvailable(): Boolean = true

    override fun getSupportedInputTypes(): Set<KClass<out AnalyzeInputData>> = setOf(
        AnalyzeInputData.Photo::class,
        AnalyzeInputData.PdfDocument::class,
        AnalyzeInputData.TextFile::class,
        AnalyzeInputData.WebLink::class,
        AnalyzeInputData.RawText::class,
        AnalyzeInputData.Audio::class
    )

    private fun generateMockItems(inputData: AnalyzeInputData): List<ChecklistItem> {
        // Generate different mock items based on input type
        return when (inputData) {
            is AnalyzeInputData.Photo -> listOf(
                ChecklistItem("Проверить состояние стен", false),
                ChecklistItem("Проверить окна на герметичность", false),
                ChecklistItem("Оценить естественное освещение", false),
                ChecklistItem("Проверить состояние пола", false),
                ChecklistItem("Осмотреть потолок на наличие пятен", false)
            )

            is AnalyzeInputData.PdfDocument -> listOf(
                ChecklistItem("Проверить площадь квартиры", false),
                ChecklistItem("Уточнить этаж и этажность дома", false),
                ChecklistItem("Проверить год постройки", false),
                ChecklistItem("Уточнить тип собственности", false),
                ChecklistItem("Проверить наличие обременений", false)
            )

            is AnalyzeInputData.TextFile, is AnalyzeInputData.RawText -> listOf(
                ChecklistItem("Проверить адрес объекта", false),
                ChecklistItem("Уточнить стоимость", false),
                ChecklistItem("Проверить контактные данные", false),
                ChecklistItem("Уточнить условия сделки", false)
            )

            is AnalyzeInputData.WebLink -> listOf(
                ChecklistItem("Проверить актуальность объявления", false),
                ChecklistItem("Сравнить с аналогичными предложениями", false),
                ChecklistItem("Проверить историю цены", false),
                ChecklistItem("Изучить район на карте", false),
                ChecklistItem("Проверить транспортную доступность", false),
                ChecklistItem("Найти отзывы о застройщике/доме", false)
            )

            is AnalyzeInputData.Audio -> listOf(
                ChecklistItem("Первый пункт из голосовой записи", false),
                ChecklistItem("Второй пункт из голосовой записи", false),
                ChecklistItem("Третий пункт из голосовой записи", false),
                ChecklistItem("Уточнить детали из аудио", false)
            )
        }
    }

    private fun generateMockSummary(inputData: AnalyzeInputData): String {
        return when (inputData) {
            is AnalyzeInputData.Photo ->
                "Анализ фотографии завершен. Обнаружены элементы интерьера для проверки."

            is AnalyzeInputData.PdfDocument ->
                "PDF документ обработан. Извлечена информация о недвижимости."

            is AnalyzeInputData.TextFile ->
                "Текстовый файл проанализирован. Найдены ключевые параметры."

            is AnalyzeInputData.WebLink ->
                "Веб-страница загружена и проанализирована. Найдена информация об объекте."

            is AnalyzeInputData.RawText ->
                "Текст проанализирован. Выделены основные пункты для проверки."

            is AnalyzeInputData.Audio ->
                "Голосовая запись расшифрована. Извлечены упомянутые задачи."
        }
    }
}

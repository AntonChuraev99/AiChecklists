package com.antonchuraev.homesearchchecklist.feature.aichat.impl.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatHistoryRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChatHistoryDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChatHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Room-backed implementation of [ChatHistoryRepository].
 *
 * Maps [ChatHistoryEntry] ↔ [ChatMessage] bidirectionally.
 * Unknown [role] strings default to [ChatRole.User] on read (defensive).
 * Unknown [routedLayer] strings default to null (forward-compatible with future layers).
 */
internal class ChatHistoryRepositoryImpl(
    private val dao: ChatHistoryDao,
    private val logger: AppLogger,
) : ChatHistoryRepository {

    companion object {
        private const val TAG = "ChatHistoryRepository"

        // ignoreUnknownKeys: forward-compat — future ChatAttachment fields won't crash old installs.
        private val json = Json { ignoreUnknownKeys = true }
        private val attachmentListSerializer = ListSerializer(ChatAttachment.serializer())
    }

    override fun observeRecent(limit: Int): Flow<List<ChatMessage>> =
        dao.observeRecent(limit).map { entries ->
            entries.map { it.toChatMessage() }
        }

    override suspend fun append(message: ChatMessage) {
        logger.debug(TAG, "append: id=${message.id} role=${message.role} layer=${message.routedLayer}")
        dao.insert(message.toEntry())
    }

    override suspend fun clear() {
        logger.info(TAG, "clear: deleting all chat history")
        dao.deleteAll()
    }

    override suspend fun count(): Int = dao.count()

    // ─── Mapping ──────────────────────────────────────────────────────────────

    private fun ChatHistoryEntry.toChatMessage(): ChatMessage = ChatMessage(
        id = id,
        role = when (role) {
            "user" -> ChatRole.User
            "assistant" -> ChatRole.Assistant
            else -> {
                logger.warning(TAG, "toChatMessage: unknown role '$role', defaulting to User")
                ChatRole.User
            }
        },
        content = content,
        timestamp = timestamp,
        costCredits = costCredits,
        routedLayer = routedLayer?.toRoutingLayer(),
        linkedChecklistId = linkedChecklistId,
        attachments = decodeAttachments(attachmentsJson),
    )

    private fun ChatMessage.toEntry(): ChatHistoryEntry = ChatHistoryEntry(
        id = id,
        role = when (role) {
            ChatRole.User -> "user"
            ChatRole.Assistant -> "assistant"
        },
        content = content,
        timestamp = timestamp,
        costCredits = costCredits,
        routedLayer = routedLayer?.name,
        linkedChecklistId = linkedChecklistId,
        attachmentsJson = encodeAttachments(attachments),
    )

    /** Decodes a nullable JSON string → List<ChatAttachment>. Null / blank → emptyList(). */
    private fun decodeAttachments(json: String?): List<ChatAttachment> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            ChatHistoryRepositoryImpl.json.decodeFromString(attachmentListSerializer, json)
        }.getOrElse { e ->
            logger.warning(TAG, "decodeAttachments: failed to parse json — ${e.message}")
            emptyList()
        }
    }

    /** Encodes List<ChatAttachment> → nullable JSON string. Empty list → null (saves storage). */
    private fun encodeAttachments(list: List<ChatAttachment>): String? {
        if (list.isEmpty()) return null
        return runCatching {
            json.encodeToString(attachmentListSerializer, list)
        }.getOrElse { e ->
            logger.warning(TAG, "encodeAttachments: failed to encode — ${e.message}")
            null
        }
    }

    private fun String.toRoutingLayer(): RoutingLayer? = when (this) {
        "Local" -> RoutingLayer.Local
        "Classifier" -> RoutingLayer.Classifier
        "FullChat" -> RoutingLayer.FullChat
        else -> null
    }
}

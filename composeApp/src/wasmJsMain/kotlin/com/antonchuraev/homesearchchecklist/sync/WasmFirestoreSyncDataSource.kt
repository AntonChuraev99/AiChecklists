@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.sync

import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.ChecklistSyncData
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.FirestoreSyncDataSource
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.UserDocSyncData
import kotlin.js.Promise
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@JsFun("(collectionPath, docId, dataJson) => globalThis.__firestoreSetDoc(collectionPath, docId, dataJson)")
private external fun jsFirestoreSetDoc(collectionPath: String, docId: String, dataJson: String): Promise<JsAny?>

@JsFun("(collectionPath, docId) => globalThis.__firestoreGetDoc(collectionPath, docId)")
private external fun jsFirestoreGetDoc(collectionPath: String, docId: String): Promise<JsAny?>

@JsFun("(collectionPath) => globalThis.__firestoreGetDocs(collectionPath)")
private external fun jsFirestoreGetDocs(collectionPath: String): Promise<JsAny?>

@JsFun("(collectionPath, field, value) => globalThis.__firestoreQueryByField(collectionPath, field, value)")
private external fun jsFirestoreQueryByField(collectionPath: String, field: String, value: String): Promise<JsAny?>

@JsFun("(collectionPath, docId) => globalThis.__firestoreDeleteDoc(collectionPath, docId)")
private external fun jsFirestoreDeleteDoc(collectionPath: String, docId: String): Promise<JsAny?>

@JsFun("(operationsJson) => globalThis.__firestoreBatchWrite(operationsJson)")
private external fun jsFirestoreBatchWrite(operationsJson: String): Promise<JsAny?>

@JsFun("(collectionPath, callbackId) => globalThis.__firestoreOnSnapshot(collectionPath, callbackId)")
private external fun jsFirestoreOnSnapshot(collectionPath: String, callbackId: String): String

@JsFun("(collectionPath, docId, callbackId) => globalThis.__firestoreOnDocSnapshot(collectionPath, docId, callbackId)")
private external fun jsFirestoreOnDocSnapshot(collectionPath: String, docId: String, callbackId: String): String

@JsFun("(listenerId) => globalThis.__firestoreUnsubscribe(listenerId)")
private external fun jsFirestoreUnsubscribe(listenerId: String)

@JsFun("(v) => String(v)")
private external fun jsAnyToString(v: JsAny): String

private fun initSnapshotSlot(slotKey: String) { initSnapshotSlotRaw(slotKey) }
private fun installCallbackRouter(callbackId: String, slotKey: String) { installCallbackRouterRaw(callbackId, slotKey) }
private fun clearSnapshotSlot(slotKey: String) { clearSnapshotSlotRaw(slotKey) }
private fun removeCallbackRouter(callbackId: String) { removeCallbackRouterRaw(callbackId) }

private fun drainNextPayload(slotKey: String): String? {
    val raw = drainNextPayloadRaw(slotKey) ?: return null
    return jsAnyToString(raw).takeIf { it.isNotEmpty() }
}

private fun initSnapshotSlotRaw(slotKey: String) { js("globalThis[slotKey] = []") }

private fun installCallbackRouterRaw(callbackId: String, slotKey: String) {
    js("""(function() {
        if (!globalThis.__firestoreSnapshotCallbacks) globalThis.__firestoreSnapshotCallbacks = new Map();
        globalThis.__firestoreSnapshotCallbacks.set(callbackId, function(cbId, payload) {
            var arr = globalThis[slotKey];
            if (arr) arr.push(payload);
        });
    })()""")
}

private fun drainNextPayloadRaw(slotKey: String): JsAny? =
    js("""(function() { var arr = globalThis[slotKey]; if (!arr || arr.length === 0) return null; return arr.shift(); })()""")

private fun clearSnapshotSlotRaw(slotKey: String) { js("delete globalThis[slotKey]") }

private fun removeCallbackRouterRaw(callbackId: String) {
    js("""(function() { if (globalThis.__firestoreSnapshotCallbacks) globalThis.__firestoreSnapshotCallbacks.delete(callbackId); })()""")
}

private fun nowMs(): Long = nowMsRaw().toLong()
private fun nowMsRaw(): Double = js("Date.now()")

private val jsonParser = Json { ignoreUnknownKeys = true }

@Serializable
private data class JsResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val data: kotlinx.serialization.json.JsonElement? = null,
)

private suspend fun Promise<JsAny?>.awaitAsString(): String? {
    val raw = this.await<JsAny?>() ?: return null
    return jsAnyToString(raw)
}

private fun checklistsPath(userId: String) = "users/$userId/checklists"

internal class WasmFirestoreSyncDataSource : FirestoreSyncDataSource {

    override fun observeChecklistIds(userId: String): Flow<AppResult<List<String>>> = callbackFlow {
        val callbackId = "ids:$userId:${nowMs()}"
        val slotKey = "__ktcb_$callbackId"
        initSnapshotSlot(slotKey)
        installCallbackRouter(callbackId, slotKey)
        val listenerId = jsFirestoreOnSnapshot(checklistsPath(userId), callbackId)

        while (true) {
            val payload = drainNextPayload(slotKey)
            if (payload == null) { delay(150); continue }
            try {
                val resp = jsonParser.decodeFromString(JsResponse.serializer(), payload)
                if (resp.ok) {
                    val ids = resp.data?.let {
                        jsonParser.decodeFromString(ListSerializer(CloudIdEntry.serializer()), it.toString())
                    }?.map { it.cloudId } ?: emptyList()
                    trySend(AppResult.Success(ids))
                } else {
                    trySend(AppResult.Error(Exception(resp.error ?: "Listener error")))
                }
            } catch (e: Exception) {
                trySend(AppResult.Error(e))
            }
        }

        @Suppress("UNREACHABLE_CODE")
        awaitClose {
            jsFirestoreUnsubscribe(listenerId)
            removeCallbackRouter(callbackId)
            clearSnapshotSlot(slotKey)
        }
    }

    override fun observeChecklist(userId: String, cloudId: String): Flow<AppResult<ChecklistSyncData>> = callbackFlow {
        val callbackId = "doc:$userId:$cloudId:${nowMs()}"
        val slotKey = "__ktcb_$callbackId"
        initSnapshotSlot(slotKey)
        installCallbackRouter(callbackId, slotKey)
        val listenerId = jsFirestoreOnDocSnapshot(checklistsPath(userId), cloudId, callbackId)

        while (true) {
            val payload = drainNextPayload(slotKey)
            if (payload == null) { delay(150); continue }
            try {
                val resp = jsonParser.decodeFromString(JsResponse.serializer(), payload)
                if (resp.ok && resp.data != null) {
                    val data = jsonParser.decodeFromString(ChecklistSyncData.serializer(), resp.data.toString())
                    trySend(AppResult.Success(data))
                } else {
                    trySend(AppResult.Error(Exception(resp.error ?: "Document not found")))
                }
            } catch (e: Exception) {
                trySend(AppResult.Error(e))
            }
        }

        @Suppress("UNREACHABLE_CODE")
        awaitClose {
            jsFirestoreUnsubscribe(listenerId)
            removeCallbackRouter(callbackId)
            clearSnapshotSlot(slotKey)
        }
    }

    override suspend fun uploadChecklist(userId: String, data: ChecklistSyncData): AppResult<Unit> =
        runCatching {
            val dataJson = jsonParser.encodeToString(ChecklistSyncData.serializer(), data)
            val response = jsFirestoreSetDoc(checklistsPath(userId), data.cloudId, dataJson)
                .awaitAsString() ?: return AppResult.Error(Exception("Null response"))
            val resp = jsonParser.decodeFromString(JsResponse.serializer(), response)
            if (resp.ok) AppResult.Success(Unit)
            else AppResult.Error(Exception(resp.error ?: "Upload failed"))
        }.getOrElse { AppResult.Error(Exception(it.message ?: "uploadChecklist failed")) }

    override suspend fun deleteChecklist(userId: String, cloudId: String): AppResult<Unit> =
        runCatching {
            val response = jsFirestoreDeleteDoc(checklistsPath(userId), cloudId)
                .awaitAsString() ?: return AppResult.Error(Exception("Null response"))
            val resp = jsonParser.decodeFromString(JsResponse.serializer(), response)
            if (resp.ok) AppResult.Success(Unit)
            else AppResult.Error(Exception(resp.error ?: "Delete failed"))
        }.getOrElse { AppResult.Error(Exception(it.message ?: "deleteChecklist failed")) }

    override suspend fun uploadBatch(userId: String, checklists: List<ChecklistSyncData>): AppResult<Unit> =
        runCatching {
            val path = checklistsPath(userId)
            val ops = checklists.map { checklist ->
                BatchOp(path, checklist.cloudId, jsonParser.encodeToString(ChecklistSyncData.serializer(), checklist))
            }
            val opsJson = jsonParser.encodeToString(ListSerializer(BatchOp.serializer()), ops)
            val response = jsFirestoreBatchWrite(opsJson)
                .awaitAsString() ?: return AppResult.Error(Exception("Null response"))
            val resp = jsonParser.decodeFromString(JsResponse.serializer(), response)
            if (resp.ok) AppResult.Success(Unit)
            else AppResult.Error(Exception(resp.error ?: "Batch failed"))
        }.getOrElse { AppResult.Error(Exception(it.message ?: "uploadBatch failed")) }

    override suspend fun fetchAllChecklists(userId: String): AppResult<List<ChecklistSyncData>> =
        runCatching {
            val response = jsFirestoreGetDocs(checklistsPath(userId))
                .awaitAsString() ?: return AppResult.Error(Exception("Null response"))
            val resp = jsonParser.decodeFromString(JsResponse.serializer(), response)
            if (resp.ok) {
                val list = resp.data?.let {
                    jsonParser.decodeFromString(ListSerializer(ChecklistSyncData.serializer()), it.toString())
                } ?: emptyList()
                AppResult.Success(list)
            } else {
                AppResult.Error(Exception(resp.error ?: "Fetch failed"))
            }
        }.getOrElse { AppResult.Error(Exception(it.message ?: "fetchAllChecklists failed")) }

    override fun observeUserDoc(userId: String): Flow<AppResult<UserDocSyncData?>> = callbackFlow {
        val callbackId = "userdoc:$userId:${nowMs()}"
        val slotKey = "__ktcb_$callbackId"
        initSnapshotSlot(slotKey)
        installCallbackRouter(callbackId, slotKey)
        val listenerId = jsFirestoreOnDocSnapshot("users", userId, callbackId)

        while (true) {
            val payload = drainNextPayload(slotKey)
            if (payload == null) { delay(150); continue }
            try {
                val resp = jsonParser.decodeFromString(JsResponse.serializer(), payload)
                if (resp.ok) {
                    val data = resp.data?.let {
                        jsonParser.decodeFromString(UserDocSyncData.serializer(), it.toString())
                    }
                    trySend(AppResult.Success(data))
                } else {
                    trySend(AppResult.Error(Exception(resp.error ?: "Listener error")))
                }
            } catch (e: Exception) {
                trySend(AppResult.Error(e))
            }
        }

        @Suppress("UNREACHABLE_CODE")
        awaitClose {
            jsFirestoreUnsubscribe(listenerId)
            removeCallbackRouter(callbackId)
            clearSnapshotSlot(slotKey)
        }
    }

    /**
     * Resolves the canonical credit-doc id by querying `users` for the doc whose `google_uid`
     * equals [googleUid]. Lets a diverged web session (logged in on an old build → stranded on
     * its own 0-credit device-id doc, no google_email) self-heal onto the shared Google-linked
     * doc — the same convergence the Android client does. Returns null when no linked doc exists.
     */
    override suspend fun findUserIdByGoogleUid(googleUid: String): AppResult<String?> =
        runCatching {
            val response = jsFirestoreQueryByField("users", "google_uid", googleUid)
                .awaitAsString() ?: return AppResult.Error(Exception("Null response"))
            val resp = jsonParser.decodeFromString(JsResponse.serializer(), response)
            if (resp.ok) {
                AppResult.Success(resp.data?.jsonPrimitive?.contentOrNull)
            } else {
                AppResult.Error(Exception(resp.error ?: "google_uid query failed"))
            }
        }.getOrElse { AppResult.Error(Exception(it.message ?: "findUserIdByGoogleUid failed")) }
}

@Serializable
private data class CloudIdEntry(val cloudId: String = "")

@Serializable
private data class BatchOp(val collectionPath: String, val docId: String, val data: String)

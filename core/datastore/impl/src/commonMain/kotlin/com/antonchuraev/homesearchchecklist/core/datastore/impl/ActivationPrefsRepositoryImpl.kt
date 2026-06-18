package com.antonchuraev.homesearchchecklist.core.datastore.impl

import com.antonchuraev.homesearchchecklist.core.datastore.api.ActivationPrefsRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import kotlinx.coroutines.flow.first

private const val PENDING_KEY_PREFIX = "activation_new_user_pending_"
private const val REMINDER_SHOWN_KEY_PREFIX = "activation_reminder_optin_shown_"

class ActivationPrefsRepositoryImpl(
    private val dataStore: AppDatastore,
) : ActivationPrefsRepository {

    override suspend fun isNewUserPending(uid: String): Boolean =
        dataStore.observeBoolean(pendingKey(uid), defaultValue = false).first()

    override suspend fun setNewUserPending(uid: String) {
        dataStore.saveBoolean(pendingKey(uid), true)
    }

    override suspend fun clearNewUserPending(uid: String) {
        dataStore.saveBoolean(pendingKey(uid), false)
    }

    override suspend fun isReminderOptInShown(uid: String): Boolean =
        dataStore.observeBoolean(reminderShownKey(uid), defaultValue = false).first()

    override suspend fun markReminderOptInShown(uid: String) {
        dataStore.saveBoolean(reminderShownKey(uid), true)
    }

    private fun pendingKey(uid: String) = "$PENDING_KEY_PREFIX$uid"
    private fun reminderShownKey(uid: String) = "$REMINDER_SHOWN_KEY_PREFIX$uid"
}

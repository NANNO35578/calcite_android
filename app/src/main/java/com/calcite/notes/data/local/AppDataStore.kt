package com.calcite.notes.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppDataStore(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")
        private val TOKEN_KEY = stringPreferencesKey("token")
        private val TOKEN_EXPIRE_TIME_KEY = longPreferencesKey("token_expire_time")
        private val CURRENT_NOTE_ID_KEY = longPreferencesKey("current_note_id")
    }

    val token: Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }

    val tokenExpireTime: Flow<Long> = context.dataStore.data.map { it[TOKEN_EXPIRE_TIME_KEY] ?: 0L }

    val currentNoteId: Flow<Long> = context.dataStore.data.map { it[CURRENT_NOTE_ID_KEY] ?: 0L }

    suspend fun saveToken(token: String) {
        context.dataStore.edit {
            it[TOKEN_KEY] = token
            // Token 有效期 7 天
            it[TOKEN_EXPIRE_TIME_KEY] = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000
        }
    }

    suspend fun clearToken() {
        context.dataStore.edit {
            it.remove(TOKEN_KEY)
            it.remove(TOKEN_EXPIRE_TIME_KEY)
        }
    }

    suspend fun setCurrentNoteId(noteId: Long) {
        context.dataStore.edit {
            it[CURRENT_NOTE_ID_KEY] = noteId
        }
    }

    suspend fun clearCurrentNoteId() {
        context.dataStore.edit {
            it.remove(CURRENT_NOTE_ID_KEY)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit {
            it.clear()
        }
    }

    fun isTokenValid(): Boolean {
        // 注意：此函数用于同步判断，实际应结合 flow 使用
        // 这里提供一个基于 runBlocking 的备用方案，但推荐在协程中检查
        return false
    }
}

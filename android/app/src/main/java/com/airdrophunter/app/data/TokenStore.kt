package com.airdrophunter.app.data
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
private val Context.authDataStore by preferencesDataStore(name = "airdrop_hunter_auth")
object TokenStore {
    private val key = stringPreferencesKey("access_token")
    @Volatile private var appContext: Context? = null
    fun initialize(context: Context) { appContext = context.applicationContext }
    fun observeToken(context: Context): Flow<String?> = context.applicationContext.authDataStore.data.map { it[key] }
    suspend fun saveToken(context: Context, token: String) { context.applicationContext.authDataStore.edit { it[key] = token } }
    suspend fun clearToken(context: Context) { context.applicationContext.authDataStore.edit { it.remove(key) } }
    fun readTokenBlocking(): String? {
        val ctx = appContext ?: return null
        return runBlocking(Dispatchers.IO) { ctx.authDataStore.data.first()[key] }
    }
}

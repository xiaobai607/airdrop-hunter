package com.airdrophunter.app.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Mock-only handoff point for the future cloud sync.
 * It deliberately does not call any API: local records remain untouched until
 * a real, conflict-aware sync protocol is implemented.
 */
object LocalDataSyncManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun prepareMockSync(context: Context) {
        scope.launch {
            val gson = Gson()
            val projectsType = object : TypeToken<List<ProjectDto>>() {}.type
            val walletsType = object : TypeToken<List<WalletDto>>() {}.type
            val projects = CacheStore.readLocal("projects")?.let {
                runCatching { gson.fromJson<List<ProjectDto>>(it, projectsType) }.getOrNull()
            }.orEmpty()
            val wallets = CacheStore.readLocal("wallets")?.let {
                runCatching { gson.fromJson<List<WalletDto>>(it, walletsType) }.getOrNull()
            }.orEmpty()
            Log.i("LocalDataSync", "Mock sync prepared: ${projects.size} projects, ${wallets.size} wallets. No data uploaded.")
        }
    }
}

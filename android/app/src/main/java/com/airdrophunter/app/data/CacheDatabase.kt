package com.airdrophunter.app.data

import android.content.Context
import android.util.Base64
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Entity(tableName = "cached_payloads")
data class CachedPayload(@PrimaryKey val cacheKey: String, val payload: String, val savedAtEpochMillis: Long)

@Dao
interface CacheDao {
    @Query("SELECT payload FROM cached_payloads WHERE cacheKey = :key LIMIT 1")
    suspend fun read(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun write(entry: CachedPayload)
    @Query("DELETE FROM cached_payloads WHERE cacheKey = :key")
    suspend fun delete(key: String)
}

@Database(entities = [CachedPayload::class], version = 1, exportSchema = false)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
}

object CacheStore {
    @Volatile private var database: CacheDatabase? = null
    fun initialize(context: Context) {
        if (database != null) return
        synchronized(this) {
            if (database == null) database = Room.databaseBuilder(context.applicationContext, CacheDatabase::class.java, "airdrop_hunter_cache").build()
        }
    }

    // Cache keys are account-scoped: offline data from account A is never shown in account B.
    private fun scopedKey(key: String): String {
        val token = TokenStore.readTokenBlocking() ?: return "signed-out:$key"
        val payload = token.split(".").getOrNull(1) ?: return "signed-out:$key"
        return try {
            val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val userId = JSONObject(String(decoded, Charsets.UTF_8)).optString("sub", "unknown")
            "$userId:$key"
        } catch (_: Exception) { "signed-out:$key" }
    }

    suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        runCatching { database?.cacheDao()?.read(scopedKey(key)) }.getOrNull()
    }

    suspend fun write(key: String, payload: String) = withContext(Dispatchers.IO) {
        runCatching { database?.cacheDao()?.write(CachedPayload(scopedKey(key), payload, System.currentTimeMillis())) }
        Unit
    }
}

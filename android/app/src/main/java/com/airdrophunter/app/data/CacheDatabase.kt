package com.airdrophunter.app.data

import android.content.Context
import android.util.Base64
import androidx.room.*
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Entity(tableName = "cached_payloads")
data class CachedPayload(@PrimaryKey val cacheKey: String, val payload: String, val savedAtEpochMillis: Long)

@Entity(tableName = "pending_bills")
data class PendingBillEntity(@PrimaryKey val id: String, val txHash: String, val walletAddress: String, val gasCostUsd: Double, val interactionMethod: String, val timestamp: Long)

@Entity(tableName = "pnl_records")
data class PnlRecordEntity(@PrimaryKey val projectId: String, val walletAddress: String, val totalGasCostUsd: Double, val totalRewardUsd: Double, val netProfitUsd: Double)

@Dao
interface CacheDao {
    @Query("SELECT payload FROM cached_payloads WHERE cacheKey = :key LIMIT 1")
    suspend fun read(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun write(entry: CachedPayload)
    @Query("DELETE FROM cached_payloads WHERE cacheKey = :key")
    suspend fun delete(key: String)
}

@Dao
interface PendingBillDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bill: PendingBillEntity)
    @Query("SELECT * FROM pending_bills ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<PendingBillEntity>>
    @Query("SELECT * FROM pending_bills WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): PendingBillEntity?
    @Query("DELETE FROM pending_bills WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PnlRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: PnlRecordEntity)
    @Query("SELECT * FROM pnl_records ORDER BY netProfitUsd DESC")
    fun observeAll(): Flow<List<PnlRecordEntity>>
    @Query("SELECT * FROM pnl_records WHERE projectId = :projectId LIMIT 1")
    suspend fun findByProject(projectId: String): PnlRecordEntity?
    @Query("UPDATE pnl_records SET walletAddress = :walletAddress, totalGasCostUsd = totalGasCostUsd + :gas, totalRewardUsd = totalRewardUsd + :reward, netProfitUsd = netProfitUsd + :net WHERE projectId = :projectId")
    suspend fun addAmounts(projectId: String, walletAddress: String, gas: Double, reward: Double, net: Double): Int
    @Transaction
    suspend fun addToProject(projectId: String, walletAddress: String, gas: Double, reward: Double) {
        if (addAmounts(projectId, walletAddress, gas, reward, reward - gas) == 0) insert(PnlRecordEntity(projectId, walletAddress, gas, reward, reward - gas))
    }
}

@Database(entities = [CachedPayload::class, PendingBillEntity::class, PnlRecordEntity::class], version = 2, exportSchema = false)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun pendingBillDao(): PendingBillDao
    abstract fun pnlRecordDao(): PnlRecordDao
}

object CacheStore {
    @Volatile private var database: CacheDatabase? = null
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS pending_bills (id TEXT NOT NULL PRIMARY KEY, txHash TEXT NOT NULL, walletAddress TEXT NOT NULL, gasCostUsd REAL NOT NULL, interactionMethod TEXT NOT NULL, timestamp INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS pnl_records (projectId TEXT NOT NULL PRIMARY KEY, walletAddress TEXT NOT NULL, totalGasCostUsd REAL NOT NULL, totalRewardUsd REAL NOT NULL, netProfitUsd REAL NOT NULL)")
        }
    }
    fun initialize(context: Context) {
        if (database != null) return
        synchronized(this) {
            if (database == null) database = Room.databaseBuilder(context.applicationContext, CacheDatabase::class.java, "airdrop_hunter_cache").addMigrations(MIGRATION_1_2).build()
        }
    }
    fun observePendingBills(): Flow<List<PendingBillEntity>> = database?.pendingBillDao()?.observeAll() ?: flowOf(emptyList())
    fun observePnlRecords(): Flow<List<PnlRecordEntity>> = database?.pnlRecordDao()?.observeAll() ?: flowOf(emptyList())
    suspend fun addPendingBill(bill: PendingBillEntity) = withContext(Dispatchers.IO) { database?.pendingBillDao()?.insert(bill) }
    suspend fun confirmPendingBill(id: String, projectId: String, gas: Double, reward: Double): Boolean = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext false
        db.withTransaction {
            val bill = db.pendingBillDao().findById(id) ?: return@withTransaction false
            db.pnlRecordDao().addToProject(projectId, bill.walletAddress, gas, reward)
            db.pendingBillDao().delete(id)
            true
        }
    }
    private fun scopedKey(key: String): String {
        val token = TokenStore.readTokenBlocking() ?: return "signed-out:$key"
        val payload = token.split(".").getOrNull(1) ?: return "signed-out:$key"
        return try {
            val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val userId = JSONObject(String(decoded, Charsets.UTF_8)).optString("sub", "unknown")
            "$userId:$key"
        } catch (_: Exception) { "signed-out:$key" }
    }
    suspend fun read(key: String): String? = withContext(Dispatchers.IO) { runCatching { database?.cacheDao()?.read(scopedKey(key)) }.getOrNull() }
    suspend fun write(key: String, payload: String) = withContext(Dispatchers.IO) {
        runCatching { database?.cacheDao()?.write(CachedPayload(scopedKey(key), payload, System.currentTimeMillis())) }; Unit
    }
    suspend fun readLocal(key: String): String? = withContext(Dispatchers.IO) { runCatching { database?.cacheDao()?.read("local:$key") }.getOrNull() }
    suspend fun writeLocal(key: String, payload: String) = withContext(Dispatchers.IO) {
        runCatching { database?.cacheDao()?.write(CachedPayload("local:$key", payload, System.currentTimeMillis())) }; Unit
    }
}

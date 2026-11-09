package com.airdrophunter.app.data

import android.content.Context
import android.util.Base64
import androidx.room.*
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant

@Entity(tableName = "cached_payloads")
data class CachedPayload(@PrimaryKey val cacheKey: String, val payload: String, val savedAtEpochMillis: Long)

@Entity(tableName = "pending_bills")
data class PendingBillEntity(@PrimaryKey val id: String, val txHash: String, val walletAddress: String, val gasCostUsd: Double, val interactionMethod: String, val timestamp: Long, @ColumnInfo(defaultValue = "'unknown'") val chain: String = "unknown", @ColumnInfo(defaultValue = "'guest'") val accountScope: String = "guest", @ColumnInfo(defaultValue = "'unknown'") val network: String = "unknown")

@Entity(
    tableName = "pnl_records",
    indices = [Index(value = ["accountScope", "projectLocalId"], unique = true)]
)
data class PnlRecordEntity(
    @PrimaryKey val id: String,
    /** Nullable by design. Only an explicit community association may populate this field. */
    val projectOfficialId: String?,
    /** Stable local project ID; custom projects aggregate here without entering community totals. */
    val projectLocalId: String,
    val projectCustomName: String,
    val walletAddress: String,
    val totalGasCostUsd: Double,
    val totalRewardUsd: Double,
    val netProfitUsd: Double,
    @ColumnInfo(defaultValue = "'guest'") val accountScope: String = "guest"
)


data class CommunitySyncBatch(
    val accountScope: String,
    val authorizationHeader: String?,
    val entries: List<CommunitySyncOutboxEntity>
)

@Entity(tableName = "user_projects", indices = [Index(value = ["accountScope", "projectLocalId"], unique = true), Index(value = ["projectOfficialId"])])
data class UserProjectEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "'guest'") val accountScope: String = "guest",
    val projectLocalId: String, val projectOfficialId: String?, val projectName: String,
    val status: String, val totalGasCostUsd: Double, val totalActualRewardUsd: Double,
    val netProfitUsd: Double, val interactionCount: Int, val lastInteractionAt: Long,
    val createdAt: Long, val updatedAt: Long
)

@Entity(tableName = "user_interactions", indices = [Index(value = ["accountScope", "projectLocalId", "timestamp"]), Index(value = ["projectOfficialId"])])
data class UserInteractionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "'guest'") val accountScope: String = "guest",
    val projectLocalId: String, val projectOfficialId: String?, val projectName: String,
    val txHash: String, val walletAddress: String, val network: String,
    val gasCostUsd: Double, val actualRewardUsd: Double, val interactionMethod: String, val timestamp: Long
)

@Entity(tableName = "community_sync_outbox", indices = [Index(value = ["accountScope", "createdAt"])])
data class CommunitySyncOutboxEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "'guest'") val accountScope: String = "guest",
    val projectOfficialId: String, val gasCostUsd: Double, val actualRewardUsd: Double,
    val interactionCount: Int, val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null, val lastError: String? = null
)

@Entity(
    tableName = "project_ratings",
    indices = [Index(value = ["accountScope", "projectOfficialId", "walletAddress"], unique = true)]
)
data class ProjectRatingEntity(
    @PrimaryKey val id: String,
    val projectOfficialId: String,
    val walletAddress: String,
    val score: Int,
    val timestamp: Long,
    @ColumnInfo(defaultValue = "'guest'") val accountScope: String = "guest"
)

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
    @Query("SELECT * FROM pending_bills WHERE accountScope = :accountScope ORDER BY timestamp DESC")
    fun observeAll(accountScope: String): Flow<List<PendingBillEntity>>
    @Query("SELECT * FROM pending_bills WHERE id = :id AND accountScope = :accountScope LIMIT 1")
    suspend fun findById(id: String, accountScope: String): PendingBillEntity?
    @Query("DELETE FROM pending_bills WHERE id = :id AND accountScope = :accountScope")
    suspend fun delete(id: String, accountScope: String)
}

@Dao
interface PnlRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: PnlRecordEntity)

    @Query("SELECT * FROM pnl_records WHERE accountScope = :accountScope ORDER BY netProfitUsd DESC")
    fun observeAll(accountScope: String): Flow<List<PnlRecordEntity>>

    @Query("SELECT * FROM pnl_records WHERE projectOfficialId = :projectOfficialId AND accountScope = :accountScope LIMIT 1")
    suspend fun findByProject(projectOfficialId: String, accountScope: String): PnlRecordEntity?

    @Query("UPDATE pnl_records SET projectOfficialId = :projectOfficialId, projectCustomName = :customName, walletAddress = :walletAddress, totalGasCostUsd = totalGasCostUsd + :gas, totalRewardUsd = totalRewardUsd + :reward, netProfitUsd = (totalRewardUsd + :reward) - (totalGasCostUsd + :gas) WHERE projectLocalId = :projectLocalId AND accountScope = :accountScope")
    suspend fun addAmounts(projectLocalId: String, projectOfficialId: String?, customName: String, walletAddress: String, gas: Double, reward: Double, accountScope: String): Int

    @Transaction
    suspend fun addToProject(projectLocalId: String, projectOfficialId: String?, customName: String, walletAddress: String, gas: Double, reward: Double, accountScope: String) {
        if (addAmounts(projectLocalId, projectOfficialId, customName, walletAddress, gas, reward, accountScope) == 0) {
            insert(PnlRecordEntity(
                java.util.UUID.randomUUID().toString(), projectOfficialId, projectLocalId,
                customName, walletAddress, gas, reward, reward - gas, accountScope
            ))
        }
    }
}


@Dao
interface UserProjectDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(project: UserProjectEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(project: UserProjectEntity)
    @Query("SELECT * FROM user_projects WHERE projectLocalId = :projectLocalId AND accountScope = :accountScope LIMIT 1")
    suspend fun findByLocalId(projectLocalId: String, accountScope: String): UserProjectEntity?
    @Query("SELECT * FROM user_projects WHERE accountScope = :accountScope ORDER BY lastInteractionAt DESC, projectName COLLATE NOCASE")
    fun observeAll(accountScope: String): Flow<List<UserProjectEntity>>
    @Query("SELECT * FROM user_projects WHERE accountScope = :accountScope ORDER BY lastInteractionAt DESC, projectName COLLATE NOCASE")
    suspend fun getAll(accountScope: String): List<UserProjectEntity>
    @Query("SELECT * FROM user_projects WHERE projectLocalId = :projectLocalId AND accountScope = :accountScope LIMIT 1")
    fun observeByLocalId(projectLocalId: String, accountScope: String): Flow<UserProjectEntity?>
    @Query("UPDATE user_projects SET projectOfficialId = :projectOfficialId, projectName = :projectName, totalGasCostUsd = totalGasCostUsd + :gas, totalActualRewardUsd = totalActualRewardUsd + :reward, netProfitUsd = (totalActualRewardUsd + :reward) - (totalGasCostUsd + :gas), interactionCount = interactionCount + 1, lastInteractionAt = :timestamp, updatedAt = :timestamp WHERE projectLocalId = :projectLocalId AND accountScope = :accountScope")
    suspend fun addAmounts(projectLocalId: String, projectOfficialId: String?, projectName: String, gas: Double, reward: Double, timestamp: Long, accountScope: String): Int
}

@Dao
interface UserInteractionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(interaction: UserInteractionEntity)
    @Query("SELECT * FROM user_interactions WHERE projectLocalId = :projectLocalId AND accountScope = :accountScope ORDER BY timestamp DESC")
    fun observeByProject(projectLocalId: String, accountScope: String): Flow<List<UserInteractionEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM user_interactions WHERE accountScope = :accountScope AND LOWER(network) = LOWER(:network) AND ((LOWER(:network) = 'solana' AND txHash = :txHash) OR (LOWER(:network) != 'solana' AND LOWER(txHash) = LOWER(:txHash))) AND ((LOWER(:network) IN ('ethereum', 'bsc', 'polygon', 'arbitrum', 'optimism', 'base', 'avalanche') AND LOWER(walletAddress) = LOWER(:walletAddress)) OR (LOWER(:network) NOT IN ('ethereum', 'bsc', 'polygon', 'arbitrum', 'optimism', 'base', 'avalanche') AND walletAddress = :walletAddress)))")
    suspend fun hasTransaction(accountScope: String, network: String, walletAddress: String, txHash: String): Boolean
}

@Dao
interface CommunitySyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entry: CommunitySyncOutboxEntity): Long
    @Query("SELECT * FROM community_sync_outbox WHERE accountScope = :accountScope ORDER BY createdAt ASC")
    fun observeAll(accountScope: String): Flow<List<CommunitySyncOutboxEntity>>
    @Query("SELECT * FROM community_sync_outbox WHERE accountScope = :accountScope ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPending(accountScope: String, limit: Int): List<CommunitySyncOutboxEntity>
    @Query("DELETE FROM community_sync_outbox WHERE id = :id AND accountScope = :accountScope")
    suspend fun deleteById(id: String, accountScope: String)
    @Query("UPDATE community_sync_outbox SET attemptCount = attemptCount + 1, lastAttemptAt = :attemptAt, lastError = :error WHERE id = :id AND accountScope = :accountScope")
    suspend fun markAttemptById(id: String, accountScope: String, attemptAt: Long, error: String?)
}

@Dao
interface ProjectRatingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rating: ProjectRatingEntity)
    @Query("SELECT * FROM project_ratings WHERE projectOfficialId = :projectOfficialId AND accountScope = :accountScope ORDER BY timestamp DESC")
    fun observeByProject(projectOfficialId: String, accountScope: String): Flow<List<ProjectRatingEntity>>
    @Query("SELECT * FROM project_ratings WHERE projectOfficialId = :projectOfficialId AND walletAddress = :walletAddress AND accountScope = :accountScope LIMIT 1")
    suspend fun findByWallet(projectOfficialId: String, walletAddress: String, accountScope: String): ProjectRatingEntity?
    @Query("SELECT AVG(score) FROM project_ratings WHERE projectOfficialId = :projectOfficialId AND accountScope = :accountScope")
    fun observeAverage(projectOfficialId: String, accountScope: String): Flow<Double?>
    @Query("SELECT * FROM project_ratings WHERE accountScope = :accountScope ORDER BY timestamp DESC")
    fun observeAll(accountScope: String): Flow<List<ProjectRatingEntity>>
}

@Database(entities = [CachedPayload::class, PendingBillEntity::class, PnlRecordEntity::class, ProjectRatingEntity::class, UserProjectEntity::class, UserInteractionEntity::class, CommunitySyncOutboxEntity::class], version = 8, exportSchema = false)
internal fun shouldPersistPrivateAccounting(accountScope: String): Boolean =
    accountScope != "guest"

abstract class CacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun pendingBillDao(): PendingBillDao
    abstract fun pnlRecordDao(): PnlRecordDao
    abstract fun projectRatingDao(): ProjectRatingDao
    abstract fun userProjectDao(): UserProjectDao
    abstract fun userInteractionDao(): UserInteractionDao
    abstract fun communitySyncOutboxDao(): CommunitySyncOutboxDao
}

@OptIn(ExperimentalCoroutinesApi::class)
object CacheStore {
    @Volatile private var database: CacheDatabase? = null
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS pending_bills (id TEXT NOT NULL PRIMARY KEY, txHash TEXT NOT NULL, walletAddress TEXT NOT NULL, gasCostUsd REAL NOT NULL, interactionMethod TEXT NOT NULL, timestamp INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS pnl_records (projectId TEXT NOT NULL PRIMARY KEY, walletAddress TEXT NOT NULL, totalGasCostUsd REAL NOT NULL, totalRewardUsd REAL NOT NULL, netProfitUsd REAL NOT NULL)")
        }
    }
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS pnl_records_new (projectOfficialId TEXT NOT NULL PRIMARY KEY, projectCustomName TEXT NOT NULL, walletAddress TEXT NOT NULL, totalGasCostUsd REAL NOT NULL, totalRewardUsd REAL NOT NULL, netProfitUsd REAL NOT NULL)")
            db.execSQL("INSERT INTO pnl_records_new (projectOfficialId, projectCustomName, walletAddress, totalGasCostUsd, totalRewardUsd, netProfitUsd) SELECT projectId, '', walletAddress, totalGasCostUsd, totalRewardUsd, netProfitUsd FROM pnl_records")
            db.execSQL("DROP TABLE pnl_records")
            db.execSQL("ALTER TABLE pnl_records_new RENAME TO pnl_records")
            db.execSQL("CREATE TABLE IF NOT EXISTS project_ratings (id TEXT NOT NULL PRIMARY KEY, projectOfficialId TEXT NOT NULL, walletAddress TEXT NOT NULL, score INTEGER NOT NULL, timestamp INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_project_ratings_projectOfficialId_walletAddress ON project_ratings(projectOfficialId, walletAddress)")
        }
    }
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS pnl_records_new (id TEXT NOT NULL PRIMARY KEY, projectOfficialId TEXT NOT NULL, projectCustomName TEXT NOT NULL, walletAddress TEXT NOT NULL, totalGasCostUsd REAL NOT NULL, totalRewardUsd REAL NOT NULL, netProfitUsd REAL NOT NULL)")
            db.execSQL("INSERT INTO pnl_records_new (id, projectOfficialId, projectCustomName, walletAddress, totalGasCostUsd, totalRewardUsd, netProfitUsd) SELECT projectOfficialId, projectOfficialId, projectCustomName, walletAddress, totalGasCostUsd, totalRewardUsd, netProfitUsd FROM pnl_records")
            db.execSQL("DROP TABLE pnl_records")
            db.execSQL("ALTER TABLE pnl_records_new RENAME TO pnl_records")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pnl_records_projectOfficialId ON pnl_records(projectOfficialId)")
        }
    }
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pending_bills ADD COLUMN chain TEXT NOT NULL DEFAULT 'unknown'")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pending_bills ADD COLUMN network TEXT NOT NULL DEFAULT 'unknown'")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS user_projects (
                    id TEXT NOT NULL PRIMARY KEY, accountScope TEXT NOT NULL DEFAULT 'guest',
                    projectLocalId TEXT NOT NULL, projectOfficialId TEXT, projectName TEXT NOT NULL,
                    status TEXT NOT NULL, totalGasCostUsd REAL NOT NULL, totalActualRewardUsd REAL NOT NULL,
                    netProfitUsd REAL NOT NULL, interactionCount INTEGER NOT NULL,
                    lastInteractionAt INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_user_projects_accountScope_projectLocalId ON user_projects(accountScope, projectLocalId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_user_projects_projectOfficialId ON user_projects(projectOfficialId)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS user_interactions (
                    id TEXT NOT NULL PRIMARY KEY, accountScope TEXT NOT NULL DEFAULT 'guest',
                    projectLocalId TEXT NOT NULL, projectOfficialId TEXT, projectName TEXT NOT NULL,
                    txHash TEXT NOT NULL, walletAddress TEXT NOT NULL, network TEXT NOT NULL,
                    gasCostUsd REAL NOT NULL, actualRewardUsd REAL NOT NULL,
                    interactionMethod TEXT NOT NULL, timestamp INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_user_interactions_accountScope_projectLocalId_timestamp ON user_interactions(accountScope, projectLocalId, timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_user_interactions_projectOfficialId ON user_interactions(projectOfficialId)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS community_sync_outbox (
                    id TEXT NOT NULL PRIMARY KEY, accountScope TEXT NOT NULL DEFAULT 'guest',
                    projectOfficialId TEXT NOT NULL, gasCostUsd REAL NOT NULL, actualRewardUsd REAL NOT NULL,
                    interactionCount INTEGER NOT NULL, createdAt INTEGER NOT NULL,
                    attemptCount INTEGER NOT NULL DEFAULT 0, lastAttemptAt INTEGER, lastError TEXT
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_community_sync_outbox_accountScope_createdAt ON community_sync_outbox(accountScope, createdAt)")
            db.execSQL("""
                INSERT OR IGNORE INTO user_projects (
                    id, accountScope, projectLocalId, projectOfficialId, projectName, status,
                    totalGasCostUsd, totalActualRewardUsd, netProfitUsd, interactionCount,
                    lastInteractionAt, createdAt, updatedAt
                )
                SELECT id, accountScope, projectLocalId, projectOfficialId, projectCustomName, 'tracking',
                    totalGasCostUsd, totalRewardUsd, netProfitUsd, 0, 0, 0, 0 FROM pnl_records
            """.trimIndent())
        }
    }

    @Volatile private var appContext: Context? = null
    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (database != null) return
        synchronized(this) {
            if (database == null) database = Room.databaseBuilder(context.applicationContext, CacheDatabase::class.java, "airdrop_hunter_cache")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build()
        }
    }
    private fun accountScopeFromToken(token: String?): String {
        if (token.isNullOrBlank()) return "guest"
        val payload = token.split(".").getOrNull(1) ?: return "invalid-session"
        return try {
            val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val userId = JSONObject(String(decoded, Charsets.UTF_8)).optString("sub", "")
            if (userId.isBlank()) "invalid-session" else "user:$userId"
        } catch (_: Exception) { "invalid-session" }
    }
    private fun currentAccountScope(): String = accountScopeFromToken(TokenStore.readTokenBlocking())
    private fun observeAccountScope(): Flow<String> {
        val context = appContext ?: return flowOf("guest")
        return TokenStore.observeToken(context).map { accountScopeFromToken(it) }
    }
    fun observePendingBills(): Flow<List<PendingBillEntity>> = observeAccountScope().flatMapLatest { scope ->
        database?.pendingBillDao()?.observeAll(scope) ?: flowOf(emptyList())
    }
    fun observePnlRecords(): Flow<List<PnlRecordEntity>> = observeAccountScope().flatMapLatest { scope ->
        database?.pnlRecordDao()?.observeAll(scope) ?: flowOf(emptyList())
    }

    fun observeUserProjects(): Flow<List<UserProjectEntity>> = observeAccountScope().flatMapLatest { scope ->
        database?.userProjectDao()?.observeAll(scope) ?: flowOf(emptyList())
    }

    suspend fun readUserProjects(): List<UserProjectEntity> = withContext(Dispatchers.IO) {
        database?.userProjectDao()?.getAll(currentAccountScope()).orEmpty()
    }

    suspend fun joinCommunityProject(projectOfficialId: String, projectName: String): Boolean =
        withContext(Dispatchers.IO) {
            val officialId = projectOfficialId.trim()
            val name = projectName.trim()
            if (officialId.isEmpty() || officialId.length > 120 || name.isEmpty()) return@withContext false
            val db = database ?: return@withContext false
            val scope = currentAccountScope()
            if (scope == "invalid-session") return@withContext false
            val localId = "community:$officialId"
            db.withTransaction {
                val existing = db.userProjectDao().findByLocalId(localId, scope)
                val now = System.currentTimeMillis()
                val project = existing?.copy(
                    projectOfficialId = officialId,
                    projectName = name,
                    status = existing.status.ifBlank { "tracking" },
                    updatedAt = now
                ) ?: UserProjectEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    accountScope = scope,
                    projectLocalId = localId,
                    projectOfficialId = officialId,
                    projectName = name,
                    status = "tracking",
                    totalGasCostUsd = 0.0,
                    totalActualRewardUsd = 0.0,
                    netProfitUsd = 0.0,
                    interactionCount = 0,
                    lastInteractionAt = 0L,
                    createdAt = now,
                    updatedAt = now
                )
                db.userProjectDao().upsert(project)
            }
            true
        }

    fun observeUserProject(projectLocalId: String): Flow<UserProjectEntity?> =
        observeAccountScope().flatMapLatest { scope ->
            database?.userProjectDao()?.observeByLocalId(projectLocalId, scope) ?: flowOf(null)
        }

    fun observeUserInteractions(projectLocalId: String): Flow<List<UserInteractionEntity>> =
        observeAccountScope().flatMapLatest { scope ->
            database?.userInteractionDao()?.observeByProject(projectLocalId, scope) ?: flowOf(emptyList())
        }

    fun observeCommunitySyncOutbox(): Flow<List<CommunitySyncOutboxEntity>> =
        observeAccountScope().flatMapLatest { scope ->
            database?.communitySyncOutboxDao()?.observeAll(scope) ?: flowOf(emptyList())
        }

    suspend fun getPendingCommunitySyncBatches(limit: Int = 50): List<CommunitySyncBatch> =
        withContext(Dispatchers.IO) {
            val token = TokenStore.readTokenBlocking()
            val activeScope = accountScopeFromToken(token)
            val dao = database?.communitySyncOutboxDao() ?: return@withContext emptyList()
            val result = mutableListOf<CommunitySyncBatch>()
            val guestEntries = dao.getPending("guest", limit)
            if (guestEntries.isNotEmpty()) result += CommunitySyncBatch("guest", "", guestEntries)
            if (activeScope != "guest" && activeScope != "invalid-session") {
                val accountEntries = dao.getPending(activeScope, limit)
                if (accountEntries.isNotEmpty() && !token.isNullOrBlank()) {
                    result += CommunitySyncBatch(activeScope, "Bearer $token", accountEntries)
                }
            }
            result
        }

    suspend fun completeCommunitySync(id: String, accountScope: String) = withContext(Dispatchers.IO) {
        database?.communitySyncOutboxDao()?.deleteById(id, accountScope)
    }

    suspend fun markCommunitySyncAttempt(
        id: String,
        accountScope: String,
        error: String
    ) = withContext(Dispatchers.IO) {
        database?.communitySyncOutboxDao()?.markAttemptById(
            id, accountScope, System.currentTimeMillis(), error.take(500)
        )
    }

    fun observeProjectRatings(projectOfficialId: String): Flow<List<ProjectRatingEntity>> =
        observeAccountScope().flatMapLatest { scope ->
            database?.projectRatingDao()?.observeByProject(projectOfficialId, scope) ?: flowOf(emptyList())
        }
    fun observeProjectRatingAverage(projectOfficialId: String): Flow<Double?> =
        observeAccountScope().flatMapLatest { scope ->
            database?.projectRatingDao()?.observeAverage(projectOfficialId, scope) ?: flowOf(null)
        }
    fun observeAllProjectRatings(): Flow<List<ProjectRatingEntity>> = observeAccountScope().flatMapLatest { scope ->
        database?.projectRatingDao()?.observeAll(scope) ?: flowOf(emptyList())
    }
    suspend fun hasPnlParticipation(projectOfficialId: String): Boolean = withContext(Dispatchers.IO) {
        val scope = currentAccountScope()
        database?.pnlRecordDao()?.findByProject(projectOfficialId, scope) != null
    }
    suspend fun saveProjectRating(projectOfficialId: String, walletAddress: String, score: Int): Boolean = withContext(Dispatchers.IO) {
        if (score !in 1..5) return@withContext false
        val db = database ?: return@withContext false
        val scope = currentAccountScope()
        if (db.pnlRecordDao().findByProject(projectOfficialId, scope) == null) return@withContext false
        db.projectRatingDao().insert(ProjectRatingEntity(java.util.UUID.randomUUID().toString(), projectOfficialId, walletAddress, score, System.currentTimeMillis(), scope))
        true
    }
    suspend fun addPendingBill(bill: PendingBillEntity) = withContext(Dispatchers.IO) {
        database?.pendingBillDao()?.insert(bill.copy(accountScope = currentAccountScope()))
    }
    suspend fun hasRecordedTransaction(network: String, walletAddress: String, txHash: String): Boolean =
        withContext(Dispatchers.IO) {
            database?.userInteractionDao()?.hasTransaction(
                currentAccountScope(), network, walletAddress, txHash
            ) ?: false
        }
    suspend fun deletePendingBill(id: String) = withContext(Dispatchers.IO) {
        database?.pendingBillDao()?.delete(id, currentAccountScope())
    }
    suspend fun confirmPendingBill(
        id: String,
        projectLocalId: String,
        projectOfficialId: String?,
        projectCustomName: String,
        gas: Double,
        reward: Double
    ): Boolean = withContext(Dispatchers.IO) {
        if (projectLocalId.isBlank() || !gas.isFinite() || !reward.isFinite() || gas < 0.0 || reward < 0.0) return@withContext false
        val db = database ?: return@withContext false
        val scope = currentAccountScope()
        db.withTransaction {
            val bill = db.pendingBillDao().findById(id, scope) ?: return@withTransaction false
            val canonicalOfficialId = projectOfficialId?.trim()?.takeIf { it.isNotEmpty() && it.length <= 120 }
            val effectiveNetwork = bill.network.takeIf { it.isNotBlank() && !it.equals("unknown", true) }
                ?: when (bill.chain.trim().lowercase()) {
                    "ethereum", "erc20", "evm" -> "ethereum"
                    "tron", "trc20" -> "tron"
                    "bitcoin", "btc" -> "bitcoin"
                    "bsc", "polygon", "arbitrum", "optimism", "base", "avalanche" -> bill.chain.trim().lowercase()
                    else -> "unknown"
                }
            val effectiveBill = bill.copy(network = effectiveNetwork)
            if (
                bill.txHash.isNotBlank() && bill.walletAddress.isNotBlank() &&
                WalletActivityNetworkPolicy.isSupported(effectiveNetwork) &&
                db.userInteractionDao().hasTransaction(scope, effectiveNetwork, bill.walletAddress, bill.txHash)
            ) return@withTransaction false
            // Guests contribute only the public aggregate. Their detailed wallet,
            // transaction, project and P&L records must not be persisted after confirmation.
            if (!shouldPersistPrivateAccounting(scope)) {
                canonicalOfficialId?.takeIf { isEligibleForPublicContribution(effectiveBill) }?.let { officialId ->
                    db.communitySyncOutboxDao().insert(
                        CommunitySyncOutboxEntity(
                            id = bill.id,
                            accountScope = scope,
                            projectOfficialId = officialId,
                            gasCostUsd = gas,
                            actualRewardUsd = reward,
                            interactionCount = 1,
                            createdAt = bill.timestamp
                        )
                    )
                }
                db.pendingBillDao().delete(id, scope)
                return@withTransaction true
            }
            val ledgerKey = "${scope}:local:personal_ledger"
            val ledgerType = object : TypeToken<List<PersonalLedgerWriteRequest>>() {}.type
            val gson = Gson()
            val existingLedger = db.cacheDao().read(ledgerKey)?.let { raw ->
                runCatching { gson.fromJson<List<PersonalLedgerWriteRequest>>(raw, ledgerType) }.getOrNull()
            }.orEmpty()
            val ledgerEntry = PersonalLedgerWriteRequest(
                client_record_id = bill.id,
                project_id = projectLocalId,
                project_name = projectCustomName,
                chain = bill.chain.ifBlank { "unknown" },
                wallet_address = bill.walletAddress,
                tx_hash = bill.txHash,
                interaction_method = bill.interactionMethod,
                expected_gas_cost_usd = bill.gasCostUsd,
                // No reliable reward estimate exists at confirmation time; keep it unknown.
                expected_reward_usd = null,
                actual_gas_cost_usd = gas,
                actual_reward_usd = reward,
                occurred_at = Instant.ofEpochMilli(bill.timestamp).toString(),
                extra = mapOf(
                    "project_official_id" to (canonicalOfficialId ?: ""),
                    // Demo transactions remain available for local UI testing but are never synced.
                    "is_demo" to bill.txHash.startsWith("MOCK-").toString()
                )
            )
            val updatedLedger = (existingLedger.filterNot { it.client_record_id == bill.id } + ledgerEntry)
            db.cacheDao().write(CachedPayload(ledgerKey, gson.toJson(updatedLedger), System.currentTimeMillis()))
            db.pnlRecordDao().addToProject(projectLocalId, canonicalOfficialId, projectCustomName, bill.walletAddress, gas, reward, scope)
            val recordedAt = bill.timestamp
            val updatedProjects = db.userProjectDao().addAmounts(
                projectLocalId, canonicalOfficialId, projectCustomName, gas, reward, recordedAt, scope
            )
            if (updatedProjects == 0) {
                db.userProjectDao().insert(
                    UserProjectEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        accountScope = scope,
                        projectLocalId = projectLocalId,
                        projectOfficialId = canonicalOfficialId,
                        projectName = projectCustomName,
                        status = "tracking",
                        totalGasCostUsd = gas,
                        totalActualRewardUsd = reward,
                        netProfitUsd = reward - gas,
                        interactionCount = 1,
                        lastInteractionAt = recordedAt,
                        createdAt = recordedAt,
                        updatedAt = recordedAt
                    )
                )
            }
            db.userInteractionDao().insert(
                UserInteractionEntity(
                    id = bill.id,
                    accountScope = scope,
                    projectLocalId = projectLocalId,
                    projectOfficialId = canonicalOfficialId,
                    projectName = projectCustomName,
                    txHash = bill.txHash,
                    walletAddress = bill.walletAddress,
                    network = effectiveNetwork,
                    gasCostUsd = gas,
                    actualRewardUsd = reward,
                    interactionMethod = bill.interactionMethod,
                    timestamp = recordedAt
                )
            )
            canonicalOfficialId?.takeIf {
                scope != "invalid-session" && isEligibleForPublicContribution(effectiveBill)
            }?.let { officialId ->
                db.communitySyncOutboxDao().insert(
                    CommunitySyncOutboxEntity(
                        id = bill.id,
                        accountScope = scope,
                        projectOfficialId = officialId,
                        gasCostUsd = gas,
                        actualRewardUsd = reward,
                        interactionCount = 1,
                        createdAt = recordedAt
                    )
                )
            }
            db.pendingBillDao().delete(id, scope)
            true
        }
        .also { confirmed ->
            if (confirmed) appContext?.let { CommunityContributionSyncScheduler.enqueue(it) }
        }
    }
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pending_bills ADD COLUMN accountScope TEXT NOT NULL DEFAULT 'guest'")
            db.execSQL("ALTER TABLE pnl_records ADD COLUMN accountScope TEXT NOT NULL DEFAULT 'guest'")
            db.execSQL("ALTER TABLE project_ratings ADD COLUMN accountScope TEXT NOT NULL DEFAULT 'guest'")
            db.execSQL("DROP INDEX IF EXISTS index_pnl_records_projectOfficialId")
            db.execSQL("DROP INDEX IF EXISTS index_project_ratings_projectOfficialId_walletAddress")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pnl_records_accountScope_projectOfficialId ON pnl_records(accountScope, projectOfficialId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_project_ratings_accountScope_projectOfficialId_walletAddress ON project_ratings(accountScope, projectOfficialId, walletAddress)")
            db.execSQL("UPDATE cached_payloads SET cacheKey = 'guest:' || cacheKey WHERE cacheKey LIKE 'local:%'")
        }
    }
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE pnl_records_new (
                    id TEXT NOT NULL,
                    projectOfficialId TEXT,
                    projectLocalId TEXT NOT NULL,
                    projectCustomName TEXT NOT NULL,
                    walletAddress TEXT NOT NULL,
                    totalGasCostUsd REAL NOT NULL,
                    totalRewardUsd REAL NOT NULL,
                    netProfitUsd REAL NOT NULL,
                    accountScope TEXT NOT NULL DEFAULT 'guest',
                    PRIMARY KEY(id)
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO pnl_records_new (
                    id, projectOfficialId, projectLocalId, projectCustomName, walletAddress,
                    totalGasCostUsd, totalRewardUsd, netProfitUsd, accountScope
                )
                SELECT id,
                    CASE WHEN projectOfficialId IN ('linea','scroll','zksync','starknet','base','arbitrum','optimism')
                         THEN projectOfficialId ELSE NULL END,
                    projectOfficialId, projectCustomName, walletAddress,
                    totalGasCostUsd, totalRewardUsd, netProfitUsd, accountScope
                FROM pnl_records
            """.trimIndent())
            db.execSQL("DROP TABLE pnl_records")
            db.execSQL("ALTER TABLE pnl_records_new RENAME TO pnl_records")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pnl_records_accountScope_projectLocalId ON pnl_records(accountScope, projectLocalId)")
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
    suspend fun readLocal(key: String): String? = withContext(Dispatchers.IO) { runCatching { database?.cacheDao()?.read("${currentAccountScope()}:local:$key") }.getOrNull() }
    suspend fun writeLocal(key: String, payload: String) = withContext(Dispatchers.IO) {
        runCatching { database?.cacheDao()?.write(CachedPayload("${currentAccountScope()}:local:$key", payload, System.currentTimeMillis())) }; Unit
    }
}

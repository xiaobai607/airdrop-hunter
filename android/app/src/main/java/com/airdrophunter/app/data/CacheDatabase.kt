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

@Entity(tableName = "pending_bills", primaryKeys = ["accountScope", "id"])
data class PendingBillEntity(val id: String, val txHash: String, val walletAddress: String, val gasCostUsd: Double, val interactionMethod: String, val timestamp: Long, @ColumnInfo(defaultValue = "'unknown'") val chain: String = "unknown", @ColumnInfo(defaultValue = "'guest'") val accountScope: String = "guest", @ColumnInfo(defaultValue = "'unknown'") val network: String = "unknown", val assetBreakdownJson: String? = null)

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
    @ColumnInfo(defaultValue = "'guest'") val accountScope: String = "guest",
    @ColumnInfo(defaultValue = "1") val usdProfitComplete: Boolean = true
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
    val createdAt: Long, val updatedAt: Long,
    @ColumnInfo(defaultValue = "1") val usdProfitComplete: Boolean = true
)

@Entity(tableName = "user_interactions", primaryKeys = ["accountScope", "id"], indices = [Index(value = ["accountScope", "projectLocalId", "timestamp"]), Index(value = ["projectOfficialId"])])
data class UserInteractionEntity(
    val id: String,
    @ColumnInfo(defaultValue = "'guest'") val accountScope: String = "guest",
    val projectLocalId: String, val projectOfficialId: String?, val projectName: String,
    val txHash: String, val walletAddress: String, val network: String,
    val gasCostUsd: Double, val actualRewardUsd: Double, val interactionMethod: String, val timestamp: Long,
    val assetBreakdownJson: String? = null,
    val expectedGasCostUsd: Double? = null,
    val expectedRewardUsd: Double? = null,
    @ColumnInfo(defaultValue = "'USD'") val rewardKind: String = "USD",
    val rewardAmount: Double? = null,
    val rewardUnit: String? = null,
    val rewardUsdValuation: Double? = null,
    val rewardQuoteSource: String? = null,
    val rewardQuoteTimestamp: Long? = null,
    @ColumnInfo(defaultValue = "1") val usdProfitComplete: Boolean = true,
    @ColumnInfo(defaultValue = "0") val publicContributionOptIn: Boolean = false,
    @ColumnInfo(defaultValue = "'private'") val publicContributionStatus: String = "private",
    val publicContributionTier: String? = null,
    val publicContributionError: String? = null
)

@Entity(tableName = "community_sync_outbox", primaryKeys = ["accountScope", "id"], indices = [Index(value = ["accountScope", "createdAt"])])
data class CommunitySyncOutboxEntity(
    val id: String,
    @ColumnInfo(defaultValue = "'guest'") val accountScope: String = "guest",
    val projectOfficialId: String, val gasCostUsd: Double, val actualRewardUsd: Double,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null, val lastError: String? = null,
    val assetBreakdownJson: String? = null,
    @ColumnInfo(defaultValue = "0") val terminalFailure: Boolean = false,
    val network: String? = null,
    val walletAddress: String? = null,
    val txHash: String? = null,
    val occurredAt: Long? = null
)

@Entity(
    tableName = "community_favorites",
    primaryKeys = ["accountScope", "projectOfficialId"],
    indices = [Index(value = ["accountScope", "createdAt"])]
)
data class CommunityFavoriteEntity(
    @ColumnInfo(defaultValue = "'guest'") val accountScope: String = "guest",
    val projectOfficialId: String,
    val projectName: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "1") val isFavorite: Boolean = true,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt
)

@Entity(
    tableName = "project_wallet_assignments",
    primaryKeys = ["accountScope", "projectKey", "walletId"],
    indices = [Index(value = ["accountScope", "projectKey", "createdAt"])]
)
data class ProjectWalletAssignmentEntity(
    @ColumnInfo(defaultValue = "'guest'") val accountScope: String = "guest",
    val projectKey: String,
    val walletId: String,
    val createdAt: Long
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
interface MarketCoinDao {
    @Query("SELECT * FROM market_coins ORDER BY CASE WHEN rank IS NULL THEN 1 ELSE 0 END, rank ASC, symbol COLLATE NOCASE ASC")
    suspend fun getAll(): List<MarketCoinEntity>
    @Query("DELETE FROM market_coins") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<MarketCoinEntity>)
}

@Dao
interface HotCoinCacheDao {
    @Query("SELECT * FROM hot_coin_cache WHERE cacheKey = :cacheKey ORDER BY sortOrder ASC")
    suspend fun getAll(cacheKey: String): List<HotCoinCacheEntity>
    @Query("DELETE FROM hot_coin_cache WHERE cacheKey = :cacheKey") suspend fun clear(cacheKey: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<HotCoinCacheEntity>)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history WHERE accountScope = :scope ORDER BY lastUsedAt DESC")
    fun observeAll(scope: String): Flow<List<SearchHistoryEntity>>
    @Query("SELECT * FROM search_history WHERE accountScope = :scope ORDER BY lastUsedAt DESC")
    suspend fun getAll(scope: String): List<SearchHistoryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: SearchHistoryEntity)
    @Query("DELETE FROM search_history WHERE accountScope = :scope AND category = :category AND normalizedQuery = :normalizedQuery")
    suspend fun delete(scope: String, category: String, normalizedQuery: String)
    @Query("DELETE FROM search_history WHERE accountScope = :scope") suspend fun clear(scope: String)
}

@Dao
interface UnifiedAssetDao {
    @Query("SELECT * FROM unified_assets WHERE accountScope = :scope AND walletId = :walletId AND chain = :chain ORDER BY usdValue DESC")
    fun observeForWallet(scope: String, walletId: String, chain: String): Flow<List<UnifiedAssetEntity>>
    @Query("SELECT * FROM unified_assets WHERE accountScope = :scope AND walletId = :walletId AND chain = :chain ORDER BY usdValue DESC")
    suspend fun getForWallet(scope: String, walletId: String, chain: String): List<UnifiedAssetEntity>
    @Query("DELETE FROM unified_assets WHERE accountScope = :scope AND walletId = :walletId AND chain = :chain")
    suspend fun clearForWallet(scope: String, walletId: String, chain: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<UnifiedAssetEntity>)
}

@Dao
interface CacheMetadataDao {
    @Query("SELECT * FROM cache_metadata WHERE cacheKey = :cacheKey AND source = :source LIMIT 1")
    suspend fun find(cacheKey: String, source: String): CacheMetadataEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: CacheMetadataEntity)
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

    @Query("UPDATE pnl_records SET projectOfficialId = :projectOfficialId, projectCustomName = :customName, walletAddress = :walletAddress, totalGasCostUsd = totalGasCostUsd + :gas, totalRewardUsd = totalRewardUsd + :reward, netProfitUsd = (totalRewardUsd + :reward) - (totalGasCostUsd + :gas), usdProfitComplete = usdProfitComplete AND :complete WHERE projectLocalId = :projectLocalId AND accountScope = :accountScope")
    suspend fun addAmounts(projectLocalId: String, projectOfficialId: String?, customName: String, walletAddress: String, gas: Double, reward: Double, complete: Boolean, accountScope: String): Int

    @Transaction
    suspend fun addToProject(projectLocalId: String, projectOfficialId: String?, customName: String, walletAddress: String, gas: Double, reward: Double, complete: Boolean, accountScope: String) {
        if (addAmounts(projectLocalId, projectOfficialId, customName, walletAddress, gas, reward, complete, accountScope) == 0) {
            insert(PnlRecordEntity(
                java.util.UUID.randomUUID().toString(), projectOfficialId, projectLocalId,
                customName, walletAddress, gas, reward, reward - gas, accountScope, complete
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
    @Query("UPDATE user_projects SET projectOfficialId = :projectOfficialId, projectName = :projectName, totalGasCostUsd = totalGasCostUsd + :gas, totalActualRewardUsd = totalActualRewardUsd + :reward, netProfitUsd = (totalActualRewardUsd + :reward) - (totalGasCostUsd + :gas), interactionCount = interactionCount + 1, lastInteractionAt = :timestamp, updatedAt = :timestamp, usdProfitComplete = usdProfitComplete AND :complete WHERE projectLocalId = :projectLocalId AND accountScope = :accountScope")
    suspend fun addAmounts(projectLocalId: String, projectOfficialId: String?, projectName: String, gas: Double, reward: Double, timestamp: Long, complete: Boolean, accountScope: String): Int
}

@Dao
interface UserInteractionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(interaction: UserInteractionEntity)
    @Query("SELECT * FROM user_interactions WHERE projectLocalId = :projectLocalId AND accountScope = :accountScope ORDER BY timestamp DESC")
    fun observeByProject(projectLocalId: String, accountScope: String): Flow<List<UserInteractionEntity>>
    @Query("SELECT * FROM user_interactions WHERE accountScope = :accountScope ORDER BY timestamp DESC")
    suspend fun getAll(accountScope: String): List<UserInteractionEntity>
    @Query("SELECT * FROM user_interactions WHERE accountScope = :accountScope AND id = :id LIMIT 1")
    suspend fun findById(id: String, accountScope: String): UserInteractionEntity?
    @Query("UPDATE user_interactions SET publicContributionStatus = :status, publicContributionTier = :verificationTier, publicContributionError = :error WHERE id = :id AND accountScope = :accountScope")
    suspend fun updateContributionStatus(id: String, accountScope: String, status: String, verificationTier: String?, error: String?): Int
    @Query("SELECT EXISTS(SELECT 1 FROM user_interactions WHERE accountScope = :accountScope AND LOWER(network) = LOWER(:network) AND ((LOWER(:network) = 'solana' AND txHash = :txHash) OR (LOWER(:network) != 'solana' AND LOWER(txHash) = LOWER(:txHash))) AND ((LOWER(:network) IN ('ethereum', 'bsc', 'polygon', 'arbitrum', 'optimism', 'base', 'avalanche') AND LOWER(walletAddress) = LOWER(:walletAddress)) OR (LOWER(:network) NOT IN ('ethereum', 'bsc', 'polygon', 'arbitrum', 'optimism', 'base', 'avalanche') AND walletAddress = :walletAddress)))")
    suspend fun hasTransaction(accountScope: String, network: String, walletAddress: String, txHash: String): Boolean
}

@Dao
interface CommunitySyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entry: CommunitySyncOutboxEntity): Long
    @Query("SELECT * FROM community_sync_outbox WHERE accountScope = :accountScope ORDER BY createdAt ASC")
    fun observeAll(accountScope: String): Flow<List<CommunitySyncOutboxEntity>>
    @Query("SELECT * FROM community_sync_outbox WHERE accountScope = :accountScope AND terminalFailure = 0 ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPending(accountScope: String, limit: Int): List<CommunitySyncOutboxEntity>
    @Query("DELETE FROM community_sync_outbox WHERE id = :id AND accountScope = :accountScope")
    suspend fun deleteById(id: String, accountScope: String)
    @Query("UPDATE community_sync_outbox SET attemptCount = attemptCount + 1, lastAttemptAt = :attemptAt, lastError = :error WHERE id = :id AND accountScope = :accountScope")
    suspend fun markAttemptById(id: String, accountScope: String, attemptAt: Long, error: String?)

    @Query("UPDATE community_sync_outbox SET terminalFailure = 1, attemptCount = attemptCount + 1, lastAttemptAt = :attemptAt, lastError = :error WHERE id = :id AND accountScope = :accountScope")
    suspend fun markTerminalFailureById(id: String, accountScope: String, attemptAt: Long, error: String?)
}

@Dao
interface CommunityFavoriteDao {
    @Query("SELECT * FROM community_favorites WHERE accountScope = :accountScope AND isFavorite = 1 ORDER BY createdAt DESC")
    fun observeAll(accountScope: String): Flow<List<CommunityFavoriteEntity>>

    @Query("SELECT * FROM community_favorites WHERE accountScope = :accountScope ORDER BY updatedAt ASC")
    suspend fun getAll(accountScope: String): List<CommunityFavoriteEntity>

    @Query("SELECT * FROM community_favorites WHERE accountScope = :accountScope AND projectOfficialId = :projectOfficialId LIMIT 1")
    suspend fun find(accountScope: String, projectOfficialId: String): CommunityFavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: CommunityFavoriteEntity)
}

@Dao
interface ProjectWalletAssignmentDao {
    @Query("SELECT walletId FROM project_wallet_assignments WHERE accountScope = :accountScope AND projectKey = :projectKey ORDER BY createdAt ASC")
    fun observeWalletIds(accountScope: String, projectKey: String): Flow<List<String>>

    @Query("SELECT walletId FROM project_wallet_assignments WHERE accountScope = :accountScope AND projectKey = :projectKey ORDER BY createdAt ASC")
    suspend fun getWalletIds(accountScope: String, projectKey: String): List<String>

    @Query("DELETE FROM project_wallet_assignments WHERE accountScope = :accountScope AND projectKey = :projectKey")
    suspend fun deleteForProject(accountScope: String, projectKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assignments: List<ProjectWalletAssignmentEntity>)
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

@Dao
interface LocalBackupDao {
 @Query("SELECT * FROM cached_payloads WHERE cacheKey LIKE :prefix || '%'") suspend fun cachedPayloadsByPrefix(prefix:String):List<CachedPayload>
 @Query("DELETE FROM cached_payloads WHERE cacheKey LIKE :prefix || '%'") suspend fun deleteCachedPayloadsByPrefix(prefix:String)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertCachedPayloads(rows:List<CachedPayload>)
 @Query("SELECT * FROM pending_bills WHERE accountScope=:scope") suspend fun pendingBills(scope:String):List<PendingBillEntity>
 @Query("DELETE FROM pending_bills WHERE accountScope=:scope") suspend fun clearPendingBills(scope:String)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertPendingBills(rows:List<PendingBillEntity>)
 @Query("SELECT * FROM pnl_records WHERE accountScope=:scope") suspend fun pnlRecords(scope:String):List<PnlRecordEntity>
 @Query("DELETE FROM pnl_records WHERE accountScope=:scope") suspend fun clearPnlRecords(scope:String)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertPnlRecords(rows:List<PnlRecordEntity>)
 @Query("SELECT * FROM project_ratings WHERE accountScope=:scope") suspend fun projectRatings(scope:String):List<ProjectRatingEntity>
 @Query("DELETE FROM project_ratings WHERE accountScope=:scope") suspend fun clearProjectRatings(scope:String)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertProjectRatings(rows:List<ProjectRatingEntity>)
 @Query("SELECT * FROM user_projects WHERE accountScope=:scope") suspend fun userProjects(scope:String):List<UserProjectEntity>
 @Query("DELETE FROM user_projects WHERE accountScope=:scope") suspend fun clearUserProjects(scope:String)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertUserProjects(rows:List<UserProjectEntity>)
 @Query("SELECT * FROM user_interactions WHERE accountScope=:scope") suspend fun userInteractions(scope:String):List<UserInteractionEntity>
 @Query("DELETE FROM user_interactions WHERE accountScope=:scope") suspend fun clearUserInteractions(scope:String)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertUserInteractions(rows:List<UserInteractionEntity>)
 @Query("SELECT * FROM community_sync_outbox WHERE accountScope=:scope") suspend fun communityOutbox(scope:String):List<CommunitySyncOutboxEntity>
 @Query("DELETE FROM community_sync_outbox WHERE accountScope=:scope") suspend fun clearCommunityOutbox(scope:String)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertCommunityOutbox(rows:List<CommunitySyncOutboxEntity>)
 @Query("SELECT * FROM community_favorites WHERE accountScope=:scope") suspend fun communityFavorites(scope:String):List<CommunityFavoriteEntity>
 @Query("DELETE FROM community_favorites WHERE accountScope=:scope") suspend fun clearCommunityFavorites(scope:String)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertCommunityFavorites(rows:List<CommunityFavoriteEntity>)
 @Query("SELECT * FROM project_wallet_assignments WHERE accountScope=:scope") suspend fun projectWalletAssignments(scope:String):List<ProjectWalletAssignmentEntity>
 @Query("DELETE FROM project_wallet_assignments WHERE accountScope=:scope") suspend fun clearProjectWalletAssignments(scope:String)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertProjectWalletAssignments(rows:List<ProjectWalletAssignmentEntity>)
}

@Database(entities = [CachedPayload::class, PendingBillEntity::class, PnlRecordEntity::class, ProjectRatingEntity::class, UserProjectEntity::class, UserInteractionEntity::class, CommunitySyncOutboxEntity::class, CommunityFavoriteEntity::class, ProjectWalletAssignmentEntity::class, MarketCoinEntity::class, HotCoinCacheEntity::class, SearchHistoryEntity::class, UnifiedAssetEntity::class, CacheMetadataEntity::class], version = 20, exportSchema = false)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun pendingBillDao(): PendingBillDao
    abstract fun pnlRecordDao(): PnlRecordDao
    abstract fun projectRatingDao(): ProjectRatingDao
    abstract fun communityFavoriteDao(): CommunityFavoriteDao
    abstract fun projectWalletAssignmentDao(): ProjectWalletAssignmentDao
    abstract fun userProjectDao(): UserProjectDao
    abstract fun userInteractionDao(): UserInteractionDao
    abstract fun communitySyncOutboxDao(): CommunitySyncOutboxDao
    abstract fun localBackupDao(): LocalBackupDao
    abstract fun marketCoinDao(): MarketCoinDao
    abstract fun hotCoinCacheDao(): HotCoinCacheDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun unifiedAssetDao(): UnifiedAssetDao
    abstract fun cacheMetadataDao(): CacheMetadataDao
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

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS project_wallet_assignments (
                    accountScope TEXT NOT NULL DEFAULT 'guest',
                    projectKey TEXT NOT NULL,
                    walletId TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    PRIMARY KEY(accountScope, projectKey, walletId)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_project_wallet_assignments_accountScope_projectKey_createdAt ON project_wallet_assignments(accountScope, projectKey, createdAt)")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS community_favorites (
                    accountScope TEXT NOT NULL DEFAULT 'guest',
                    projectOfficialId TEXT NOT NULL,
                    projectName TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    PRIMARY KEY(accountScope, projectOfficialId)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_community_favorites_accountScope_createdAt ON community_favorites(accountScope, createdAt)")
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

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS community_sync_outbox_new (
                    id TEXT NOT NULL PRIMARY KEY, accountScope TEXT NOT NULL DEFAULT 'guest',
                    projectOfficialId TEXT NOT NULL, gasCostUsd REAL NOT NULL, actualRewardUsd REAL NOT NULL,
                    createdAt INTEGER NOT NULL, attemptCount INTEGER NOT NULL DEFAULT 0,
                    lastAttemptAt INTEGER, lastError TEXT
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO community_sync_outbox_new (
                    id, accountScope, projectOfficialId, gasCostUsd, actualRewardUsd,
                    createdAt, attemptCount, lastAttemptAt, lastError
                )
                SELECT id, accountScope, projectOfficialId, gasCostUsd, actualRewardUsd,
                    createdAt, attemptCount, lastAttemptAt, lastError
                FROM community_sync_outbox
            """.trimIndent())
            db.execSQL("DROP TABLE community_sync_outbox")
            db.execSQL("ALTER TABLE community_sync_outbox_new RENAME TO community_sync_outbox")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_community_sync_outbox_accountScope_createdAt ON community_sync_outbox(accountScope, createdAt)")
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS pending_bills_new (
                    id TEXT NOT NULL, txHash TEXT NOT NULL, walletAddress TEXT NOT NULL,
                    gasCostUsd REAL NOT NULL, interactionMethod TEXT NOT NULL, timestamp INTEGER NOT NULL,
                    chain TEXT NOT NULL DEFAULT 'unknown', accountScope TEXT NOT NULL DEFAULT 'guest',
                    network TEXT NOT NULL DEFAULT 'unknown', PRIMARY KEY(accountScope, id)
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO pending_bills_new (
                    id, txHash, walletAddress, gasCostUsd, interactionMethod, timestamp, chain, accountScope, network
                )
                SELECT id, txHash, walletAddress, gasCostUsd, interactionMethod, timestamp, chain, accountScope, network
                FROM pending_bills
            """.trimIndent())
            db.execSQL("DROP TABLE pending_bills")
            db.execSQL("ALTER TABLE pending_bills_new RENAME TO pending_bills")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS user_interactions_new (
                    id TEXT NOT NULL, accountScope TEXT NOT NULL DEFAULT 'guest',
                    projectLocalId TEXT NOT NULL, projectOfficialId TEXT, projectName TEXT NOT NULL,
                    txHash TEXT NOT NULL, walletAddress TEXT NOT NULL, network TEXT NOT NULL,
                    gasCostUsd REAL NOT NULL, actualRewardUsd REAL NOT NULL,
                    interactionMethod TEXT NOT NULL, timestamp INTEGER NOT NULL,
                    PRIMARY KEY(accountScope, id)
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO user_interactions_new (
                    id, accountScope, projectLocalId, projectOfficialId, projectName, txHash,
                    walletAddress, network, gasCostUsd, actualRewardUsd, interactionMethod, timestamp
                )
                SELECT id, accountScope, projectLocalId, projectOfficialId, projectName, txHash,
                    walletAddress, network, gasCostUsd, actualRewardUsd, interactionMethod, timestamp
                FROM user_interactions
            """.trimIndent())
            db.execSQL("DROP TABLE user_interactions")
            db.execSQL("ALTER TABLE user_interactions_new RENAME TO user_interactions")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_user_interactions_accountScope_projectLocalId_timestamp ON user_interactions(accountScope, projectLocalId, timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_user_interactions_projectOfficialId ON user_interactions(projectOfficialId)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS community_sync_outbox_new (
                    id TEXT NOT NULL, accountScope TEXT NOT NULL DEFAULT 'guest',
                    projectOfficialId TEXT NOT NULL, gasCostUsd REAL NOT NULL, actualRewardUsd REAL NOT NULL,
                    createdAt INTEGER NOT NULL, attemptCount INTEGER NOT NULL DEFAULT 0,
                    lastAttemptAt INTEGER, lastError TEXT, PRIMARY KEY(accountScope, id)
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO community_sync_outbox_new (
                    id, accountScope, projectOfficialId, gasCostUsd, actualRewardUsd,
                    createdAt, attemptCount, lastAttemptAt, lastError
                )
                SELECT id, accountScope, projectOfficialId, gasCostUsd, actualRewardUsd,
                    createdAt, attemptCount, lastAttemptAt, lastError
                FROM community_sync_outbox
            """.trimIndent())
            db.execSQL("DROP TABLE community_sync_outbox")
            db.execSQL("ALTER TABLE community_sync_outbox_new RENAME TO community_sync_outbox")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_community_sync_outbox_accountScope_createdAt ON community_sync_outbox(accountScope, createdAt)")
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pending_bills ADD COLUMN assetBreakdownJson TEXT")
            db.execSQL("ALTER TABLE user_interactions ADD COLUMN assetBreakdownJson TEXT")
            db.execSQL("ALTER TABLE community_sync_outbox ADD COLUMN assetBreakdownJson TEXT")
        }
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE community_favorites ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE community_favorites ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE community_favorites SET updatedAt = createdAt WHERE updatedAt = 0")
        }
    }

    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // NULL means unknown for historical rows; never fabricate a zero estimate.
            db.execSQL("ALTER TABLE user_interactions ADD COLUMN expectedGasCostUsd REAL")
            db.execSQL("ALTER TABLE user_interactions ADD COLUMN expectedRewardUsd REAL")
        }
    }
    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Preserve conflict rows for diagnosis, but keep them out of automatic retries.
            db.execSQL("ALTER TABLE community_sync_outbox ADD COLUMN terminalFailure INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pnl_records ADD COLUMN usdProfitComplete INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE user_projects ADD COLUMN usdProfitComplete INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE user_interactions ADD COLUMN rewardKind TEXT NOT NULL DEFAULT 'USD'")
            db.execSQL("ALTER TABLE user_interactions ADD COLUMN rewardAmount REAL")
            db.execSQL("ALTER TABLE user_interactions ADD COLUMN rewardUnit TEXT")
            db.execSQL("ALTER TABLE user_interactions ADD COLUMN rewardUsdValuation REAL")
            db.execSQL("ALTER TABLE user_interactions ADD COLUMN rewardQuoteSource TEXT")
            db.execSQL("ALTER TABLE user_interactions ADD COLUMN rewardQuoteTimestamp INTEGER")
            db.execSQL("ALTER TABLE user_interactions ADD COLUMN usdProfitComplete INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE user_interactions ADD COLUMN publicContributionOptIn INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE user_interactions SET rewardAmount = actualRewardUsd, rewardUnit = 'USD', rewardUsdValuation = actualRewardUsd, rewardQuoteSource = 'legacy-recorded'")
        }
    }

    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE community_sync_outbox ADD COLUMN network TEXT")
            db.execSQL("ALTER TABLE community_sync_outbox ADD COLUMN walletAddress TEXT")
            db.execSQL("ALTER TABLE community_sync_outbox ADD COLUMN txHash TEXT")
            db.execSQL("ALTER TABLE community_sync_outbox ADD COLUMN occurredAt INTEGER")
        }
    }

    private val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS market_coins (sourceId TEXT NOT NULL, source TEXT NOT NULL, symbol TEXT NOT NULL, name TEXT NOT NULL, priceUsd REAL, change24hPercent REAL, marketCapUsd REAL, rank INTEGER, contractAddress TEXT, network TEXT, logoUrl TEXT, fetchedAt INTEGER NOT NULL, PRIMARY KEY(sourceId))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_market_coins_symbol ON market_coins(symbol)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_market_coins_name ON market_coins(name)")
            db.execSQL("CREATE TABLE IF NOT EXISTS hot_coin_cache (cacheKey TEXT NOT NULL, symbol TEXT NOT NULL, name TEXT NOT NULL, lastPrice REAL NOT NULL, change24hPercent REAL NOT NULL, logoUrl TEXT, contractAddress TEXT, network TEXT, sortOrder INTEGER NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(cacheKey, symbol))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_hot_coin_cache_cacheKey_sortOrder ON hot_coin_cache(cacheKey, sortOrder)")
            db.execSQL("CREATE TABLE IF NOT EXISTS search_history (accountScope TEXT NOT NULL DEFAULT 'guest', category TEXT NOT NULL, normalizedQuery TEXT NOT NULL, query TEXT NOT NULL, lastUsedAt INTEGER NOT NULL, PRIMARY KEY(accountScope, category, normalizedQuery))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_accountScope_lastUsedAt ON search_history(accountScope, lastUsedAt)")
            db.execSQL("CREATE TABLE IF NOT EXISTS unified_assets (accountScope TEXT NOT NULL DEFAULT 'guest', walletId TEXT NOT NULL, chain TEXT NOT NULL, assetId TEXT NOT NULL, contractAddress TEXT, symbol TEXT NOT NULL, name TEXT NOT NULL, decimals INTEGER, rawQuantity TEXT NOT NULL, formattedQuantity TEXT NOT NULL, priceUsd REAL, usdValue REAL, isVerified INTEGER NOT NULL, source TEXT NOT NULL, fetchedAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(accountScope, walletId, chain, assetId))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_unified_assets_accountScope_walletId_chain_usdValue ON unified_assets(accountScope, walletId, chain, usdValue)")
            db.execSQL("CREATE TABLE IF NOT EXISTS cache_metadata (cacheKey TEXT NOT NULL, source TEXT NOT NULL, fetchedAt INTEGER, expiresAt INTEGER, ttlMillis INTEGER NOT NULL, refreshStatus TEXT NOT NULL, lastError TEXT, lastManualRefreshAt INTEGER, PRIMARY KEY(cacheKey, source))")
        }
    }
    private val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE user_interactions ADD COLUMN publicContributionStatus TEXT NOT NULL DEFAULT 'private'")
            db.execSQL("ALTER TABLE user_interactions ADD COLUMN publicContributionTier TEXT")
            db.execSQL("ALTER TABLE user_interactions ADD COLUMN publicContributionError TEXT")
        }
    }
    @Volatile private var appContext: Context? = null
    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (database != null) return
        synchronized(this) {
            if (database == null) database = Room.databaseBuilder(context.applicationContext, CacheDatabase::class.java, "airdrop_hunter_cache")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20).build()
        }
    }
    fun schedulePrivateLedgerSync() {
        appContext?.let { PersonalLedgerSyncScheduler.enqueue(it) }
    }
    fun accountScopeKey(token: String?): String = accountScopeFromToken(token)

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

    suspend fun getUserStats(): UserStatsDto = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext UserStatsDto()
        val scope = currentAccountScope()
        calculateLocalUserStats(db.userProjectDao().getAll(scope), db.userInteractionDao().getAll(scope))
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

    fun observeCommunityFavorites(): Flow<List<CommunityFavoriteEntity>> =
        observeAccountScope().flatMapLatest { scope ->
            database?.communityFavoriteDao()?.observeAll(scope) ?: flowOf(emptyList())
        }

    suspend fun toggleCommunityFavorite(projectOfficialId: String, projectName: String): Boolean =
        withContext(Dispatchers.IO) {
            val officialId = projectOfficialId.trim()
            val name = projectName.trim()
            if (officialId.isEmpty() || officialId.length > 120 || name.isEmpty()) return@withContext false
            val db = database ?: return@withContext false
            val scope = currentAccountScope()
            if (scope == "invalid-session") return@withContext false
            db.withTransaction {
                val dao = db.communityFavoriteDao()
                val existing = dao.find(scope, officialId)
                val now = maxOf(System.currentTimeMillis(), (existing?.updatedAt ?: 0L) + 1L)
                dao.upsert(
                    if (existing == null) CommunityFavoriteEntity(
                        accountScope = scope,
                        projectOfficialId = officialId,
                        projectName = name,
                        createdAt = now,
                        isFavorite = true,
                        updatedAt = now
                    ) else existing.copy(
                        projectName = if (existing.isFavorite) existing.projectName else name,
                        isFavorite = !existing.isFavorite,
                        updatedAt = now
                    )
                )
            }
            true
        }

    /**
     * Merge account favorites by their per-project timestamp, then upload the merged state.
     * Guest favorites never enter this path. The captured bearer token and account scope
     * remain fixed for the entire operation, including a concurrent account switch.
     */
    suspend fun syncCommunityFavorites(): Boolean = withContext(Dispatchers.IO) {
        val token = TokenStore.readTokenBlocking()
        val scope = accountScopeFromToken(token)
        if (token.isNullOrBlank() || scope == "guest" || scope == "invalid-session") return@withContext false
        val db = database ?: return@withContext false
        val dao = db.communityFavoriteDao()
        val authorization = "Bearer $token"
        val local = dao.getAll(scope)
        val remote = runCatching { ApiClient.service.getCommunityFavorites(authorization) }.getOrElse {
            return@withContext false
        }
        val merged = mergeCommunityFavoriteStates(
            local.map { CommunityFavoriteStateDto(it.projectOfficialId, it.projectName, it.isFavorite, it.updatedAt) },
            remote
        )
        val saved = runCatching {
            ApiClient.service.syncCommunityFavorites(authorization, CommunityFavoriteSyncRequest(merged))
        }.getOrElse {
            return@withContext false
        }
        db.withTransaction {
            saved.forEach { state ->
                val current = dao.find(scope, state.project_official_id)
                if (current == null || current.updatedAt <= state.updated_at) {
                    dao.upsert(CommunityFavoriteEntity(
                        accountScope = scope,
                        projectOfficialId = state.project_official_id,
                        projectName = state.project_name,
                        createdAt = current?.createdAt ?: state.updated_at,
                        isFavorite = state.is_favorite,
                        updatedAt = state.updated_at
                    ))
                }
            }
        }
        true
    }

    fun observeProjectWalletAssignments(projectKey: String): Flow<List<String>> =
        observeAccountScope().flatMapLatest { scope ->
            database?.projectWalletAssignmentDao()?.observeWalletIds(scope, projectKey) ?: flowOf(emptyList())
        }

    suspend fun readProjectWalletAssignments(projectKey: String): Set<String> = withContext(Dispatchers.IO) {
        if (projectKey.isBlank() || projectKey.length > 160) return@withContext emptySet()
        val scope = currentAccountScope()
        if (scope == "invalid-session") return@withContext emptySet()
        database?.projectWalletAssignmentDao()?.getWalletIds(scope, projectKey)?.toSet().orEmpty()
    }

    suspend fun saveProjectWalletAssignments(projectKey: String, walletIds: Set<String>): Boolean =
        withContext(Dispatchers.IO) {
            if (projectKey.isBlank() || projectKey.length > 160 || walletIds.size > 100 ||
                walletIds.any { it.isBlank() || it.length > 80 }) return@withContext false
            val db = database ?: return@withContext false
            val scope = currentAccountScope()
            if (scope == "invalid-session") return@withContext false
            db.withTransaction {
                val dao = db.projectWalletAssignmentDao()
                dao.deleteForProject(scope, projectKey)
                dao.insertAll(walletIds.map { walletId ->
                    ProjectWalletAssignmentEntity(
                        accountScope = scope,
                        projectKey = projectKey,
                        walletId = walletId,
                        createdAt = System.currentTimeMillis()
                    )
                })
            }
            true
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
                if (accountEntries.isNotEmpty()) {
                    // Current-release contributions are anonymous; no account bearer is attached.
                    result += CommunitySyncBatch(activeScope, "", accountEntries)
                }
            }
            result
        }

    private suspend fun updatePublicContributionLedgerStatus(
        id: String,
        accountScope: String,
        status: String,
        verificationTier: String? = null,
        error: String? = null
    ) {
        val db = database ?: return
        val interactionUpdated = db.userInteractionDao().updateContributionStatus(
            id, accountScope, status, verificationTier, error?.takeIf { it.isNotBlank() }?.take(240)
        ) > 0
        val ledgerKey = "$accountScope:local:personal_ledger"
        val ledgerType = object : TypeToken<List<PersonalLedgerWriteRequest>>() {}.type
        val gson = Gson()
        val existing = db.cacheDao().read(ledgerKey)?.let { raw ->
            runCatching { gson.fromJson<List<PersonalLedgerWriteRequest>>(raw, ledgerType) }.getOrNull()
        }.orEmpty()
        var changed = false
        val updated = existing.map { entry ->
            if (entry.client_record_id != id) return@map entry
            changed = true
            val metadata = entry.extra.toMutableMap()
            metadata["public_contribution_status"] = status
            if (verificationTier.isNullOrBlank()) metadata.remove("public_contribution_tier")
            else metadata["public_contribution_tier"] = verificationTier
            if (error.isNullOrBlank()) metadata.remove("public_contribution_error")
            else metadata["public_contribution_error"] = error.take(240)
            entry.copy(extra = metadata)
        }
        if (changed) db.cacheDao().write(CachedPayload(ledgerKey, gson.toJson(updated), System.currentTimeMillis()))
        if ((changed || interactionUpdated) && accountScope.startsWith("user:")) schedulePrivateLedgerSync()
    }

    suspend fun completeCommunitySync(
        id: String,
        accountScope: String,
        verificationTier: String
    ) = withContext(Dispatchers.IO) {
        database?.communitySyncOutboxDao()?.deleteById(id, accountScope)
        updatePublicContributionLedgerStatus(id, accountScope, "verified", verificationTier)
    }

    suspend fun markCommunitySyncAttempt(
        id: String,
        accountScope: String,
        error: String
    ) = withContext(Dispatchers.IO) {
        database?.communitySyncOutboxDao()?.markAttemptById(
            id, accountScope, System.currentTimeMillis(), error.take(500)
        )
        updatePublicContributionLedgerStatus(id, accountScope, "pending_verification", error = error)
    }

    suspend fun markCommunitySyncTerminalFailure(
        id: String,
        accountScope: String,
        error: String
    ) = withContext(Dispatchers.IO) {
        database?.communitySyncOutboxDao()?.markTerminalFailureById(
            id, accountScope, System.currentTimeMillis(), error.take(500)
        )
        updatePublicContributionLedgerStatus(id, accountScope, "rejected", error = error)
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
        reward: Double,
        assetBreakdownJson: String? = null,
        publicContributionOptIn: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (projectLocalId.isBlank() || !gas.isFinite() || !reward.isFinite() || gas < 0.0 || reward < 0.0) return@withContext false
        val db = database ?: return@withContext false
        val scope = currentAccountScope()
        db.withTransaction {
            val bill = db.pendingBillDao().findById(id, scope) ?: return@withTransaction false
            val canonicalOfficialId = projectOfficialId?.trim()?.takeIf { it.isNotEmpty() && it.length <= 120 }
            val effectiveNetwork = bill.network.takeIf { it.isNotBlank() && !it.equals("unknown", true) }
                ?: when (bill.chain.trim().lowercase()) {
                    "ethereum", "erc20" -> "ethereum"
                    "tron", "trc20" -> "tron"
                    "bitcoin", "btc" -> "bitcoin"
                    "bsc", "polygon", "arbitrum", "optimism", "base", "avalanche" -> bill.chain.trim().lowercase()
                    else -> "unknown"
                }
            if (publicContributionOptIn && (
                    canonicalOfficialId == null ||
                    !ManualProjectAccountingPolicy.isTransactionHashFormatValid(effectiveNetwork, bill.txHash)
                )) return@withTransaction false
            val effectiveAssetBreakdown = assetBreakdownJson ?: bill.assetBreakdownJson
            if (
                bill.txHash.isNotBlank() && bill.walletAddress.isNotBlank() &&
                WalletActivityNetworkPolicy.isSupported(effectiveNetwork) &&
                db.userInteractionDao().hasTransaction(scope, effectiveNetwork, bill.walletAddress, bill.txHash)
            ) return@withTransaction false
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
                expected_gas_cost_usd = bill.gasCostUsd.takeIf { it.isFinite() && it > 0.0 },
                // No reliable reward estimate exists at confirmation time; keep it unknown.
                expected_reward_usd = null,
                actual_gas_cost_usd = gas,
                actual_reward_usd = reward,
                occurred_at = Instant.ofEpochMilli(bill.timestamp).toString(),
                extra = mapOf(
                    "project_official_id" to (canonicalOfficialId ?: ""),
                    "public_contribution_opt_in" to publicContributionOptIn.toString(),
                    "public_contribution_status" to if (publicContributionOptIn) "pending_verification" else "private",
                    // Demo transactions remain available for local UI testing but are never synced.
                    "is_demo" to isDemoAccountingTransaction(bill.txHash, bill.walletAddress, bill.interactionMethod).toString()
                ) + (effectiveAssetBreakdown?.let { mapOf("asset_breakdown" to it) } ?: emptyMap())
            )
            val updatedLedger = (existingLedger.filterNot { it.client_record_id == bill.id } + ledgerEntry)
            db.cacheDao().write(CachedPayload(ledgerKey, gson.toJson(updatedLedger), System.currentTimeMillis()))
            db.pnlRecordDao().addToProject(projectLocalId, canonicalOfficialId, projectCustomName, bill.walletAddress, gas, reward, true, scope)
            val recordedAt = bill.timestamp
            val updatedProjects = db.userProjectDao().addAmounts(
                projectLocalId, canonicalOfficialId, projectCustomName, gas, reward, recordedAt, true, scope
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
                    timestamp = recordedAt,
                    assetBreakdownJson = effectiveAssetBreakdown,
                    expectedGasCostUsd = bill.gasCostUsd.takeIf { it.isFinite() && it > 0.0 },
                    expectedRewardUsd = null,
                    publicContributionOptIn = publicContributionOptIn,
                    publicContributionStatus = if (publicContributionOptIn) "pending_verification" else "private"
                )
            )
            if (publicContributionOptIn) {
                db.communitySyncOutboxDao().insert(
                    CommunitySyncOutboxEntity(
                        id = bill.id,
                        accountScope = scope,
                        projectOfficialId = canonicalOfficialId!!,
                        gasCostUsd = gas,
                        actualRewardUsd = reward,
                        createdAt = recordedAt,
                        assetBreakdownJson = effectiveAssetBreakdown,
                        network = effectiveNetwork,
                        walletAddress = bill.walletAddress,
                        txHash = bill.txHash,
                        occurredAt = recordedAt
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
    suspend fun recordManualAccountingBatch(
        batchId: String,
        projectLocalId: String,
        projectOfficialId: String?,
        projectName: String,
        batch: ManualAccountingBatch
    ): Boolean = withContext(Dispatchers.IO) {
        if (batchId.isBlank() || batchId.length > 100 || projectLocalId.isBlank() || projectName.isBlank()) return@withContext false
        val db = database ?: return@withContext false
        val scope = currentAccountScope()
        if (scope == "invalid-session") return@withContext false
        val officialId = projectOfficialId?.trim()?.takeIf { it.isNotEmpty() && it.length <= 120 }
        if (batch.entries.any { it.publicContributionOptIn } && officialId == null) return@withContext false
        val timestamp = System.currentTimeMillis()
        val rowIds = batch.entries.map { entry ->
            java.util.UUID.nameUUIDFromBytes("$batchId:${entry.walletId}".toByteArray(Charsets.UTF_8)).toString()
        }
        val saved = db.withTransaction {
            val existingCount = rowIds.count { db.userInteractionDao().findById(it, scope) != null }
            if (existingCount == rowIds.size) return@withTransaction true
            if (existingCount != 0) return@withTransaction false
            batch.entries.forEach { entry ->
                if (entry.txHash.isNotBlank() && db.userInteractionDao().hasTransaction(scope, entry.network, entry.walletAddress, entry.txHash)) {
                    return@withTransaction false
                }
            }
            val ledgerKey = "$scope:local:personal_ledger"
            val ledgerType = object : TypeToken<List<PersonalLedgerWriteRequest>>() {}.type
            val gson = Gson()
            val ledgerRows = db.cacheDao().read(ledgerKey)?.let { raw ->
                runCatching { gson.fromJson<List<PersonalLedgerWriteRequest>>(raw, ledgerType) }.getOrNull()
            }.orEmpty().toMutableList()
            batch.entries.forEachIndexed { index, entry ->
                val reward = entry.reward
                val rewardUsd = entry.actualRewardUsd?.toDouble()
                val complete = rewardUsd != null
                val gas = entry.gasCostUsd.toDouble()
                val rewardValue = rewardUsd ?: 0.0
                val recordedAt = timestamp + index
                val id = rowIds[index]
                val recordedAtIso = Instant.ofEpochMilli(recordedAt).toString()
                val assetRows = buildList {
                    add(LedgerAssetAmount(
                        kind = "gas", amount_native = entry.gasCostUsd.toPlainString(), symbol = "USD",
                        decimals = 2, usd_valuation = gas, valuation_timestamp = recordedAtIso,
                        valuation_source = "manual_usd_entry"
                    ))
                    reward.amount?.let { amount ->
                        add(LedgerAssetAmount(
                            kind = "reward", amount_native = amount.toPlainString(),
                            symbol = reward.unit?.takeIf { it.isNotBlank() } ?: "USD",
                            decimals = if (reward.unit.equals("USD", true)) 2 else null,
                            usd_valuation = rewardUsd,
                            valuation_timestamp = reward.quoteTimestamp?.let { Instant.ofEpochMilli(it).toString() }
                                ?: recordedAtIso.takeIf { rewardUsd != null },
                            valuation_source = reward.quoteSource ?: if (rewardUsd != null) "manual_usd_entry" else null
                        ))
                    }
                }
                val assetJson = LedgerAssetBreakdownCodec.encode(assetRows)
                val metadata = mapOf(
                    "reward_kind" to reward.kind.name,
                    "reward_amount" to (reward.amount?.toPlainString() ?: ""),
                    "reward_unit" to (reward.unit ?: ""),
                    "reward_usd_valuation" to (rewardUsd?.toString() ?: ""),
                    "reward_quote_source" to (reward.quoteSource ?: ""),
                    "reward_quote_timestamp" to (reward.quoteTimestamp?.toString() ?: ""),
                    "usd_profit_complete" to complete.toString(),
                    "public_contribution_opt_in" to entry.publicContributionOptIn.toString(),
                    "public_contribution_status" to if (entry.publicContributionOptIn && complete && officialId != null) "pending_verification" else if (entry.publicContributionOptIn) "not_eligible" else "private",
                    "project_official_id" to (officialId ?: ""),
                    "asset_breakdown" to (assetJson ?: "")
                )
                ledgerRows.removeAll { it.client_record_id == id }
                ledgerRows += PersonalLedgerWriteRequest(
                    client_record_id = id, project_id = projectLocalId, project_name = projectName,
                    chain = entry.network, wallet_address = entry.walletAddress, tx_hash = entry.txHash,
                    interaction_method = "manual_project_entry", expected_gas_cost_usd = null,
                    expected_reward_usd = null, actual_gas_cost_usd = gas,
                    actual_reward_usd = rewardValue, occurred_at = Instant.ofEpochMilli(recordedAt).toString(),
                    extra = metadata
                )
                db.pnlRecordDao().addToProject(projectLocalId, officialId, projectName, entry.walletAddress, gas, rewardValue, complete, scope)
                val updated = db.userProjectDao().addAmounts(projectLocalId, officialId, projectName, gas, rewardValue, recordedAt, complete, scope)
                if (updated == 0) {
                    db.userProjectDao().insert(UserProjectEntity(
                        id = java.util.UUID.randomUUID().toString(), accountScope = scope,
                        projectLocalId = projectLocalId, projectOfficialId = officialId,
                        projectName = projectName, status = "tracking", totalGasCostUsd = gas,
                        totalActualRewardUsd = rewardValue, netProfitUsd = rewardValue - gas,
                        interactionCount = 1, lastInteractionAt = recordedAt, createdAt = recordedAt,
                        updatedAt = recordedAt, usdProfitComplete = complete
                    ))
                }
                db.userInteractionDao().insert(UserInteractionEntity(
                    id = id, accountScope = scope, projectLocalId = projectLocalId,
                    projectOfficialId = officialId, projectName = projectName,
                    txHash = entry.txHash, walletAddress = entry.walletAddress, network = entry.network,
                    gasCostUsd = gas, actualRewardUsd = rewardValue, interactionMethod = "manual_project_entry",
                    timestamp = recordedAt, assetBreakdownJson = assetJson, rewardKind = reward.kind.name,
                    rewardAmount = reward.amount?.toDouble(), rewardUnit = reward.unit,
                    rewardUsdValuation = rewardUsd, rewardQuoteSource = reward.quoteSource,
                    rewardQuoteTimestamp = reward.quoteTimestamp, usdProfitComplete = complete,
                    publicContributionOptIn = entry.publicContributionOptIn,
                    publicContributionStatus = if (entry.publicContributionOptIn && complete && officialId != null) "pending_verification" else if (entry.publicContributionOptIn) "not_eligible" else "private"
                ))
                if (entry.publicContributionOptIn && complete) {
                    db.communitySyncOutboxDao().insert(
                        CommunitySyncOutboxEntity(
                            id = id,
                            accountScope = scope,
                            projectOfficialId = officialId!!,
                            gasCostUsd = gas,
                            actualRewardUsd = rewardValue,
                            createdAt = recordedAt,
                            assetBreakdownJson = assetJson,
                            network = entry.network,
                            walletAddress = entry.walletAddress,
                            txHash = entry.txHash,
                            occurredAt = recordedAt
                        )
                    )
                }
            }
            db.cacheDao().write(CachedPayload(ledgerKey, gson.toJson(ledgerRows), System.currentTimeMillis()))
            true
        }
        if (saved && scope.startsWith("user:")) schedulePrivateLedgerSync()
        if (saved && batch.entries.any { it.publicContributionOptIn && it.actualRewardUsd != null }) {
            appContext?.let { CommunityContributionSyncScheduler.enqueue(it) }
        }
        saved
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
    /** Read from the account that initiated sync, even if another account is now active. */
    suspend fun readForToken(key: String, accessToken: String): String? = withContext(Dispatchers.IO) {
        val scope = accountScopeFromToken(accessToken)
        if (scope == "invalid-session" || scope == "guest") return@withContext null
        runCatching { database?.cacheDao()?.read("${scope.removePrefix("user:")}:$key") }.getOrNull()
    }
    suspend fun writeForToken(key: String, payload: String, accessToken: String): Boolean = withContext(Dispatchers.IO) {
        val scope = accountScopeFromToken(accessToken)
        if (scope == "invalid-session" || scope == "guest") return@withContext false
        val db = database ?: return@withContext false
        db.cacheDao().write(CachedPayload("${scope.removePrefix("user:")}:$key", payload, System.currentTimeMillis()))
        true
    }
    /** Read/write local cache rows for the exact guest or account session that started background work. */
    suspend fun readLocalForToken(key: String, accessToken: String?): String? = withContext(Dispatchers.IO) {
        val scope = accountScopeFromToken(accessToken)
        if (scope == "invalid-session") return@withContext null
        runCatching { database?.cacheDao()?.read("$scope:local:$key") }.getOrNull()
    }
    suspend fun writeLocalForToken(key: String, payload: String, accessToken: String?): Boolean = withContext(Dispatchers.IO) {
        val scope = accountScopeFromToken(accessToken)
        if (scope == "invalid-session") return@withContext false
        val db = database ?: return@withContext false
        db.cacheDao().write(CachedPayload("$scope:local:$key", payload, System.currentTimeMillis()))
        true
    }
    /**
     * Atomically merge canonical cloud rows with local-only rows for the captured account.
     * Room serializes this transaction against confirmPendingBill's local ledger write.
     */
    suspend fun mergePersonalLedgerForToken(
        key: String,
        cloudEntries: List<PersonalLedgerWriteRequest>,
        accessToken: String
    ): Boolean = withContext(Dispatchers.IO) {
        val scope = accountScopeFromToken(accessToken)
        if (scope == "invalid-session") return@withContext false
        val db = database ?: return@withContext false
        db.withTransaction {
            val cacheKey = "$scope:local:$key"
            val type = object : TypeToken<List<PersonalLedgerWriteRequest>>() {}.type
            val localEntries = db.cacheDao().read(cacheKey)?.let { raw ->
                runCatching { Gson().fromJson<List<PersonalLedgerWriteRequest>>(raw, type) }.getOrNull()
            }.orEmpty()
            val cloudIds = cloudEntries.mapTo(mutableSetOf()) { it.client_record_id }
            val merged = (cloudEntries + localEntries.filterNot { it.client_record_id in cloudIds })
                .distinctBy { it.client_record_id }
            db.cacheDao().write(
                CachedPayload(cacheKey, Gson().toJson(merged), System.currentTimeMillis())
            )
        }
        true
    }
    suspend fun exportLocalBackup(): LocalBackupSnapshot = withContext(Dispatchers.IO) {
        val db = database ?: error("本地数据库尚未初始化")
        val scope = currentAccountScope()
        require(scope == "guest" || scope.startsWith("user:")) { "当前账户状态无效，无法备份" }
        val dao = db.localBackupDao()
        val prefixes = backupCachePrefixes(scope)
        LocalBackupSnapshot(
            createdAt = System.currentTimeMillis(), sourceScope = scope,
            cachedPayloads = prefixes.flatMap { dao.cachedPayloadsByPrefix(it) },
            pendingBills = dao.pendingBills(scope), pnlRecords = dao.pnlRecords(scope),
            projectRatings = dao.projectRatings(scope), userProjects = dao.userProjects(scope),
            userInteractions = dao.userInteractions(scope), communityOutbox = dao.communityOutbox(scope),
            communityFavorites = dao.communityFavorites(scope),
            projectWalletAssignments = dao.projectWalletAssignments(scope)
        )
    }

    suspend fun restoreLocalBackup(snapshot: LocalBackupSnapshot) = withContext(Dispatchers.IO) {
        require(snapshot.formatVersion == LocalBackupSnapshot.CURRENT_VERSION) { "备份文件版本不受支持" }
        require(snapshot.sourceScope == "guest" || snapshot.sourceScope.startsWith("user:")) { "备份文件账户范围无效" }
        val db = database ?: error("本地数据库尚未初始化")
        val targetScope = currentAccountScope()
        require(targetScope == "guest" || targetScope.startsWith("user:")) { "当前账户状态无效，无法导入" }
        require(snapshot.pendingBills.all { it.accountScope == snapshot.sourceScope } &&
            snapshot.pnlRecords.all { it.accountScope == snapshot.sourceScope } &&
            snapshot.projectRatings.all { it.accountScope == snapshot.sourceScope } &&
            snapshot.userProjects.all { it.accountScope == snapshot.sourceScope } &&
            snapshot.userInteractions.all { it.accountScope == snapshot.sourceScope } &&
            snapshot.communityOutbox.all { it.accountScope == snapshot.sourceScope } &&
            snapshot.communityFavorites.all { it.accountScope == snapshot.sourceScope } &&
            snapshot.projectWalletAssignments.all { it.accountScope == snapshot.sourceScope }) {
            "备份文件包含不一致的账户数据"
        }
        val sourcePrefixes = backupCachePrefixes(snapshot.sourceScope)
        val targetPrefixes = backupCachePrefixes(targetScope)
        val remapped = snapshot.cachedPayloads.map { row ->
            val index = sourcePrefixes.indexOfFirst { row.cacheKey.startsWith(it) }
            require(index >= 0) { "备份文件包含越权缓存数据" }
            row.copy(cacheKey = targetPrefixes[index] + row.cacheKey.removePrefix(sourcePrefixes[index]))
        }
        val dao = db.localBackupDao()
        db.withTransaction {
            dao.clearPendingBills(targetScope); dao.clearPnlRecords(targetScope)
            dao.clearProjectRatings(targetScope); dao.clearUserProjects(targetScope)
            dao.clearUserInteractions(targetScope); dao.clearCommunityOutbox(targetScope)
            dao.clearCommunityFavorites(targetScope); dao.clearProjectWalletAssignments(targetScope)
            targetPrefixes.forEach { dao.deleteCachedPayloadsByPrefix(it) }
            dao.insertPendingBills(snapshot.pendingBills.map { it.copy(accountScope = targetScope) })
            dao.insertPnlRecords(snapshot.pnlRecords.map { it.copy(accountScope = targetScope) })
            dao.insertProjectRatings(snapshot.projectRatings.map { it.copy(accountScope = targetScope) })
            dao.insertUserProjects(snapshot.userProjects.map { it.copy(accountScope = targetScope) })
            dao.insertUserInteractions(snapshot.userInteractions.map { it.copy(accountScope = targetScope) })
            dao.insertCommunityOutbox(snapshot.communityOutbox.map { it.copy(accountScope = targetScope) })
            dao.insertCommunityFavorites(snapshot.communityFavorites.map { it.copy(accountScope = targetScope) })
            dao.insertProjectWalletAssignments(snapshot.projectWalletAssignments.map { it.copy(accountScope = targetScope) })
            dao.insertCachedPayloads(remapped)
        }
    }

    private fun backupCachePrefixes(scope: String): List<String> =
        if (scope == "guest") listOf("guest:", "signed-out:")
        else listOf("$scope:", "${scope.removePrefix("user:")}:")


    suspend fun readMarketCoins(): List<MarketCoinEntity> = withContext(Dispatchers.IO) {
        database?.marketCoinDao()?.getAll().orEmpty()
    }
    suspend fun replaceMarketCoins(rows: List<MarketCoinEntity>) = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        db.withTransaction {
            val dao = db.marketCoinDao()
            dao.clear()
            dao.upsertAll(rows)
        }
    }
    suspend fun readHotCoins(cacheKey: String): List<HotCoinCacheEntity> = withContext(Dispatchers.IO) {
        database?.hotCoinCacheDao()?.getAll(cacheKey).orEmpty()
    }
    suspend fun replaceHotCoins(cacheKey: String, rows: List<HotCoinCacheEntity>) = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        db.withTransaction {
            val dao = db.hotCoinCacheDao()
            dao.clear(cacheKey)
            dao.upsertAll(rows)
        }
    }
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>> = observeAccountScope().flatMapLatest { scope ->
        database?.searchHistoryDao()?.observeAll(scope) ?: flowOf(emptyList())
    }
    suspend fun saveSearchHistory(query: String, category: String) = withContext(Dispatchers.IO) {
        val value = query.trim().take(160)
        val normalized = normalizeSearchText(value)
        val kind = category.trim().lowercase().take(16)
        val scope = currentAccountScope()
        val db = database ?: return@withContext
        if (scope == "invalid-session" || normalized.isBlank() || kind !in setOf("coin", "dapp")) return@withContext
        db.withTransaction {
            val dao = db.searchHistoryDao()
            dao.upsert(SearchHistoryEntity(scope, kind, normalized, value, System.currentTimeMillis()))
            dao.getAll(scope).drop(10).forEach { dao.delete(scope, it.category, it.normalizedQuery) }
        }
    }
    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        val scope = currentAccountScope()
        if (scope != "invalid-session") database?.searchHistoryDao()?.clear(scope)
    }
    fun observeUnifiedAssets(walletId: String, chain: String): Flow<List<UnifiedAssetEntity>> =
        observeAccountScope().flatMapLatest { scope ->
            database?.unifiedAssetDao()?.observeForWallet(scope, walletId, chain.lowercase()) ?: flowOf(emptyList())
        }
    suspend fun readUnifiedAssets(walletId: String, chain: String): List<UnifiedAssetEntity> = withContext(Dispatchers.IO) {
        val scope = currentAccountScope()
        if (scope == "invalid-session") emptyList() else database?.unifiedAssetDao()?.getForWallet(scope, walletId, chain.lowercase()).orEmpty()
    }
    suspend fun replaceUnifiedAssets(walletId: String, chain: String, rows: List<UnifiedAssetEntity>) = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        val scope = currentAccountScope()
        if (scope == "invalid-session" || walletId.isBlank()) return@withContext
        db.withTransaction {
            val dao = db.unifiedAssetDao()
            dao.clearForWallet(scope, walletId, chain.lowercase())
            dao.upsertAll(rows.map { it.copy(accountScope = scope, walletId = walletId, chain = chain.lowercase()) })
        }
    }
    suspend fun readUnifiedAssetsForToken(walletId: String, chain: String, accessToken: String?): List<UnifiedAssetEntity> = withContext(Dispatchers.IO) {
        val scope = accountScopeFromToken(accessToken)
        if (scope == "invalid-session") emptyList()
        else database?.unifiedAssetDao()?.getForWallet(scope, walletId, chain.lowercase()).orEmpty()
    }
    suspend fun replaceUnifiedAssetsForToken(
        walletId: String, chain: String, rows: List<UnifiedAssetEntity>, accessToken: String?
    ) = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        val scope = accountScopeFromToken(accessToken)
        if (scope == "invalid-session" || walletId.isBlank()) return@withContext
        db.withTransaction {
            val dao = db.unifiedAssetDao()
            dao.clearForWallet(scope, walletId, chain.lowercase())
            dao.upsertAll(rows.map { it.copy(accountScope = scope, walletId = walletId, chain = chain.lowercase()) })
        }
    }
    suspend fun readCacheMetadata(cacheKey: String, source: String): CacheMetadataEntity? = withContext(Dispatchers.IO) {
        database?.cacheMetadataDao()?.find(cacheKey, source)
    }
    suspend fun writeCacheMetadata(metadata: CacheMetadataEntity) = withContext(Dispatchers.IO) {
        database?.cacheMetadataDao()?.upsert(metadata)
    }
    suspend fun readRemoteCache(key: String): String? = withContext(Dispatchers.IO) {
        runCatching { database?.cacheDao()?.read("remote:$key") }.getOrNull()
    }
    suspend fun writeRemoteCache(key: String, payload: String) = withContext(Dispatchers.IO) {
        runCatching {
            database?.cacheDao()?.write(CachedPayload("remote:$key", payload, System.currentTimeMillis()))
        }
        Unit
    }
    // Public community snapshots are shared across guest and signed-in sessions.
    suspend fun readPublic(key: String): String? = withContext(Dispatchers.IO) {
        runCatching { database?.cacheDao()?.read("public:community:$key") }.getOrNull()
    }
    suspend fun writePublic(key: String, payload: String) = withContext(Dispatchers.IO) {
        runCatching { database?.cacheDao()?.write(CachedPayload("public:community:$key", payload, System.currentTimeMillis())) }; Unit
    }
}

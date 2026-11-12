package com.airdrophunter.app.data

import android.content.Context
import android.net.Uri
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.airdrophunter.app.AirdropHunterApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit

object Web3DraftStatus {
    const val ESTIMATING = "estimating"
    const val PENDING_WALLET = "pending_wallet"
    const val WAITING_HASH = "waiting_hash"
    const val WAITING_RECEIPT = "waiting_receipt"
    const val CONFIRMED = "confirmed"
    const val CONFIRMED_FAILED = "confirmed_failed"
    const val WAITING_L1_FEE = "waiting_l1_fee"
    const val TIMED_OUT_NEEDS_SYNC = "timed_out_needs_sync"
    const val CANCELLED = "cancelled"
    const val WALLET_UNAVAILABLE = "wallet_unavailable"
}

@Entity(
    tableName = "web3_transaction_drafts",
    indices = [
        Index(value = ["accountScope", "createdAt"]),
        Index(value = ["accountScope", "status"]),
        Index(value = ["chainId", "txHash"], unique = true),
        Index(value = ["walletConnectRequestId"], unique = true)
    ]
)
data class Web3TransactionDraftEntity(
    @PrimaryKey val id: String,
    val accountScope: String,
    val origin: String,
    val chainId: Long,
    val walletAddress: String?,
    val toAddress: String?,
    val valueWei: String?,
    val dataHex: String?,
    val callbackState: String,
    val walletConnectRequestId: Long?,
    val txHash: String?,
    val status: String,
    val estimatedGas: String?,
    val gasUsed: String?,
    val effectiveGasPrice: String?,
    val l1Fee: String?,
    val totalFeeWei: String?,
    val receiptStatus: Int?,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Dao
interface Web3TransactionDraftDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: Web3TransactionDraftEntity)

    @Update
    suspend fun update(row: Web3TransactionDraftEntity)

    @Query("SELECT * FROM web3_transaction_drafts WHERE id = :id AND accountScope = :scope LIMIT 1")
    suspend fun find(id: String, scope: String): Web3TransactionDraftEntity?

    @Query("SELECT * FROM web3_transaction_drafts WHERE accountScope = :scope AND walletConnectRequestId = :requestId LIMIT 1")
    suspend fun findByWalletRequest(scope: String, requestId: Long): Web3TransactionDraftEntity?

    @Query("SELECT * FROM web3_transaction_drafts WHERE accountScope = :scope AND status IN ('waiting_receipt','waiting_l1_fee','timed_out_needs_sync') AND txHash IS NOT NULL")
    suspend fun receiptCandidates(scope: String): List<Web3TransactionDraftEntity>

    @Query("SELECT * FROM web3_transaction_drafts WHERE accountScope = :scope AND status IN ('pending_wallet','waiting_hash') AND updatedAt <= :cutoff")
    suspend fun staleWalletRequests(scope: String, cutoff: Long): List<Web3TransactionDraftEntity>

    @Query("UPDATE web3_transaction_drafts SET walletConnectRequestId = :requestId, status = 'waiting_hash', updatedAt = :now WHERE id = :id AND accountScope = :scope AND walletConnectRequestId IS NULL")
    suspend fun bindWalletRequest(id: String, scope: String, requestId: Long, now: Long): Int

    @Query("UPDATE web3_transaction_drafts SET txHash = :hash, status = 'waiting_receipt', lastError = NULL, updatedAt = :now WHERE id = :id AND accountScope = :scope AND txHash IS NULL")
    suspend fun attachHash(id: String, scope: String, hash: String, now: Long): Int

    @Query("UPDATE web3_transaction_drafts SET status = :status, lastError = :error, updatedAt = :now WHERE id = :id AND accountScope = :scope")
    suspend fun setStatus(id: String, scope: String, status: String, error: String?, now: Long): Int
}

data class ParsedWalletTransaction(
    val chainId: Long,
    val from: String?,
    val to: String,
    val valueWei: BigInteger,
    val data: String
)

object WalletTransactionLinkParser {
    private val addressPattern = Regex("^0x[a-fA-F0-9]{40}$")
    private val dataPattern = Regex("^0x([a-fA-F0-9]{2})*$")

    fun parse(uri: Uri): ParsedWalletTransaction? {
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in setOf("metamask", "trust", "trustwallet")) return null

        val to = uri.getQueryParameter("to")
            ?: uri.getQueryParameter("address")
            ?: return null
        if (!addressPattern.matches(to)) return null

        val rawChain = uri.getQueryParameter("chainId") ?: return null
        val chainId = parseQuantity(rawChain)?.takeIf {
            it.signum() > 0 && it.bitLength() <= 63
        }?.toLong() ?: return null

        val value = parseQuantity(uri.getQueryParameter("value") ?: "0x0")
            ?: return null
        val data = uri.getQueryParameter("data") ?: "0x"
        if (!dataPattern.matches(data)) return null

        val from = uri.getQueryParameter("from")
            ?.takeIf(addressPattern::matches)

        return ParsedWalletTransaction(chainId, from, to, value, data)
    }

    fun parseQuantity(raw: String): BigInteger? = runCatching {
        when {
            raw.startsWith("0x", true) ->
                BigInteger(raw.substring(2).ifBlank { "0" }, 16)
            raw.all(Char::isDigit) -> BigInteger(raw)
            else -> return null
        }.takeIf { it.signum() >= 0 }
    }.getOrNull()
}

object Web3RpcRegistry {
    private val endpoints = mapOf(
        1L to "https://ethereum-rpc.publicnode.com",
        10L to "https://mainnet.optimism.io",
        56L to "https://bsc-rpc.publicnode.com",
        137L to "https://polygon-bor-rpc.publicnode.com",
        8453L to "https://mainnet.base.org",
        42161L to "https://arb1.arbitrum.io/rpc"
    )

    fun endpoint(chainId: Long): String? = endpoints[chainId]
    fun isOptimisticRollup(chainId: Long): Boolean = chainId in setOf(10L, 8453L)
    fun requiresL1Fee(chainId: Long): Boolean = chainId in setOf(10L, 8453L, 42161L)
}

class Web3RpcClient {
    suspend fun request(chainId: Long, method: String, params: JSONArray): JSONObject =
        withContext(Dispatchers.IO) {
            val endpoint = Web3RpcRegistry.endpoint(chainId)
                ?: throw IllegalArgumentException("暂不支持该链的 RPC")
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "AirdropHunter-Android")
            }
            try {
                val payload = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", method)
                    .put("params", params)
                    .toString()
                    .toByteArray(Charsets.UTF_8)
                connection.outputStream.use { it.write(payload) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) error("RPC HTTP $code")
                val json = JSONObject(body)
                if (json.has("error")) error(json.optJSONObject("error")?.optString("message") ?: "RPC error")
                json
            } finally {
                connection.disconnect()
            }
        }

    suspend fun estimateGas(tx: ParsedWalletTransaction): BigInteger {
        val request = JSONObject()
            .put("to", tx.to)
            .put("value", "0x" + tx.valueWei.toString(16))
            .put("data", tx.data)
            .apply { tx.from?.let { put("from", it) } }
        val result = request(tx.chainId, "eth_estimateGas", JSONArray().put(request))
            .getString("result")
        return parseRpcQuantity(result) ?: error("Invalid eth_estimateGas result")
    }

    suspend fun getReceipt(chainId: Long, txHash: String): JSONObject? {
        val result = request(
            chainId,
            "eth_getTransactionReceipt",
            JSONArray().put(txHash)
        ).opt("result")
        return result as? JSONObject
    }

    companion object {
        fun parseRpcQuantity(raw: String?): BigInteger? {
            if (raw.isNullOrBlank() || !raw.startsWith("0x", true)) return null
            return runCatching {
                BigInteger(raw.substring(2).ifBlank { "0" }, 16)
            }.getOrNull()
        }
    }
}

class Web3TransactionRepository(
    private val dao: Web3TransactionDraftDao,
    private val scopeProvider: () -> String,
    private val rpc: Web3RpcClient = Web3RpcClient()
) {
    suspend fun createDappDraft(
        chainId: Long,
        walletAddress: String,
        origin: String,
        transaction: JSONObject
    ): Web3TransactionDraftEntity {
        require(chainId > 0)
        require(walletAddress.matches(Regex("^0x[a-fA-F0-9]{40}$")))
        val to = transaction.optString("to").takeIf {
            it.matches(Regex("^0x[a-fA-F0-9]{40}$"))
        }
        val value = transaction.optString("value").takeIf { it.isNotBlank() }
            ?.let(WalletTransactionLinkParser::parseQuantity)?.toString()
        val data = transaction.optString("data", "0x")
            .takeIf { it.matches(Regex("^0x([a-fA-F0-9]{2})*$")) }
        val now = System.currentTimeMillis()
        val row = Web3TransactionDraftEntity(
            id = UUID.randomUUID().toString(),
            accountScope = scopeProvider(),
            origin = origin.take(512),
            chainId = chainId,
            walletAddress = walletAddress,
            toAddress = to,
            valueWei = value,
            dataHex = data,
            callbackState = newCallbackState(),
            walletConnectRequestId = null,
            txHash = null,
            status = Web3DraftStatus.PENDING_WALLET,
            estimatedGas = null,
            gasUsed = null,
            effectiveGasPrice = null,
            l1Fee = null,
            totalFeeWei = null,
            receiptStatus = null,
            lastError = null,
            createdAt = now,
            updatedAt = now
        )
        dao.insert(row)
        if (to != null && data != null && value != null) {
            runCatching {
                val parsed = ParsedWalletTransaction(
                    chainId, walletAddress, to, BigInteger(value), data
                )
                val estimate = rpc.estimateGas(parsed)
                val current = dao.find(row.id, row.accountScope) ?: return@runCatching
                dao.update(current.copy(
                    estimatedGas = estimate.toString(),
                    updatedAt = System.currentTimeMillis()
                ))
            }.onFailure {
                val current = dao.find(row.id, row.accountScope)
                if (current != null) dao.update(current.copy(
                    lastError = "Gas 预估不可用：${it.message.orEmpty().take(180)}",
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }
        return dao.find(row.id, row.accountScope) ?: row
    }

    suspend fun createExternalLinkDraft(
        parsed: ParsedWalletTransaction,
        origin: String,
        walletAddress: String?
    ): Web3TransactionDraftEntity {
        val now = System.currentTimeMillis()
        val row = Web3TransactionDraftEntity(
            id = UUID.randomUUID().toString(),
            accountScope = scopeProvider(),
            origin = origin.take(512),
            chainId = parsed.chainId,
            walletAddress = walletAddress ?: parsed.from,
            toAddress = parsed.to,
            valueWei = parsed.valueWei.toString(),
            dataHex = parsed.data,
            callbackState = newCallbackState(),
            walletConnectRequestId = null,
            txHash = null,
            status = Web3DraftStatus.ESTIMATING,
            estimatedGas = null,
            gasUsed = null,
            effectiveGasPrice = null,
            l1Fee = null,
            totalFeeWei = null,
            receiptStatus = null,
            lastError = null,
            createdAt = now,
            updatedAt = now
        )
        dao.insert(row)
        return try {
            val estimate = rpc.estimateGas(parsed)
            val updated = row.copy(
                estimatedGas = estimate.toString(),
                status = Web3DraftStatus.PENDING_WALLET,
                updatedAt = System.currentTimeMillis()
            )
            dao.update(updated)
            updated
        } catch (error: Exception) {
            val updated = row.copy(
                status = Web3DraftStatus.PENDING_WALLET,
                lastError = "Gas 预估失败：${error.message.orEmpty().take(180)}",
                updatedAt = System.currentTimeMillis()
            )
            dao.update(updated)
            updated
        }
    }

    suspend fun bindWalletConnectRequest(draftId: String, requestId: Long): Boolean {
        return dao.bindWalletRequest(
            draftId, scopeProvider(), requestId, System.currentTimeMillis()
        ) == 1
    }

    suspend fun attachWalletConnectHash(
        draftId: String,
        requestId: Long,
        chainId: Long,
        hash: String
    ): Boolean {
        if (!isValidTxHash(hash)) return false
        val scope = scopeProvider()
        val draft = dao.find(draftId, scope) ?: return false
        if (draft.walletConnectRequestId != requestId || draft.chainId != chainId) return false
        if (draft.txHash != null) return draft.txHash.equals(hash, true)
        return dao.attachHash(draftId, scope, hash.lowercase(), System.currentTimeMillis()) == 1
    }

    suspend fun attachCallbackHash(
        draftId: String,
        callbackState: String,
        chainId: Long,
        hash: String
    ): Boolean {
        if (!isValidTxHash(hash)) return false
        val scope = scopeProvider()
        val draft = dao.find(draftId, scope) ?: return false
        if (!constantTimeEquals(draft.callbackState, callbackState)) return false
        if (draft.chainId != chainId) return false
        if (draft.txHash != null) return draft.txHash.equals(hash, true)
        return dao.attachHash(draftId, scope, hash.lowercase(), System.currentTimeMillis()) == 1
    }

    suspend fun markWalletRejected(draftId: String) {
        dao.setStatus(
            draftId, scopeProvider(), Web3DraftStatus.CANCELLED,
            "钱包明确拒绝或取消了请求", System.currentTimeMillis()
        )
    }

    suspend fun markWalletTimedOut(draftId: String) {
        dao.setStatus(
            draftId, scopeProvider(), Web3DraftStatus.TIMED_OUT_NEEDS_SYNC,
            "钱包未返回交易 Hash；需要同步链上记录确认", System.currentTimeMillis()
        )
    }

    suspend fun findById(id: String): Web3TransactionDraftEntity? =
        dao.find(id, scopeProvider())

    suspend fun getReceipt(draft: Web3TransactionDraftEntity): JSONObject? {
        val hash = draft.txHash ?: return null
        return rpc.getReceipt(draft.chainId, hash)
    }

    suspend fun saveReceipt(
        draft: Web3TransactionDraftEntity,
        receipt: JSONObject
    ): Boolean {
        val gasUsed = Web3RpcClient.parseRpcQuantity(receipt.optString("gasUsed")) ?: return false
        val effectiveGasPrice = Web3RpcClient.parseRpcQuantity(
            receipt.optString("effectiveGasPrice").takeIf { it.isNotBlank() }
                ?: receipt.optString("gasPrice")
        ) ?: return false
        val receiptStatus = Web3RpcClient.parseRpcQuantity(receipt.optString("status"))
            ?.takeIf { it == BigInteger.ZERO || it == BigInteger.ONE }
            ?.toInt() ?: return false

        val l1Fee = receipt.optString("l1Fee").takeIf { it.isNotBlank() }
            ?.let(Web3RpcClient::parseRpcQuantity)

        if (Web3RpcRegistry.requiresL1Fee(draft.chainId) && l1Fee == null) {
            dao.update(draft.copy(
                gasUsed = gasUsed.toString(),
                effectiveGasPrice = effectiveGasPrice.toString(),
                receiptStatus = receiptStatus,
                status = Web3DraftStatus.WAITING_L1_FEE,
                lastError = "已取得交易回执，但 RPC 未提供该 L2 的 L1 费用字段；总费用暂不确认",
                updatedAt = System.currentTimeMillis()
            ))
            return true
        }

        val l2Fee = gasUsed.multiply(effectiveGasPrice)
        val totalFee = l2Fee.add(l1Fee ?: BigInteger.ZERO)
        dao.update(draft.copy(
            gasUsed = gasUsed.toString(),
            effectiveGasPrice = effectiveGasPrice.toString(),
            l1Fee = (l1Fee ?: BigInteger.ZERO).toString(),
            totalFeeWei = totalFee.toString(),
            receiptStatus = receiptStatus,
            status = if (receiptStatus == 1) Web3DraftStatus.CONFIRMED
                else Web3DraftStatus.CONFIRMED_FAILED,
            lastError = null,
            updatedAt = System.currentTimeMillis()
        ))
        return true
    }

    suspend fun markReceiptPending(draft: Web3TransactionDraftEntity, message: String?) {
        dao.setStatus(
            draft.id, draft.accountScope, Web3DraftStatus.WAITING_RECEIPT,
            message?.take(180), System.currentTimeMillis()
        )
    }

    suspend fun markReceiptNeedsManualSync(draftId: String) {
        dao.setStatus(
            draftId, scopeProvider(), Web3DraftStatus.WAITING_L1_FEE,
            "回执或 L1 费用自动核验达到重试上限，请手动同步", System.currentTimeMillis()
        )
    }

    suspend fun markOldWalletRequestsForSync() {
        val cutoff = System.currentTimeMillis() - 30_000L
        dao.staleWalletRequests(scopeProvider(), cutoff).forEach {
            markWalletTimedOut(it.id)
        }
    }

    suspend fun scheduleReceipt(draftId: String) {
        // Work scheduling is intentionally performed by the caller with an application Context.
    }

    companion object {
        private val secureRandom = SecureRandom()
        fun isValidTxHash(hash: String): Boolean =
            Regex("^0x[a-fA-F0-9]{64}$").matches(hash)

        private fun newCallbackState(): String {
            val bytes = ByteArray(24)
            secureRandom.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        private fun constantTimeEquals(left: String, right: String): Boolean {
            if (left.length != right.length) return false
            var difference = 0
            for (index in left.indices) {
                difference = difference or (left[index].code xor right[index].code)
            }
            return difference == 0
        }
    }
}

class Web3ReceiptWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val draftId = inputData.getString(KEY_DRAFT_ID) ?: return Result.failure()
        val app = applicationContext as? AirdropHunterApplication ?: return Result.failure()
        val repository = app.appContainer.web3TransactionRepository
        val draft = repository.findById(draftId) ?: return Result.success()
        if (draft.txHash.isNullOrBlank()) return Result.success()

        return try {
            val receipt = repository.getReceipt(draft)
            if (receipt == null) {
                return if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
                else {
                    repository.markReceiptNeedsManualSync(draftId)
                    Result.success()
                }
            }
            repository.saveReceipt(draft, receipt)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
            else {
                repository.markReceiptNeedsManualSync(draftId)
                Result.success()
            }
        }
    }

    companion object {
        const val KEY_DRAFT_ID = "web3_draft_id"
        const val MAX_ATTEMPTS = 12
    }
}

object Web3ReceiptScheduler {
    fun enqueue(context: Context, draftId: String) {
        val request = OneTimeWorkRequestBuilder<Web3ReceiptWorker>()
            .setInputData(androidx.work.workDataOf(Web3ReceiptWorker.KEY_DRAFT_ID to draftId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "web3_receipt_$draftId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

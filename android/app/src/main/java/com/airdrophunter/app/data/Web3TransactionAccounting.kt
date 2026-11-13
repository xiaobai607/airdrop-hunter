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
import kotlinx.coroutines.flow.Flow
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
    const val WAITING_OPERATOR_FEE = "waiting_operator_fee"
    const val RECEIPT_NEEDS_SYNC = "receipt_needs_sync"
    const val TIMED_OUT_NEEDS_SYNC = "timed_out_needs_sync"
    const val CANCELLED = "cancelled"
    const val ACCOUNTED = "accounted"
    const val WALLET_UNAVAILABLE = "wallet_unavailable"

    fun canAttachHash(status: String): Boolean =
        status in setOf(PENDING_WALLET, WAITING_HASH, TIMED_OUT_NEEDS_SYNC)

    fun canCreateManualReviewBill(status: String): Boolean =
        status in setOf(RECEIPT_NEEDS_SYNC, WAITING_OPERATOR_FEE, WAITING_L1_FEE)

    fun canConfirmAccounting(status: String): Boolean =
        status in setOf(CONFIRMED, CONFIRMED_FAILED) || canCreateManualReviewBill(status)

    fun isReceiptWorkComplete(status: String): Boolean = status == ACCOUNTED

    fun canUpdateReceiptStatus(status: String): Boolean =
        status in setOf(WAITING_RECEIPT, WAITING_L1_FEE, WAITING_OPERATOR_FEE, RECEIPT_NEEDS_SYNC, TIMED_OUT_NEEDS_SYNC)

    fun shouldRefreshPendingAccountingBill(existingAssetBreakdownJson: String?): Boolean =
        existingAssetBreakdownJson.isNullOrBlank()
}

@Entity(
    tableName = "web3_transaction_drafts",
    indices = [
        Index(value = ["accountScope", "createdAt"]),
        Index(value = ["accountScope", "status"]),
        Index(value = ["accountScope", "chainId", "txHash"], unique = true),
        Index(value = ["accountScope", "walletConnectRequestId"]),
        Index(value = ["accountScope", "walletConnectRequestId", "walletConnectSessionTopic"])
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
    val walletConnectSessionTopic: String? = null,
    val txHash: String?,
    val status: String,
    val estimatedGas: String?,
    val gasUsed: String?,
    val effectiveGasPrice: String?,
    val l1Fee: String?,
    val operatorFeeWei: String? = null,
    val totalFeeWei: String?,
    val receiptStatus: Int?,
    val lastError: String?,
    val nativeSymbol: String? = null,
    val nativePriceUsd: Double? = null,
    val nativePriceTimestamp: Long? = null,
    val actualGasUsd: Double? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Dao
interface Web3TransactionDraftDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: Web3TransactionDraftEntity)

    @Update
    suspend fun update(row: Web3TransactionDraftEntity)

    @Query("""
        UPDATE web3_transaction_drafts SET
            gasUsed = :gasUsed,
            effectiveGasPrice = :effectiveGasPrice,
            l1Fee = :l1Fee,
            operatorFeeWei = :operatorFeeWei,
            totalFeeWei = :totalFeeWei,
            nativeSymbol = :nativeSymbol,
            nativePriceUsd = :nativePriceUsd,
            nativePriceTimestamp = :nativePriceTimestamp,
            actualGasUsd = :actualGasUsd,
            receiptStatus = :receiptStatus,
            status = :status,
            lastError = :lastError,
            updatedAt = :updatedAt
        WHERE id = :id AND accountScope = :scope
          AND status IN ('waiting_receipt','waiting_l1_fee','waiting_operator_fee','receipt_needs_sync','timed_out_needs_sync')
    """)
    suspend fun updateReceiptIfPending(
        id: String,
        scope: String,
        gasUsed: String?,
        effectiveGasPrice: String?,
        l1Fee: String?,
        operatorFeeWei: String?,
        totalFeeWei: String?,
        nativeSymbol: String?,
        nativePriceUsd: Double?,
        nativePriceTimestamp: Long?,
        actualGasUsd: Double?,
        receiptStatus: Int?,
        status: String,
        lastError: String?,
        updatedAt: Long
    ): Int

    @Query("SELECT * FROM web3_transaction_drafts WHERE accountScope = :scope ORDER BY createdAt DESC")
    fun observeAll(scope: String): kotlinx.coroutines.flow.Flow<List<Web3TransactionDraftEntity>>

    @Query("SELECT * FROM web3_transaction_drafts WHERE id = :id AND accountScope = :scope LIMIT 1")
    suspend fun find(id: String, scope: String): Web3TransactionDraftEntity?

    @Query("SELECT * FROM web3_transaction_drafts WHERE accountScope = :scope AND walletConnectRequestId = :requestId AND walletConnectSessionTopic = :sessionTopic")
    suspend fun findByWalletRequest(scope: String, requestId: Long, sessionTopic: String): List<Web3TransactionDraftEntity>

    @Query("SELECT * FROM web3_transaction_drafts WHERE accountScope = :scope AND status IN ('waiting_receipt','waiting_l1_fee','waiting_operator_fee','receipt_needs_sync','timed_out_needs_sync') AND txHash IS NOT NULL")
    suspend fun receiptCandidates(scope: String): List<Web3TransactionDraftEntity>

    @Query("SELECT id FROM web3_transaction_drafts WHERE accountScope = :scope AND status IN ('waiting_receipt','waiting_l1_fee','waiting_operator_fee') AND txHash IS NOT NULL")
    suspend fun pendingReceiptDraftIds(scope: String): List<String>

    @Query("SELECT * FROM web3_transaction_drafts WHERE accountScope = :scope AND status IN ('pending_wallet','waiting_hash') AND updatedAt <= :cutoff")
    suspend fun staleWalletRequests(scope: String, cutoff: Long): List<Web3TransactionDraftEntity>

    @Query("""
        UPDATE web3_transaction_drafts
        SET walletConnectRequestId = :requestId,
            walletConnectSessionTopic = :sessionTopic,
            status = CASE WHEN status = 'timed_out_needs_sync' THEN status ELSE 'waiting_hash' END,
            updatedAt = :now
        WHERE id = :id AND accountScope = :scope AND walletConnectRequestId IS NULL
          AND status IN ('pending_wallet','waiting_hash','timed_out_needs_sync')
    """)
    suspend fun bindWalletRequest(id: String, scope: String, requestId: Long, sessionTopic: String, now: Long): Int

    @Query("UPDATE web3_transaction_drafts SET txHash = :hash, status = 'waiting_receipt', lastError = NULL, updatedAt = :now WHERE id = :id AND accountScope = :scope AND txHash IS NULL AND status IN ('pending_wallet','waiting_hash','timed_out_needs_sync')")
    suspend fun attachHash(id: String, scope: String, hash: String, now: Long): Int

    @Query("""
        UPDATE web3_transaction_drafts
        SET status = :status, lastError = :error, updatedAt = :now
        WHERE id = :id AND accountScope = :scope
          AND status IN ('waiting_receipt','waiting_l1_fee','waiting_operator_fee','receipt_needs_sync','timed_out_needs_sync')
    """)
    suspend fun setReceiptStatusIfPending(id: String, scope: String, status: String, error: String?, now: Long): Int

    @Query("UPDATE web3_transaction_drafts SET status = :status, lastError = :error, updatedAt = :now WHERE id = :id AND accountScope = :scope AND status IN ('pending_wallet','waiting_hash','timed_out_needs_sync')")
    suspend fun setWalletStatus(id: String, scope: String, status: String, error: String?, now: Long): Int

    @Query("UPDATE web3_transaction_drafts SET status = 'accounted', lastError = NULL, updatedAt = :now WHERE id = :id AND accountScope = :scope AND status IN ('confirmed','confirmed_failed','waiting_l1_fee','waiting_operator_fee','receipt_needs_sync','timed_out_needs_sync')")
    suspend fun markAccounted(id: String, scope: String, now: Long): Int
}

data class ParsedWalletTransaction(
    val chainId: Long,
    val from: String?,
    val to: String?,
    val valueWei: BigInteger,
    val data: String
)

/**
 * Strictly binds a manually supplied transaction hash to the original wallet request.
 * Missing RPC fields are not treated as successful matches when the request had a value.
 */
object Web3ManualTransactionVerifier {
    fun matches(
        expectedHash: String,
        actualHash: String?,
        expectedFrom: String,
        actualFrom: String?,
        expectedTo: String?,
        actualTo: String?,
        expectedValueWei: BigInteger?,
        actualValueWei: BigInteger?,
        expectedData: String?,
        actualData: String?
    ): Boolean {
        if (actualHash.isNullOrBlank() || !actualHash.equals(expectedHash, ignoreCase = true)) return false
        if (actualFrom.isNullOrBlank() || !actualFrom.equals(expectedFrom, ignoreCase = true)) return false
        if (!expectedTo.isNullOrBlank() &&
            (actualTo.isNullOrBlank() || !actualTo.equals(expectedTo, ignoreCase = true))
        ) return false
        if (expectedValueWei != null && actualValueWei != expectedValueWei) return false
        if (!expectedData.isNullOrBlank() &&
            (actualData.isNullOrBlank() || !actualData.equals(expectedData, ignoreCase = true))
        ) return false
        return true
    }
}

object WalletTransactionLinkParser {
    private val addressPattern = Regex("^0x[a-fA-F0-9]{40}$")
    private val dataPattern = Regex("^0x([a-fA-F0-9]{2})*$")
    private val maxUint256 = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)
    const val MAX_CALLDATA_LENGTH = 262_146

    fun parse(uri: Uri): ParsedWalletTransaction? {
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in setOf("metamask", "trust", "trustwallet", "ethereum")) return null

        var pathRecipient: String? = null
        var pathChainId: String? = null
        when (scheme) {
            "ethereum" -> {
                // EIP-681 native transfer: ethereum:<address>@<chainId>?value=<wei>
                val authority = uri.toString().substringAfter(":", "")
                    .substringBefore("?").substringBefore("#").trimStart('/')
                // ERC-20/function routes require ABI decoding; do not misread their
                // address/uint256 parameters as a native transfer.
                if (authority.contains("/")) return null
                pathRecipient = authority.substringBefore("@").takeIf { it.contains("0x") }
                pathChainId = authority.substringAfter("@", "").takeIf { it.isNotBlank() }
            }
            "metamask" -> {
                // MetaMask mobile send route: metamask://send/<recipient>@<chainId>?value=<wei>
                val route = (listOf(uri.host.orEmpty()) + uri.pathSegments).filter { it.isNotBlank() }
                val sendRoute = route.dropWhile { it != "send" }.drop(1)
                // Only native send routes are parsed here. Token/function routes are
                // still intercepted and opened, but are not assigned a false estimate.
                if (sendRoute.size > 1) return null
                val sendTarget = sendRoute.firstOrNull()
                if (sendTarget != null) {
                    pathRecipient = sendTarget.substringBefore("@")
                    pathChainId = sendTarget.substringAfter("@", "").takeIf { it.isNotBlank() }
                }
            }
        }

        val queryAddress = if (scheme == "trust" || scheme == "trustwallet") null
            else queryParameter(uri, "address")
        val to = queryParameter(uri, "to")
            ?: queryAddress
            ?: pathRecipient
            ?: return null
        if (!addressPattern.matches(to)) return null

        val rawChain = queryParameter(uri, "chainId") ?: pathChainId ?: return null
        val chainId = parseQuantity(rawChain)?.takeIf {
            it.signum() > 0 && it.bitLength() <= 63
        }?.toLong() ?: return null

        val value = parseQuantity(queryParameter(uri, "value") ?: "0x0")
            ?: return null
        val data = queryParameter(uri, "data") ?: "0x"
        if (data.length > MAX_CALLDATA_LENGTH || !dataPattern.matches(data)) return null

        val from = queryParameter(uri, "from")
            ?.takeIf(addressPattern::matches)

        return ParsedWalletTransaction(chainId, from, to, value, data)
    }

    private fun queryParameter(uri: Uri, name: String): String? {
        val query = uri.toString().substringAfter("?", "").substringBefore("#")
        if (query.isBlank()) return null
        query.split("&").forEach { part ->
            val pieces = part.split("=", limit = 2)
            if (Uri.decode(pieces[0]) == name) {
                return Uri.decode(pieces.getOrElse(1) { "" })
            }
        }
        return null
    }

    fun parseQuantity(raw: String): BigInteger? {
        if (raw.length > 100) return null
        return runCatching {
            when {
                raw.startsWith("0x", true) ->
                    BigInteger(raw.substring(2).ifBlank { "0" }, 16)
                raw.matches(Regex("^[0-9]+$")) -> BigInteger(raw)
                else -> java.math.BigDecimal(raw).toBigIntegerExact()
            }.takeIf { it.signum() >= 0 && it <= maxUint256 }
        }.getOrNull()
    }
}

data class Web3FeeBreakdown(
    val l1FeeWei: BigInteger,
    val l2FeeWei: BigInteger,
    val operatorFeeWei: BigInteger,
    val totalFeeWei: BigInteger
)

object Web3ReceiptFeeCalculator {
    fun calculate(
        chainId: Long,
        gasUsed: BigInteger,
        effectiveGasPrice: BigInteger,
        l1FeeWei: BigInteger?,
        operatorFeeWei: BigInteger?
    ): Web3FeeBreakdown? {
        val receiptGasFee = gasUsed.multiply(effectiveGasPrice)
        return if (chainId == 42161L) {
            // Arbitrum receipt gasUsed already includes the L1 component.
            val l1 = l1FeeWei ?: return null
            val l2 = receiptGasFee.subtract(l1).takeIf { it.signum() >= 0 } ?: return null
            Web3FeeBreakdown(l1, l2, BigInteger.ZERO, receiptGasFee)
        } else {
            val l1 = l1FeeWei ?: BigInteger.ZERO
            val operator = if (Web3RpcRegistry.requiresOperatorFee(chainId)) {
                operatorFeeWei ?: return null
            } else BigInteger.ZERO
            Web3FeeBreakdown(l1, receiptGasFee, operator, receiptGasFee.add(l1).add(operator))
        }
    }
}

/** Rejects a receipt that does not belong to the exact transaction draft being reconciled. */
object Web3ReceiptIdentityVerifier {
    fun matches(expectedHash: String?, receiptHash: String?): Boolean {
        val hashPattern = Regex("^0x[a-fA-F0-9]{64}$")
        if (expectedHash.isNullOrBlank() || receiptHash.isNullOrBlank()) return false
        if (!hashPattern.matches(expectedHash) || !hashPattern.matches(receiptHash)) return false
        return expectedHash.equals(receiptHash, ignoreCase = true)
    }
}

object Web3WalletResponseIdentityVerifier {
    fun matches(method: String, sessionTopic: String?, chainId: Long, txHash: String?): Boolean =
        method.equals("eth_sendTransaction", ignoreCase = true) &&
            !sessionTopic.isNullOrBlank() && sessionTopic.length <= 256 &&
            chainId > 0 && Web3TransactionRepository.isValidTxHash(txHash.orEmpty())
}

object Web3ReceiptFeeParser {
    fun isOpStackDepositTransaction(typeRaw: String?): Boolean =
        Web3RpcClient.parseRpcQuantity(typeRaw) == BigInteger.valueOf(126L)

    /**
     * Arbitrum reports gasUsedForL1 in child-chain gas units, so its charged
     * parent-chain component is gasUsedForL1 * effectiveGasPrice.
     * OP Stack chains expose the transaction-scoped l1Fee in wei.
     */
    fun l1FeeWei(
        chainId: Long,
        l1FeeRaw: String?,
        gasUsedForL1Raw: String?,
        effectiveGasPrice: BigInteger,
        transactionTypeRaw: String? = null
    ): BigInteger? {
        if (chainId in setOf(10L, 8453L) && isOpStackDepositTransaction(transactionTypeRaw)) {
            // Deposits are derived from L1 and are exempt from the L2 L1-data fee.
            return BigInteger.ZERO
        }
        return when (chainId) {
            // Arbitrum's documented receipt extension is gasUsedForL1 (in child-chain
            // gas units). Do not guess from a provider-specific, ambiguously named l1Fee.
            42161L -> Web3RpcClient.parseRpcQuantity(gasUsedForL1Raw)
                ?.multiply(effectiveGasPrice)
            10L, 8453L -> Web3RpcClient.parseRpcQuantity(l1FeeRaw)
            else -> BigInteger.ZERO
        }
    }
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
    fun requiresOperatorFee(chainId: Long): Boolean = chainId in setOf(10L, 8453L)
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
            .put("value", "0x" + tx.valueWei.toString(16))
            .put("data", tx.data)
            .apply {
                tx.to?.let { put("to", it) }
                tx.from?.let { put("from", it) }
            }
        val result = request(tx.chainId, "eth_estimateGas", JSONArray().put(request))
            .getString("result")
        return parseRpcQuantity(result) ?: error("Invalid eth_estimateGas result")
    }

    suspend fun getOperatorFee(
        chainId: Long,
        gasUsed: BigInteger,
        blockNumber: String?
    ): BigInteger? {
        if (!Web3RpcRegistry.requiresOperatorFee(chainId)) return BigInteger.ZERO
        val block = blockNumber?.takeIf { it.startsWith("0x") } ?: return null
        val calldata = "0x275aedd2" + gasUsed.toString(16).padStart(64, '0')
        val params = JSONArray()
            .put(JSONObject()
                .put("to", "0x420000000000000000000000000000000000000F")
                .put("data", calldata))
            .put(block)
        val result = request(chainId, "eth_call", params).optString("result")
        if (!result.matches(Regex("^0x[a-fA-F0-9]{64}$"))) return null
        return parseRpcQuantity(result)
    }

    suspend fun getTransaction(chainId: Long, txHash: String): JSONObject? {
        val result = request(
            chainId,
            "eth_getTransactionByHash",
            JSONArray().put(txHash)
        ).opt("result")
        return result as? JSONObject
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
            if (raw.isNullOrBlank() || raw.length <= 2 || !raw.startsWith("0x", true)) return null
            return runCatching {
                BigInteger(raw.substring(2).ifBlank { "0" }, 16)
            }.getOrNull()
        }
    }
}

class Web3TransactionRepository(
    private val dao: Web3TransactionDraftDao,
    private val scopeProvider: () -> String,
    private val rpc: Web3RpcClient = Web3RpcClient(),
    private val nativePriceProvider: suspend (String) -> OkxTickerDetail? = { null }
) {
    suspend fun createDappDraft(
        chainId: Long,
        walletAddress: String,
        origin: String,
        transaction: JSONObject
    ): Web3TransactionDraftEntity {
        require(chainId > 0)
        require(walletAddress.matches(Regex("^0x[a-fA-F0-9]{40}$")))
        val rawTo = transaction.optString("to").takeUnless { it.isBlank() || it == "null" }
        require(rawTo == null || rawTo.matches(Regex("^0x[a-fA-F0-9]{40}$"))) {
            "交易目标地址格式无效"
        }
        val to = rawTo
        val rawValue = transaction.optString("value").ifBlank { "0" }
        val value = WalletTransactionLinkParser.parseQuantity(rawValue)?.toString()
            ?: throw IllegalArgumentException("交易 value 参数无效")
        val rawData = transaction.optString("data")
            .ifBlank { transaction.optString("input") }
            .ifBlank { "0x" }
        require(rawData.length <= WalletTransactionLinkParser.MAX_CALLDATA_LENGTH &&
            rawData.matches(Regex("^0x([a-fA-F0-9]{2})*$"))) {
            "交易 data 参数无效或超过安全长度"
        }
        val data = rawData
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
        if (data != null && value != null) {
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
            val estimate = rpc.estimateGas(
                parsed.copy(from = parsed.from ?: walletAddress)
            )
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

    suspend fun bindWalletConnectRequest(draftId: String, requestId: Long, sessionTopic: String): Boolean {
        if (sessionTopic.isBlank() || sessionTopic.length > 256) return false
        return dao.bindWalletRequest(
            draftId, scopeProvider(), requestId, sessionTopic, System.currentTimeMillis()
        ) == 1
    }

    /**
     * Reconciles a WalletConnect success after BrowserActivity's in-memory JS bridge
     * has been lost. Only one exact request/topic/chain match may receive the hash.
     */
    suspend fun attachWalletConnectResponse(
        requestId: Long,
        method: String,
        sessionTopic: String,
        chainId: Long,
        hash: String
    ): String? {
        if (!Web3WalletResponseIdentityVerifier.matches(method, sessionTopic, chainId, hash)) return null
        val scope = scopeProvider()
        val candidates = dao.findByWalletRequest(scope, requestId, sessionTopic)
            .filter { it.chainId == chainId }
        if (candidates.size != 1) return null
        val draft = candidates.single()
        if (draft.txHash != null) {
            return draft.id.takeIf {
                draft.txHash.equals(hash, true) && draft.status != Web3DraftStatus.ACCOUNTED
            }
        }
        if (!Web3DraftStatus.canAttachHash(draft.status)) return null
        return draft.id.takeIf {
            dao.attachHash(it, scope, hash.lowercase(), System.currentTimeMillis()) == 1
        }
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
        if (!Web3DraftStatus.canAttachHash(draft.status)) return false
        return dao.attachHash(draftId, scope, hash.lowercase(), System.currentTimeMillis()) == 1
    }

    suspend fun attachCallbackHash(
        draftId: String,
        callbackState: String,
        chainId: Long,
        hash: String
    ): Boolean {
        if (!isValidTxHash(hash) || callbackState.length != 48) return false
        val scope = scopeProvider()
        val draft = dao.find(draftId, scope) ?: return false
        if (!constantTimeEquals(draft.callbackState, callbackState)) return false
        if (draft.chainId != chainId) return false
        if (draft.txHash != null) return draft.txHash.equals(hash, true)
        if (!Web3DraftStatus.canAttachHash(draft.status)) return false
        return dao.attachHash(draftId, scope, hash.lowercase(), System.currentTimeMillis()) == 1
    }

    suspend fun markWalletRejected(draftId: String) {
        dao.setWalletStatus(
            draftId, scopeProvider(), Web3DraftStatus.CANCELLED,
            "钱包明确拒绝或取消了请求", System.currentTimeMillis()
        )
    }

    suspend fun markWalletUnavailable(draftId: String) {
        dao.setWalletStatus(
            draftId, scopeProvider(), Web3DraftStatus.WALLET_UNAVAILABLE,
            "设备上没有可处理该钱包链接的应用", System.currentTimeMillis()
        )
    }

    suspend fun markWalletTimedOut(draftId: String) {
        dao.setWalletStatus(
            draftId, scopeProvider(), Web3DraftStatus.TIMED_OUT_NEEDS_SYNC,
            "钱包未返回交易 Hash；需要同步链上记录确认", System.currentTimeMillis()
        )
    }

    fun observeDrafts(): Flow<List<Web3TransactionDraftEntity>> =
        dao.observeAll(scopeProvider())

    suspend fun createPendingAccountingBill(draftId: String): PendingBillEntity? {
        val draft = dao.find(draftId, scopeProvider()) ?: return null
        if (draft.status !in setOf(Web3DraftStatus.CONFIRMED, Web3DraftStatus.CONFIRMED_FAILED)) return null
        CacheStore.findPendingBill("WEB3-${draft.id}", draft.accountScope)?.let { existing ->
            if (!Web3DraftStatus.shouldRefreshPendingAccountingBill(existing.assetBreakdownJson)) return existing
        }
        val totalWei = draft.totalFeeWei?.toBigIntegerOrNull() ?: return null
        val l1Wei = draft.l1Fee?.toBigIntegerOrNull() ?: BigInteger.ZERO
        val operatorWei = draft.operatorFeeWei?.toBigIntegerOrNull() ?: BigInteger.ZERO
        val l2Wei = totalWei.subtract(l1Wei).subtract(operatorWei)
            .takeIf { it.signum() >= 0 } ?: return null
        val symbol = draft.nativeSymbol ?: nativeSymbolForChain(draft.chainId)
        if (symbol == "UNKNOWN") return null
        val quoteTimestamp = draft.nativePriceTimestamp?.let {
            java.time.Instant.ofEpochMilli(it).toString()
        }
        val l1Usd = draft.nativePriceUsd?.let {
            java.math.BigDecimal(l1Wei).movePointLeft(18)
                .multiply(java.math.BigDecimal.valueOf(it)).toDouble()
        }?.takeIf { it.isFinite() && it >= 0.0 }
        val l2Usd = draft.nativePriceUsd?.let {
            java.math.BigDecimal(l2Wei).movePointLeft(18)
                .multiply(java.math.BigDecimal.valueOf(it)).toDouble()
        }?.takeIf { it.isFinite() && it >= 0.0 }
        fun nativeAmount(wei: BigInteger): String =
            java.math.BigDecimal(wei).movePointLeft(18).stripTrailingZeros().toPlainString()
        val assets = mutableListOf(
            LedgerAssetAmount("gas", nativeAmount(totalWei), symbol, 18, draft.actualGasUsd, quoteTimestamp, "OKX".takeIf { draft.actualGasUsd != null })
        )
        if (Web3RpcRegistry.requiresL1Fee(draft.chainId)) {
            assets += LedgerAssetAmount(
                "l2_fee", nativeAmount(l2Wei), symbol, 18,
                l2Usd, quoteTimestamp, "OKX".takeIf { l2Usd != null }
            )
            assets += LedgerAssetAmount(
                "l1_fee", nativeAmount(l1Wei), symbol, 18,
                l1Usd, quoteTimestamp, "OKX".takeIf { l1Usd != null }
            )
        } else {
            assets += LedgerAssetAmount(
                "execution_fee", nativeAmount(totalWei), symbol, 18,
                draft.actualGasUsd, quoteTimestamp, "OKX".takeIf { draft.actualGasUsd != null }
            )
        }
        if (Web3RpcRegistry.requiresOperatorFee(draft.chainId)) {
            val operatorUsd = draft.nativePriceUsd?.let {
                java.math.BigDecimal(operatorWei).movePointLeft(18)
                    .multiply(java.math.BigDecimal.valueOf(it)).toDouble()
            }?.takeIf { it.isFinite() && it >= 0.0 }
            assets += LedgerAssetAmount(
                "operator_fee", nativeAmount(operatorWei), symbol, 18,
                operatorUsd, quoteTimestamp, "OKX".takeIf { operatorUsd != null }
            )
        }
        CacheStore.findPendingBill("WEB3-${draft.id}", draft.accountScope)?.let { existing ->
            if (!Web3DraftStatus.shouldRefreshPendingAccountingBill(existing.assetBreakdownJson)) return existing
        }
        val network = when (draft.chainId) {
            1L -> "ethereum"
            10L -> "optimism"
            56L -> "bsc"
            137L -> "polygon"
            8453L -> "base"
            42161L -> "arbitrum"
            else -> "eip155:${draft.chainId}"
        }
        val bill = PendingBillEntity(
            id = "WEB3-${draft.id}",
            txHash = draft.txHash ?: return null,
            walletAddress = draft.walletAddress.orEmpty(),
            gasCostUsd = 0.0,
            interactionMethod = if (draft.receiptStatus == 1) "Web3 链上交互" else "Web3 失败交易 Gas",
            timestamp = draft.updatedAt,
            chain = network,
            accountScope = draft.accountScope,
            network = network,
            assetBreakdownJson = LedgerAssetBreakdownCodec.encode(assets)
        )
        if (!CacheStore.materializeWeb3PendingBill(bill, draft.id, draft.accountScope)) return null
        return bill
    }

    suspend fun createManualReviewBillFromDraft(draftId: String): PendingBillEntity? {
        val draft = dao.find(draftId, scopeProvider()) ?: return null
        if (!Web3DraftStatus.canCreateManualReviewBill(draft.status) ||
            draft.txHash.isNullOrBlank()
        ) return null
        val network = when (draft.chainId) {
            1L -> "ethereum"
            10L -> "optimism"
            56L -> "bsc"
            137L -> "polygon"
            8453L -> "base"
            42161L -> "arbitrum"
            else -> "eip155:${draft.chainId}"
        }
        val bill = PendingBillEntity(
            id = "WEB3-${draft.id}",
            txHash = draft.txHash,
            walletAddress = draft.walletAddress.orEmpty(),
            gasCostUsd = 0.0,
            interactionMethod = "Web3 费用手动核验",
            timestamp = System.currentTimeMillis(),
            chain = network,
            accountScope = draft.accountScope,
            network = network,
            assetBreakdownJson = null
        )
        if (!CacheStore.materializeWeb3PendingBill(bill, draft.id, draft.accountScope)) return null
        return bill
    }

    suspend fun attachManuallyVerifiedHash(draftId: String, hash: String): Boolean {
        if (!isValidTxHash(hash)) return false
        val draft = dao.find(draftId, scopeProvider()) ?: return false
        if (draft.txHash != null) return draft.txHash.equals(hash, true)
        if (!Web3DraftStatus.canAttachHash(draft.status)) return false
        val transaction = rpc.getTransaction(draft.chainId, hash) ?: return false
        val actualHash = transaction.optString("hash").takeIf { it.isNotBlank() && it != "null" }
        val from = transaction.optString("from").takeIf { it.isNotBlank() && it != "null" }
        val to = transaction.optString("to").takeIf { it.isNotBlank() && it != "null" }
        val value = Web3RpcClient.parseRpcQuantity(transaction.optString("value"))
        val input = transaction.optString("input").takeIf { it.isNotBlank() }
            ?: transaction.optString("data").takeIf { it.isNotBlank() }
        val expectedFrom = draft.walletAddress?.takeIf { it.isNotBlank() } ?: return false
        if (!Web3ManualTransactionVerifier.matches(
                expectedHash = hash,
                actualHash = actualHash,
                expectedFrom = expectedFrom,
                actualFrom = from,
                expectedTo = draft.toAddress,
                actualTo = to,
                expectedValueWei = draft.valueWei?.toBigIntegerOrNull(),
                actualValueWei = value,
                expectedData = draft.dataHex,
                actualData = input
            )
        ) return false

        return runCatching {
            dao.attachHash(
                draft.id, draft.accountScope, hash.lowercase(), System.currentTimeMillis()
            ) == 1
        }.getOrDefault(false)
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
        suspend fun persistReceipt(updated: Web3TransactionDraftEntity): Boolean {
            val rows = dao.updateReceiptIfPending(
                updated.id, updated.accountScope, updated.gasUsed, updated.effectiveGasPrice,
                updated.l1Fee, updated.operatorFeeWei, updated.totalFeeWei, updated.nativeSymbol,
                updated.nativePriceUsd, updated.nativePriceTimestamp, updated.actualGasUsd,
                updated.receiptStatus, updated.status, updated.lastError, updated.updatedAt
            )
            if (rows == 1) return true
            // A concurrent user confirmation or another worker may already have finalized it.
            return dao.find(updated.id, updated.accountScope)?.status in setOf(
                Web3DraftStatus.CONFIRMED, Web3DraftStatus.CONFIRMED_FAILED, Web3DraftStatus.ACCOUNTED
            )
        }

        // RPC data is not trusted until the receipt is bound to this exact saved TxHash.
        // Never let a malformed or unrelated receipt update the draft's fee/status fields.
        if (!Web3ReceiptIdentityVerifier.matches(
                draft.txHash,
                receipt.optString("transactionHash").takeIf { it.isNotBlank() }
            )
        ) return false

        val gasUsed = Web3RpcClient.parseRpcQuantity(receipt.optString("gasUsed")) ?: return false
        val effectiveGasPrice = Web3RpcClient.parseRpcQuantity(
            receipt.optString("effectiveGasPrice").takeIf { it.isNotBlank() }
                ?: receipt.optString("gasPrice")
        ) ?: return false
        val receiptStatus = Web3RpcClient.parseRpcQuantity(receipt.optString("status"))
            ?.takeIf { it == BigInteger.ZERO || it == BigInteger.ONE }
            ?.toInt() ?: return false

        val l1Fee = Web3ReceiptFeeParser.l1FeeWei(
            draft.chainId,
            receipt.optString("l1Fee").takeIf { it.isNotBlank() },
            receipt.optString("gasUsedForL1").takeIf { it.isNotBlank() },
            effectiveGasPrice,
            receipt.optString("type").takeIf { it.isNotBlank() }
        )

        if (Web3RpcRegistry.requiresL1Fee(draft.chainId) && l1Fee == null) {
            if (!persistReceipt(draft.copy(
                gasUsed = gasUsed.toString(),
                effectiveGasPrice = effectiveGasPrice.toString(),
                receiptStatus = receiptStatus,
                status = Web3DraftStatus.WAITING_L1_FEE,
                lastError = "已取得交易回执，但 RPC 未提供该 L2 的 L1 费用字段；总费用暂不确认",
                updatedAt = System.currentTimeMillis()
            ))) return false
            return true
        }

        val isOpStackDeposit = Web3ReceiptFeeParser.isOpStackDepositTransaction(
            receipt.optString("type").takeIf { it.isNotBlank() }
        )
        val operatorFee = if (isOpStackDeposit) {
            // OP Stack deposit transactions are explicitly exempt from Operator Fee.
            BigInteger.ZERO
        } else runCatching {
            rpc.getOperatorFee(
                draft.chainId,
                gasUsed,
                receipt.optString("blockNumber").takeIf { it.isNotBlank() }
            )
        }.getOrNull()
        if (Web3RpcRegistry.requiresOperatorFee(draft.chainId) && operatorFee == null) {
            if (!persistReceipt(draft.copy(
                gasUsed = gasUsed.toString(),
                effectiveGasPrice = effectiveGasPrice.toString(),
                l1Fee = (l1Fee ?: BigInteger.ZERO).toString(),
                receiptStatus = receiptStatus,
                status = Web3DraftStatus.WAITING_OPERATOR_FEE,
                lastError = "OP Stack 运营商费用无法从交易所在区块核实",
                updatedAt = System.currentTimeMillis()
            ))) return false
            return true
        }
        val feeBreakdown = Web3ReceiptFeeCalculator.calculate(
            draft.chainId, gasUsed, effectiveGasPrice, l1Fee, operatorFee
        ) ?: return false
        val l2Fee = feeBreakdown.l2FeeWei
        val totalFee = feeBreakdown.totalFeeWei
        val nativeSymbol = nativeSymbolForChain(draft.chainId)
        val quote = runCatching { nativePriceProvider(nativeSymbol) }.getOrNull()
        val nativePriceUsd = quote?.lastPrice?.takeIf { it.isFinite() && it > 0.0 }
        val nativePriceTimestamp = quote?.timestamp?.takeIf { nativePriceUsd != null && it > 0L }
        val totalNative = java.math.BigDecimal(totalFee).movePointLeft(18)
        val actualGasUsd = nativePriceUsd
            ?.let { totalNative.multiply(java.math.BigDecimal.valueOf(it)).toDouble() }
            ?.takeIf { it.isFinite() && it >= 0.0 }
        if (!persistReceipt(draft.copy(
            gasUsed = gasUsed.toString(),
            effectiveGasPrice = effectiveGasPrice.toString(),
            l1Fee = (l1Fee ?: BigInteger.ZERO).toString(),
            operatorFeeWei = feeBreakdown.operatorFeeWei.toString(),
            totalFeeWei = totalFee.toString(),
            nativeSymbol = nativeSymbol,
            nativePriceUsd = nativePriceUsd,
            nativePriceTimestamp = nativePriceTimestamp,
            actualGasUsd = actualGasUsd,
            receiptStatus = receiptStatus,
            status = if (receiptStatus == 1) Web3DraftStatus.CONFIRMED
                else Web3DraftStatus.CONFIRMED_FAILED,
            lastError = if (actualGasUsd == null) "真实 Gas 已确认；当前缺少可用币价，USD 估值待补" else null,
            updatedAt = System.currentTimeMillis()
        ))) return false
        return true
    }

    suspend fun markReceiptPending(draft: Web3TransactionDraftEntity, message: String?) {
        dao.setReceiptStatusIfPending(
            draft.id, draft.accountScope, Web3DraftStatus.WAITING_RECEIPT,
            message?.take(180), System.currentTimeMillis()
        )
    }

    suspend fun markReceiptNeedsManualSync(draftId: String) {
        dao.setReceiptStatusIfPending(
            draftId, scopeProvider(), Web3DraftStatus.RECEIPT_NEEDS_SYNC,
            if (Web3RpcRegistry.endpoint(findById(draftId)?.chainId ?: -1L) == null)
                "当前链尚未配置受信任 RPC，需手动记账"
            else "链上回执自动核验达到重试上限，请重新同步",
            System.currentTimeMillis()
        )
    }

    suspend fun pendingReceiptDraftIds(): List<String> =
        dao.pendingReceiptDraftIds(scopeProvider())

    suspend fun markOldWalletRequestsForSync() {
        val cutoff = System.currentTimeMillis() - 30_000L
        dao.staleWalletRequests(scopeProvider(), cutoff).forEach {
            markWalletTimedOut(it.id)
        }
    }

    companion object {
        fun nativeSymbolForChain(chainId: Long): String = when (chainId) {
            1L, 10L, 8453L, 42161L -> "ETH"
            56L -> "BNB"
            137L -> "POL"
            else -> "UNKNOWN"
        }

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
        if (Web3DraftStatus.isReceiptWorkComplete(draft.status)) return Result.success()

        suspend fun materializeOrAlreadyAccounted(): Boolean {
            val bill = repository.createPendingAccountingBill(draftId)
            if (bill != null) return true
            // The user may confirm the ledger candidate while this Worker is running.
            // The repository then correctly refuses to create a second candidate.
            return repository.findById(draftId)?.status == Web3DraftStatus.ACCOUNTED
        }

        // Receipt confirmation and ledger-candidate creation are separate durable
        // steps. If the receipt was already saved but bill insertion failed, retry
        // only materialization; never re-poll or downgrade a confirmed receipt.
        if (draft.status == Web3DraftStatus.CONFIRMED ||
            draft.status == Web3DraftStatus.CONFIRMED_FAILED
        ) {
            return try {
                check(materializeOrAlreadyAccounted()) {
                    "Confirmed Web3 draft could not be materialized"
                }
                Result.success()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            }
        }

        if (draft.txHash.isNullOrBlank()) return Result.success()
        if (Web3RpcRegistry.endpoint(draft.chainId) == null) {
            repository.markReceiptNeedsManualSync(draftId)
            return Result.success()
        }

        return try {
            val receipt = repository.getReceipt(draft)
            if (receipt == null) {
                return if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
                else {
                    repository.markReceiptNeedsManualSync(draftId)
                    Result.success()
                }
            }
            val saved = repository.saveReceipt(draft, receipt)
            if (saved) {
                // Materialize the ledger candidate in the worker, not only when the
                // browser UI is visible. If this fails, the next attempt enters the
                // confirmed-draft branch above and retries only this durable step.
                check(materializeOrAlreadyAccounted()) {
                    "Confirmed Web3 draft could not be materialized"
                }
                Result.success()
            } else if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                repository.markReceiptNeedsManualSync(draftId)
                Result.success()
            }
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

class Web3DraftTimeoutWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val draftId = inputData.getString(KEY_DRAFT_ID) ?: return Result.failure()
        val app = applicationContext as? AirdropHunterApplication ?: return Result.failure()
        val repository = app.appContainer.web3TransactionRepository
        val draft = repository.findById(draftId) ?: return Result.success()
        if (draft.status in setOf(Web3DraftStatus.PENDING_WALLET, Web3DraftStatus.WAITING_HASH)) {
            repository.markWalletTimedOut(draftId)
        }
        return Result.success()
    }

    companion object {
        const val KEY_DRAFT_ID = "web3_timeout_draft_id"
    }
}

object Web3DraftTimeoutScheduler {
    fun enqueue(context: Context, draftId: String) {
        val request = OneTimeWorkRequestBuilder<Web3DraftTimeoutWorker>()
            .setInputData(androidx.work.workDataOf(Web3DraftTimeoutWorker.KEY_DRAFT_ID to draftId))
            .setInitialDelay(30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "web3_wallet_timeout_$draftId",
            ExistingWorkPolicy.KEEP,
            request
        )
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

package com.airdrophunter.app.data

import java.math.BigInteger
import java.security.MessageDigest

data class WalletAddressValidation(val valid: Boolean, val message: String)

/** Encoding/checksum validation only; it does not prove address ownership or on-chain activity. */
object WalletAddressValidator {
    private const val BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val BECH32 = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val BECH32_CONST = 1
    private const val BECH32M_CONST = 0x2bc830a3

    fun validate(chain: String, address: String): WalletAddressValidation {
        if (address.isBlank()) return WalletAddressValidation(false, "请输入钱包地址")
        val valid = isValidForChain(chain, address)
        return if (valid) WalletAddressValidation(true, "地址格式和校验码正确")
        else WalletAddressValidation(false, "地址格式或校验码不正确，请检查所选链和地址")
    }

    fun isValidForChain(chain: String, address: String): Boolean {
        val value = address.trim()
        if (value.isEmpty()) return false
        return when (canonicalChain(chain)) {
            WalletEcosystem.EVM -> Regex("(?i)^0x[a-f0-9]{40}$").matches(value)
            WalletEcosystem.TRON -> isBase58Check(value, setOf(0x41), 21)
            WalletEcosystem.SOL -> decodeBase58(value)?.size == 32
            WalletEcosystem.BTC -> isBase58Check(value, setOf(0x00, 0x05), 21) || isValidSegwit(value)
            else -> false
        }
    }

    fun canonicalChain(chain: String): String = when (chain.trim().lowercase()) {
        "evm", "ethereum", "erc20" -> WalletEcosystem.EVM
        "tron", "trc20" -> WalletEcosystem.TRON
        "sol", "solana" -> WalletEcosystem.SOL
        "btc", "bitcoin" -> WalletEcosystem.BTC
        else -> ""
    }

    private fun isBase58Check(value: String, versions: Set<Int>, payloadSize: Int): Boolean {
        val decoded = decodeBase58(value) ?: return false
        if (decoded.size != payloadSize + 4 || (decoded[0].toInt() and 0xff) !in versions) return false
        val checksum = sha256(sha256(decoded.copyOfRange(0, payloadSize))).copyOfRange(0, 4)
        return checksum.contentEquals(decoded.copyOfRange(payloadSize, decoded.size))
    }

    private fun decodeBase58(value: String): ByteArray? {
        if (value.isEmpty() || value.any { it !in BASE58 }) return null
        var number = BigInteger.ZERO
        value.forEach { char ->
            number = number.multiply(BigInteger.valueOf(58))
                .add(BigInteger.valueOf(BASE58.indexOf(char).toLong()))
        }
        val encoded = number.toByteArray()
        val body = if (encoded.firstOrNull() == 0.toByte()) encoded.copyOfRange(1, encoded.size) else encoded
        return ByteArray(value.takeWhile { it == '1' }.length) + body
    }

    private fun isValidSegwit(input: String): Boolean {
        if (input.length !in 14..90 || input.any { it.code !in 33..126 }) return false
        if (input != input.lowercase() && input != input.uppercase()) return false
        val value = input.lowercase()
        val separator = value.lastIndexOf('1')
        if (separator !in 1..83 || separator + 7 >= value.length || value.substring(0, separator) != "bc") return false
        val values = value.substring(separator + 1).map { BECH32.indexOf(it) }
        if (values.size < 7 || values.any { it < 0 }) return false
        val checksum = polymod(hrpExpand("bc") + values)
        if (checksum != BECH32_CONST && checksum != BECH32M_CONST) return false
        val version = values.first()
        if (version !in 0..16 || (version == 0 && checksum != BECH32_CONST) ||
            (version != 0 && checksum != BECH32M_CONST)) return false
        val data = values.drop(1).dropLast(6)
        var accumulator = 0
        var bits = 0
        val decoded = ArrayList<Int>()
        data.forEach { item ->
            accumulator = (accumulator shl 5) or item
            bits += 5
            while (bits >= 8) {
                bits -= 8
                decoded += (accumulator shr bits) and 0xff
            }
        }
        if (bits >= 5 || ((accumulator shl (8 - bits)) and 0xff) != 0 || decoded.size !in 2..40) return false
        return when (version) {
            0 -> decoded.size == 20 || decoded.size == 32
            1 -> decoded.size == 32
            else -> true
        }
    }

    private fun hrpExpand(hrp: String): List<Int> =
        hrp.map { it.code shr 5 } + listOf(0) + hrp.map { it.code and 31 }

    private fun polymod(values: List<Int>): Int {
        val generators = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var checksum = 1
        values.forEach { value ->
            val top = checksum ushr 25
            checksum = ((checksum and 0x1ffffff) shl 5) xor value
            for (index in 0..4) if (((top ushr index) and 1) != 0) checksum = checksum xor generators[index]
        }
        return checksum
    }

    private fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)
}

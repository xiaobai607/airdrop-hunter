package com.airdrophunter.app.data

import java.math.BigInteger
import java.security.MessageDigest

/** Strict Base58 helpers to distinguish raw 32-byte Solana keys from Base58Check coin addresses. */
internal object Base58Address {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val BECH32_GENERATORS = longArrayOf(
        0x3b6a57b2L, 0x26508e6dL, 0x1ea119faL, 0x3d4233ddL, 0x2a1462b3L
    )

    private fun decode(value: String): ByteArray? {
        if (value.isEmpty() || value.any { ALPHABET.indexOf(it) < 0 }) return null
        var number = BigInteger.ZERO
        value.forEach { character ->
            number = number.multiply(BigInteger.valueOf(58L))
                .add(BigInteger.valueOf(ALPHABET.indexOf(character).toLong()))
        }
        val leadingZeroes = value.takeWhile { it == '1' }.length
        val significant = if (number.signum() == 0) byteArrayOf() else {
            number.toByteArray().let { bytes ->
                if (bytes.firstOrNull() == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
            }
        }
        return ByteArray(leadingZeroes + significant.size).also {
            significant.copyInto(it, destinationOffset = leadingZeroes)
        }
    }

    fun is32BytePublicKey(value: String): Boolean = decode(value)?.size == 32

    /** BIP-173 v0 Bech32 and BIP-350 v1+ Bech32m mainnet witness address validation. */
    fun isBitcoinSegwitAddress(value: String): Boolean {
        if (value.length !in 8..90 || value.any { it.code !in 33..126 }) return false
        if (value != value.lowercase() && value != value.uppercase()) return false
        val normalized = value.lowercase()
        val separator = normalized.lastIndexOf('1')
        if (separator != 2 || normalized.substring(0, separator) != "bc") return false
        if (separator + 7 > normalized.length) return false
        val data = normalized.substring(separator + 1).map { character ->
            BECH32_CHARSET.indexOf(character).takeIf { it >= 0 } ?: return false
        }
        if (data.size < 7) return false
        val witnessVersion = data.first()
        if (witnessVersion !in 0..16) return false
        val checksum = polymod(expandHrp("bc") + data)
        val expectedChecksum = if (witnessVersion == 0) 1L else 0x2bc830a3L
        if (checksum != expectedChecksum) return false
        val witnessProgram = convertFiveToEight(data.subList(1, data.size - 6)) ?: return false
        if (witnessProgram.size !in 2..40) return false
        if (witnessVersion == 0 && witnessProgram.size !in setOf(20, 32)) return false
        if (witnessVersion == 1 && witnessProgram.size != 32) return false
        return true
    }

    private fun expandHrp(hrp: String): List<Int> =
        hrp.map { it.code shr 5 } + 0 + hrp.map { it.code and 31 }

    private fun polymod(values: List<Int>): Long {
        var checksum = 1L
        for (value in values) {
            val top = checksum ushr 25
            checksum = ((checksum and 0x1ffffffL) shl 5) xor value.toLong()
            for (index in 0..4) {
                if (((top shr index) and 1L) != 0L) checksum = checksum xor BECH32_GENERATORS[index]
            }
        }
        return checksum
    }

    private fun convertFiveToEight(values: List<Int>): ByteArray? {
        var accumulator = 0
        var bitCount = 0
        val output = ArrayList<Byte>()
        for (value in values) {
            if (value !in 0..31) return null
            accumulator = ((accumulator shl 5) or value) and 0x0fff
            bitCount += 5
            while (bitCount >= 8) {
                bitCount -= 8
                output += ((accumulator shr bitCount) and 0xff).toByte()
            }
        }
        if (bitCount >= 5 || ((accumulator shl (8 - bitCount)) and 0xff) != 0) return null
        return output.toByteArray()
    }

    fun isBase58Check(value: String, acceptedVersions: Set<Int>): Boolean {
        val decoded = decode(value) ?: return false
        if (decoded.size != 25) return false
        val version = decoded[0].toInt() and 0xff
        if (version !in acceptedVersions) return false
        val payload = decoded.copyOfRange(0, 21)
        val first = MessageDigest.getInstance("SHA-256").digest(payload)
        val checksum = MessageDigest.getInstance("SHA-256").digest(first).copyOfRange(0, 4)
        return decoded.copyOfRange(21, 25).contentEquals(checksum)
    }
}

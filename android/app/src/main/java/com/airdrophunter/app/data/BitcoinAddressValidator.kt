package com.airdrophunter.app.data

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Mainnet Bitcoin address validation: Base58Check plus SegWit Bech32/Bech32m.
 * This validates the address encoding and witness program, not ownership.
 */
object BitcoinAddressValidator {
    private const val BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val BECH32 = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val BECH32_CONST = 1
    private const val BECH32M_CONST = 0x2bc830a3

    fun isValid(value: String): Boolean =
        isValidBase58(value) || isValidSegwit(value)

    private fun isValidBase58(value: String): Boolean {
        if (value.length !in 26..35 || value.any { it !in BASE58 }) return false
        var number = BigInteger.ZERO
        value.forEach { char ->
            number = number.multiply(BigInteger.valueOf(58))
                .add(BigInteger.valueOf(BASE58.indexOf(char).toLong()))
        }
        val encoded = number.toByteArray()
        val decoded = if (encoded.firstOrNull() == 0.toByte()) encoded.drop(1).toByteArray() else encoded
        val leadingZeroes = value.takeWhile { it == '1' }.length
        val payload = ByteArray(leadingZeroes) + decoded
        if (payload.size != 25 || (payload[0].toInt() and 0xff) !in setOf(0, 5)) return false
        val digest = MessageDigest.getInstance("SHA-256")
        val checksum = digest.digest(digest.digest(payload.copyOfRange(0, 21))).copyOfRange(0, 4)
        return checksum.contentEquals(payload.copyOfRange(21, 25))
    }

    private fun isValidSegwit(input: String): Boolean {
        if (input.length !in 14..90 || input.any { it.code !in 33..126 }) return false
        if (input != input.lowercase() && input != input.uppercase()) return false
        val value = input.lowercase()
        val separator = value.lastIndexOf('1')
        if (separator !in 1..83 || separator + 7 >= value.length) return false
        val hrp = value.substring(0, separator)
        if (hrp != "bc") return false
        val values = value.substring(separator + 1).map { BECH32.indexOf(it) }
        if (values.size < 7 || values.any { it < 0 }) return false

        val checksum = polymod(hrpExpand(hrp) + values)
        if (checksum != BECH32_CONST && checksum != BECH32M_CONST) return false
        val version = values.first()
        if (version !in 0..16) return false
        if (version == 0 && checksum != BECH32_CONST) return false
        if (version != 0 && checksum != BECH32M_CONST) return false

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
        if (bits >= 5 || ((accumulator shl (8 - bits)) and 0xff) != 0) return false
        if (decoded.size !in 2..40) return false
        return version != 0 || decoded.size == 20 || decoded.size == 32
    }

    private fun hrpExpand(hrp: String): List<Int> =
        hrp.map { it.code shr 5 } + listOf(0) + hrp.map { it.code and 31 }

    private fun polymod(values: List<Int>): Int {
        val generators = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var checksum = 1
        values.forEach { value ->
            val top = checksum ushr 25
            checksum = ((checksum and 0x1ffffff) shl 5) xor value
            for (index in 0..4) {
                if (((top ushr index) and 1) != 0) checksum = checksum xor generators[index]
            }
        }
        return checksum
    }
}

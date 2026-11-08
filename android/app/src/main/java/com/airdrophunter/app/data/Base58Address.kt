package com.airdrophunter.app.data

import java.math.BigInteger
import java.security.MessageDigest

/** Strict Base58 helpers to distinguish raw 32-byte Solana keys from Base58Check coin addresses. */
internal object Base58Address {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

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

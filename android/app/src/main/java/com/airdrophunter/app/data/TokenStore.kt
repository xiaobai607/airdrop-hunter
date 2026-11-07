package com.airdrophunter.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.authDataStore by preferencesDataStore(name = "airdrop_hunter_auth")

object TokenStore {
    private const val KEY_ALIAS = "airdrop_hunter_access_token_v1"
    private const val ENCRYPTED_PREFIX = "enc:v1:"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    private val key = stringPreferencesKey("access_token")
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun observeToken(context: Context): Flow<String?> =
        context.applicationContext.authDataStore.data.map { preferences ->
            preferences[key]?.let(::decryptIfNeeded)
        }

    suspend fun saveToken(context: Context, token: String) = withContext(Dispatchers.IO) {
        val encrypted = encrypt(token)
        context.applicationContext.authDataStore.edit { it[key] = encrypted }
    }

    suspend fun clearToken(context: Context) = withContext(Dispatchers.IO) {
        context.applicationContext.authDataStore.edit { it.remove(key) }
    }

    fun readTokenBlocking(): String? {
        val context = appContext ?: return null
        return runBlocking(Dispatchers.IO) {
            val stored = context.authDataStore.data.first()[key] ?: return@runBlocking null
            if (stored.startsWith(ENCRYPTED_PREFIX)) {
                val decoded = decryptIfNeeded(stored)
                if (decoded == null) context.authDataStore.edit { it.remove(key) }
                decoded
            } else {
                // Migrate existing plaintext tokens in place without forcing users to sign in again.
                val encrypted = runCatching { encrypt(stored) }.getOrNull()
                if (encrypted != null) context.authDataStore.edit { it[key] = encrypted }
                stored
            }
        }
    }

    private fun decryptIfNeeded(value: String): String? {
        if (!value.startsWith(ENCRYPTED_PREFIX)) return value
        return runCatching {
            val payload = Base64.decode(value.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
            require(payload.size > GCM_IV_LENGTH + 16)
            val iv = payload.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = payload.copyOfRange(GCM_IV_LENGTH, payload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + ciphertext
        return ENCRYPTED_PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
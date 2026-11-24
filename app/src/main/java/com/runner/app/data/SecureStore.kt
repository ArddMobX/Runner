package com.runner.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.UnrecoverableEntryException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Шифрование секретов (API-ключей) через Android Keystore, AES-256-GCM.
 *
 * Сам ключ шифрования генерируется в системном Keystore и никогда не покидает
 * устройство — в SharedPreferences уходит только base64 от IV и шифротекста.
 * Это заменяет deprecated EncryptedSharedPreferences без лишней зависимости.
 */
object SecureStore {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "runner_secrets_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    private val keyLock = Any()

    /**
     * Загружает существующий ключ из Keystore без создания нового.
     * Используется при расшифровке, чтобы чтение никогда не мутировало Keystore.
     */
    private fun loadExistingKey(): SecretKey? = synchronized(keyLock) {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) return@synchronized null
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Возвращает существующий ключ или потокобезопасно создаёт новый.
     * Если запись в Keystore стала невосстановимой — удаляет её и пересоздаёт.
     */
    private fun getOrCreateKey(): SecretKey? = synchronized(keyLock) {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                val existing = try {
                    keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                } catch (_: UnrecoverableEntryException) {
                    null
                }
                if (existing != null) return@synchronized existing
                runCatching { keyStore.deleteEntry(KEY_ALIAS) }
            }
            generateKeyLocked()
        } catch (_: Exception) {
            null
        }
    }

    /** Удаляет инвалидированный алиас и генерирует новый ключ. */
    private fun regenerateKey(): SecretKey? = synchronized(keyLock) {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                runCatching { keyStore.deleteEntry(KEY_ALIAS) }
            }
            generateKeyLocked()
        } catch (_: Exception) {
            null
        }
    }

    private fun generateKeyLocked(): SecretKey? = try {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        generator.generateKey()
    } catch (_: Exception) {
        null
    }

    private fun encryptWithKey(plain: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /** Возвращает "base64(iv):base64(ciphertext)" либо null, если Keystore недоступен. */
    fun encrypt(plain: String): String? {
        val key = getOrCreateKey() ?: return null
        return try {
            encryptWithKey(plain, key)
        } catch (_: InvalidKeyException) {
            // KeyPermanentlyInvalidatedException наследует InvalidKeyException:
            // пересоздаём алиас один раз, чтобы не застревать в plaintext fallback.
            val regenerated = regenerateKey() ?: return null
            try {
                encryptWithKey(plain, regenerated)
            } catch (_: Exception) {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Расшифровывает то, что вернул [encrypt].
     * null означает «прочитать не удалось» — ключ отсутствует, сменился или данные повреждены.
     */
    fun decrypt(blob: String): String? = try {
        val separator = blob.indexOf(':')
        if (separator <= 0 || separator >= blob.lastIndex) {
            null
        } else {
            val iv = Base64.decode(blob.substring(0, separator), Base64.NO_WRAP)
            val body = Base64.decode(blob.substring(separator + 1), Base64.NO_WRAP)
            val key = loadExistingKey()
            if (key == null || iv.isEmpty() || body.isEmpty()) {
                null
            } else {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                String(cipher.doFinal(body), Charsets.UTF_8)
            }
        }
    } catch (_: Exception) {
        null
    }
}

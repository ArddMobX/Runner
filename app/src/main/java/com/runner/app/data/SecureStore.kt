package com.runner.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
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

    private fun secretKey(): SecretKey? = try {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: generateKey()
    } catch (e: Exception) {
        null
    }

    private fun generateKey(): SecretKey? = try {
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
    } catch (e: Exception) {
        null
    }

    /** Возвращает "base64(iv):base64(ciphertext)" либо null, если Keystore недоступен. */
    fun encrypt(plain: String): String? = try {
        val key = secretKey()
        if (key == null) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                    Base64.encodeToString(encrypted, Base64.NO_WRAP)
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Расшифровывает то, что вернул [encrypt].
     * null означает «прочитать не удалось» — ключ сменился или данные повреждены.
     */
    fun decrypt(blob: String): String? = try {
        val separator = blob.indexOf(':')
        if (separator <= 0) {
            null
        } else {
            val iv = Base64.decode(blob.substring(0, separator), Base64.NO_WRAP)
            val body = Base64.decode(blob.substring(separator + 1), Base64.NO_WRAP)
            val key = secretKey()
            if (key == null) {
                null
            } else {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                String(cipher.doFinal(body), Charsets.UTF_8)
            }
        }
    } catch (e: Exception) {
        null
    }
}

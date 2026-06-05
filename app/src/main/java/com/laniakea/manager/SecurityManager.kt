package com.laniakea.manager

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.laniakea.data.DiaryEntry
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val Context.dataStore by preferencesDataStore(name = "secure_prefs")

class SecurityManager(private val context: Context) {

    private val algorithm = "AES/GCM/NoPadding"
    private val tagLength = 128
    private val ivLength = 12
    private val saltLength = 16
    private val iterations = 10000
    private val keyLength = 256
    private val masterKeyAlias = "laniakea_master_key"
    private val keyDiaryEncryptionKey = stringPreferencesKey("diary_encryption_key")

    private var cachedKey: ByteArray? = null

    private fun getMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(masterKeyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                masterKeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
        return keyStore.getKey(masterKeyAlias, null) as SecretKey
    }

    private fun wrapKey(key: ByteArray): String {
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())
        val iv = cipher.iv
        val encryptedKey = cipher.doFinal(key)
        
        val combined = ByteArray(iv.size + encryptedKey.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedKey, 0, combined, iv.size, encryptedKey.size)
        
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    private fun unwrapKey(wrappedKeyString: String): ByteArray {
        val combined = Base64.decode(wrappedKeyString, Base64.DEFAULT)
        val iv = ByteArray(ivLength)
        System.arraycopy(combined, 0, iv, 0, ivLength)
        val encryptedKey = ByteArray(combined.size - ivLength)
        System.arraycopy(combined, ivLength, encryptedKey, 0, encryptedKey.size)
        
        val cipher = Cipher.getInstance(algorithm)
        val spec = GCMParameterSpec(tagLength, iv)
        cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), spec)
        return cipher.doFinal(encryptedKey)
    }

    private fun getOrGenerateKey(): ByteArray {
        cachedKey?.let { return it }

        val key = runBlocking(Dispatchers.IO) {
            val wrappedKeyString = context.dataStore.data.map { it[keyDiaryEncryptionKey] }.first()
            if (wrappedKeyString != null) {
                try {
                    unwrapKey(wrappedKeyString)
                } catch (_: Exception) {
                    val newKey = ByteArray(32) // AES-256
                    SecureRandom().nextBytes(newKey)
                    val newWrappedKeyString = wrapKey(newKey)
                    context.dataStore.edit { it[keyDiaryEncryptionKey] = newWrappedKeyString }
                    newKey
                }
            } else {
                val newKey = ByteArray(32) // AES-256
                SecureRandom().nextBytes(newKey)
                val newWrappedKeyString = wrapKey(newKey)
                context.dataStore.edit { it[keyDiaryEncryptionKey] = newWrappedKeyString }
                newKey
            }
        }
        cachedKey = key
        return key
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val key = getOrGenerateKey()
        return encryptWithKey(plainText, key)
    }

    fun decrypt(encryptedText: String): String {
        if (encryptedText.isEmpty()) return ""
        val key = getOrGenerateKey()
        return try {
            decryptWithKey(encryptedText, key)
        } catch (_: Exception) {
            // If decryption fails, it might be unencrypted legacy data
            encryptedText
        }
    }

    fun encryptEntry(entry: DiaryEntry): DiaryEntry {
        return entry.copy(
            content = encrypt(entry.content),
            mood = encrypt(entry.mood),
            category = encrypt(entry.category),
            weather = encrypt(entry.weather),
            activities = encrypt(entry.activities)
        )
    }

    fun decryptEntry(entry: DiaryEntry): DiaryEntry {
        return entry.copy(
            content = try { decrypt(entry.content) } catch (_: Exception) { "[Encrypted]" },
            mood = try { decrypt(entry.mood) } catch (_: Exception) { "" },
            category = try { decrypt(entry.category) } catch (_: Exception) { "" },
            weather = try { decrypt(entry.weather) } catch (_: Exception) { "" },
            activities = try { decrypt(entry.activities) } catch (_: Exception) { "" }
        )
    }

    private fun encryptWithKey(plainText: String, key: ByteArray): String {
        val cipher = Cipher.getInstance(algorithm)
        val iv = ByteArray(ivLength)
        SecureRandom().nextBytes(iv)
        val gcmSpec = GCMParameterSpec(tagLength, iv)
        val secretKey = SecretKeySpec(key, "AES")
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
        
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    private fun decryptWithKey(encryptedText: String, key: ByteArray): String {
        val combined = Base64.decode(encryptedText, Base64.DEFAULT)
        if (combined.size < ivLength) throw Exception("Encrypted text too short")
        
        val iv = ByteArray(ivLength)
        System.arraycopy(combined, 0, iv, 0, ivLength)
        
        val encryptedBytes = ByteArray(combined.size - ivLength)
        System.arraycopy(combined, ivLength, encryptedBytes, 0, encryptedBytes.size)
        
        val cipher = Cipher.getInstance(algorithm)
        val gcmSpec = GCMParameterSpec(tagLength, iv)
        val secretKey = SecretKeySpec(key, "AES")
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        
        return String(decryptedBytes, Charsets.UTF_8)
    }

    fun getEncryptingStream(outputStream: OutputStream, password: CharArray): OutputStream {
        val salt = ByteArray(saltLength)
        SecureRandom().nextBytes(salt)
        outputStream.write(salt)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password, salt, iterations, keyLength)
        val tmp = factory.generateSecret(spec)
        val secretKey = SecretKeySpec(tmp.encoded, "AES")

        val cipher = Cipher.getInstance(algorithm)
        val iv = ByteArray(ivLength)
        SecureRandom().nextBytes(iv)
        outputStream.write(iv)
        
        val gcmSpec = GCMParameterSpec(tagLength, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        
        return CipherOutputStream(outputStream, cipher)
    }

    fun getDecryptingStream(inputStream: InputStream, password: CharArray): InputStream {
        val salt = ByteArray(saltLength)
        var readTotal = 0
        while (readTotal < saltLength) {
            val r = inputStream.read(salt, readTotal, saltLength - readTotal)
            if (r == -1) throw Exception("Invalid backup file: salt truncated")
            readTotal += r
        }

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password, salt, iterations, keyLength)
        val tmp = factory.generateSecret(spec)
        val secretKey = SecretKeySpec(tmp.encoded, "AES")

        val iv = ByteArray(ivLength)
        readTotal = 0
        while (readTotal < ivLength) {
            val r = inputStream.read(iv, readTotal, ivLength - readTotal)
            if (r == -1) throw Exception("Invalid backup file: IV truncated")
            readTotal += r
        }

        val cipher = Cipher.getInstance(algorithm)
        val gcmSpec = GCMParameterSpec(tagLength, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        
        return CipherInputStream(inputStream, cipher)
    }
}
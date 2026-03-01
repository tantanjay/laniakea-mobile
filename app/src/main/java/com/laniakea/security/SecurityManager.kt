package com.laniakea.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import androidx.core.content.edit
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class SecurityManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val ALGORITHM = "AES/GCM/NoPadding"
    private val TAG_LENGTH = 128
    private val IV_LENGTH = 12
    private val SALT_LENGTH = 16
    private val ITERATIONS = 10000
    private val KEY_LENGTH = 256

    private fun getOrGenerateKey(): ByteArray {
        val keyString: String? = sharedPreferences.getString("diary_encryption_key", null)
        return if (!keyString.isNullOrEmpty()) {
            Base64.decode(keyString, Base64.DEFAULT)
        } else {
            val key = ByteArray(32) // AES-256
            SecureRandom().nextBytes(key)
            val newKeyString = Base64.encodeToString(key, Base64.DEFAULT)
            sharedPreferences.edit { putString("diary_encryption_key", newKeyString) }
            key
        }
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
        } catch (e: Exception) {
            // If decryption fails, it might be unencrypted legacy data
            encryptedText
        }
    }

    private fun encryptWithKey(plainText: String, key: ByteArray): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH, iv)
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
        if (combined.size < IV_LENGTH) throw Exception("Encrypted text too short")
        
        val iv = ByteArray(IV_LENGTH)
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH)
        
        val encryptedBytes = ByteArray(combined.size - IV_LENGTH)
        System.arraycopy(combined, IV_LENGTH, encryptedBytes, 0, encryptedBytes.size)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH, iv)
        val secretKey = SecretKeySpec(key, "AES")
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        
        return String(decryptedBytes, Charsets.UTF_8)
    }

    fun encryptWithPassword(plainText: String, password: CharArray): String {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        val secretKey = SecretKeySpec(tmp.encoded, "AES")

        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH, iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        
        val combined = ByteArray(salt.size + iv.size + encryptedBytes.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, salt.size + iv.size, encryptedBytes.size)
        
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    fun decryptWithPassword(encryptedText: String, password: CharArray): String {
        val combined = Base64.decode(encryptedText, Base64.DEFAULT)
        if (combined.size < SALT_LENGTH + IV_LENGTH) throw Exception("Backup data corrupted")
        
        val salt = ByteArray(SALT_LENGTH)
        System.arraycopy(combined, 0, salt, 0, SALT_LENGTH)
        
        val iv = ByteArray(IV_LENGTH)
        System.arraycopy(combined, SALT_LENGTH, iv, 0, IV_LENGTH)
        
        val encryptedBytes = ByteArray(combined.size - SALT_LENGTH - IV_LENGTH)
        System.arraycopy(combined, SALT_LENGTH + IV_LENGTH, encryptedBytes, 0, encryptedBytes.size)
        
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        val secretKey = SecretKeySpec(tmp.encoded, "AES")

        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH, iv)
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        
        return String(decryptedBytes, Charsets.UTF_8)
    }

    fun getEncryptingStream(outputStream: OutputStream, password: CharArray): OutputStream {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        outputStream.write(salt)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        val secretKey = SecretKeySpec(tmp.encoded, "AES")

        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        outputStream.write(iv)
        
        val gcmSpec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        
        return CipherOutputStream(outputStream, cipher)
    }

    fun getDecryptingStream(inputStream: InputStream, password: CharArray): InputStream {
        val salt = ByteArray(SALT_LENGTH)
        var readTotal = 0
        while (readTotal < SALT_LENGTH) {
            val r = inputStream.read(salt, readTotal, SALT_LENGTH - readTotal)
            if (r == -1) throw Exception("Invalid backup file: salt truncated")
            readTotal += r
        }

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        val secretKey = SecretKeySpec(tmp.encoded, "AES")

        val iv = ByteArray(IV_LENGTH)
        readTotal = 0
        while (readTotal < IV_LENGTH) {
            val r = inputStream.read(iv, readTotal, IV_LENGTH - readTotal)
            if (r == -1) throw Exception("Invalid backup file: IV truncated")
            readTotal += r
        }

        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        
        return CipherInputStream(inputStream, cipher)
    }
}
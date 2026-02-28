package com.laniakea.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import androidx.core.content.edit

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
        val key = getOrGenerateKey()
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

    fun decrypt(encryptedText: String): String {
        val key = getOrGenerateKey()
        val combined = Base64.decode(encryptedText, Base64.DEFAULT)
        
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
}
package com.neverlands.anlc.data.local

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.DESedeKeySpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import java.nio.charset.Charset

object CryptoHelper {

    private val SALT_BINARY = byteArrayOf(0x49, 0x76, 0x61, 0x6e, 0x20, 0x4d, 0x65, 0x64, 0x76, 0x65, 0x64, 0x65, 0x76)
    private const val SALT_TEXT = "we1022@alA0"
    private val RUSSIAN_CODE_PAGE_ENCODING = Charset.forName("windows-1251")

    // Implement Password2Hash (MD5)
    fun password2Hash(password: String): String {
        val combined = SALT_TEXT + password
        val bytes = combined.toByteArray(RUSSIAN_CODE_PAGE_ENCODING)
        val md = MessageDigest.getInstance("MD5")
        val hash = md.digest(bytes)
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    // Implement EncryptString (TripleDES with PBKDF2)
    fun encryptString(str: String, password: String): String {
        val clearData = str.toByteArray(RUSSIAN_CODE_PAGE_ENCODING)
        val derivedKey = deriveKey(password, SALT_BINARY)
        val key = derivedKey.copyOfRange(0, 24) // 24 bytes for DESedeKeySpec (192 bits)
        val iv = derivedKey.copyOfRange(24, 32) // 8 bytes for IV

        val cipher = Cipher.getInstance("DESede/CBC/PKCS7Padding")
        val keySpec = DESedeKeySpec(key)
        val secretKeyFactory = SecretKeyFactory.getInstance("DESede")
        val secretKey = secretKeyFactory.generateSecret(keySpec)
        val ivSpec = IvParameterSpec(iv)

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        val encryptedBytes = cipher.doFinal(clearData)
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    // Implement DecryptString (TripleDES with PBKDF2)
    fun decryptString(str: String, password: String): String {
        val encryptedBytes = Base64.decode(str, Base64.NO_WRAP)
        val derivedKey = deriveKey(password, SALT_BINARY)
        val key = derivedKey.copyOfRange(0, 24) // 24 bytes for DESedeKeySpec (192 bits)
        val iv = derivedKey.copyOfRange(24, 32) // 8 bytes for IV

        val cipher = Cipher.getInstance("DESede/CBC/PKCS7Padding")
        val keySpec = DESedeKeySpec(key)
        val secretKeyFactory = SecretKeyFactory.getInstance("DESede")
        val secretKey = secretKeyFactory.generateSecret(keySpec)
        val ivSpec = IvParameterSpec(iv)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, RUSSIAN_CODE_PAGE_ENCODING)
    }

    // Helper function to derive key using PBKDF2
    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 1000, 256) // 1000 iterations, 256 bits (32 bytes)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return skf.generateSecret(spec).encoded
    }
}
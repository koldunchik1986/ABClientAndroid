package com.neverlands.anlc.util

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

/**
 * Хелпер для всех криптографических операций, порт с C# версии.
 * Использует TripleDES для шифрования/дешифрования и MD5 для хеширования.
 */
object CryptoHelper {

    // Соль для шифрования, соответствует "Ivan Medvedev" в ASCII
    private val SALT_BINARY = byteArrayOf(
        0x49, 0x76, 0x61, 0x6e, 0x20, 0x4d, 0x65, 0x64, 0x76, 0x65, 0x64, 0x65, 0x76
    )

    // Соль для хеширования пароля
    private const val SALT_TEXT = "we1022@alA0"

    // Кодировка для работы со строками, как в ПК-версии
    private val CODEPAGE = charset("windows-1251")

    /**
     * Шифрует строку с использованием TripleDES.
     * @param str Строка для шифрования.
     * @param password Пароль для генерации ключа.
     * @return Зашифрованная строка в формате Base64.
     */
    fun encryptString(str: String, password: String): String {
        val buffer = str.toByteArray(CODEPAGE)
        val encryptedBuffer = encryptData(buffer, password)
        return Base64.getEncoder().encodeToString(encryptedBuffer)
    }

    /**
     * Расшифровывает строку, зашифрованную TripleDES.
     * @param str Зашифрованная строка в формате Base64.
     * @param password Пароль для генерации ключа.
     * @return Расшифрованная строка.
     */
    fun decryptString(str: String, password: String): String {
        val encryptedBuffer = Base64.getDecoder().decode(str)
        val buffer = decryptData(encryptedBuffer, password)
        return String(buffer, CODEPAGE)
    }

    /**
     * Создает MD5-хеш из пароля и соли.
     * @param password Пароль для хеширования.
     * @return Хеш в формате Base64.
     */
    fun passwordToHash(password: String): String {
        val textToHash = SALT_TEXT + password
        val md = MessageDigest.getInstance("MD5")
        val hash = md.digest(textToHash.toByteArray(CODEPAGE))
        return Base64.getEncoder().encodeToString(hash)
    }

    /**
     * Шифрует данные для запроса авторизации клиента.
     */
    fun encryptAsk(askCode: String): String {
        val key = "p@ssw0rdDR0wSS@P6660juht".toByteArray(Charsets.US_ASCII)
        val iv = "p@ssw0rd".toByteArray(Charsets.US_ASCII)
        val data = askCode.toByteArray(CODEPAGE)

        val cipher = Cipher.getInstance("DESede/CBC/NoPadding")
        val keySpec = SecretKeySpec(key, "DESede")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

        // Manual zero padding
        val blockSize = 8
        val padding = blockSize - (data.size % blockSize)
        val paddedData = data + ByteArray(padding)

        val encrypted = cipher.doFinal(paddedData)
        return Base64.getEncoder().encodeToString(encrypted)
    }

    /**
     * Шифрует массив байт.
     * @param data Данные для шифрования.
     * @param password Пароль.
     * @return Зашифрованные данные.
     */
    private fun encryptData(data: ByteArray, password: String): ByteArray {
        val (key, iv) = deriveKeyAndIv(password)
        val cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "DESede"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    /**
     * Расшифровывает массив байт.
     * @param encryptedData Зашифрованные данные.
     * @param password Пароль.
     * @return Расшифрованные данные.
     */
    private fun decryptData(encryptedData: ByteArray, password: String): ByteArray {
        val (key, iv) = deriveKeyAndIv(password)
        val cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "DESede"), IvParameterSpec(iv))
        return cipher.doFinal(encryptedData)
    }

    /**
     * Генерирует ключ и вектор инициализации (IV) из пароля.
     * Логика полностью повторяет C# Rfc2898DeriveBytes.
     * @param password Пароль.
     * @return Пара (ключ, IV).
     */
    private fun deriveKeyAndIv(password: String): Pair<ByteArray, ByteArray> {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        // TripleDES ключ должен быть 24 байта, но C# реализация использует 16 байт (128 бит), дополняя его.
        // Мы генерируем 24 байта и берем первые 16, чтобы соответствовать C#.
        // IV для TripleDES - 8 байт.
        val spec = PBEKeySpec(password.toCharArray(), SALT_BINARY, 1000, (16 + 8) * 8) // 24 байта * 8 бит
        val key = factory.generateSecret(spec).encoded
        val desKey = key.copyOfRange(0, 16)
        val iv = key.copyOfRange(16, 16 + 8)
        return Pair(desKey, iv)
    }
}

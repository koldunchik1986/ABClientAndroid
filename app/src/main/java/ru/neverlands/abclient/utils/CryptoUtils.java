package ru.neverlands.abclient.utils;

import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.security.spec.KeySpec;

/**
 * Утилиты для шифрования, совместимого с реализацией в C# ABClient.
 * Использует TripleDES с ключом, сгенерированным по стандарту PBKDF2 (Rfc2898DeriveBytes в .NET).
 */
public class CryptoUtils {

    private static final String ALGORITHM = "DESede/CBC/PKCS5Padding";
    private static final String KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final int ITERATION_COUNT = 1000; // Значение по умолчанию для Rfc2898DeriveBytes в .NET
    private static final int KEY_LENGTH_BITS = 128; // 16 байт для TripleDES
    private static final int IV_LENGTH_BYTES = 8;   // 8 байт для TripleDES
    private static final byte[] SALT = new byte[] { 0x49, 0x76, 0x61, 0x6e, 0x20, 0x4d, 0x65, 0x64, 0x76, 0x65, 0x64, 0x65, 0x76 }; // "Ivan Medvedev"
    private static final Charset CODEPAGE = Charset.forName("windows-1251");

    public static String encrypt(String plainText, String password) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
        // В C# версии ключ (16 байт) и вектор инициализации (IV, 8 байт) генерируются последовательными вызовами pdb.GetBytes().
        // Java PBEKeySpec генерирует все байты за один раз, что дает идентичный результат.
        KeySpec spec = new PBEKeySpec(password.toCharArray(), SALT, ITERATION_COUNT, KEY_LENGTH_BITS + IV_LENGTH_BYTES * 8);
        byte[] derivedKeyAndIv = factory.generateSecret(spec).getEncoded();

        byte[] keyBytes = new byte[KEY_LENGTH_BITS / 8];
        byte[] ivBytes = new byte[IV_LENGTH_BYTES];

        System.arraycopy(derivedKeyAndIv, 0, keyBytes, 0, keyBytes.length);
        System.arraycopy(derivedKeyAndIv, keyBytes.length, ivBytes, 0, ivBytes.length);

        // .NET TripleDES провайдер может принимать 16-байтный ключ, автоматически расширяя его до 24 байт
        // путем повторения первых 8 байт. Воссоздаем эту логику для совместимости с Java.
        final byte[] tripleDesKeyBytes = new byte[24];
        System.arraycopy(keyBytes, 0, tripleDesKeyBytes, 0, 16);
        System.arraycopy(keyBytes, 0, tripleDesKeyBytes, 16, 8);
        SecretKeySpec secretKey = new SecretKeySpec(tripleDesKeyBytes, "DESede");

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(ivBytes));

        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(CODEPAGE));
        // Используем Base64.DEFAULT для совместимости с C# Convert.ToBase64String, который вставляет переносы строк.
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
    }

    public static String decrypt(String encryptedText, String password) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), SALT, ITERATION_COUNT, KEY_LENGTH_BITS + IV_LENGTH_BYTES * 8);
        byte[] derivedKeyAndIv = factory.generateSecret(spec).getEncoded();

        byte[] keyBytes = new byte[KEY_LENGTH_BITS / 8];
        byte[] ivBytes = new byte[IV_LENGTH_BYTES];

        System.arraycopy(derivedKeyAndIv, 0, keyBytes, 0, keyBytes.length);
        System.arraycopy(derivedKeyAndIv, keyBytes.length, ivBytes, 0, ivBytes.length);

        final byte[] tripleDesKeyBytes = new byte[24];
        System.arraycopy(keyBytes, 0, tripleDesKeyBytes, 0, 16);
        System.arraycopy(keyBytes, 0, tripleDesKeyBytes, 16, 8);
        SecretKeySpec secretKey = new SecretKeySpec(tripleDesKeyBytes, "DESede");

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(ivBytes));

        // Используем Base64.DEFAULT, так как он может обрабатывать строки с переносами, которые генерирует C#.
        byte[] decodedBytes = Base64.decode(encryptedText, Base64.DEFAULT);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes, CODEPAGE);
    }
}

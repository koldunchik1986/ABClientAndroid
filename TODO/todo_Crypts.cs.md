# План портирования Crypts.cs

Файл `Crypts.cs` — критически важный компонент, реализующий всю логику хэширования и шифрования, используемую в приложении, в частности, для защиты профилей.

## Функциональность в C#

Класс реализует два основных механизма:

### 1. Хэширование пароля (`Password2Hash`)

*   **Назначение**: Используется для быстрой проверки мастер-пароля при доступе к зашифрованному профилю.
*   **Алгоритм**:
    1.  Берется текстовая соль `AppConsts.SaltText`.
    2.  Вычисляется строка `SaltText + password`.
    3.  Строка преобразуется в байты в кодировке `windows-1251`.
    4.  От полученного массива байт вычисляется хэш **MD5**.
    5.  Результат кодируется в **Base64**.

### 2. Шифрование данных (`EncryptString` / `DecryptString`)

*   **Назначение**: Используется для шифрования и дешифрования чувствительных данных профиля (например, игрового пароля) с помощью мастер-пароля.
*   **Алгоритм**:
    1.  **Генерация ключа**: Из мастер-пароля и бинарной соли `AppConsts.SaltBinary` с помощью `Rfc2898DeriveBytes` (**PBKDF2**) генерируются 16 байт для ключа и 8 байт для вектора инициализации (IV).
    2.  **Шифрование**: Данные шифруются симметричным алгоритмом **TripleDES** (3DES) с полученными ключом и IV.
    3.  **Кодирование**: Зашифрованный результат кодируется в **Base64** для хранения в виде строки.

## План портирования на Android

Необходимо **точно воссоздать** оба алгоритма на Java, используя стандартные криптографические библиотеки, чтобы обеспечить полную совместимость.

1.  **Найти соли**: Найти значения констант `AppConsts.SaltText` и `AppConsts.SaltBinary` в файле `AppConsts.cs` и перенести их в Java.

2.  **Создать `Crypts.java`** в пакете `ru.neverlands.abclient.utils`.

3.  **Реализовать `password2Hash(String password)`**:
    ```java
    import java.security.MessageDigest;
    import android.util.Base64;

    public static String password2Hash(String password) {
        try {
            String saltedPass = AppConsts.SALT_TEXT + password;
            byte[] passBytes = saltedPass.getBytes("windows-1251");
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(passBytes);
            return Base64.encodeToString(hashBytes, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    ```

4.  **Реализовать `encryptString(String str, String password)` и `decryptString(...)`**:
    ```java
    import javax.crypto.Cipher;
    import javax.crypto.SecretKeyFactory;
    import javax.crypto.spec.DESedeKeySpec;
    import javax.crypto.spec.IvParameterSpec;
    import javax.crypto.spec.PBEKeySpec;
    import java.security.Key;

    // Вспомогательный метод для генерации ключа и IV
    private static KeyAndIv generateKeyAndIv(String password) throws Exception {
        // В C# Rfc2898DeriveBytes по умолчанию использует HMACSHA1
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        // Генерируем 24 байта (16 для ключа + 8 для IV)
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), AppConsts.SALT_BINARY, 1000, 24 * 8);
        byte[] keyAndIv = factory.generateSecret(spec).getEncoded();
        
        byte[] keyBytes = new byte[16];
        byte[] ivBytes = new byte[8];
        System.arraycopy(keyAndIv, 0, keyBytes, 0, 16);
        System.arraycopy(keyAndIv, 16, ivBytes, 0, 8);

        // TripleDES использует 24-байтный ключ, но C# позволяет передать 16, дублируя первые 8
        byte[] key24Bytes = new byte[24];
        System.arraycopy(keyBytes, 0, key24Bytes, 0, 16);
        System.arraycopy(keyBytes, 0, key24Bytes, 16, 8);

        DESedeKeySpec keySpec = new DESedeKeySpec(key24Bytes);
        Key key = SecretKeyFactory.getInstance("DESede").generateSecret(keySpec);
        IvParameterSpec iv = new IvParameterSpec(ivBytes);
        return new KeyAndIv(key, iv);
    }

    public static String encryptString(String str, String password) {
        try {
            KeyAndIv keyAndIv = generateKeyAndIv(password);
            Cipher cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keyAndIv.key, keyAndIv.iv);
            byte[] plainTextBytes = str.getBytes("windows-1251");
            byte[] cipherText = cipher.doFinal(plainTextBytes);
            return Base64.encodeToString(cipherText, Base64.DEFAULT);
        } catch (Exception e) { ... }
    }
    
    // decryptString реализуется аналогично с Cipher.DECRYPT_MODE
    ```
    **Примечание:** Особое внимание нужно уделить генерации ключа. `TripleDES` требует 24-байтный ключ. `Rfc2898DeriveBytes` в .NET при запросе 16 байт для 3DES, вероятно, использует 2-ключевой 3DES. В Java нужно будет эмулировать это поведение, скорее всего, дублируя первые 8 байт ключа в конец, чтобы получить 24-байтный ключ из 16-байтного. Это требует тщательного тестирования для совместимости.

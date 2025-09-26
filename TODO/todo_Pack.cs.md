# План портирования Pack.cs

Файл `Pack.cs` — это утилитарный класс для сжатия/распаковки данных с использованием GZip и кодирования в Base64.

## Функциональность в C#

*   **`PackArray`/`UnpackArray`**: Сжимает и распаковывает массив байт с помощью `System.IO.Compression.GZipStream`.
*   **`PackString`/`UnpackString`**: Комбинированная операция. Сначала строка преобразуется в байты (в кодировке UTF-8), затем сжимается GZip, а результат кодируется в Base64. Обратная операция выполняет действия в обратном порядке.

**Назначение:** Компактное хранение или передача больших объемов данных.

## План портирования на Android

Это полезная утилита, которую необходимо портировать. Все необходимые классы для этого есть в стандартной библиотеке Java и Android SDK.

1.  **Создать `Pack.java`** в пакете `ru.neverlands.abclient.utils`.
2.  **Реализовать статические методы**:

    *   **`packArray(byte[] data)` и `unpackArray(byte[] compressedData)`**:
        ```java
        import java.io.ByteArrayInputStream;
        import java.io.ByteArrayOutputStream;
        import java.util.zip.GZIPInputStream;
        import java.util.zip.GZIPOutputStream;

        public static byte[] packArray(byte[] data) throws IOException {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream(data.length);
            try (GZIPOutputStream zipStream = new GZIPOutputStream(byteStream)) {
                zipStream.write(data);
            }
            return byteStream.toByteArray();
        }

        public static byte[] unpackArray(byte[] compressedData) throws IOException {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            try (GZIPInputStream zipStream = new GZIPInputStream(new ByteArrayInputStream(compressedData))) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zipStream.read(buffer)) != -1) {
                    byteStream.write(buffer, 0, len);
                }
            }
            return byteStream.toByteArray();
        }
        ```

    *   **`packString(String data)` и `unpackString(String data)`**:
        ```java
        import android.util.Base64;
        import java.nio.charset.StandardCharsets;

        public static String packString(String data) {
            try {
                byte[] utf8Bytes = data.getBytes(StandardCharsets.UTF_8);
                byte[] packedBytes = packArray(utf8Bytes);
                return Base64.encodeToString(packedBytes, Base64.DEFAULT);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        public static String unpackString(String data) {
            try {
                byte[] packedBytes = Base64.decode(data, Base64.DEFAULT);
                byte[] unpackedBytes = unpackArray(packedBytes);
                return new String(unpackedBytes, StandardCharsets.UTF_8);
            } catch (Exception e) { // IOException or IllegalArgumentException from Base64
                e.printStackTrace();
                return null;
            }
        }
        ```

3.  **Использование**: Заменить все вызовы `Pack.Method(...)` на `Pack.method(...)` в портируемом коде.

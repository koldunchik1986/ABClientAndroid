# План портирования Pack.cs

Файл `Pack.cs` - это статический класс с утилитами для сжатия и распаковки данных.

## Функциональность в C#

*   **Назначение**: Предоставить простые методы для сжатия/распаковки данных с использованием алгоритма GZip и кодирования/декодирования в Base64.
*   **Ключевые методы**:
    *   `PackArray(byte[] writeData)`: Сжимает массив байт с помощью `GZipStream`.
    *   `UnpackArray(byte[] compressedData)`: Распаковывает массив байт.
    *   `PackString(string data)`: Удобная обертка, которая сжимает строку и кодирует ее в Base64.
    *   `UnpackString(string data)`: Удобная обертка, которая декодирует строку из Base64 и распаковывает ее.

## Проверка на существующую реализацию в Android

- **Результат:** Функциональность не реализована.
- **Объяснение**: В проекте нет прямого аналога этого класса. Функциональность распаковки HTTP-ответов берет на себя библиотека `OkHttp`. Однако, утилит для ручного сжатия/распаковки строк или массивов байт нет.

## Решение для портирования на Android

Хотя `OkHttp` решает задачу для сетевых запросов, может возникнуть потребность в сжатии данных для других целей (например, для сохранения в `SharedPreferences` или на диске). Поэтому целесообразно создать аналогичный утилитарный класс `PackUtils.java`.

## План реализации

- [ ] **Создать файл `PackUtils.java`** в пакете `ru.neverlands.abclient.utils`.

- [ ] **Реализовать статические методы**:
    - [ ] `public static byte[] pack(byte[] data)`:
        - Использовать `java.util.zip.GZIPOutputStream` и `java.io.ByteArrayOutputStream` для сжатия данных.
    - [ ] `public static byte[] unpack(byte[] compressedData)`:
        - Использовать `java.util.zip.GZIPInputStream` и `java.io.ByteArrayInputStream` для распаковки.
    - [ ] `public static String packString(String data)`:
        - Переиспользовать `pack()` и `android.util.Base64.encodeToString()`.
    - [ ] `public static String unpackString(String data)`:
        - Переиспользовать `unpack()` и `android.util.Base64.decode()`.

- [ ] **Обновить `todo_Helpers.md`**, отметив `Pack.cs` как проанализированный.

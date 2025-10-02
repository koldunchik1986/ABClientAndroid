# План реализации дискового кэширования (abcache)

## 1. Анализ и цель

### Текущая реализация (Asset-First)
Сейчас приложение использует простую модель кэширования: при перехвате запроса в `shouldInterceptRequest` оно сначала ищет файл в папке `assets` APK. Если файл найден, он отдается `WebView`, и сетевой запрос не выполняется. Если нет — выполняется сетевой запрос, но его результат **не сохраняется** на диске для будущего использования.

### Целевая реализация (PC C# версия)
Необходимо реализовать полноценное дисковое кэширование, аналогичное оригинальному клиенту. Приложение должно сохранять полученные из сети файлы в специальную директорию (`abcache`) и при последующих запросах отдавать их оттуда, если они не устарели. Это позволит кэшировать ресурсы, которых нет в `assets`, и обновлять их без переустановки приложения.

**Папка кэша:** `context.getExternalFilesDir("abcache")`

## 2. Предлагаемое решение для Android

Нужно модифицировать логику `shouldInterceptRequest` в `MainActivity.java` и создать новый класс-хелпер для управления кэшем на диске.

### Новый класс: `DiskCacheManager.java`
-   **Расположение:** `app/src/main/java/ru/neverlands/abclient/proxy/DiskCacheManager.java`
-   **Назначение:** Инкапсулировать всю логику работы с файлами в папке `abcache`.
-   **Поля:**
    -   `private static File cacheDir;`
-   **Методы:**
    -   `public static void init(Context context)`: Инициализирует `cacheDir`.
    -   `public static byte[] get(String url)`: Проверяет наличие файла в кэше по URL, проверяет его актуальность (на первом этапе можно без проверки) и возвращает его содержимое в виде `byte[]` или `null`, если файла нет.
    -   `public static void put(String url, byte[] data)`: Сохраняет массив байт `data` в файл в папке `abcache`.
    -   `private static String getCacheKey(String url)`: Преобразует URL в безопасное имя файла (например, путем хэширования).

### Модификация `MainActivity.CustomWebViewClient`
Логика `shouldInterceptRequest` должна быть изменена на следующий приоритет:
1.  **Поиск в `assets`:** Попытаться загрузить ресурс из `assets` (сохраняем текущее поведение).
2.  **Поиск в `abcache`:** Если в `assets` нет, вызвать `DiskCacheManager.get(url)`. Если ресурс найден в дисковом кэше, отдать его.
3.  **Сетевой запрос:** Если нигде в кэше нет, выполнить `HttpURLConnection`.
4.  **Сохранение в кэш:** После успешного получения ответа из сети и перед его обработкой, вызвать `DiskCacheManager.put(url, responseData)`, чтобы сохранить свежую версию файла на диске.

## 3. План реализации

-   [ ] **Создать класс `DiskCacheManager.java`**
    -   [ ] Определить константу `private static final String TAG = "DiskCacheManager";`.
    -   [ ] Определить поле `private static File cacheDir;`.
    -   [ ] Реализовать метод `public static void init(Context context)`:
        -   `cacheDir = context.getExternalFilesDir("abcache");`
        -   Проверять, что `cacheDir != null` и создавать директорию, если ее нет (`cacheDir.mkdirs()`).
    -   [ ] Реализовать метод `private static String getCacheKey(String url)`:
        -   В качестве ключа использовать хэш от URL, например, MD5, чтобы получить валидное имя файла.
    -   [ ] Реализовать метод `public static byte[] get(String url)`:
        -   `String key = getCacheKey(url);`
        -   `File file = new File(cacheDir, key);`
        -   Проверить `file.exists()`.
        -   Если существует, прочитать файл в `byte[]` и вернуть его.
        -   Если нет, вернуть `null`.
    -   [ ] Реализовать метод `public static void put(String url, byte[] data)`:
        -   `String key = getCacheKey(url);`
        -   `File file = new File(cacheDir, key);`
        -   Записать `data` в `file` с помощью `FileOutputStream`.

-   [ ] **Интегрировать `DiskCacheManager` в `ABClientApplication`**
    -   [ ] В методе `onCreate()` класса `ABClientApplication.java` добавить вызов `DiskCacheManager.init(this);`.

-   [ ] **Модифицировать `MainActivity.CustomWebViewClient.shouldInterceptRequest`**
    -   [ ] После блока `try-catch` для чтения из `assets`, добавить новый блок:
        ```java
        // Попытка загрузки из дискового кэша
        byte[] cachedData = DiskCacheManager.get(url);
        if (cachedData != null) {
            String mimeType = getMimeTypeFromUrl(url);
            // Применяем фильтры к кэшированным данным так же, как к сетевым
            byte[] processedData = ru.neverlands.abclient.postfilter.Filter.process(url, cachedData);
            return new WebResourceResponse(mimeType, "windows-1251", new ByteArrayInputStream(processedData));
        }
        ```
    -   [ ] В блоке сетевого запроса, после успешного чтения данных из `InputStream` в `byte[] data`, добавить вызов сохранения в кэш:
        ```java
        // ... после чтения в baos и получения byte[] data ...
        // Сохраняем оригинальный, несжатый ответ сервера в кэш
        if (isCacheable(url)) { // isCacheable - уже существующий метод
            DiskCacheManager.put(url, data);
        }
        // ... дальнейшая обработка (декомпрессия, фильтры) ...
        ```

-   [ ] **Тестирование**
    -   [ ] Убедиться, что после первого входа в игру папка `abcache` наполняется файлами.
    -   [ ] Убедиться, что при повторном открытии страниц (например, инвентаря) сетевые запросы за кэшируемыми ресурсами не выполняются (можно отследить по логам).
    -   [ ] Проверить, что динамические `.php` запросы не кэшируются (если `isCacheable` настроен правильно).

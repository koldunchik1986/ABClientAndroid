# Анализ логирования в Cache.java

## Обзор

Данный документ содержит анализ существующего логирования в файле `proxy/Cache.java` и предложения по добавлению нового логирования для более детальной отладки механизма кэширования.

## Константа TAG

`private static final String TAG = "Cache";`

## Существующее логирование по функциям/методам

### Статический блок инициализации
*   **Тип:** `Log.e`
*   **Сообщение:** `"Failed to create cache directory"`
*   **Назначение:** Логирует ошибку, если директория кэша `abcache` не может быть создана.

### `getDisk(String key)`
*   **Тип:** `Log.e`
*   **Сообщение:** `"Error reading from disk cache: " + key` (с трассировкой стека `e`)
*   **Назначение:** Логирует ошибки `IOException` при чтении данных из дискового кэша.

### `storeDisk(String key, byte[] data)`
*   **Тип:** `Log.e`
*   **Сообщение:** `"Failed to create parent directories for: " + key`
*   **Назначение:** Логирует ошибку, если родительские директории для файла кэша не могут быть созданы.
*   **Тип:** `Log.e`
*   **Сообщение:** `"Error writing to disk cache: " + key` (с трассировкой стека `e`)
*   **Назначение:** Логирует ошибки `IOException` при записи данных в дисковый кэш.

## Предлагаемое дополнительное логирование

Для более полного понимания работы кэша и отладки проблем с отображением/сохранением, предлагается добавить следующие `Log.d` сообщения:

```java
// В статическом блоке инициализации
static {
    if (!cacheDir.exists()) {
        if (!cacheDir.mkdirs()) {
            Log.e(TAG, "Failed to create cache directory: " + cacheDir.getAbsolutePath());
        } else {
            Log.d(TAG, "Cache directory created: " + cacheDir.getAbsolutePath());
        }
    } else {
        Log.d(TAG, "Cache directory already exists: " + cacheDir.getAbsolutePath());
    }
}

// В getKey(String url)
private static String getKey(String url) {
    String originalUrl = url; // Сохраняем оригинальный URL для логирования
    String key = url.toLowerCase();
    if (key.startsWith("http://")) {
        key = key.substring(7);
    }
    
    int posAsk = key.lastIndexOf('?');
    if (posAsk != -1) {
        key = key.substring(0, posAsk);
    }
    
    Log.d(TAG, "Generated cache key for URL: " + originalUrl + " -> " + key);
    return key;
}

// В get(String url, boolean cacheRefresh)
public static byte[] get(String url, boolean cacheRefresh) {
    if (url == null || url.isEmpty()) {
        Log.d(TAG, "Attempt to get from cache with null or empty URL.");
        return null;
    }
    if (cacheRefresh) {
        Log.d(TAG, "Cache refresh requested for URL: " + url + ". Returning null.");
        return null;
    }

    String key = getKey(url);

    synchronized (lock) {
        byte[] data = memCache.get(key);
        if (data != null) {
            Log.d(TAG, "Memory cache HIT for key: " + key);
            return data;
        } else {
            Log.d(TAG, "Memory cache MISS for key: " + key + ". Checking disk cache.");
            data = getDisk(key);
            if (data != null) {
                Log.d(TAG, "Disk cache HIT for key: " + key + ". Storing in memory cache.");
                memCache.put(key, data);
            } else {
                Log.d(TAG, "Disk cache MISS for key: " + key + ".");
            }
        }
        return data;
    }
}

// В store(String url, byte[] data, boolean storeToDisk)
public static void store(String url, byte[] data, boolean storeToDisk) {
    if (url == null || url.isEmpty() || data == null || data.length == 0) {
        Log.d(TAG, "Attempt to store null/empty data or URL in cache.");
        return;
    }
    
    String key = getKey(url);
    Log.d(TAG, "Attempting to store data for key: " + key + " (size: " + data.length + " bytes).");

    synchronized (lock) {
        memCache.put(key, data);
        Log.d(TAG, "Data stored in memory cache for key: " + key);
    }

    if (storeToDisk) {
        Log.d(TAG, "Attempting to store data to disk for key: " + key);
        storeDisk(key, data);
    } else {
        Log.d(TAG, "Skipping disk storage for key: " + key);
    }
}

// В clear()
public static void clear() {
    synchronized (lock) {
        memCache.clear();
        Log.d(TAG, "Memory cache cleared.");
    }
    // При необходимости можно добавить логирование очистки дискового кэша
}

// В getDisk(String key)
private static byte[] getDisk(String key) {
    if (key == null || key.isEmpty()) {
        Log.d(TAG, "Attempt to get from disk cache with null or empty key.");
        return null;
    }
    
    File file = new File(cacheDir, key.replace('/', File.separatorChar));
    if (!file.exists()) {
        Log.d(TAG, "Disk cache file NOT found for key: " + key + " at " + file.getAbsolutePath());
        return null;
    }
    
    try (FileInputStream fis = new FileInputStream(file)) {
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        Log.d(TAG, "Successfully read " + data.length + " bytes from disk cache for key: " + key + " at " + file.getAbsolutePath());
        return data;
    } catch (IOException e) {
        Log.e(TAG, "Error reading from disk cache: " + key + " at " + file.getAbsolutePath(), e);
        return null;
    }
}

// В storeDisk(String key, byte[] data)
private static void storeDisk(String key, byte[] data) {
    if (key == null || key.isEmpty() || data == null || data.length == 0) {
        Log.d(TAG, "Attempt to store null/empty data or key to disk cache.");
        return;
    }
    
    try {
        File file = new File(cacheDir, key.replace('/', File.separatorChar));
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                Log.e(TAG, "Failed to create parent directories for: " + key + " at " + parent.getAbsolutePath());
                return;
            } else {
                Log.d(TAG, "Parent directories created for: " + key + " at " + parent.getAbsolutePath());
            }
        }
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
            Log.d(TAG, "Successfully wrote " + data.length + " bytes to disk cache for key: " + key + " at " + file.getAbsolutePath());
        }
    } catch (IOException e) {
        Log.e(TAG, "Error writing to disk cache: " + key + " at " + file.getAbsolutePath(), e);
    }
}
```
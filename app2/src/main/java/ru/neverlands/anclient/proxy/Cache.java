package ru.neverlands.anclient.proxy;

import ru.neverlands.anclient.utils.AppLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import ru.neverlands.anclient.ANClientApplication;

/**
 * Класс для работы с кэшем.
 * Аналог Cache.cs в оригинальном приложении.
 */
public class Cache {
    private static final String TAG = "Cache";
    private static final Object lock = new Object();
    private static final Map<String, byte[]> memCache = new HashMap<>();
    private static final File cacheDir = new File(ANClientApplication.getAppContext().getExternalFilesDir(null), "ancache");
    
    static {
        if (!cacheDir.exists()) {
            if (!cacheDir.mkdirs()) {
                AppLog.e(TAG, "Failed to create cache directory");
            }
        }
    }
    
    /**
     * Получение ключа для URL
     * @param url URL
     * @return ключ
     */
    private static String getKey(String url) {
        String key = url.toLowerCase();
        if (key.startsWith("http://")) {
            key = key.substring(7);
        }
        
        int posAsk = key.lastIndexOf('?');
        if (posAsk != -1) {
            key = key.substring(0, posAsk);
        }
        
        return key;
    }
    
    /**
     * Получение данных из кэша
     * @param url URL
     * @param cacheRefresh флаг обновления кэша
     * @return данные или null, если данных нет или требуется обновление
     */
    public static byte[] get(String url, boolean cacheRefresh) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        String key = getKey(url);

        synchronized (lock) {
            byte[] data = memCache.get(key);
            if (data == null) {
                data = getDisk(key, cacheRefresh);
                if (data != null) {
                    memCache.put(key, data);
                }
            }
            return data;
        }
    }
    
    /**
     * Сохранение данных в кэш
     * @param url URL
     * @param data данные
     * @param storeToDisk флаг сохранения на диск
     */
    public static void store(String url, byte[] data, boolean storeToDisk) {
        if (url == null || url.isEmpty() || data == null || data.length == 0) {
            return;
        }
        
        String key = getKey(url);

        synchronized (lock) {
            memCache.put(key, data);
        }

        if (storeToDisk) {
            storeDisk(key, data);
        }
    }
    
    /**
     * Очистка кэша
     */
    public static void clear() {
        synchronized (lock) {
            memCache.clear();
        }
    }
    
    /**
     * Получение данных с диска
     * @param key ключ
     * @param cacheRefresh если true — не читать с диска (только MemCache)
     * @return данные или null при ошибке
     */
    private static byte[] getDisk(String key, boolean cacheRefresh) {
        if (key == null || key.isEmpty() || cacheRefresh) {
            return null;
        }
        if (key.contains(".js")) {
            return null;
        }
        
        File file = new File(cacheDir, key.replace('/', File.separatorChar));
        if (!file.exists()) {
            return null;
        }
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            return data;
        } catch (IOException e) {
            AppLog.e(TAG, "Error reading from disk cache: " + key, e);
            return null;
        }
    }
    
    /**
     * Сохранение данных на диск
     * @param key ключ
     * @param data данные
     */
    private static void storeDisk(String key, byte[] data) {
        if (key == null || key.isEmpty() || data == null || data.length == 0) {
            return;
        }
        
        try {
            File file = new File(cacheDir, key.replace('/', File.separatorChar));
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs()) {
                    AppLog.e(TAG, "Failed to create parent directories for: " + key);
                    return;
                }
            }
            
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
            }
        } catch (IOException e) {
            AppLog.e(TAG, "Error writing to disk cache: " + key, e);
        }
    }
}

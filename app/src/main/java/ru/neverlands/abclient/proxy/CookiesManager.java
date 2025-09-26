package ru.neverlands.abclient.proxy;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import ru.neverlands.abclient.utils.DataManager;

/**
 * Менеджер куки.
 * Аналог CookiesManager.cs в оригинальном приложении.
 */
public class CookiesManager {
    private static final String TAG = "CookiesManager";
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final Map<String, Map<String, String>> cookieStore = new HashMap<>();
    
    /**
     * Получение куки для хоста
     * @param host хост
     * @return строка с куки
     */
    public static String obtain(String host) {
        if (host == null || host.isEmpty()) {
            return "";
        }
        
        String normalizedHost = normalizeHost(host);
        StringBuilder result = new StringBuilder();
        
        try {
            lock.readLock().lock();
            Map<String, String> hostCookies = cookieStore.get(normalizedHost);
            if (hostCookies != null) {
                boolean first = true;
                for (Map.Entry<String, String> entry : hostCookies.entrySet()) {
                    if (!first) {
                        result.append("; ");
                    }
                    result.append(entry.getKey()).append("=").append(entry.getValue());
                    first = false;
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        
        return result.toString();
    }
    
    /**
     * Назначение куки для хоста
     * @param host хост
     * @param cookieHeader заголовок Set-Cookie
     */
    public static void assign(String host, String cookieHeader) {
        if (host == null || host.isEmpty() || cookieHeader == null || cookieHeader.isEmpty()) {
            return;
        }
        
        String normalizedHost = normalizeHost(host);
        
        try {
            lock.writeLock().lock();
            Map<String, String> hostCookies = cookieStore.get(normalizedHost);
            if (hostCookies == null) {
                hostCookies = new HashMap<>();
                cookieStore.put(normalizedHost, hostCookies);
            }
            
            // Парсинг заголовка Set-Cookie
            String[] parts = cookieHeader.split(";");
            if (parts.length > 0) {
                String[] nameValue = parts[0].split("=", 2);
                if (nameValue.length == 2) {
                    String name = nameValue[0].trim();
                    String value = nameValue[1].trim();
                    
                    // Проверка на удаление куки
                    if (value.isEmpty() || "deleted".equalsIgnoreCase(value)) {
                        hostCookies.remove(name);
                    } else {
                        hostCookies.put(name, value);
                    }
                }
            }
            
            // Сохранение куки в файл
            saveCookies();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Очистка всех куки
     */
    public static void clear() {
        try {
            lock.writeLock().lock();
            cookieStore.clear();
            saveCookies();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Загрузка куки из файла
     */
    public static void load() {
        try {
            lock.writeLock().lock();
            cookieStore.clear();
            
            String cookiesData = DataManager.readFileToString("cookies.dat");
            if (cookiesData != null && !cookiesData.isEmpty()) {
                String[] lines = cookiesData.split("\\n");
                for (String line : lines) {
                    String[] parts = line.split("\\|", 3);
                    if (parts.length == 3) {
                        String host = parts[0];
                        String name = parts[1];
                        String value = parts[2];
                        
                        Map<String, String> hostCookies = cookieStore.get(host);
                        if (hostCookies == null) {
                            hostCookies = new HashMap<>();
                            cookieStore.put(host, hostCookies);
                        }
                        
                        hostCookies.put(name, value);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading cookies", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Сохранение куки в файл
     */
    private static void saveCookies() {
        try {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Map<String, String>> hostEntry : cookieStore.entrySet()) {
                String host = hostEntry.getKey();
                Map<String, String> hostCookies = hostEntry.getValue();
                
                for (Map.Entry<String, String> cookieEntry : hostCookies.entrySet()) {
                    sb.append(host).append("|")
                      .append(cookieEntry.getKey()).append("|")
                      .append(cookieEntry.getValue()).append("\n");
                }
            }
            
            DataManager.writeStringToFile("cookies.dat", sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error saving cookies", e);
        }
    }
    
    /**
     * Нормализация имени хоста
     * @param host имя хоста
     * @return нормализованное имя хоста
     */
    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String h = host.trim().toLowerCase();
        if (h.equals("forum.neverlands.ru")) {
            return "www.neverlands.ru";
        }
        // Do not force-prepend www for unrelated hosts; keep exact host like PC version
        return h;
    }
}

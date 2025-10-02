package ru.neverlands.abclient.proxy;

import android.webkit.CookieManager;
import android.util.Log; // Импортируем класс Log

/**
 * Менеджер куки.
 * Аналог CookiesManager.cs в оригинальном приложении.
 * Эта версия действует как обертка для android.webkit.CookieManager,
 * чтобы обеспечить единый источник правды для cookies между WebView и HTTP-клиентами.
 */
public class CookiesManager {

    private static final String TAG = "CookiesManager"; // Добавляем константу TAG

    /**
     * Получение куки для хоста из системного CookieManager.
     * @param host хост
     * @return строка с куки
     */
    public static String obtain(String host) {
        if (host == null || host.isEmpty()) {
            Log.d(TAG, "Attempt to obtain cookies with null or empty host. Returning empty string.");
            return "";
        }
        String normalizedHost = normalizeHost(host);
        String cookies = CookieManager.getInstance().getCookie(normalizedHost);
        Log.d(TAG, "Obtained cookies for host: " + normalizedHost + " -> " + (cookies != null ? cookies : "[No cookies]"));
        return cookies;
    }

    /**
     * Назначение куки для хоста в системном CookieManager.
     * @param host хост
     * @param cookieHeader заголовок Set-Cookie
     */
    public static void assign(String host, String cookieHeader) {
        if (host == null || host.isEmpty() || cookieHeader == null || cookieHeader.isEmpty()) {
            Log.d(TAG, "Attempt to assign cookies with null/empty host or cookieHeader. Skipping.");
            return;
        }
        String normalizedHost = normalizeHost(host);
        CookieManager.getInstance().setCookie(normalizedHost, cookieHeader);
        Log.d(TAG, "Assigned cookie for host: " + normalizedHost + " -> " + cookieHeader);
    }

    /**
     * Очистка всех куки в системном CookieManager.
     */
    public static void clear() {
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        Log.d(TAG, "All cookies cleared and flushed.");
    }

    /**
     * Загрузка куки. (Не используется, т.к. CookieManager управляет своим состоянием).
     */
    public static void load() {
        Log.d(TAG, "load() called (No-op as CookieManager manages its state).");
        // No-op
    }

    /**
     * Сохранение куки. (Не используется, т.к. CookieManager управляет своим состоянием).
     */
    private static void saveCookies() {
        Log.d(TAG, "saveCookies() called (No-op as CookieManager manages its state).");
        // No-op
    }

    /**
     * Нормализация имени хоста.
     * @param host имя хоста
     * @return нормализованное имя хоста
     */
    private static String normalizeHost(String host) {
        if (host == null) {
            Log.d(TAG, "normalizeHost called with null host. Returning empty string.");
            return "";
        }
        String h = host.trim().toLowerCase();
        if (h.equals("forum.neverlands.ru")) {
            Log.d(TAG, "Normalizing host 'forum.neverlands.ru' to 'www.neverlands.ru'");
            return "www.neverlands.ru";
        }
        Log.d(TAG, "Normalized host: " + host + " -> " + h);
        return h;
    }
}

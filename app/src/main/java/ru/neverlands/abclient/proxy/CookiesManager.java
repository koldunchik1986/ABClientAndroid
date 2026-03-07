package ru.neverlands.abclient.proxy;

import android.webkit.CookieManager;
import android.webkit.ValueCallback;

/**
 * Менеджер куки.
 * Аналог CookiesManager.cs в оригинальном приложении.
 * Эта версия действует как обертка для android.webkit.CookieManager,
 * чтобы обеспечить единый источник правды для cookies между WebView и HTTP-клиентами.
 */
public class CookiesManager {

    /**
     * Получение куки для хоста из системного CookieManager.
     * @param host хост
     * @return строка с куки
     */
    public static String obtain(String host) {
        if (host == null || host.isEmpty()) {
            return "";
        }
        try {
            return CookieManager.getInstance().getCookie(normalizeHost(host));
        } catch (Throwable t) {
            android.util.Log.e("CookiesManager", "obtain: CookieManager unavailable", t);
            return "";
        }
    }

    /**
     * Назначение куки для хоста в системном CookieManager.
     * @param host хост
     * @param cookieHeader заголовок Set-Cookie
     */
    public static void assign(String host, String cookieHeader) {
        if (host == null || host.isEmpty() || cookieHeader == null || cookieHeader.isEmpty()) {
            return;
        }
        try {
            CookieManager.getInstance().setCookie(normalizeHost(host), cookieHeader);
        } catch (Throwable t) {
            android.util.Log.e("CookiesManager", "assign: CookieManager unavailable", t);
        }
    }

    /**
     * Очистка всех куки в системном CookieManager.
     */
    public static void clear() {
        clear(null);
    }

    /**
     * Asynchronously clears all WebView cookies and invokes callback when done.
     */
    public static void clear(ValueCallback<Boolean> callback) {
        try {
            CookieManager manager = CookieManager.getInstance();
            manager.removeAllCookies(value -> {
                try {
                    manager.flush();
                } catch (Throwable flushError) {
                    android.util.Log.e("CookiesManager", "clear: flush failed", flushError);
                }
                if (callback != null) {
                    callback.onReceiveValue(value);
                }
            });
        } catch (Throwable t) {
            android.util.Log.e("CookiesManager", "clear: CookieManager unavailable, continue without WebView cookie reset", t);
            if (callback != null) {
                callback.onReceiveValue(false);
            }
        }
    }

    /**
     * Загрузка куки. (Не используется, т.к. CookieManager управляет своим состоянием).
     */
    public static void load() {
        // No-op
    }

    /**
     * Сохранение куки. (Не используется, т.к. CookieManager управляет своим состоянием).
     */
    private static void saveCookies() {
        // No-op
    }

    /**
     * Нормализация имени хоста.
     * @param host имя хоста
     * @return нормализованное имя хоста
     */
    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        // Логика нормализации из старой версии сохранена на всякий случай.
        String h = host.trim().toLowerCase();
        if (h.equals("forum.neverlands.ru")) {
            return "neverlands.ru";
        }
        return h;
    }
}

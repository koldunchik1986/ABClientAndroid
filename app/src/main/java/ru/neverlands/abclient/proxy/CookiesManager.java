package ru.neverlands.abclient.proxy;

import android.webkit.CookieManager;
import android.webkit.ValueCallback;

import ru.neverlands.abclient.utils.AppVars;

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
            String normalized = normalizeHost(host);
            CookieManager manager = CookieManager.getInstance();
            String cookie = manager.getCookie(toCookieUrl(normalized));
            if (cookie != null && !cookie.isEmpty()) {
                return cookie;
            }
            if ("neverlands.ru".equals(normalized)) {
                cookie = manager.getCookie("http://www.neverlands.ru/");
            } else if ("www.neverlands.ru".equals(normalized)) {
                cookie = manager.getCookie("http://neverlands.ru/");
            }
            return cookie == null ? "" : cookie;
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
            String normalized = normalizeHost(host);

            if ("www.neverlands.ru".equals(normalized) || "neverlands.ru".equals(normalized)) {
                if (cookieHeader.toLowerCase().startsWith("nevernick=")) {
                    String encodedNick = cookieHeader.substring("NeverNick=".length());
                    int semiPos = encodedNick.indexOf(';');
                    if (semiPos != -1) encodedNick = encodedNick.substring(0, semiPos);
                    try {
                        String nick = java.net.URLDecoder.decode(encodedNick, "windows-1251");
                        if (AppVars.Profile != null && !nick.isEmpty()) {
                            if (!nick.equalsIgnoreCase(AppVars.Profile.UserNick)) {
                                android.util.Log.e("CookiesManager", "NeverNick mismatch: expected="
                                        + AppVars.Profile.UserNick + ", got=" + nick);
                            }
                            AppVars.Profile.UserNick = nick;
                        }
                    } catch (Exception ignored) {}
                }
            }

            CookieManager manager = CookieManager.getInstance();
            manager.setCookie(toCookieUrl(normalized), cookieHeader);
            if ("neverlands.ru".equals(normalized)) {
                manager.setCookie("http://www.neverlands.ru/", cookieHeader);
            } else if ("www.neverlands.ru".equals(normalized)) {
                manager.setCookie("http://neverlands.ru/", cookieHeader);
            }
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
            return "www.neverlands.ru";
        }
        return h;
    }

    private static String toCookieUrl(String host) {
        if (host == null || host.isEmpty()) {
            return "http://neverlands.ru/";
        }
        return "http://" + host + "/";
    }
}

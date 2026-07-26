package ru.neverlands.anclient.proxy;


import ru.neverlands.anclient.utils.AppLog;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;

import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.GameServerUrls;

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
            if (GameServerUrls.isNeverlandsHost(normalized)) {
                for (String cookieUrl : GameServerUrls.cookieUrls()) {
                    cookie = manager.getCookie(cookieUrl);
                    if (cookie != null && !cookie.isEmpty()) {
                        return cookie;
                    }
                }
            }
            if ("neverlands.ru".equals(normalized)) {
                cookie = manager.getCookie("http://www.neverlands.ru/");
            } else if ("www.neverlands.ru".equals(normalized)) {
                cookie = manager.getCookie("http://neverlands.ru/");
            }
            return cookie == null ? "" : cookie;
        } catch (Throwable t) {
            AppLog.e("CookiesManager", "obtain: CookieManager unavailable", t);
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
                                AppLog.e("CookiesManager", "NeverNick mismatch: expected="
                                        + AppVars.Profile.UserNick + ", got=" + nick);
                            }
                            AppVars.Profile.UserNick = nick;
                        }
                    } catch (Exception e) {
                        AppLog.d("CookiesManager", "NeverNick decode failed: " + e.getClass().getSimpleName());
                    }
                }
            }

            CookieManager manager = CookieManager.getInstance();
            String hostCookieHeader = toHostOnlyCookieHeader(cookieHeader);
            if (hostCookieHeader.isEmpty()) {
                return;
            }
            manager.setCookie(toCookieUrl(normalized), hostCookieHeader);
            if (GameServerUrls.isNeverlandsHost(normalized)) {
                for (String cookieUrl : GameServerUrls.cookieUrls()) {
                    manager.setCookie(cookieUrl, hostCookieHeader);
                }
            }
            manager.flush();
            AppLog.d("CookiesManager", "assign: mirrored host-safe cookie name=" + cookieName(hostCookieHeader)
                    + ", sourceHost=" + normalized);
        } catch (Throwable t) {
            AppLog.e("CookiesManager", "assign: CookieManager unavailable", t);
        }
    }

    /**
     * Приводит серверный {@code Set-Cookie} к виду, который хранится в WebView-CookieManager.
     *
     * <p><b>Поведение 1:1 с ПК-версией</b> (см. {@code ANClient/ANProxy/CookiesManager.cs} →
     * {@code Assign(...)} и {@code ANClient/ANProxy/CookiePack.cs}).</p>
     *
     * В C# из заголовка берётся строго часть до первой {@code ';'}:
     * <pre>
     *   var posemi = data.IndexOf(';', poseq);
     *   var svalue = (posemi == -1) ? data.Substring(poseq + 1)
     *                               : data.Substring(poseq + 1, posemi - poseq - 1);
     * </pre>
     * а {@code CookiePack} хранит только пары {@code Name}/{@code Value} и отдаёт их как
     * {@code name=value; name2=value2}. То есть ПК-клиент <b>физически не переносит</b>
     * атрибуты cookie.
     *
     * <p><b>Почему это критично для сессии.</b> Прежняя Java-реализация отбрасывала только
     * {@code Domain}, а {@code Expires}, {@code Max-Age}, {@code Secure} и {@code SameSite}
     * передавала дальше. Android CookieManager честно их исполняет, поэтому:</p>
     * <ul>
     *   <li>{@code Expires}/{@code Max-Age} в прошлом (или короткий срок) — cookie удаляется,
     *       сессия «слетает» прямо во время игры;</li>
     *   <li>{@code Secure} — cookie перестаёт отправляться по {@code http://},
     *       а игра работает именно по cleartext HTTP;</li>
     *   <li>{@code SameSite} — cookie может не уйти при переходах между фреймами/хостами.</li>
     * </ul>
     *
     * Поэтому здесь сохраняется только {@code name=value} и добавляется {@code Path=/},
     * чтобы cookie была видна всем путям игрового хоста.
     *
     * @param cookieHeader сырое значение заголовка {@code Set-Cookie}
     * @return {@code name=value; Path=/} либо пустая строка, если заголовок некорректен
     */
    private static String toHostOnlyCookieHeader(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.trim().isEmpty()) {
            return "";
        }
        // Как в C#: всё, что идёт после первой ';', является атрибутами и отбрасывается.
        int semiPos = cookieHeader.indexOf(';');
        String nameValue = (semiPos == -1 ? cookieHeader : cookieHeader.substring(0, semiPos)).trim();
        if (nameValue.isEmpty() || nameValue.indexOf('=') <= 0) {
            return "";
        }
        return nameValue + "; Path=/";
    }

    private static String cookieName(String cookieHeader) {
        if (cookieHeader == null) {
            return "";
        }
        int eq = cookieHeader.indexOf('=');
        return eq <= 0 ? "" : cookieHeader.substring(0, eq).trim();
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
                    AppLog.e("CookiesManager", "clear: flush failed", flushError);
                }
                if (callback != null) {
                    callback.onReceiveValue(value);
                }
            });
        } catch (Throwable t) {
            AppLog.e("CookiesManager", "clear: CookieManager unavailable, continue without WebView cookie reset", t);
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

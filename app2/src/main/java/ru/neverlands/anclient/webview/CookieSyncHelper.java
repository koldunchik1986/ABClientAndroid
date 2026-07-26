package ru.neverlands.anclient.webview;

import android.webkit.CookieManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.GameServerUrls;

/**
 * Синхронизация игровых cookie между хостами (`neverlands.ru` / `www.neverlands.ru` / серверы DE-KZ).
 *
 * <p>Выделено из {@code MainActivity} в рамках D6: блок был полностью изолирован
 * (зависел только от {@link CookieManager} и {@link GameServerUrls}), не обращался
 * к состоянию Activity и занимал в god-классе ~176 строк.</p>
 *
 * <p>Назначение:</p>
 * <ul>
 *   <li>после auth-flow через один host устранить рассинхрон host-only cookie;</li>
 *   <li>гарантировать валидную сессию для room/chat-фреймов.</li>
 * </ul>
 *
 * <p>Логика перенесена без изменений — это структурный рефакторинг, поведение и формат логов
 * ({@code AUTH_COOKIE_SYNC}) сохранены.</p>
 */
public final class CookieSyncHelper {

    private static final String TAG = "CookieSyncHelper";

    private CookieSyncHelper() {
    }

    /**
     * Применяет cookie, полученные при авторизации, ко всем игровым хостам и синхронизирует их.
     *
     * @param cookies список cookie из auth-flow (может быть null/пустым — тогда выполняется только синхронизация)
     * @param stage   метка этапа для логов ({@code lastCookies_apply}, {@code session_relogin}, ...)
     */
    public static void applyAuthCookiesToWebView(List<java.net.HttpCookie> cookies, String stage) {
        CookieManager cookieManager = CookieManager.getInstance();
        if (cookies != null && !cookies.isEmpty()) {
            // Оставляем только последнее значение для каждого имени cookie.
            List<java.net.HttpCookie> filteredCookies = new ArrayList<>();
            Set<String> names = new HashSet<>();
            for (int i = cookies.size() - 1; i >= 0; i--) {
                java.net.HttpCookie cookie = cookies.get(i);
                if (!names.contains(cookie.getName())) {
                    filteredCookies.add(0, cookie);
                    names.add(cookie.getName());
                }
            }

            List<String> cookieUrls = GameServerUrls.cookieUrls();
            for (java.net.HttpCookie cookie : filteredCookies) {
                StringBuilder cookieValue = new StringBuilder()
                        .append(cookie.getName())
                        .append("=")
                        .append(cookie.getValue() == null ? "" : cookie.getValue())
                        .append("; Path=/");
                if (cookie.getSecure()) {
                    cookieValue.append("; Secure");
                }
                for (String cookieUrl : cookieUrls) {
                    cookieManager.setCookie(cookieUrl, cookieValue.toString());
                }
            }
            cookieManager.flush();
            AppLog.d(TAG, "AUTH_COOKIE_SYNC: applied " + stage + " names=" + names);
        }
        syncSessionCookiesAcrossHosts(cookieManager, "after_" + stage);
    }

    /**
     * Синхронизирует cookie между игровыми хостами.
     *
     * <p>Зависимости:</p>
     * <ul>
     *   <li>{@link CookieManager} — общее WebView-хранилище cookie;</li>
     *   <li>{@link #mirrorCookieHeaderToHost(CookieManager, String, String)} — перенос cookie-пар в host без сессии.</li>
     * </ul>
     */
    public static void syncSessionCookiesAcrossHosts(CookieManager manager, String stage) {
        if (manager == null) {
            return;
        }
        List<String> cookieUrls = GameServerUrls.cookieUrls();
        boolean changed = false;
        StringBuilder before = new StringBuilder();
        for (String sourceUrl : cookieUrls) {
            String sourceCookie = manager.getCookie(sourceUrl);
            if (before.length() > 0) {
                before.append("; ");
            }
            before.append(sourceUrl).append("=").append(summarizeCookieHeaderNames(sourceCookie));
            if (sourceCookie == null || sourceCookie.isEmpty()) {
                continue;
            }
            for (String targetUrl : cookieUrls) {
                if (!sourceUrl.equalsIgnoreCase(targetUrl)) {
                    changed |= mirrorCookieHeaderToHost(manager, sourceCookie, targetUrl);
                }
            }
        }
        AppLog.d(TAG, "AUTH_COOKIE_SYNC[" + stage + "]: " + before);

        if (changed) {
            manager.flush();
            StringBuilder after = new StringBuilder();
            for (String cookieUrl : cookieUrls) {
                if (after.length() > 0) {
                    after.append("; ");
                }
                after.append(cookieUrl).append("=").append(summarizeCookieHeaderNames(manager.getCookie(cookieUrl)));
            }
            AppLog.d(TAG, "AUTH_COOKIE_SYNC[" + stage + "]: mirrored " + after);
        }
    }

    /**
     * Копирует {@code name=value} cookie-пары из source-header в target-host.
     *
     * <p>Важно: cookie-атрибуты ({@code Path}, {@code Domain}, {@code Expires}, ...) не копируются —
     * переносится только полезная сессионная часть.</p>
     */
    private static boolean mirrorCookieHeaderToHost(CookieManager manager, String sourceHeader, String targetUrl) {
        if (sourceHeader == null || sourceHeader.isEmpty() || targetUrl == null || targetUrl.isEmpty()) {
            return false;
        }
        boolean changed = false;
        String[] parts = sourceHeader.split(";");
        for (String rawPart : parts) {
            if (rawPart == null) {
                continue;
            }
            String part = rawPart.trim();
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = part.substring(0, eq).trim();
            if (name.isEmpty() || isCookieAttributeName(name)) {
                continue;
            }
            String value = part.substring(eq + 1).trim();
            manager.setCookie(targetUrl, name + "=" + value + "; Path=/");
            changed = true;
        }
        return changed;
    }

    /**
     * Формирует краткую сводку cookie ({@code count + names}) для логов {@code AUTH_COOKIE_SYNC}.
     */
    private static String summarizeCookieHeaderNames(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return "empty";
        }
        ArrayList<String> names = new ArrayList<>();
        String[] parts = cookieHeader.split(";");
        for (String rawPart : parts) {
            if (rawPart == null) {
                continue;
            }
            String part = rawPart.trim();
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = part.substring(0, eq).trim();
            if (!name.isEmpty() && !isCookieAttributeName(name)) {
                names.add(name);
            }
        }
        return "count=" + names.size() + ", names=" + names;
    }

    /**
     * Возвращает {@code true}, если token является cookie-атрибутом, а не именем cookie.
     */
    private static boolean isCookieAttributeName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return "path".equals(lower)
                || "domain".equals(lower)
                || "expires".equals(lower)
                || "max-age".equals(lower)
                || "secure".equals(lower)
                || "httponly".equals(lower)
                || "samesite".equals(lower);
    }
}

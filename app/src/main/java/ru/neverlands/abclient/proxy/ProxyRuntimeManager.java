package ru.neverlands.abclient.proxy;

import android.content.Context;
import android.util.Log;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import ru.neverlands.abclient.model.UserConfig;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.WebViewProxyHelper;

/**
 * Единая orchestration-точка proxy runtime для Android клиента.
 *
 * Назначение:
 * - поднимает локальный loopback proxy (127.0.0.1:port),
 * - определяет режим DIRECT/UPSTREAM по профилю,
 * - отдает единый proxy endpoint для OkHttp/HttpURLConnection/WebView.
 *
 * Зависимости:
 * - {@link LocalHttpProxyServer}: низкоуровневый listener + forwarding.
 * - {@link UserConfig}: профильные поля прокси (`DoProxy`, `UseProxy`, `ProxyAddress`, `ProxyUserName`, `ProxyPassword`).
 * - {@link WebViewProxyHelper}: привязка WebView к loopback proxy.
 * - {@link AppVars}: публикация активного локального порта (`LocalProxyPort`, `LocalProxyAddress`).
 */
public final class ProxyRuntimeManager {
    private static final String TAG = "ProxyRuntimeManager";
    private static final Object LOCK = new Object();
    private static final int LOG_DEDUP_WINDOW_MS = 5_000;

    private static LocalHttpProxyServer proxyServer;
    private static ProxyUpstreamSettings activeUpstream = ProxyUpstreamSettings.disabled();
    private static int activePort = -1;
    private static String activeSignature = "";
    private static String lastStartError = "";

    private ProxyRuntimeManager() {
    }

    /**
     * Гарантирует поднятый proxy runtime для текущего профиля.
     *
     * Поведение:
     * - если конфиг не изменился и runtime уже активен — повторный старт не делается;
     * - если конфиг изменился — runtime перезапускается с новым режимом.
     *
     * @param context контекст приложения (нужен для корректного применения WebView proxy override).
     * @param profile активный профиль с proxy-настройками.
     * @return true если runtime активен после вызова.
     */
    public static boolean ensureStarted(Context context, UserConfig profile) {
        synchronized (LOCK) {
            lastStartError = "";
            String signature = buildSignature(profile);
            Log.i(TAG, "PROXY_BOOT: ensureStarted, doProxy="
                    + (profile != null && profile.DoProxy)
                    + ", useProxy=" + (profile != null && profile.UseProxy)
                    + ", proxyAddress=" + (profile == null ? "" : safeLower(profile.ProxyAddress))
                    + ", running=" + (proxyServer != null && activePort > 0));
            if (proxyServer != null && activePort > 0 && signature.equals(activeSignature)) {
                Log.i(TAG, "PROXY_BOOT: reuse active runtime, port=" + activePort);
                return true;
            }

            stopLocked(false);
            ProxyUpstreamSettings upstream = buildUpstreamSettings(profile);
            if (!upstream.validationError.isEmpty()) {
                lastStartError = upstream.validationError;
                ProxyLogDeduper.warn(
                        TAG,
                        "invalid_upstream_config",
                        "PROXY_FAIL: invalid upstream config: " + upstream.validationError,
                        null,
                        LOG_DEDUP_WINDOW_MS
                );
                return false;
            }
            LocalHttpProxyServer newServer = new LocalHttpProxyServer(AppVars.LocalProxyPort, upstream);
            try {
                int bound = newServer.start();
                proxyServer = newServer;
                activePort = bound;
                activeUpstream = upstream;
                activeSignature = signature;
                AppVars.LocalProxyAddress = "127.0.0.1";
                AppVars.LocalProxyPort = bound;

                Log.i(TAG, "PROXY_BOOT: started, host=127.0.0.1, port=" + bound
                        + ", mode=" + (upstream.enabled ? "UPSTREAM" : "DIRECT"));
                if (upstream.enabled) {
                    Log.i(TAG, "PROXY_UPSTREAM: host=" + upstream.host
                            + ", port=" + upstream.port
                            + ", auth=" + (upstream.basicAuthHeader != null && !upstream.basicAuthHeader.isEmpty()));
                    Log.i(TAG, "PROXY_AUTH: upstream basic auth enabled="
                            + (upstream.basicAuthHeader != null && !upstream.basicAuthHeader.isEmpty()));
                } else {
                    Log.i(TAG, "PROXY_AUTH: upstream auth disabled (direct mode)");
                }

                applyWebViewProxyOverrideLocked(context);
                return true;
            } catch (Exception e) {
                lastStartError = "start failed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                ProxyLogDeduper.warn(TAG, "start_failed", "PROXY_FAIL: start failed", e, LOG_DEDUP_WINDOW_MS);
                stopLocked(true);
                return false;
            }
        }
    }

    /**
     * Останавливает proxy runtime и (опционально) снимает WebView proxy override.
     *
     * Зависимости:
     * - должен вызываться при полном завершении сессии/приложения,
     *   чтобы очистить socket и не оставлять stale-override.
     *
     * @param clearWebViewProxy true — очистить override в WebView.
     */
    public static void stop(boolean clearWebViewProxy) {
        synchronized (LOCK) {
            Log.i(TAG, "PROXY_BOOT: stop requested, clearWebViewProxy=" + clearWebViewProxy);
            stopLocked(clearWebViewProxy);
        }
    }

    /**
     * Возвращает java.net.Proxy для прямых HttpURLConnection/OkHttp вызовов.
     *
     * Зависимости:
     * - используется в сетевом слое (`NetworkClient`, `WebViewRequestInterceptor`, `NeverApi`, `WebAppInterface`).
     *
     * @return HTTP proxy на localhost:activePort или null, если runtime не поднят.
     */
    public static Proxy getActiveJavaProxyOrNull() {
        synchronized (LOCK) {
            if (activePort <= 0 || proxyServer == null) {
                return null;
            }
            Log.d(TAG, "PROXY_BINDING: java.net.Proxy endpoint=127.0.0.1:" + activePort);
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", activePort));
        }
    }

    /**
     * Возвращает сигнатуру текущего proxy runtime состояния.
     * Используется сетевым слоем для безопасного auto-rebuild клиента при смене конфига.
     */
    public static String getRuntimeSignature() {
        synchronized (LOCK) {
            if (activePort <= 0 || proxyServer == null) {
                return "proxy:stopped";
            }
            return "proxy:running:port=" + activePort + ":mode=" + (activeUpstream.enabled ? "upstream" : "direct")
                    + ":up=" + (activeUpstream.host == null ? "" : activeUpstream.host + ":" + activeUpstream.port);
        }
    }

    /**
     * Принудительно применяет proxy override для WebView на текущий localhost runtime.
     */
    public static void applyWebViewProxyOverride(Context context) {
        synchronized (LOCK) {
            Log.i(TAG, "PROXY_BINDING: applyWebViewProxyOverride requested, running=" + (activePort > 0));
            applyWebViewProxyOverrideLocked(context);
        }
    }

    public static boolean isRunning() {
        synchronized (LOCK) {
            return proxyServer != null && activePort > 0;
        }
    }

    public static int getActivePort() {
        synchronized (LOCK) {
            return activePort;
        }
    }

    /**
     * Возвращает `true`, если для текущего профиля запрещен прямой egress.
     *
     * Назначение:
     * - when `DoProxy/UseProxy=true` любой direct fallback должен блокироваться,
     *   чтобы реальный IP клиента не ушел на игровой сервер при сбое прокси-контура.
     *
     * Зависимости:
     * - используется в `NetworkClient`, `WebViewRequestInterceptor`, `NeverApi`,
     *   `WebAppInterface`, `MainActivity` при выборе маршрута соединения.
     */
    public static boolean isStrictProxyRequiredForCurrentProfile() {
        synchronized (LOCK) {
            return isStrictProxyRequired(AppVars.Profile);
        }
    }

    /**
     * Возвращает текст последней ошибки запуска proxy runtime.
     *
     * Назначение:
     * - используется UI/диагностикой, чтобы показать причину fail-fast без повторного парсинга логов.
     *
     * Зависимости:
     * - заполняется только в `ensureStarted(...)` при ошибках валидации/старта.
     */
    public static String getLastStartError() {
        synchronized (LOCK) {
            return lastStartError;
        }
    }

    private static void applyWebViewProxyOverrideLocked(Context context) {
        if (context == null || activePort <= 0 || proxyServer == null) {
            return;
        }
        WebViewProxyHelper.setWebViewProxy("127.0.0.1", activePort, () ->
                Log.i(TAG, "PROXY_BINDING: WebView override applied to 127.0.0.1:" + activePort));
    }

    private static void stopLocked(boolean clearWebViewProxy) {
        if (proxyServer != null) {
            proxyServer.stop();
            proxyServer = null;
        }
        activePort = -1;
        activeUpstream = ProxyUpstreamSettings.disabled();
        activeSignature = "";

        if (clearWebViewProxy) {
            try {
                WebViewProxyHelper.clearWebViewProxy();
                Log.i(TAG, "PROXY_BINDING: WebView override cleared");
            } catch (Throwable t) {
                ProxyLogDeduper.warn(TAG, "clear_webview_override", "PROXY_FAIL: clear WebView override failed", t, LOG_DEDUP_WINDOW_MS);
            }
        }
        Log.i(TAG, "PROXY_BOOT: runtime stopped");
    }

    private static String buildSignature(UserConfig profile) {
        ProxyUpstreamSettings s = buildUpstreamSettings(profile);
        return "upstream=" + s.enabled
                + ";host=" + safeLower(s.host)
                + ";port=" + s.port
                + ";auth=" + (s.basicAuthHeader == null ? "" : s.basicAuthHeader);
    }

    private static String safeLower(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).trim();
    }

    private static ProxyUpstreamSettings buildUpstreamSettings(UserConfig profile) {
        if (profile == null) {
            return ProxyUpstreamSettings.disabled();
        }

        // Для обратной совместимости Android-профилей поддерживаем оба флага:
        // - DoProxy (основной, как в C#),
        // - UseProxy (legacy UI-поле ранних Android-профилей).
        boolean enabled = profile.DoProxy || profile.UseProxy;
        if (!enabled) {
            return ProxyUpstreamSettings.disabled();
        }

        String rawAddress = profile.ProxyAddress == null ? "" : profile.ProxyAddress.trim();
        if (rawAddress.isEmpty()) {
            return ProxyUpstreamSettings.invalid("ProxyAddress is empty while proxy is enabled");
        }

        ProxyAddressParseResult parseResult = parseProxyAddress(rawAddress);
        if (!parseResult.errorMessage.isEmpty()) {
            return ProxyUpstreamSettings.invalid(parseResult.errorMessage);
        }

        String basicAuthHeader = "";
        String user = profile.ProxyUserName == null ? "" : profile.ProxyUserName.trim();
        String pass = profile.ProxyPassword == null ? "" : profile.ProxyPassword.trim();
        if (!user.isEmpty() || !pass.isEmpty()) {
            String pair = user + ":" + pass;
            String encoded = android.util.Base64.encodeToString(pair.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
            basicAuthHeader = "Basic " + encoded;
        }

        return new ProxyUpstreamSettings(true, parseResult.host, parseResult.port, basicAuthHeader, "");
    }

    private static boolean isStrictProxyRequired(UserConfig profile) {
        if (profile == null) {
            return false;
        }
        return profile.DoProxy || profile.UseProxy;
    }

    /**
     * Строго валидирует и разбирает `ProxyAddress` в формате `host[:port]` или `[ipv6][:port]`.
     *
     * Назначение:
     * - fail-fast по ошибочной конфигурации upstream вместо скрытого fallback на `:8080`;
     * - единый формат для C#-совместимого режима `DoProxy=true`.
     *
     * Зависимости:
     * - вызывается из `buildUpstreamSettings(...)`;
     * - ошибка поднимается в `ensureStarted(...)` через `validationError`.
     */
    private static ProxyAddressParseResult parseProxyAddress(String rawAddress) {
        if (rawAddress == null || rawAddress.trim().isEmpty()) {
            return ProxyAddressParseResult.error("ProxyAddress is empty");
        }
        String value = rawAddress.trim();
        String host;
        int port = 8080;

        if (value.startsWith("[")) {
            int closeIdx = value.indexOf(']');
            if (closeIdx <= 1) {
                return ProxyAddressParseResult.error("IPv6 host must be in [host] format");
            }
            host = value.substring(1, closeIdx).trim();
            String tail = value.substring(closeIdx + 1).trim();
            if (!tail.isEmpty()) {
                if (!tail.startsWith(":")) {
                    return ProxyAddressParseResult.error("Unexpected suffix after IPv6 host: " + tail);
                }
                String portText = tail.substring(1).trim();
                if (portText.isEmpty()) {
                    return ProxyAddressParseResult.error("Port is empty");
                }
                try {
                    port = Integer.parseInt(portText);
                } catch (NumberFormatException e) {
                    return ProxyAddressParseResult.error("Port is not numeric: " + portText);
                }
            }
        } else {
            int firstColon = value.indexOf(':');
            int lastColon = value.lastIndexOf(':');
            if (firstColon != -1 && firstColon != lastColon) {
                return ProxyAddressParseResult.error("IPv6 without brackets is not supported, use [ipv6]:port");
            }
            if (lastColon > 0) {
                host = value.substring(0, lastColon).trim();
                String portText = value.substring(lastColon + 1).trim();
                if (portText.isEmpty()) {
                    return ProxyAddressParseResult.error("Port is empty");
                }
                try {
                    port = Integer.parseInt(portText);
                } catch (NumberFormatException e) {
                    return ProxyAddressParseResult.error("Port is not numeric: " + portText);
                }
            } else {
                host = value;
            }
        }

        if (host == null || host.trim().isEmpty()) {
            return ProxyAddressParseResult.error("Host is empty");
        }
        if (host.contains(" ")) {
            return ProxyAddressParseResult.error("Host contains spaces");
        }
        if (port < 1 || port > 65535) {
            return ProxyAddressParseResult.error("Port out of range: " + port);
        }
        return ProxyAddressParseResult.ok(host.trim(), port);
    }

    /**
     * DTO upstream-настроек для локального прокси-сервера.
     */
    static final class ProxyUpstreamSettings {
        final boolean enabled;
        final String host;
        final int port;
        final String basicAuthHeader;
        final String validationError;

        ProxyUpstreamSettings(boolean enabled, String host, int port, String basicAuthHeader, String validationError) {
            this.enabled = enabled;
            this.host = host;
            this.port = port;
            this.basicAuthHeader = basicAuthHeader;
            this.validationError = validationError == null ? "" : validationError;
        }

        static ProxyUpstreamSettings disabled() {
            return new ProxyUpstreamSettings(false, "", 0, "", "");
        }

        static ProxyUpstreamSettings invalid(String validationError) {
            return new ProxyUpstreamSettings(false, "", 0, "", validationError);
        }
    }

    /**
     * Результат валидации `ProxyAddress`.
     */
    private static final class ProxyAddressParseResult {
        final String host;
        final int port;
        final String errorMessage;

        ProxyAddressParseResult(String host, int port, String errorMessage) {
            this.host = host;
            this.port = port;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }

        static ProxyAddressParseResult ok(String host, int port) {
            return new ProxyAddressParseResult(host, port, "");
        }

        static ProxyAddressParseResult error(String errorMessage) {
            return new ProxyAddressParseResult("", 0, errorMessage);
        }
    }
}

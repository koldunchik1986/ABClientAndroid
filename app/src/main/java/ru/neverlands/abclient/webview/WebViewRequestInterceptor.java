package ru.neverlands.abclient.webview;

import android.net.Uri;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import ru.neverlands.abclient.postfilter.Filter;
import ru.neverlands.abclient.proxy.CookiesManager;
import ru.neverlands.abclient.proxy.ProxyRuntimeManager;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.RuntimeNetTrace;

public class WebViewRequestInterceptor {
    private static final String TAG = "WebViewInterceptor";
    private static final Pattern CHAT_TIME_PATTERN = Pattern.compile("chattime[^>]*>\\s*&nbsp;\\s*(\\d{1,2}):(\\d{2}):(\\d{2})\\s*&nbsp;", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVER_DATE_PATTERN = Pattern.compile(
            "serverDate\\s*=\\s*new\\s+Date\\((\\d{4})\\s*,\\s*(\\d{1,2})\\s*,\\s*(\\d{1,2})\\s*,\\s*(\\d{1,2})\\s*,\\s*(\\d{1,2})\\s*,\\s*(\\d{1,2})\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SERVER_TIME_DIV_PATTERN = Pattern.compile(
            "id\\s*=\\s*['\"]?serverTime['\"]?[^>]*>\\s*(\\d{1,2}):(\\d{2}):(\\d{2})\\s*<",
            Pattern.CASE_INSENSITIVE
    );
    /**
     * Детектирует наличие формы завершения {@code FEND} в HTML конца боя.
     * Зависимость: используется только в {@link #logCaptchaFlowMarkers(String, String, String)}.
     */
    private static final Pattern FEND_FORM_PATTERN = Pattern.compile(
            "<form[^>]*(name\\s*=\\s*['\"]?FEND['\"]?|id\\s*=\\s*['\"]?FEND['\"]?)[^>]*>",
            Pattern.CASE_INSENSITIVE
    );
    /**
     * Извлекает {@code action} из формы {@code FEND} для диагностики.
     * Зависимость: используется в {@link #logCaptchaFlowMarkers(String, String, String)}.
     */
    private static final Pattern FEND_ACTION_PATTERN = Pattern.compile(
            "<form[^>]*(name\\s*=\\s*['\"]?FEND['\"]?|id\\s*=\\s*['\"]?FEND['\"]?)[^>]*action\\s*=\\s*['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE
    );
    /**
     * Детектирует поле ввода капчи ({@code input[name=code]}) на странице завершения.
     * Зависимость: используется в {@link #logCaptchaFlowMarkers(String, String, String)}.
     */
    private static final Pattern CODE_INPUT_PATTERN = Pattern.compile(
            "<input[^>]*name\\s*=\\s*['\"]?code['\"]?[^>]*>",
            Pattern.CASE_INSENSITIVE
    );
    /**
     * Извлекает серверный cooldown из JS: SetNeverTimer(286).
     * Используется только для logcat-диагностики.
     */
    private static final Pattern SET_NEVER_TIMER_PATTERN = Pattern.compile(
            "setnevertimer\\s*\\(\\s*(\\d+)\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );
    /**
     * Извлекает timeout из RESO-пакета: @[0,[2,286]].
     */
    private static final Pattern RESO_TIMEOUT_PATTERN = Pattern.compile(
            "@\\[0,\\[(\\d+),(\\d+)\\]\\]",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Определяет, нужно ли перехватывать данный URL.
     * Перехватываем только URL, для которых Filter реально что-то делает.
     *
     * ВАЖНО: НЕ перехватываем frameset-страницы (main.php и ch.php без параметров),
     * т.к. WebView не может обработать <frameset> из WebResourceResponse.
     */
    private static boolean shouldIntercept(String urlString) {
        // main.php без параметров — это frameset, НЕ перехватываем
        if (urlString.endsWith("/main.php") || urlString.equals("http://neverlands.ru/main.php")) {
            return false;
        }
        // ch.php без параметров — тоже frameset
        if (urlString.endsWith("/ch.php") || urlString.equals("http://neverlands.ru/ch.php")) {
            return false;
        }
        // .php страницы с параметрами — обрабатываются Filter
        if (urlString.contains(".php")) return true;
        // .js файлы — обрабатываются Filter (счётчики, game.js, etc.)
        if (urlString.contains(".js")) return true;
        // index.cgi, pinfo.cgi, pbots.cgi
        if (urlString.contains(".cgi")) return true;
        // Форум
        if (urlString.contains("forum.neverlands.ru")) return true;
        // Всё остальное (картинки, css) — не перехватываем
        return false;
    }

    /**
     * Проверяет, относится ли host к игровым доменам Neverlands.
     */
    private static boolean isNeverlandsHost(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return "neverlands.ru".equals(lower) || lower.endsWith(".neverlands.ru");
    }

    /**
     * Быстрый фильтр внешних трекеров/счетчиков, которые часто вызывают connect timeout.
     */
    private static boolean isKnownTrackerHost(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.contains("mail.ru")
                || lower.contains("yadro.ru")
                || lower.contains("mc.yandex.ru")
                || lower.contains("google-analytics.com");
    }

    /**
     * Главная точка перехвата HTTP GET-запросов WebView для neverlands.ru.
     *
     * Зависимости:
     * - {@link #shouldIntercept(String)} для отсечения URL, где post-filter не нужен.
     * - {@link Filter#process(String, byte[])} для серверного контент-фильтра.
     * - {@link CookiesManager} и {@link CookieManager} для проброса cookie между WebView и HttpURLConnection.
     * - Вспомогательные методы: {@link #logCaptchaFlowMarkers(String, String, String)},
     *   {@link #buildAutoboiFightJsShim()}, {@link #updateServerTimeFromChat(String, Map)},
     *   {@link #updateServerTimeFromBut(String, Map)}.
     *
     * Назначение:
     * - Управлять сетевым потоком (блок трекеров, декомпрессия, фильтрация, кэш капчи, синхронизация времени)
     *   до передачи ответа в WebView.
     */
    public static WebResourceResponse intercept(WebResourceRequest request) {
        try {
            Uri uri = request.getUrl();
            String urlString = uri.toString();
            String host = uri.getHost();

            if (host == null) {
                return null;
            }

            if (!isNeverlandsHost(host)) {
                // Short-circuit slow external counters that often time out inside WebView
                if (isKnownTrackerHost(host)) {
                    Log.d(TAG, "Blocking tracker host: " + host);
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
                // Внешние подресурсы (скрипты/счетчики/пиксели) из неигровых доменов
                // блокируем централизованно, чтобы не получать ложные timeout в авто-функциях.
                if (!request.isForMainFrame()) {
                    Log.d(TAG, "Blocking external subresource host: " + host + ", url=" + urlString);
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }

            // Skip POST requests — shouldInterceptRequest only provides GET reliably
            if (!request.getMethod().equalsIgnoreCase("GET")) {
                return null;
            }

            // Перехватываем только URL, для которых Filter нужен
            if (!shouldIntercept(urlString)) {
                return null;
            }

            if (urlString.contains("/modules/code/code.php")
                    && ru.neverlands.abclient.utils.AppVars.IsFightCaptchaDialogVisible) {
                byte[] cachedCaptchaBytes = ru.neverlands.abclient.utils.AppVars.LastFightCaptchaImageBytes;
                String cachedCaptchaUrl = ru.neverlands.abclient.utils.AppVars.LastFightCaptchaImageUrl;
                long cachedCaptchaAtMs = ru.neverlands.abclient.utils.AppVars.LastFightCaptchaImageAtMs;
                long cachedAgeMs = cachedCaptchaAtMs > 0L
                        ? (System.currentTimeMillis() - cachedCaptchaAtMs)
                        : Long.MAX_VALUE;
                if (cachedCaptchaBytes != null
                        && cachedCaptchaBytes.length > 0
                        && urlString.equals(cachedCaptchaUrl)
                        && cachedAgeMs >= 0
                        && cachedAgeMs <= 30_000L) {
                    Log.d(TAG, "Serving cached fight captcha bytes: " + cachedCaptchaBytes.length
                            + " for " + urlString + ", ageMs=" + cachedAgeMs);
                    return new WebResourceResponse(
                            "image/png",
                            null,
                            new ByteArrayInputStream(cachedCaptchaBytes)
                    );
                }
            }

            // В автобое упрощаем: не тянем тяжёлые бойовые скрипты, которые не нужны нашему минимальному фрейму
            // Визуальный режим AutoBoi:
            // не подменяем fight_v*.js и не глушим hpmp.js/game.js,
            // чтобы верхний боевой фрейм продолжал отрисовываться (без белого экрана).
            //
            // Зависимости:
            // - отображение боя на странице зависит от оригинальных боевых JS;
            // - логика авто-ударов остаётся через FightViewModel + submitAutoBattleActionToWebView(...).
            if (ru.neverlands.abclient.utils.AppVars.Profile != null
                    && ru.neverlands.abclient.utils.AppVars.Profile.LezDoAutoboi
                    && ru.neverlands.abclient.utils.AppVars.Autoboi == ru.neverlands.abclient.model.AutoboiState.AutoboiOn
                    && (urlString.contains("fight_v")
                    || urlString.contains("hpmp.js")
                    || urlString.contains("game.js"))) {
                Log.d(TAG, "Autoboi visual frame mode: keep original fight JS: " + urlString);
            }

            // Запоминаем URL картинки капчи завершения боя для fallback-детекта в MainPhp.
            // Логируем только факт исходящего запроса капчи.
            // Поля LastFightCaptchaImage* обновляются ниже только после получения bytes.
            if (urlString.contains("/modules/code/code.php")) {
                Log.d(TAG, "Captured fight captcha image URL (request): " + urlString);
            }

            Log.d(TAG, "Intercepting: " + urlString);

            // Обновление списка игроков чата: добавляем timestamp для защиты от кеша.
            if (urlString.contains("ch.php?lo=1")) {
                urlString += "&" + System.currentTimeMillis();
            }

            URL url = new URL(urlString);
            java.net.Proxy activeProxy = ProxyRuntimeManager.getActiveJavaProxyOrNull();
            if (activeProxy == null && ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile()) {
                Log.e(TAG, "PROXY_FAIL: strict proxy enabled and runtime proxy unavailable, blocking direct WebView request: " + urlString);
                RuntimeNetTrace.push("PROXY_FAIL", "cmd=block scope=webview strict=1 route=direct url=" + trimUrlForTrace(urlString));
                return buildStrictProxyBlockedResponse();
            }
            if (isPinfoOrForumUrl(urlString)) {
                String routeMode = activeProxy != null ? "local-proxy" : "direct";
                Log.i(TAG, "PROXY_ROUTE: internal page via " + routeMode + ", url=" + urlString);
                RuntimeNetTrace.push("NAV", "target=pinfo_forum route=" + routeMode + " url=" + trimUrlForTrace(urlString));
            }
            Log.d(TAG, "PROXY_BINDING: interceptor openConnection via "
                    + (activeProxy != null ? "local proxy" : "direct")
                    + ", url=" + urlString);
            RuntimeNetTrace.push("HTTP_OPEN", "route=" + (activeProxy != null ? "proxy" : "direct") + " url=" + trimUrlForTrace(urlString));
            HttpURLConnection connection = activeProxy != null
                    ? (HttpURLConnection) url.openConnection(activeProxy)
                    : (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(20_000);

            // Список игроков чата — отключаем кеширование на уровне HTTP.
            if (urlString.contains("ch.php?lo=1")) {
                connection.setUseCaches(false);
                connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
                connection.setRequestProperty("Pragma", "no-cache");
                connection.setRequestProperty("Expires", "0");
            }
            connection.setRequestProperty("Accept-Encoding", "identity");

            // Cookies: prefer WebView CookieManager (has actual session cookies)
            String wvCookie = getCookieWithHostFallback(urlString, url.getHost());
            String effectiveCookie = null;
            if (wvCookie != null && !wvCookie.isEmpty()) {
                connection.setRequestProperty("Cookie", wvCookie);
                effectiveCookie = wvCookie;
            } else {
                String cookie = CookiesManager.obtain(url.getHost());
                if (cookie != null && !cookie.isEmpty()) {
                    connection.setRequestProperty("Cookie", cookie);
                    effectiveCookie = cookie;
                }
            }

            // Forward original request headers (Referer, etc.)
            java.util.Map<String, String> reqHeaders = request.getRequestHeaders();
            String reqUserAgent = null;
            String reqReferer = null;
            if (reqHeaders != null) {
                for (java.util.Map.Entry<String, String> entry : reqHeaders.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (key != null) {
                        if ("User-Agent".equalsIgnoreCase(key)) {
                            reqUserAgent = value;
                        } else if ("Referer".equalsIgnoreCase(key)) {
                            reqReferer = value;
                        }
                    }
                    // Don't override Cookie or Accept-Encoding we already set
                    if (!"Cookie".equalsIgnoreCase(key) && !"Accept-Encoding".equalsIgnoreCase(key)) {
                        connection.setRequestProperty(key, value);
                    }
                }
            }
            if (reqUserAgent == null || reqUserAgent.isEmpty()) {
                reqUserAgent = AppVars.BROWSER_USER_AGENT;
                connection.setRequestProperty("User-Agent", reqUserAgent);
            }
            if (isChatEndpoint(urlString) && (reqReferer == null || reqReferer.isEmpty())) {
                reqReferer = "http://neverlands.ru/main.php";
                connection.setRequestProperty("Referer", reqReferer);
            }
            if (isChatEndpoint(urlString)) {
                Log.d(TAG, "CHAT_REQ_HEADERS: url=" + urlString
                        + ", ua=" + (reqUserAgent == null ? "" : reqUserAgent)
                        + ", referer=" + (reqReferer == null ? "" : reqReferer)
                        + ", cookieSummary=" + summarizeCookieHeader(effectiveCookie));
            }

            int code = connection.getResponseCode();
            Log.d(TAG, "Response code: " + code + " for " + urlString);
            RuntimeNetTrace.push("HTTP_CODE", "code=" + code + " url=" + trimUrlForTrace(urlString));

            // Read response body, handling gzip if server sends it despite identity request
            String contentEncoding = connection.getContentEncoding();
            Log.d(TAG, "Content-Encoding: " + contentEncoding + " for " + urlString);
            InputStream responseStream = code >= 400 && connection.getErrorStream() != null
                    ? connection.getErrorStream()
                    : connection.getInputStream();
            byte[] bytes = readAllBytes(responseStream);
            Log.d(TAG, "Raw bytes: " + bytes.length + " for " + urlString);

            // Сохраняем свежие байты картинки капчи завершения боя для отображения в popup без повторного HTTP-запроса.
            // Консистентно обновляем URL+bytes+timestamp в один момент (после чтения response body),
            // чтобы popup капчи не получил "новый URL + старое изображение".
            if (urlString.contains("/modules/code/code.php")) {
                ru.neverlands.abclient.utils.AppVars.LastFightCaptchaImageBytes = bytes;
                ru.neverlands.abclient.utils.AppVars.LastFightCaptchaImageUrl = urlString;
                ru.neverlands.abclient.utils.AppVars.LastFightCaptchaImageAtMs = System.currentTimeMillis();
                Log.d(TAG, "Captured fight captcha image BYTES: " + bytes.length + " for " + urlString);
            }

            // Log first bytes for diagnostics
            if (bytes.length > 0) {
                StringBuilder hex = new StringBuilder();
                for (int i = 0; i < Math.min(32, bytes.length); i++) {
                    hex.append(String.format("%02x ", bytes[i] & 0xff));
                }
                Log.d(TAG, "First bytes HEX: " + hex.toString() + " for " + urlString);
            }

            if ("gzip".equalsIgnoreCase(contentEncoding) && bytes.length > 2
                    && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b) {
                Log.d(TAG, "Decompressing gzip for " + urlString);
                bytes = decompressGzip(bytes);
                Log.d(TAG, "After gzip: " + bytes.length + " bytes for " + urlString);
            }
            // Also detect gzip magic even if Content-Encoding not set
            if (bytes.length > 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b) {
                Log.d(TAG, "Detected gzip magic without Content-Encoding header, decompressing for " + urlString);
                bytes = decompressGzip(bytes);
                Log.d(TAG, "After gzip: " + bytes.length + " bytes for " + urlString);
            }

            // Capture Set-Cookie (case-insensitive)
            Map<String, List<String>> headers = connection.getHeaderFields();
            // Log all response headers for diagnostics
            for (Map.Entry<String, List<String>> hEntry : headers.entrySet()) {
                Log.d(TAG, "Header [" + hEntry.getKey() + "] = " + hEntry.getValue() + " for " + urlString);
            }
            List<String> setCookies = getHeaderIgnoreCase(headers, "Set-Cookie");
            if (setCookies != null) {
                for (String sc : setCookies) {
                    CookiesManager.assign(url.getHost(), sc);
                    CookieManager.getInstance().setCookie(url.getProtocol() + "://" + url.getHost(), sc);
                }
                CookieManager.getInstance().flush();
            }

            connection.disconnect();

            // Log first 200 chars of decoded HTML for diagnostics
            String preview = new String(bytes, Charset.forName("windows-1251"));
            Log.d(TAG, "HTML preview (" + urlString + "): " + preview.substring(0, Math.min(200, preview.length())));
            logCaptchaFlowMarkers("raw", urlString, preview);

            // Попытка синхронизировать серверное время по времени в чате + HTTP Date.
            if (urlString.contains("ch.php") && urlString.contains("show=1")) {
                updateServerTimeFromChat(preview, headers);
            }
            // Попытка синхронизировать серверное время по but.php (кнопки чата).
            if (urlString.contains("/ch/but.php")) {
                updateServerTimeFromBut(preview, headers);
            }

            // Handle "Cookie..." transitional page by re-requesting once with cookies
            if (preview.contains("Cookie...")) {
                java.net.Proxy secondProxy = ProxyRuntimeManager.getActiveJavaProxyOrNull();
                if (secondProxy == null && ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile()) {
                    Log.e(TAG, "PROXY_FAIL: strict proxy enabled and runtime proxy unavailable, blocking direct WebView retry: " + urlString);
                    RuntimeNetTrace.push("PROXY_FAIL", "cmd=block scope=webview_retry strict=1 route=direct url=" + trimUrlForTrace(urlString));
                    return buildStrictProxyBlockedResponse();
                }
                Log.d(TAG, "PROXY_BINDING: interceptor retry openConnection via "
                        + (secondProxy != null ? "local proxy" : "direct")
                        + ", url=" + urlString);
                RuntimeNetTrace.push("HTTP_RETRY", "route=" + (secondProxy != null ? "proxy" : "direct") + " url=" + trimUrlForTrace(urlString));
                HttpURLConnection second = secondProxy != null
                        ? (HttpURLConnection) url.openConnection(secondProxy)
                        : (HttpURLConnection) url.openConnection();
                second.setInstanceFollowRedirects(true);
                second.setRequestMethod("GET");
                second.setDoInput(true);
                second.setConnectTimeout(12_000);
                second.setReadTimeout(20_000);
                second.setRequestProperty("Accept-Encoding", "identity");
                String cookie2 = getCookieWithHostFallback(urlString, url.getHost());
                if (cookie2 == null || cookie2.isEmpty()) {
                    cookie2 = CookiesManager.obtain(url.getHost());
                }
                if (cookie2 != null && !cookie2.isEmpty()) {
                    second.setRequestProperty("Cookie", cookie2);
                }
                int code2 = second.getResponseCode();
                String contentEncoding2 = second.getContentEncoding();
                InputStream stream2 = code2 >= 400 && second.getErrorStream() != null
                        ? second.getErrorStream()
                        : second.getInputStream();
                byte[] secondBytes = readAllBytes(stream2);
                if ("gzip".equalsIgnoreCase(contentEncoding2) && secondBytes.length > 2
                        && (secondBytes[0] & 0xff) == 0x1f && (secondBytes[1] & 0xff) == 0x8b) {
                    secondBytes = decompressGzip(secondBytes);
                }
                Map<String, List<String>> h2 = second.getHeaderFields();
                List<String> sc2 = getHeaderIgnoreCase(h2, "Set-Cookie");
                if (sc2 != null) {
                    for (String sc : sc2) {
                        CookiesManager.assign(url.getHost(), sc);
                        CookieManager.getInstance().setCookie(url.getProtocol() + "://" + url.getHost(), sc);
                    }
                    CookieManager.getInstance().flush();
                }
                second.disconnect();
                bytes = secondBytes;
            }

            Log.d(TAG, "Calling Filter.process for " + urlString + " (" + bytes.length + " bytes)");
            byte[] processed = Filter.process(ru.neverlands.abclient.utils.AppVars.getContext(), urlString, bytes);
            if (processed == null) {
                Log.d(TAG, "Filter.process returned null, using original bytes for " + urlString);
                processed = bytes;
            } else {
                Log.d(TAG, "Filter.process returned " + processed.length + " bytes for " + urlString);
            }

            // Get Content-Type (case-insensitive)
            String contentType = null;
            List<String> ctList = getHeaderIgnoreCase(headers, "Content-Type");
            if (ctList != null && !ctList.isEmpty()) {
                contentType = ctList.get(0);
            }
            if (contentType == null || contentType.isEmpty()) {
                contentType = "text/html; charset=windows-1251";
            }

            // Inject JS fixes into the processed body
            processed = ru.neverlands.abclient.utils.HtmlUtils.injectJsFix(processed, urlString, contentType);

            // Log first 200 chars of processed HTML
            String processedPreview = new String(processed, Charset.forName("windows-1251"));
            Log.d(TAG, "Processed preview (" + urlString + "): " + processedPreview.substring(0, Math.min(200, processedPreview.length())));
            logCaptchaFlowMarkers("processed", urlString, processedPreview);
            // Диагностика ответов ch_refr: наличие add_msg/set_lmid.
            if (urlString.contains("ch.php") && urlString.contains("show=1")) {
                boolean hasAdd = processedPreview.contains("add_msg");
                boolean hasLmid = processedPreview.contains("set_lmid");
                if (!hasAdd || !hasLmid) {
                    String full = processedPreview;
                    if (processed.length > 0 && processed.length < 20000) {
                        full = new String(processed, Charset.forName("windows-1251"));
                        hasAdd = full.contains("add_msg");
                        hasLmid = full.contains("set_lmid");
                    }
                }
                Log.d(TAG, "ch_refr response markers: add_msg=" + hasAdd + ", set_lmid=" + hasLmid);
            }

            WebResourceResponse response = new WebResourceResponse(
                    getMime(contentType),
                    getCharset(contentType),
                    new ByteArrayInputStream(processed)
            );

            if (urlString.contains("ch.php?lo=1")) {
                response.setResponseHeaders(java.util.Collections.singletonMap("Cache-Control", "no-cache"));
            }

            Log.d(TAG, "Intercepted OK: " + urlString + " (" + processed.length + " bytes, " + contentType + ")");
            return response;
        } catch (Exception e) {
            Log.e(TAG, "Intercept failed: " + request.getUrl(), e);
            RuntimeNetTrace.push("HTTP_FAIL", "url=" + trimUrlForTrace(String.valueOf(request.getUrl())) + " error=" + e.getClass().getSimpleName());
            if (ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile()) {
                return buildStrictProxyBlockedResponse();
            }
            return null;
        }
    }

    /**
     * Возвращает короткий synthetic-ответ для случая, когда strict proxy включен,
     * но локальный proxy runtime недоступен.
     *
     * Назначение:
     * - не отдавать WebView управление в direct network path (`return null`),
     *   который может привести к утечке реального IP при включенном прокси.
     */
    private static WebResourceResponse buildStrictProxyBlockedResponse() {
        return new WebResourceResponse(
                "text/plain",
                "utf-8",
                new ByteArrayInputStream("proxy runtime unavailable".getBytes(Charset.forName("UTF-8")))
        );
    }

    private static boolean isPinfoOrForumUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("pinfo.php")
                || lower.contains("/forum")
                || lower.contains("forum.php")
                || lower.contains("forums.php");
    }

    private static String trimUrlForTrace(String url) {
        if (url == null) {
            return "";
        }
        if (url.length() <= 90) {
            return url;
        }
        return url.substring(0, 87) + "...";
    }

    private static boolean isChatEndpoint(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("/ch.php?")
                || lower.contains("/ch/msg.php")
                || lower.contains("/ch/but.php");
    }

    private static boolean hasSessionCookieTokens(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return false;
        }
        String lower = cookieHeader.toLowerCase(Locale.ROOT);
        return lower.contains("phpsessid=")
                || lower.contains("neverpuid=")
                || lower.contains("neverhash=")
                || lower.contains("nevercode=");
    }

    private static String mergeCookieHeaders(String primary, String secondary) {
        java.util.LinkedHashMap<String, String> byName = new java.util.LinkedHashMap<>();
        addCookiesByName(byName, primary);
        addCookiesByName(byName, secondary);
        if (byName.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : byName.entrySet()) {
            if (out.length() > 0) {
                out.append("; ");
            }
            out.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return out.toString();
    }

    private static void addCookiesByName(Map<String, String> target, String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return;
        }
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
            String value = part.substring(eq + 1).trim();
            if (!name.isEmpty()) {
                target.put(name, value);
            }
        }
    }

    private static String summarizeCookieHeader(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return "empty";
        }
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
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
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return "count=" + names.size() + ", names=" + names;
    }

    /**
     * Возвращает cookie для текущего URL и делает fallback между www/non-www host.
     * Это устраняет рассинхрон сессии, когда auth прошел на одном host, а chat/frame
     * запрашивается на sibling-host.
     */
    private static String getCookieWithHostFallback(String urlString, String host) {
        CookieManager manager = CookieManager.getInstance();
        String cookie = manager.getCookie(urlString);
        String lowerHost = host == null ? "" : host.toLowerCase(Locale.ROOT);
        String siblingCookie = null;
        if ("neverlands.ru".equals(lowerHost)) {
            siblingCookie = manager.getCookie("http://www.neverlands.ru/");
        } else if ("www.neverlands.ru".equals(lowerHost)) {
            siblingCookie = manager.getCookie("http://neverlands.ru/");
        }

        if (cookie == null || cookie.isEmpty()) {
            return siblingCookie;
        }
        if (siblingCookie == null || siblingCookie.isEmpty()) {
            return cookie;
        }

        boolean cookieHasSession = hasSessionCookieTokens(cookie);
        boolean siblingHasSession = hasSessionCookieTokens(siblingCookie);
        if (!cookieHasSession && siblingHasSession) {
            return mergeCookieHeaders(siblingCookie, cookie);
        }
        return mergeCookieHeaders(cookie, siblingCookie);
    }

    /**
     * Пишет компактные отпечатки captcha/fight-finish для сырого и обработанного ответа.
     *
     * Зависимости:
     * - Вызывается из {@code intercept(...)} на обоих этапах: до/после обработки Filter.
     * - Использует regex-шаблоны {@code FEND_FORM_PATTERN}, {@code FEND_ACTION_PATTERN}, {@code CODE_INPUT_PATTERN}.
     * - Использует URL/body-маркеры: {@code fight_ty}, {@code fkey.js}, {@code /modules/code/code.php}
     *   и финальный query ({@code get_id=61&act=7}).
     *
     * Цель:
     * - Держать runtime-логи короткими, но достаточными для классификации переходов состояния сервера
     *   и корреляции с решениями завершения в MainPhp.
     */
    private static void logCaptchaFlowMarkers(String stage, String url, String body) {
        if (body == null || body.isEmpty()) {
            return;
        }
        String urlLower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        String lower = body.toLowerCase(Locale.ROOT);
        boolean hasFightTy = lower.contains("var fight_ty");
        boolean hasFend = FEND_FORM_PATTERN.matcher(body).find();
        boolean hasCodeInput = CODE_INPUT_PATTERN.matcher(body).find();
        boolean hasFkeyJs = lower.contains("js/fkey.js") || lower.contains("d.fend.code.value");
        boolean hasCaptchaImage = lower.contains("/modules/code/code.php");
        boolean hasFinishAct7 = lower.contains("get_id=61") && lower.contains("act=7");
        boolean hasResoPacket = lower.contains("reso@[");
        boolean hasFishUrl = urlLower.contains("get_id=55") || urlLower.contains("act=4");
        boolean hasFightUrl = urlLower.contains("get_id=61") || urlLower.contains("act=7");
        boolean hasFishKeywords = lower.contains("рыбалк")
                || lower.contains("ловить")
                || lower.contains("приманк")
                || lower.contains("умелка")
                || lower.contains("потери за рыбалку");
        boolean hasFishResult = hasResoPacket && (hasFishKeywords || lower.contains("масса:"));
        int timeoutSec = extractServerTimeoutSeconds(body);
        boolean hasTimeoutMarker = timeoutSec > 0;

        boolean interesting = hasFightTy || hasFend || hasCodeInput || hasFkeyJs || hasCaptchaImage
                || hasFinishAct7 || hasResoPacket || hasFishUrl || hasFightUrl || hasTimeoutMarker
                || (url != null && (url.contains("main.php") || url.contains("get_id=61")
                || url.contains("get_id=55") || url.contains("/modules/code/code.php") || url.contains("fkey.js")));
        if (!interesting) {
            return;
        }

        String fendAction = "";
        Matcher actionMatcher = FEND_ACTION_PATTERN.matcher(body);
        if (actionMatcher.find()) {
            fendAction = actionMatcher.group(2);
        }

        String responseState = classifyServerResponse(
                hasFightTy,
                hasFightUrl,
                hasFinishAct7,
                hasFend,
                hasCodeInput,
                hasFkeyJs,
                hasCaptchaImage,
                hasFishUrl,
                hasFishKeywords,
                hasFishResult,
                hasTimeoutMarker
        );

        Log.d(TAG, "[SERVER_FLOW][" + stage + "] state=" + responseState
                + ", url=" + (url == null ? "" : url)
                + ", hasFightTy=" + hasFightTy
                + ", hasFEND=" + hasFend
                + ", hasCodeInput=" + hasCodeInput
                + ", hasFkeyJs=" + hasFkeyJs
                + ", hasCaptchaImage=" + hasCaptchaImage
                + ", hasFinishAct7=" + hasFinishAct7
                + ", hasResoPacket=" + hasResoPacket
                + ", hasFishUrl=" + hasFishUrl
                + ", hasFishKeywords=" + hasFishKeywords
                + ", hasFishResult=" + hasFishResult
                + ", timeoutSec=" + timeoutSec
                + ", fendAction=" + fendAction);
    }

    /**
     * Выделяет MIME-тип из Content-Type (без параметров).
     *
     * Зависимости:
     * - Используется при создании {@link WebResourceResponse}.
     *
     * Назначение:
     * - Корректно отдать WebView только основную часть типа контента, например {@code text/html}.
     */
    /**
     * Классифицирует тип серверного ответа по стабильным маркерам.
     *
     * Зависимости:
     * - входные маркеры формируются в {@link #logCaptchaFlowMarkers(String, String, String)};
     * - результат используется только в logcat (`[SERVER_FLOW]`), без влияния на поведение клиента.
     */
    private static String classifyServerResponse(boolean hasFightTy,
                                                 boolean hasFightUrl,
                                                 boolean hasFinishAct7,
                                                 boolean hasFend,
                                                 boolean hasCodeInput,
                                                 boolean hasFkeyJs,
                                                 boolean hasCaptchaImage,
                                                 boolean hasFishUrl,
                                                 boolean hasFishKeywords,
                                                 boolean hasFishResult,
                                                 boolean hasTimeoutMarker) {
        boolean hasCaptchaChallenge = hasFend || hasCodeInput || hasFkeyJs || hasCaptchaImage;
        if (hasCaptchaChallenge && (hasFishUrl || hasFishKeywords)) {
            return "FISH_CAPTCHA";
        }
        if (hasCaptchaChallenge) {
            return "FIGHT_CAPTCHA";
        }
        if (hasFightTy || hasFightUrl || hasFinishAct7) {
            return "FIGHT";
        }
        if (hasFishResult) {
            return "FISH_RESULT";
        }
        if (hasFishUrl || hasFishKeywords) {
            return "FISH";
        }
        if (hasTimeoutMarker) {
            return "TIMEOUT";
        }
        return "COMMON";
    }

    /**
     * Извлекает серверный timeout/cooldown в секундах из тела ответа.
     *
     * Форматы:
     * - JS: {@code SetNeverTimer(286)}
     * - RESO: {@code @[0,[2,286]]}
     *
     * Зависимости:
     * - {@link #SET_NEVER_TIMER_PATTERN}
     * - {@link #RESO_TIMEOUT_PATTERN}
     */
    private static int extractServerTimeoutSeconds(String body) {
        int timeoutSec = 0;

        Matcher jsMatcher = SET_NEVER_TIMER_PATTERN.matcher(body);
        if (jsMatcher.find()) {
            try {
                timeoutSec = Integer.parseInt(jsMatcher.group(1));
            } catch (NumberFormatException ignored) {
                // Диагностический парсер не должен ломать основной поток.
            }
        }

        Matcher resoMatcher = RESO_TIMEOUT_PATTERN.matcher(body);
        if (resoMatcher.find()) {
            try {
                int resoTimeoutSec = Integer.parseInt(resoMatcher.group(2));
                if (resoTimeoutSec > timeoutSec) {
                    timeoutSec = resoTimeoutSec;
                }
            } catch (NumberFormatException ignored) {
                // Диагностический парсер не должен ломать основной поток.
            }
        }

        return timeoutSec;
    }

    private static String getMime(String contentType) {
        int p = contentType.indexOf(';');
        return p > 0 ? contentType.substring(0, p).trim() : contentType;
    }

    /**
     * Извлекает charset из Content-Type или возвращает fallback {@code windows-1251}.
     *
     * Зависимости:
     * - Используется в {@link WebResourceResponse} и декодировании текста после загрузки.
     *
     * Назначение:
     * - Поддержать legacy-кодировку сервера при отсутствии явного charset в заголовке.
     */
    private static String getCharset(String contentType) {
        String lower = contentType.toLowerCase();
        int p = lower.indexOf("charset=");
        if (p >= 0) {
            return contentType.substring(p + 8).trim();
        }
        return "windows-1251";
    }

    /**
     * Минимальный JS-shim для AutoBoi вместо полного `fight_v*.js`.
     *
     * Назначение:
     * - убрать ошибки `magic_slots is not defined` / `AutoSubmit is not defined`;
     * - сохранить рабочий fallback для страниц, где вызывается AutoSubmit(...).
     *
     * Поведение:
     * - `magic_slots()` -> no-op;
     * - `AutoSubmit(...)` -> пробует отправить `document.ff` либо первую доступную форму.
     *
     * Зависимости:
     * - используется в intercept(...) только при `AutoboiOn`.
     */
    private static String buildAutoboiFightJsShim() {
        return "(function(){"
                + "if(typeof window.magic_slots!=='function'){window.magic_slots=function(){};}"
                + "if(typeof window.AutoSubmit!=='function'){window.AutoSubmit=function(){"
                + "try{"
                + "if(document&&document.ff&&typeof document.ff.submit==='function'){document.ff.submit();return true;}"
                + "if(document&&document.forms&&document.forms.length>0&&typeof document.forms[0].submit==='function'){document.forms[0].submit();return true;}"
                + "}catch(e){console.log('ABCLIENT_AUTOSUBMIT_SHIM_ERR:'+e);}"
                + "return false;"
                + "};}"
                + "})();";
    }

    /**
     * Поиск заголовка без учёта регистра из HttpURLConnection.getHeaderFields().
     * Зависимости:
     * - Структура Map<String, List<String>> из HttpURLConnection.
     * Назначение:
     * - Надёжно получить заголовки вроде Date/Content-Type независимо от регистра.
     */
    private static List<String> getHeaderIgnoreCase(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Полностью читает InputStream в byte[].
     *
     * Зависимости:
     * - Используется для чтения тела ответа HttpURLConnection (в том числе gzip/не-gzip).
     *
     * Назначение:
     * - Получить буфер ответа для последующей фильтрации и/или декомпрессии.
     */
    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

    /**
     * Декомпрессирует gzip-буфер в исходный byte[].
     *
     * Зависимости:
     * - {@link GZIPInputStream} и {@link #readAllBytes(InputStream)}-подобная логика чтения.
     *
     * Назначение:
     * - Привести сжатые ответы сервера к виду, пригодному для post-filter обработки.
     */
    private static byte[] decompressGzip(byte[] compressed) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        GZIPInputStream gzis = new GZIPInputStream(bais);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = gzis.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

    /**
     * Синхронизация времени по ch.php (сообщения чата).
     * Зависимости:
     * - CHAT_TIME_PATTERN: ищет <font class=chattime>HH:mm:ss</font>.
     * - HTTP Date заголовок: используется в updateServerTimeFromParts для вычисления даты.
     * - shouldApplyServerTime(): защита от резких скачков после боя.
     * Назначение:
     * - Поддерживать время актуальным, но не ломать синхронизацию, полученную из but.php.
     */
    private static void updateServerTimeFromChat(String html, Map<String, List<String>> headers) {
        if (html == null || headers == null) return;
        Matcher matcher = CHAT_TIME_PATTERN.matcher(html);
        if (!matcher.find()) return;

        try {
            int hour = Integer.parseInt(matcher.group(1));
            int min = Integer.parseInt(matcher.group(2));
            int sec = Integer.parseInt(matcher.group(3));
            updateServerTimeFromParts(hour, min, sec, headers, "chat", 5 * 60 * 1000L);
        } catch (Exception ignored) {
        }
    }

    /**
     * Синхронизация времени по but.php.
     * Зависимости:
     * - HTML but.php: serverDate (JS), serverTime (DIV), либо просто HH:mm:ss.
     * - HTTP Date заголовок: используется для проверки валидности serverDate
     *   и для получения даты, если в HTML есть только время.
     * Назначение:
     * - Обновить AppVars.Profile.ServDiff и AppVars.ServerDateTime сразу при входе,
     *   не дожидаясь системных сообщений чата/боя.
     */
    /**
     * Синхронизация времени по but.php.
     * Зависимости:
     * - SERVER_DATE_PATTERN: парсит new Date(Y,M,D,h,m,s).
     * - SERVER_TIME_DIV_PATTERN: извлекает текст serverTime (HH:mm:ss).
     * - HTTP Date заголовок: проверка валидности serverDate, а также дата для HH:mm:ss.
     * Назначение:
     * - Самый ранний и приоритетный источник серверного времени при входе.
     */
    private static void updateServerTimeFromBut(String html, Map<String, List<String>> headers) {
        if (html == null) return;
        if (updateServerTimeFromJsDate(html, headers, "but")) {
            return;
        }
        Matcher div = SERVER_TIME_DIV_PATTERN.matcher(html);
        if (div.find()) {
            try {
                int hour = Integer.parseInt(div.group(1));
                int min = Integer.parseInt(div.group(2));
                int sec = Integer.parseInt(div.group(3));
                updateServerTimeFromParts(hour, min, sec, headers, "but", Long.MAX_VALUE);
                return;
            } catch (Exception ignored) {
            }
        }
        Pattern hmsPattern = Pattern.compile("\\b(\\d{1,2}):(\\d{2}):(\\d{2})\\b");
        Matcher hms = hmsPattern.matcher(html);
        if (hms.find()) {
            try {
                int hour = Integer.parseInt(hms.group(1));
                int min = Integer.parseInt(hms.group(2));
                int sec = Integer.parseInt(hms.group(3));
                updateServerTimeFromParts(hour, min, sec, headers, "but", Long.MAX_VALUE);
                return;
            } catch (Exception ignored) {
            }
        }
        Pattern hPattern = Pattern.compile("\\bhour\\s*=\\s*(\\d{1,2})", Pattern.CASE_INSENSITIVE);
        Pattern mPattern = Pattern.compile("\\bmin\\s*=\\s*(\\d{1,2})", Pattern.CASE_INSENSITIVE);
        Pattern sPattern = Pattern.compile("\\bsec\\s*=\\s*(\\d{1,2})", Pattern.CASE_INSENSITIVE);
        Matcher mh = hPattern.matcher(html);
        Matcher mm = mPattern.matcher(html);
        Matcher ms = sPattern.matcher(html);
        if (mh.find() && mm.find() && ms.find()) {
            try {
                int hour = Integer.parseInt(mh.group(1));
                int min = Integer.parseInt(mm.group(1));
                int sec = Integer.parseInt(ms.group(1));
                updateServerTimeFromParts(hour, min, sec, headers, "but", Long.MAX_VALUE);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Синхронизация по JS-конструктору serverDate из but.php.
     * Зависимости:
     * - SERVER_DATE_PATTERN: парсит new Date(Y,M,D,h,m,s).
     * - HTTP Date header: проверка "разумности" (расхождение > 24ч — игнор).
     * Назначение:
     * - Дать точное серверное время сразу при загрузке but.php.
     */
    /**
     * Синхронизация по JS-конструктору serverDate из but.php.
     * Зависимости:
     * - SERVER_DATE_PATTERN: парсит new Date(Y,M,D,h,m,s).
     * - HTTP Date header: проверка "разумности" (расхождение > 24ч — игнор).
     * - applyServerTime(): записывает ServDiff и ServerDateTime.
     * Назначение:
     * - Дать максимально точное время при загрузке but.php.
     */
    private static boolean updateServerTimeFromJsDate(String html, Map<String, List<String>> headers, String source) {
        if (html == null) return false;
        Matcher matcher = SERVER_DATE_PATTERN.matcher(html);
        if (!matcher.find()) return false;
        try {
            int year = Integer.parseInt(matcher.group(1));
            int rawMonth = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            int hour = Integer.parseInt(matcher.group(4));
            int min = Integer.parseInt(matcher.group(5));
            int sec = Integer.parseInt(matcher.group(6));
            int month = rawMonth;
            List<String> dateHeader = headers != null ? getHeaderIgnoreCase(headers, "Date") : null;
            if (rawMonth >= 1 && rawMonth <= 12 && dateHeader != null && !dateHeader.isEmpty()) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US);
                    sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
                    Date httpDate = sdf.parse(dateHeader.get(0));
                    if (httpDate != null) {
                        java.util.Calendar httpCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT"));
                        httpCal.setTime(httpDate);
                        int httpMonth = httpCal.get(java.util.Calendar.MONTH);
                        int m1 = rawMonth - 1;
                        int m2 = rawMonth;
                        int d1 = Math.abs(m1 - httpMonth);
                        int d2 = Math.abs(m2 - httpMonth);
                        month = d1 <= d2 ? m1 : m2;
                    }
                } catch (Exception ignored) {
                }
            } else if (rawMonth >= 1 && rawMonth <= 12) {
                month = rawMonth - 1;
            }
            if (month < 0) month = 0;
            if (month > 11) month = 11;

            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(year, month, day, hour, min, sec);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            long serverMs = cal.getTimeInMillis();
            // Если HTTP Date сильно расходится (больше суток) — считаем jsDate некорректным.
            if (dateHeader != null && !dateHeader.isEmpty()) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US);
                    sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
                    Date httpDate = sdf.parse(dateHeader.get(0));
                    if (httpDate != null) {
                        long diff = Math.abs(serverMs - httpDate.getTime());
                        if (diff > 24L * 60L * 60L * 1000L) {
                            Log.w(TAG, "Server time sync (" + source + " jsDate): httpDate mismatch, skipping");
                            return false;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            applyServerTime(serverMs, source + " jsDate");
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Синхронизация по времени из HTML (HH:mm:ss).
     * Зависимости:
     * - HTTP Date заголовок: даёт дату, чтобы собрать корректный DateTime сервера.
     * - Calendar: собираем серверное время с учётом GMT.
     * Назначение:
     * - Используется для чата (ch.php) и для but.php, если нет serverDate.
     */
    /**
     * Синхронизация по времени из HTML (HH:mm:ss) с защитой от "скачков" времени.
     * Зависимости:
     * - HTTP Date заголовок: даёт дату, чтобы собрать корректный DateTime сервера.
     * - AppVars.Profile.ServDiff: используется как текущее "серверное" время для проверки дельты.
     * - shouldApplyServerTime(): отсекает подозрительные смещения (например, после боя).
     * Назначение:
     * - Используется для ch.php и but.php, если нет serverDate.
     * - Позволяет синхронизировать время, не ломая уже корректную синхронизацию.
     */
    private static void updateServerTimeFromParts(int hour, int min, int sec, Map<String, List<String>> headers, String source, long maxDeltaMs) {
        try {
            // Если есть HTTP Date — считаем серверное время на его основе + время из HTML.
            List<String> dateHeader = headers != null ? getHeaderIgnoreCase(headers, "Date") : null;
            if (dateHeader != null && !dateHeader.isEmpty()) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
                Date baseDate = sdf.parse(dateHeader.get(0));
                if (baseDate != null) {
                    java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT"));
                    cal.setTimeInMillis(baseDate.getTime());
                    int httpSec = cal.get(java.util.Calendar.HOUR_OF_DAY) * 3600
                            + cal.get(java.util.Calendar.MINUTE) * 60
                            + cal.get(java.util.Calendar.SECOND);
                    int chatSec = hour * 3600 + min * 60 + sec;
                    int diffSec = chatSec - httpSec;
                    if (diffSec > 12 * 3600) diffSec -= 24 * 3600;
                    if (diffSec < -12 * 3600) diffSec += 24 * 3600;
                    long serverMs = baseDate.getTime() + diffSec * 1000L;
                    if (shouldApplyServerTime(serverMs, maxDeltaMs, source)) {
                        applyServerTime(serverMs, source + " httpDate=" + dateHeader.get(0));
                    }
                    return;
                }
            }

            // Фоллбек: как в C# (ButPhp) — серверные часы/мин/сек + локальная дата.
            java.util.Calendar now = java.util.Calendar.getInstance();
            java.util.Calendar server = (java.util.Calendar) now.clone();
            server.set(java.util.Calendar.HOUR_OF_DAY, hour);
            server.set(java.util.Calendar.MINUTE, min);
            server.set(java.util.Calendar.SECOND, sec);
            server.set(java.util.Calendar.MILLISECOND, 0);

            long nowMs = now.getTimeInMillis();
            long serverMs = server.getTimeInMillis();
            long diffMs = nowMs - serverMs;
            if (diffMs > 24L * 3600L * 1000L || diffMs < -24L * 3600L * 1000L) {
                Log.w(TAG, "Server time sync (" + source + "): diffMs out of range, skipping");
                return;
            }
            if (shouldApplyServerTime(serverMs, maxDeltaMs, source)) {
                applyServerTime(serverMs, source + " localDate");
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Решает, можно ли применять новый serverMs, чтобы не получить резкий скачок времени.
     * Зависимости:
     * - AppVars.Profile.ServDiff: используется для вычисления текущего serverMs.
     * - maxDeltaMs: допустимый порог, задаётся источником (chat/but).
     * Назначение:
     * - Для chat ограничиваем изменение (5 минут), чтобы не было +2 часа после боя.
     * - Для but.php допускаем без ограничений (Long.MAX_VALUE).
     */
    private static boolean shouldApplyServerTime(long serverMs, long maxDeltaMs, String source) {
        if (maxDeltaMs == Long.MAX_VALUE) return true;
        if (ru.neverlands.abclient.utils.AppVars.Profile == null) return true;
        if (ru.neverlands.abclient.utils.AppVars.Profile.ServDiff == Long.MIN_VALUE) return true;
        long currentServerMs = System.currentTimeMillis() - ru.neverlands.abclient.utils.AppVars.Profile.ServDiff;
        long delta = Math.abs(currentServerMs - serverMs);
        if (delta > maxDeltaMs) {
            // Chat может приходить с часовым поясом, отличным от HTTP Date/but.php.
            // Если базовая синхронизация уже есть, пропускаем chat-коррекцию без WARN-шума.
            if ("chat".equalsIgnoreCase(source)
                    && ru.neverlands.abclient.utils.AppVars.ServerDateTime != null) {
                Log.d(TAG, "Server time sync skipped (" + source + "): deltaMs=" + delta
                        + ", maxDeltaMs=" + maxDeltaMs);
            } else {
                Log.w(TAG, "Server time sync rejected: deltaMs=" + delta
                        + ", maxDeltaMs=" + maxDeltaMs + ", source=" + source);
            }
            return false;
        }
        return true;
    }

    /**
     * Применение серверного времени: сохраняет ServDiff и ServerDateTime.
     * Зависимости:
     * - AppVars.Profile.ServDiff: используется в UI (часы/чат) для смещения.
     * - AppVars.ServerDateTime: хранит последнее известное серверное DateTime.
     * Назначение:
     * - Единая точка записи серверного времени для всех источников (but/chat).
     */
    private static void applyServerTime(long serverMs, String source) {
        long diffMs = System.currentTimeMillis() - serverMs;
        if (ru.neverlands.abclient.utils.AppVars.Profile != null) {
            ru.neverlands.abclient.utils.AppVars.Profile.ServDiff = diffMs;
        }
        ru.neverlands.abclient.utils.AppVars.ServerDateTime = new Date(serverMs);
        Log.d(TAG, "Server time sync (" + source + "): diffMs=" + diffMs);
    }
}

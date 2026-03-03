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
import java.util.Map;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import ru.neverlands.abclient.postfilter.Filter;
import ru.neverlands.abclient.proxy.CookiesManager;

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

    public static WebResourceResponse intercept(WebResourceRequest request) {
        try {
            Uri uri = request.getUrl();
            String urlString = uri.toString();
            String host = uri.getHost();

            if (host == null) {
                return null;
            }

            if (!urlString.contains("neverlands.ru")) {
                // Short-circuit slow external counters that often time out inside WebView
                if (host.contains("mail.ru") || host.contains("yadro.ru")
                        || host.contains("mc.yandex.ru") || host.contains("google-analytics.com")) {
                    Log.d(TAG, "Blocking tracker host: " + host);
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

            // В автобое упрощаем: не тянем тяжёлые бойовые скрипты, которые не нужны нашему минимальному фрейму
            if (ru.neverlands.abclient.utils.AppVars.Profile != null
                    && ru.neverlands.abclient.utils.AppVars.Profile.LezDoAutoboi
                    && ru.neverlands.abclient.utils.AppVars.Autoboi == ru.neverlands.abclient.model.AutoboiState.AutoboiOn) {
                if (urlString.contains("fight_v")) {
                    Log.d(TAG, "Replacing heavy fight js with autoboi shim: " + urlString);
                    byte[] shimBytes = buildAutoboiFightJsShim().getBytes(Charset.forName("UTF-8"));
                    return new WebResourceResponse("application/javascript", "utf-8",
                            new ByteArrayInputStream(shimBytes));
                }
                if (urlString.contains("hpmp.js") || urlString.contains("game.js")) {
                    Log.d(TAG, "Skipping heavy support js during autoboi: " + urlString);
                    byte[] emptyBytes = "/* abclient autoboi skip */".getBytes(Charset.forName("UTF-8"));
                    return new WebResourceResponse("application/javascript", "utf-8",
                            new ByteArrayInputStream(emptyBytes));
                }
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
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("GET");
            connection.setDoInput(true);

            // Список игроков чата — отключаем кеширование на уровне HTTP.
            if (urlString.contains("ch.php?lo=1")) {
                connection.setUseCaches(false);
                connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
                connection.setRequestProperty("Pragma", "no-cache");
                connection.setRequestProperty("Expires", "0");
            }
            connection.setRequestProperty("Accept-Encoding", "identity");

            // Cookies: prefer WebView CookieManager (has actual session cookies)
            String wvCookie = CookieManager.getInstance().getCookie(urlString);
            if (wvCookie != null && !wvCookie.isEmpty()) {
                connection.setRequestProperty("Cookie", wvCookie);
            } else {
                String cookie = CookiesManager.obtain(url.getHost());
                if (cookie != null && !cookie.isEmpty()) {
                    connection.setRequestProperty("Cookie", cookie);
                }
            }

            // Forward original request headers (Referer, etc.)
            java.util.Map<String, String> reqHeaders = request.getRequestHeaders();
            if (reqHeaders != null) {
                for (java.util.Map.Entry<String, String> entry : reqHeaders.entrySet()) {
                    String key = entry.getKey();
                    // Don't override Cookie or Accept-Encoding we already set
                    if (!"Cookie".equalsIgnoreCase(key) && !"Accept-Encoding".equalsIgnoreCase(key)) {
                        connection.setRequestProperty(key, entry.getValue());
                    }
                }
            }

            int code = connection.getResponseCode();
            Log.d(TAG, "Response code: " + code + " for " + urlString);

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
                HttpURLConnection second = (HttpURLConnection) url.openConnection();
                second.setInstanceFollowRedirects(true);
                second.setRequestMethod("GET");
                second.setDoInput(true);
                second.setRequestProperty("Accept-Encoding", "identity");
                String cookie2 = CookieManager.getInstance().getCookie(urlString);
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
            return null;
        }
    }

    private static String getMime(String contentType) {
        int p = contentType.indexOf(';');
        return p > 0 ? contentType.substring(0, p).trim() : contentType;
    }

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
     * Case-insensitive header lookup from HttpURLConnection.getHeaderFields().
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

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

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

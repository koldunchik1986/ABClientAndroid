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
import java.util.zip.GZIPInputStream;

import ru.neverlands.abclient.postfilter.Filter;
import ru.neverlands.abclient.proxy.CookiesManager;

public class WebViewRequestInterceptor {
    private static final String TAG = "WebViewInterceptor";

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

            if (!urlString.contains("neverlands.ru")) {
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

            Log.d(TAG, "Intercepting: " + urlString);

            if (urlString.contains("ch.php?lo=1")) {
                urlString += "&" + System.currentTimeMillis();
            }

            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("GET");
            connection.setDoInput(true);

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

            // Log first 200 chars of processed HTML
            String processedPreview = new String(processed, Charset.forName("windows-1251"));
            Log.d(TAG, "Processed preview (" + urlString + "): " + processedPreview.substring(0, Math.min(200, processedPreview.length())));

            // Get Content-Type (case-insensitive)
            String contentType = null;
            List<String> ctList = getHeaderIgnoreCase(headers, "Content-Type");
            if (ctList != null && !ctList.isEmpty()) {
                contentType = ctList.get(0);
            }
            if (contentType == null || contentType.isEmpty()) {
                contentType = "text/html; charset=windows-1251";
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
     * Case-insensitive header lookup from HttpURLConnection.getHeaderFields().
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
}

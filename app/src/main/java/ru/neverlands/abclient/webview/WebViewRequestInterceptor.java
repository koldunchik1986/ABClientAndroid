package ru.neverlands.abclient.webview;

import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import ru.neverlands.abclient.postfilter.Filter;
import ru.neverlands.abclient.proxy.CookiesManager;

public class WebViewRequestInterceptor {

    public static WebResourceResponse intercept(WebResourceRequest request) {
        try {
            Uri uri = request.getUrl();
            String urlString = uri.toString();

            if (!urlString.contains("neverlands.ru")) {
                return null;
            }

            if (urlString.contains("ch.php?lo=1")) {
                urlString += "&" + System.currentTimeMillis();
            }

            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod(request.getMethod());
            connection.setDoInput(true);

            if (urlString.contains("ch.php?lo=1")) {
                connection.setUseCaches(false);
                connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
                connection.setRequestProperty("Pragma", "no-cache");
                connection.setRequestProperty("Expires", "0");
            }
            connection.setRequestProperty("Accept-Encoding", "identity");
            String cookie = CookiesManager.obtain(url.getHost());
            if (cookie != null && !cookie.isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
            }

            byte[] body = null;
            if (request.getMethod().equalsIgnoreCase("POST")) {
                // WebViewRequest should have body only via fetch/XHR; keep simple GET-only for now
            }

            int code = connection.getResponseCode();
            byte[] bytes = ru.neverlands.abclient.utils.DataManager.readAllBytes(
                    code >= 400 && connection.getErrorStream() != null
                            ? connection.getErrorStream()
                            : connection.getInputStream());

            // Capture Set-Cookie
            Map<String, List<String>> headers = connection.getHeaderFields();
            List<String> setCookies = headers.get("Set-Cookie");
            if (setCookies != null) {
                for (String sc : setCookies) {
                    CookiesManager.assign(url.getHost(), sc);
                    CookieManager.getInstance().setCookie(url.getProtocol() + "://" + url.getHost(), sc);
                }
                CookieManager.getInstance().flush();
            }

            connection.disconnect();

            // Handle "Cookie..." transitional page by re-requesting once with cookies
            String htmlWin1251 = new String(bytes, Charset.forName("windows-1251"));
            if (htmlWin1251.contains("Cookie...")) {
                HttpURLConnection second = (HttpURLConnection) url.openConnection();
                second.setInstanceFollowRedirects(true);
                second.setRequestMethod("GET");
                second.setDoInput(true);
                second.setRequestProperty("Accept-Encoding", "identity");
                String cookie2 = CookiesManager.obtain(url.getHost());
                if (cookie2 != null && !cookie2.isEmpty()) {
                    second.setRequestProperty("Cookie", cookie2);
                }
                int code2 = second.getResponseCode();
                byte[] secondBytes = ru.neverlands.abclient.utils.DataManager.readAllBytes(
                        code2 >= 400 && second.getErrorStream() != null
                                ? second.getErrorStream()
                                : second.getInputStream());
                Map<String, List<String>> h2 = second.getHeaderFields();
                List<String> sc2 = h2.get("Set-Cookie");
                if (sc2 != null) {
                    for (String sc : sc2) {
                        CookiesManager.assign(url.getHost(), sc);
                        CookieManager.getInstance().setCookie(url.getProtocol() + "://" + url.getHost(), sc);
                    }
                    CookieManager.getInstance().flush();
                }
                second.disconnect();
                bytes = secondBytes;
                htmlWin1251 = new String(bytes, Charset.forName("windows-1251"));
            }

            byte[] processed = Filter.process(ru.neverlands.abclient.utils.AppVars.getContext(), urlString, bytes);
            if (processed == null) processed = bytes;

            String contentType = headers.get("Content-Type") != null && !headers.get("Content-Type").isEmpty()
                    ? headers.get("Content-Type").get(0)
                    : "text/html; charset=windows-1251";

            WebResourceResponse response = new WebResourceResponse(
                    getMime(contentType),
                    getCharset(contentType),
                    new ByteArrayInputStream(processed)
            );

            if (urlString.contains("ch.php?lo=1")) {
                response.setResponseHeaders(java.util.Collections.singletonMap("Cache-Control", "no-cache"));
            }

            return response;
        } catch (IOException e) {
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
}


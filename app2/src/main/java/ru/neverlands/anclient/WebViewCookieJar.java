package ru.neverlands.anclient;

import android.webkit.CookieManager;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

import ru.neverlands.anclient.utils.FileLogger;

public class WebViewCookieJar implements CookieJar {

    private final CookieManager cookieManager = CookieManager.getInstance();

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        String urlString = url.scheme() + "://" + url.host();
        FileLogger.log("WebViewCookieJar: Saving " + cookies.size() + " cookies for " + urlString);
        for (Cookie cookie : cookies) {
            cookieManager.setCookie(urlString, cookie.toString());
            FileLogger.log("  -> " + cookie.toString());
        }
        cookieManager.flush();
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        String urlString = url.scheme() + "://" + url.host();
        String cookiesString = cookieManager.getCookie(urlString);
        FileLogger.log("WebViewCookieJar: Loading cookies for " + urlString);
        if (cookiesString != null && !cookiesString.isEmpty()) {
            FileLogger.log("  -> Raw cookies: " + cookiesString);
            List<Cookie> result = new ArrayList<>();
            String[] parts = cookiesString.split(";");
            for (String part : parts) {
                Cookie cookie = Cookie.parse(url, part);
                if (cookie != null) {
                    result.add(cookie);
                }
            }
            return result;
        }
        FileLogger.log("  -> No cookies found.");
        return new ArrayList<>();
    }
}
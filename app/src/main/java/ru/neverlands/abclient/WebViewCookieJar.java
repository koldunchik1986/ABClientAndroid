package ru.neverlands.abclient;

import android.webkit.CookieManager;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class WebViewCookieJar implements CookieJar {

    private final CookieManager cookieManager = CookieManager.getInstance();

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        String urlString = url.scheme() + "://" + url.host();
        for (Cookie cookie : cookies) {
            cookieManager.setCookie(urlString, cookie.toString());
        }
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        String urlString = url.scheme() + "://" + url.host();
        String cookiesString = cookieManager.getCookie(urlString);
        if (cookiesString != null && !cookiesString.isEmpty()) {
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
        return new ArrayList<>();
    }
}

package ru.neverlands.abclient.network;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.concurrent.TimeUnit;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;

public class NetworkClient {

    private static OkHttpClient instance;
    private static CookieManager cookieManager;

    public static synchronized OkHttpClient getInstance() {
        if (instance == null) {
            cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

            instance = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .cookieJar(new JavaNetCookieJar(cookieManager))
                    .build();
        }
        return instance;
    }

    public static CookieManager getCookieManager() {
        // Убедимся, что cookieManager инициализирован
        if (cookieManager == null) {
            getInstance();
        }
        return cookieManager;
    }
}

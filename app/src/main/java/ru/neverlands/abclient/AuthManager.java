package ru.neverlands.abclient;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.net.CookiePolicy;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.neverlands.abclient.network.NetworkClient;
import ru.neverlands.abclient.utils.DebugLogger;

public class AuthManager {

    public interface AuthCallback {
        void onSuccess(List<java.net.HttpCookie> cookies);
        void onFailure(String message);
    }

    public static void authorize(Context context, String username, String password, AuthCallback callback) {
        DebugLogger.log("AuthManager: Starting synchronous authorization for user: " + username);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            OkHttpClient client = NetworkClient.getInstance();
            java.net.CookieManager cookieManager = NetworkClient.getCookieManager();

            try {
                // Step 1: Initial GET request
                Request initialRequest = new Request.Builder()
                        .url("http://neverlands.ru/")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                        .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                        .build();

                DebugLogger.log("AuthManager: 1. Initial GET request\n" + initialRequest.toString());
                try (Response initialResponse = client.newCall(initialRequest).execute()) {
                    DebugLogger.log("AuthManager: 1. Initial GET response\n" + initialResponse.toString());
                    if (!initialResponse.isSuccessful()) {
                        throw new IOException("Ошибка получения начальной страницы: " + initialResponse.code());
                    }
                }

                // Step 2: Login POST request
                RequestBody formBody = new FormBody.Builder(Charset.forName("windows-1251"))
                        .add("player_nick", username)
                        .add("player_password", password)
                        .build();

                Request loginRequest = new Request.Builder()
                        .url("http://neverlands.ru/game.php")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
                        .header("Referer", "http://neverlands.ru/")
                        .header("Origin", "http://neverlands.ru")
                        .post(formBody)
                        .build();

                DebugLogger.log("AuthManager: 2. Login POST request\n" + loginRequest.toString());
                try (Response loginResponse = client.newCall(loginRequest).execute()) {
                    DebugLogger.log("AuthManager: 2. Login POST response\n" + loginResponse.toString());
                    String loginResponseBody = loginResponse.body().string();
                    if (!loginResponse.isSuccessful()) {
                        throw new IOException("Ошибка авторизации: " + loginResponse.code());
                    }
                    if (loginResponseBody.contains("auth_form")) {
                        throw new IOException("Ошибка авторизации: неверный логин или пароль.");
                    }
                }
                DebugLogger.log("AuthManager: 2. Login POST SUCCESS.");

                // Step 3: Final GET to main.php
                Request mainRequest = new Request.Builder()
                        .url("http://neverlands.ru/main.php")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
                        .header("Referer", "http://neverlands.ru/game.php")
                        .build();

                DebugLogger.log("AuthManager: 3. Final GET request\n" + mainRequest.toString());
                try (Response mainResponse = client.newCall(mainRequest).execute()) {
                    DebugLogger.log("AuthManager: 3. Final GET response\n" + mainResponse.toString());
                    if (!mainResponse.isSuccessful()) {
                        throw new IOException("Ошибка финализации сессии: " + mainResponse.code());
                    }
                }

                // All steps successful
                DebugLogger.log("AuthManager: Full Authorization SUCCESS.");
                List<java.net.HttpCookie> cookies = cookieManager.getCookieStore().get(HttpUrl.get("http://neverlands.ru/").uri());

                // Ручная синхронизация cookies в WebView
                android.webkit.CookieManager webViewCookieManager = android.webkit.CookieManager.getInstance();
                for (java.net.HttpCookie cookie : cookies) {
                    String cookieString = cookie.getName() + "=" + cookie.getValue() + "; domain=" + cookie.getDomain();
                    webViewCookieManager.setCookie("http://neverlands.ru", cookieString);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    webViewCookieManager.flush();
                }

                handler.post(() -> callback.onSuccess(cookies));

            } catch (Exception e) {
                DebugLogger.log("AuthManager: Authorization FAILED: " + e.getMessage());
                handler.post(() -> callback.onFailure(e.getMessage()));
            } finally {
                DebugLogger.close();
            }
        });
    }
}
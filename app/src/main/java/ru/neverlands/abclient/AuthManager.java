package ru.neverlands.abclient;

import android.content.Context;
import android.os.Build;
import android.webkit.CookieManager;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.neverlands.abclient.utils.DebugLogger;

public class AuthManager {

    public interface AuthCallback {
        void onSuccess();
        void onFailure(String message);
    }

    public static void authorize(Context context, String username, String password, AuthCallback callback) {
        DebugLogger.log("AuthManager: Starting authorization for user: " + username);

        DebugLogger.log("AuthManager: Clearing all cookies.");
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .cookieJar(new WebViewCookieJar())
                .build();

        // 1. GET initial page
        Request initialRequest = new Request.Builder()
                .url("http://neverlands.ru/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .build();

        DebugLogger.log("AuthManager: 1. Initial GET request\n" + initialRequest.toString() + "\nHeaders:\n" + initialRequest.headers().toString());

        client.newCall(initialRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                DebugLogger.log("AuthManager: 1. Initial GET request FAILED: " + e.getMessage());
                DebugLogger.close();
                callback.onFailure("Ошибка соединения: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                DebugLogger.log("AuthManager: 1. Initial GET response\n" + response.toString() + "\nHeaders:\n" + response.headers().toString());
                response.body().close(); // We only need the cookies

                if (!response.isSuccessful()) {
                    DebugLogger.log("AuthManager: 1. Initial GET request was not successful. Code: " + response.code());
                    DebugLogger.close();
                    callback.onFailure("Ошибка получения начальной страницы: " + response.code());
                    return;
                }

                // 2. POST login
                try {
                    RequestBody formBody = new FormBody.Builder(Charset.forName("windows-1251"))
                            .add("player_nick", username)
                            .add("player_password", password)
                            .build();

                    Request loginRequest = new Request.Builder()
                            .url("http://neverlands.ru/game.php")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
                            .header("Referer", "http://neverlands.ru/")
                            .header("Origin", "http://neverlands.ru")
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                            .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                            .header("Upgrade-Insecure-Requests", "1")
                            .header("Content-Type", "application/x-www-form-urlencoded; charset=windows-1251")
                            .post(formBody)
                            .build();

                    DebugLogger.log("AuthManager: 2. Login POST request\n" + loginRequest.toString() + "\nHeaders:\n" + loginRequest.headers().toString());

                    client.newCall(loginRequest).enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            DebugLogger.log("AuthManager: 2. Login POST request FAILED: " + e.getMessage());
                            DebugLogger.close();
                            callback.onFailure("Ошибка авторизации: " + e.getMessage());
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            DebugLogger.log("AuthManager: 2. Login POST response\n" + response.toString() + "\nHeaders:\n" + response.headers().toString());
                            String responseBody = response.body().string(); // Read the body to check for errors

                            if (!response.isSuccessful()) {
                                DebugLogger.log("AuthManager: 2. Login POST request was not successful. Code: " + response.code());
                                DebugLogger.close();
                                callback.onFailure("Ошибка авторизации: " + response.code());
                                return;
                            }

                            if (responseBody.contains("auth_form")) {
                                DebugLogger.log("AuthManager: Authorization FAILED. Found 'auth_form' in response.");
                                DebugLogger.close();
                                callback.onFailure("Ошибка авторизации: неверный логин или пароль.");
                            } else {
                                DebugLogger.log("AuthManager: Authorization SUCCESS.");
                                DebugLogger.close();
                                // Принудительно сохраняем cookies, чтобы WebView их подхватил
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    CookieManager.getInstance().flush();
                                }

                                callback.onSuccess();
                            }
                        }
                    });
                } catch (Exception e) {
                    DebugLogger.log("AuthManager: Exception during login POST step: " + e.getMessage());
                    DebugLogger.close();
                    callback.onFailure("Ошибка авторизации: " + e.getMessage());
                }
            }
        });
    }
}
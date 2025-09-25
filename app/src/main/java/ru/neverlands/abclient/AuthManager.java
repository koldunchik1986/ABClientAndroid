package ru.neverlands.abclient;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.neverlands.abclient.utils.AppLogger;

public class AuthManager {

    public interface AuthCallback {
        void onSuccess();
        void onFailure(String message);
    }

    public static void authorize(Context context, String username, String password, AuthCallback callback) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .cookieJar(new WebViewCookieJar())
                .build();

        // 1. GET initial page
        Request initialRequest = new Request.Builder()
                .url("http://www.neverlands.ru/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                .header("Accept-Language", "en-US,en;q=0.9,ru;q=0.8")
                .build();

        AppLogger.write("AuthManager", "Initial request: " + initialRequest.toString());

        client.newCall(initialRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("Ошибка соединения: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                AppLogger.write("AuthManager", "Initial response: " + response.toString());
                response.body().close(); // We only need the cookies

                if (!response.isSuccessful()) {
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
                            .url("http://www.neverlands.ru/game.php")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36")
                            .header("Referer", "http://www.neverlands.ru/")
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                            .header("Accept-Language", "en-US,en;q=0.9,ru;q=0.8")
                            .header("Content-Type", "application/x-www-form-urlencoded; charset=windows-1251")
                            .post(formBody)
                            .build();

                    AppLogger.write("AuthManager", "Login request: " + loginRequest.toString());

                    client.newCall(loginRequest).enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            callback.onFailure("Ошибка авторизации: " + e.getMessage());
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            AppLogger.write("AuthManager", "Login response: " + response.toString());
                            response.body().close(); // We only need the cookies

                            if (!response.isSuccessful()) {
                                callback.onFailure("Ошибка авторизации: " + response.code());
                                return;
                            }

                            // 3. GET main page to verify
                            Request mainPageRequest = new Request.Builder()
                                    .url("http://www.neverlands.ru/main.php")
                                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36")
                                    .header("Referer", "http://www.neverlands.ru/game.php")
                                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                                    .header("Accept-Language", "en-US,en;q=0.9,ru;q=0.8")
                                    .build();

                            AppLogger.write("AuthManager", "Main page request: " + mainPageRequest.toString());

                            client.newCall(mainPageRequest).enqueue(new Callback() {
                                @Override
                                public void onFailure(Call call, IOException e) {
                                    callback.onFailure("Ошибка проверки авторизации: " + e.getMessage());
                                }

                                @Override
                                public void onResponse(Call call, Response response) throws IOException {
                                    AppLogger.write("AuthManager", "Main page response: " + response.toString());
                                    String mainPageBody = new String(response.body().bytes(), Charset.forName("windows-1251"));

                                    if (mainPageBody.contains("auth_form")) {
                                        callback.onFailure("Ошибка авторизации: неверный логин или пароль.");
                                    } else {
                                        callback.onSuccess();
                                    }
                                }
                            });
                        }
                    });
                } catch (Exception e) {
                    callback.onFailure("Ошибка авторизации: " + e.getMessage());
                }
            }
        });
    }
}

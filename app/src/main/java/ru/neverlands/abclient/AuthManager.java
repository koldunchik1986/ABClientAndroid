package ru.neverlands.abclient;

import android.os.Build;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.HttpCookie;
import java.nio.charset.Charset;
import java.util.List;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.neverlands.abclient.model.AuthResult;
import ru.neverlands.abclient.network.NetworkClient;
import ru.neverlands.abclient.utils.DebugLogger;

public class AuthManager {

    public AuthResult authorize(String username, String password) {
        DebugLogger.log("AuthManager: Starting synchronous authorization for user: " + username);

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
                    return new AuthResult("Ошибка получения начальной страницы: " + initialResponse.code());
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
                if (!loginResponse.isSuccessful()) {
                    return new AuthResult("Ошибка авторизации: " + loginResponse.code());
                }

                String loginResponseBody = loginResponse.body().string();
                Document doc = Jsoup.parse(loginResponseBody);

                // Check for captcha
                Element captchaImg = doc.selectFirst("img[src*='nl_reg_code.php']");
                Element vcode_el = doc.selectFirst("input[name=vcode]");

                if (captchaImg != null && vcode_el != null) {
                    String captchaUrl = captchaImg.attr("abs:src");
                    String vcode = vcode_el.val();
                    DebugLogger.log("AuthManager: Captcha detected. URL: " + captchaUrl + ", vcode: " + vcode);
                    return new AuthResult(captchaUrl, vcode);
                }

                if (loginResponseBody.contains("auth_form")) {
                    return new AuthResult("Ошибка авторизации: неверный логин или пароль.");
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
                    return new AuthResult("Ошибка финализации сессии: " + mainResponse.code());
                }
            }

            // All steps successful
            DebugLogger.log("AuthManager: Full Authorization SUCCESS.");
            List<HttpCookie> cookies = cookieManager.getCookieStore().get(HttpUrl.get("http://neverlands.ru/").uri());

            // Ручная синхронизация cookies в WebView
            android.webkit.CookieManager webViewCookieManager = android.webkit.CookieManager.getInstance();
            for (java.net.HttpCookie cookie : cookies) {
                String cookieString = cookie.getName() + "=" + cookie.getValue() + "; domain=" + cookie.getDomain();
                webViewCookieManager.setCookie("http://neverlands.ru", cookieString);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                webViewCookieManager.flush();
            }

            return new AuthResult(cookies);

        } catch (Exception e) {
            DebugLogger.log("AuthManager: Authorization FAILED: " + e.getMessage());
            return new AuthResult(e.getMessage());
        } finally {
            DebugLogger.close();
        }
    }

    public AuthResult authorizeWithCaptcha(String username, String password, String vcode, String verify) {
        DebugLogger.log("AuthManager: Starting authorization with captcha for user: " + username);

        OkHttpClient client = NetworkClient.getInstance();
        java.net.CookieManager cookieManager = NetworkClient.getCookieManager();

        try {
            RequestBody formBody = new FormBody.Builder(Charset.forName("windows-1251"))
                    .add("vcode", vcode)
                    .add("player_nick", username)
                    .add("player_password", password)
                    .add("verify", verify)
                    .build();

            Request loginRequest = new Request.Builder()
                    .url("http://neverlands.ru/game.php")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
                    .header("Referer", "http://neverlands.ru/game.php")
                    .header("Origin", "http://neverlands.ru")
                    .post(formBody)
                    .build();

            DebugLogger.log("AuthManager: 2. Captcha Login POST request\n" + loginRequest.toString());
            try (Response loginResponse = client.newCall(loginRequest).execute()) {
                DebugLogger.log("AuthManager: 2. Captcha Login POST response\n" + loginResponse.toString());
                if (!loginResponse.isSuccessful()) {
                    return new AuthResult("Ошибка авторизации с капчей: " + loginResponse.code());
                }

                String loginResponseBody = loginResponse.body().string();
                Document doc = Jsoup.parse(loginResponseBody);

                // Check for captcha again (in case of wrong captcha)
                Element captchaImg = doc.selectFirst("img[src*='nl_reg_code.php']");
                Element vcode_el = doc.selectFirst("input[name=vcode]");

                if (captchaImg != null && vcode_el != null) {
                    String captchaUrl = captchaImg.attr("abs:src");
                    String newVcode = vcode_el.val();
                    DebugLogger.log("AuthManager: Captcha detected again. URL: " + captchaUrl + ", vcode: " + newVcode);
                    return new AuthResult(captchaUrl, newVcode);
                }

                if (loginResponseBody.contains("auth_form")) {
                    return new AuthResult("Ошибка авторизации: неверный логин или пароль.");
                }
            }
            DebugLogger.log("AuthManager: 2. Captcha Login POST SUCCESS.");

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
                    return new AuthResult("Ошибка финализации сессии: " + mainResponse.code());
                }
            }

            // All steps successful
            DebugLogger.log("AuthManager: Full Authorization SUCCESS.");
            List<HttpCookie> cookies = cookieManager.getCookieStore().get(HttpUrl.get("http://neverlands.ru/").uri());

            // Ручная синхронизация cookies в WebView
            android.webkit.CookieManager webViewCookieManager = android.webkit.CookieManager.getInstance();
            for (java.net.HttpCookie cookie : cookies) {
                String cookieString = cookie.getName() + "=" + cookie.getValue() + "; domain=" + cookie.getDomain();
                webViewCookieManager.setCookie("http://neverlands.ru", cookieString);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                webViewCookieManager.flush();
            }

            return new AuthResult(cookies);

        } catch (Exception e) {
            DebugLogger.log("AuthManager: Authorization FAILED: " + e.getMessage());
            return new AuthResult(e.getMessage());
        } finally {
            DebugLogger.close();
        }
    }
}

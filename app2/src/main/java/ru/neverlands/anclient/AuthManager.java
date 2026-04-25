package ru.neverlands.anclient;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.HttpCookie;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.neverlands.anclient.model.AuthResult;
import ru.neverlands.anclient.network.NetworkClient;
import ru.neverlands.anclient.proxy.ProxyRuntimeManager;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.FileLogger;

public class AuthManager {
    private static final Pattern LAST_HTTP_CODE_PATTERN = Pattern.compile("(\\d{3})(?!.*\\d)");
    private static final Pattern FLASH_PLID_PATTERN = Pattern.compile(
            "flashvars\\s*=\\s*['\"][^'\"]*?plid=([0-9]+)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Полный auth-flow без капчи.
     *
     * Зависимости:
     * - {@link NetworkClient}: общий OkHttp-клиент и cookie-store.
     * - {@link ProxyRuntimeManager}: определение proxy-режима и выбор базового host.
     * - {@link Jsoup}: детекция captcha/auth_form по HTML-ответу.
     * - {@link DebugLogger}: детальная трассировка шагов авторизации.
     *
     * Поведение:
     * - выполняет основной сценарий через {@link #authorizeInternal(String, String, String, String)};
     * - если включен proxy и получена HTTP-ошибка уровня host-маршрута, выполняет один fallback
     *   на альтернативный host (`www <-> non-www`) после очистки cookies.
     */
    public AuthResult authorize(String username, String password) {
        return authorize(username, password, "");
    }

    public AuthResult authorize(String username, String password, String flashPassword) {
        FileLogger.log("AuthManager: Starting synchronous authorization for user: " + username);
        final String primaryBaseUrl = resolveAuthBaseUrl();
        try {
            AuthResult primary = authorizeInternal(username, password, flashPassword, primaryBaseUrl);
            if (!shouldRetryWithAlternateHost(primary)) {
                return primary;
            }

            String alternateBaseUrl = resolveAlternateAuthBaseUrl(primaryBaseUrl);
            FileLogger.log(
                    "AuthManager: proxy fallback for authorize, primary=" + primaryBaseUrl
                            + ", alternate=" + alternateBaseUrl
                            + ", reason=" + (primary == null ? "" : primary.getErrorMessage())
            );
            NetworkClient.clearCookies();
            return authorizeInternal(username, password, flashPassword, alternateBaseUrl);
        } finally {
            // DebugLogger.close() removed - using FileLogger now
        }
    }

    /**
     * Продолжение auth-flow при captcha.
     *
     * Зависимости:
     * - {@link NetworkClient}: HTTP-запросы и cookie-store.
     * - {@link ProxyRuntimeManager}: fallback host в proxy-режиме.
     * - {@link Jsoup}: повторная проверка captcha (ошибка verify).
     * - {@link DebugLogger}: журналирование этапов и причины отказа.
     */
    public AuthResult authorizeWithCaptcha(String username, String password, String vcode, String verify) {
        return authorizeWithCaptcha(username, password, "", vcode, verify);
    }

    public AuthResult authorizeWithCaptcha(String username,
                                           String password,
                                           String flashPassword,
                                           String vcode,
                                           String verify) {
        FileLogger.log("AuthManager: Starting authorization with captcha for user: " + username);
        final String primaryBaseUrl = resolveAuthBaseUrl();
        try {
            AuthResult primary = authorizeWithCaptchaInternal(username, password, flashPassword, vcode, verify, primaryBaseUrl);
            if (!shouldRetryWithAlternateHost(primary)) {
                return primary;
            }

            String alternateBaseUrl = resolveAlternateAuthBaseUrl(primaryBaseUrl);
            FileLogger.log(
                    "AuthManager: proxy fallback for authorizeWithCaptcha, primary=" + primaryBaseUrl
                            + ", alternate=" + alternateBaseUrl
                            + ", reason=" + (primary == null ? "" : primary.getErrorMessage())
            );
            NetworkClient.clearCookies();
            return authorizeWithCaptchaInternal(username, password, flashPassword, vcode, verify, alternateBaseUrl);
        } finally {
            // DebugLogger.close() removed - using FileLogger now
        }
    }

    /**
     * Внутренний 3-шаговый auth-flow:
     * 1) GET "/" для первичных cookies/watermark.
     * 2) POST "/game.php" с player_nick/player_password (windows-1251).
     * 3) при наличии flash-check страницы отправляет `flcheck`/`nid`, как ПК-версия.
     * 4) GET "/main.php" для финализации сессии.
     *
     * Важные зависимости:
     * - windows-1251 для формы логина (совместимость с сервером neverlands).
     * - Referer/Origin завязаны на конкретный host authBaseUrl.
     * - {@link #collectNeverlandsCookies(java.net.CookieManager)} собирает итоговый cookie-набор.
     */
    private AuthResult authorizeInternal(String username,
                                         String password,
                                         String flashPassword,
                                         String authBaseUrl) {
        final String refererRoot = authBaseUrl + "/";
        final String gameUrl = authBaseUrl + "/game.php";
        final String mainUrl = authBaseUrl + "/main.php";

        OkHttpClient client = NetworkClient.getInstance();
        java.net.CookieManager cookieManager = NetworkClient.getCookieManager();

        try {
            Request initialRequest = new Request.Builder()
                    .url(refererRoot)
                    .header("User-Agent", AppVars.BROWSER_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                    .build();

            FileLogger.log("AuthManager: 1. Initial GET request\n" + initialRequest);
            try (Response initialResponse = client.newCall(initialRequest).execute()) {
                FileLogger.log("AuthManager: 1. Initial GET response\n" + initialResponse);
                if (!initialResponse.isSuccessful()) {
                    return new AuthResult("Ошибка получения начальной страницы: " + initialResponse.code());
                }
            }

            RequestBody formBody = new FormBody.Builder(Charset.forName("windows-1251"))
                    .add("player_nick", username)
                    .add("player_password", password)
                    .build();

            Request loginRequest = new Request.Builder()
                    .url(gameUrl)
                    .header("User-Agent", AppVars.BROWSER_USER_AGENT)
                    .header("Referer", refererRoot)
                    .header("Origin", authBaseUrl)
                    .post(formBody)
                    .build();

            FileLogger.log("AuthManager: 2. Login POST request\n" + loginRequest);
            try (Response loginResponse = client.newCall(loginRequest).execute()) {
                FileLogger.log("AuthManager: 2. Login POST response\n" + loginResponse);
                if (!loginResponse.isSuccessful()) {
                    return new AuthResult("Ошибка авторизации: " + loginResponse.code());
                }

                String loginResponseBody = loginResponse.body().string();
                Document doc = Jsoup.parse(loginResponseBody);

                Element captchaImg = doc.selectFirst("img[src*='nl_reg_code.php']");
                Element vcodeEl = doc.selectFirst("input[name=vcode]");
                if (captchaImg != null && vcodeEl != null) {
                    String captchaUrl = captchaImg.attr("abs:src");
                    String captchaVcode = vcodeEl.val();
                    FileLogger.log("AuthManager: Captcha detected. URL: " + captchaUrl + ", vcode: " + captchaVcode);
                    return new AuthResult(captchaUrl, captchaVcode);
                }

                if (loginResponseBody.contains("auth_form")) {
                    return new AuthResult("Ошибка авторизации: неверный логин или пароль.");
                }
                AuthResult flashResult = submitFlashPasswordIfRequired(client, gameUrl, authBaseUrl, loginResponseBody, flashPassword);
                if (flashResult != null) {
                    return flashResult;
                }
            }
            FileLogger.log("AuthManager: 2. Login POST SUCCESS.");

            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Request mainRequest = new Request.Builder()
                    .url(mainUrl)
                    .header("User-Agent", AppVars.BROWSER_USER_AGENT)
                    .header("Referer", gameUrl)
                    .build();

            FileLogger.log("AuthManager: 3. Final GET request\n" + mainRequest);
            try (Response mainResponse = client.newCall(mainRequest).execute()) {
                FileLogger.log("AuthManager: 3. Final GET response\n" + mainResponse);
                if (!mainResponse.isSuccessful()) {
                    return new AuthResult("Ошибка финализации сессии: " + mainResponse.code());
                }
            }

            FileLogger.log("AuthManager: Full Authorization SUCCESS.");
            List<HttpCookie> cookies = collectNeverlandsCookies(cookieManager);
            return new AuthResult(cookies);
        } catch (Exception e) {
            FileLogger.log("AuthManager: Authorization FAILED: " + e.getMessage());
            return new AuthResult(e.getMessage());
        }
    }

    /**
     * Внутренний captcha-flow:
     * 1) POST "/game.php" с vcode/verify + credentials.
     * 2) при повторной captcha возвращает новый captchaUrl/vcode.
     * 3) при наличии flash-check страницы отправляет `flcheck`/`nid`, как ПК-версия.
     * 4) GET "/main.php" для завершения сессии.
     *
     * Зависимости:
     * - charset windows-1251;
     * - тот же host authBaseUrl, что был выбран на старте auth;
     * - итоговые cookies извлекаются через {@link #collectNeverlandsCookies(java.net.CookieManager)}.
     */
    private AuthResult authorizeWithCaptchaInternal(String username,
                                                    String password,
                                                    String flashPassword,
                                                    String vcode,
                                                    String verify,
                                                    String authBaseUrl) {
        final String gameUrl = authBaseUrl + "/game.php";
        final String mainUrl = authBaseUrl + "/main.php";

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
                    .url(gameUrl)
                    .header("User-Agent", AppVars.BROWSER_USER_AGENT)
                    .header("Referer", gameUrl)
                    .header("Origin", authBaseUrl)
                    .post(formBody)
                    .build();

            FileLogger.log("AuthManager: 2. Captcha Login POST request\n" + loginRequest);
            try (Response loginResponse = client.newCall(loginRequest).execute()) {
                FileLogger.log("AuthManager: 2. Captcha Login POST response\n" + loginResponse);
                if (!loginResponse.isSuccessful()) {
                    return new AuthResult("Ошибка авторизации с капчей: " + loginResponse.code());
                }

                String loginResponseBody = loginResponse.body().string();
                Document doc = Jsoup.parse(loginResponseBody);

                Element captchaImg = doc.selectFirst("img[src*='nl_reg_code.php']");
                Element vcodeEl = doc.selectFirst("input[name=vcode]");
                if (captchaImg != null && vcodeEl != null) {
                    String captchaUrl = captchaImg.attr("abs:src");
                    String newVcode = vcodeEl.val();
                    FileLogger.log("AuthManager: Captcha detected again. URL: " + captchaUrl + ", vcode: " + newVcode);
                    return new AuthResult(captchaUrl, newVcode);
                }

                if (loginResponseBody.contains("auth_form")) {
                    return new AuthResult("Ошибка авторизации: неверный логин или пароль.");
                }
                AuthResult flashResult = submitFlashPasswordIfRequired(client, gameUrl, authBaseUrl, loginResponseBody, flashPassword);
                if (flashResult != null) {
                    return flashResult;
                }
            }
            FileLogger.log("AuthManager: 2. Captcha Login POST SUCCESS.");

            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Request mainRequest = new Request.Builder()
                    .url(mainUrl)
                    .header("User-Agent", AppVars.BROWSER_USER_AGENT)
                    .header("Referer", gameUrl)
                    .build();

            FileLogger.log("AuthManager: 3. Final GET request\n" + mainRequest);
            try (Response mainResponse = client.newCall(mainRequest).execute()) {
                FileLogger.log("AuthManager: 3. Final GET response\n" + mainResponse);
                if (!mainResponse.isSuccessful()) {
                    return new AuthResult("Ошибка финализации сессии: " + mainResponse.code());
                }
            }

            FileLogger.log("AuthManager: Full Authorization SUCCESS.");
            List<HttpCookie> cookies = collectNeverlandsCookies(cookieManager);
            return new AuthResult(cookies);
        } catch (Exception e) {
            FileLogger.log("AuthManager: Authorization FAILED: " + e.getMessage());
            return new AuthResult(e.getMessage());
        }
    }

    /**
     * C# parity (`PostFilter.GamePhp`): после логина сервер может вернуть flash-page
     * с `flashvars="plid=..."`. ПК-клиент автоматически POST-ит `flcheck` и `nid`.
     */
    private AuthResult submitFlashPasswordIfRequired(OkHttpClient client,
                                                     String gameUrl,
                                                     String authBaseUrl,
                                                     String html,
                                                     String flashPassword) throws Exception {
        String safeFlashPassword = flashPassword == null ? "" : flashPassword.trim();
        if (safeFlashPassword.isEmpty() || html == null || html.isEmpty()) {
            return null;
        }
        Matcher matcher = FLASH_PLID_PATTERN.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        String pid = matcher.group(1);
        if (pid == null || pid.trim().isEmpty()) {
            return null;
        }

        RequestBody flashBody = new FormBody.Builder(Charset.forName("windows-1251"))
                .add("flcheck", safeFlashPassword)
                .add("nid", pid.trim())
                .build();
        Request flashRequest = new Request.Builder()
                .url(gameUrl)
                .header("User-Agent", AppVars.BROWSER_USER_AGENT)
                .header("Referer", gameUrl)
                .header("Origin", authBaseUrl)
                .post(flashBody)
                .build();
        FileLogger.log("AuthManager: Flash password POST request, nid=" + pid.trim());
        try (Response flashResponse = client.newCall(flashRequest).execute()) {
            FileLogger.log("AuthManager: Flash password POST response\n" + flashResponse);
            if (!flashResponse.isSuccessful()) {
                return new AuthResult("Ошибка ввода Flash-пароля: " + flashResponse.code());
            }
        }
        FileLogger.log("AuthManager: Flash password POST SUCCESS.");
        return null;
    }

    /**
     * Разрешает fallback host только при proxy-режиме и только для ошибок,
     * где реальный смысл есть в повторе auth-flow на альтернативном host.
     *
     * Зависимости:
     * - состояние proxy runtime: {@link ProxyRuntimeManager#isRunning()};
     * - тип результата: {@link AuthResult#isSuccess()}, {@link AuthResult#isCaptchaRequired()};
     * - HTTP-код берется из текста ошибки через {@link #extractLastHttpCode(String)}.
     *
     * Правило:
     * - fallback допустим только для сетевых/маршрутизаторных кодов,
     *   где смена host (`www <-> non-www`) реально может изменить ответ.
     */
    private boolean shouldRetryWithAlternateHost(AuthResult result) {
        if (!ProxyRuntimeManager.isRunning() || result == null || result.isSuccess() || result.isCaptchaRequired()) {
            return false;
        }
        int code = extractLastHttpCode(result.getErrorMessage());
        return code == 400
                || code == 403
                || code == 405
                || code == 407
                || code == 429
                || code == 500
                || code == 502
                || code == 503
                || code == 504;
    }

    /**
     * Извлекает последний HTTP-код из текста ошибки (если есть).
     *
     * Зависимости:
     * - {@link #LAST_HTTP_CODE_PATTERN} (`(\d{3})(?!.*\d)`);
     * - используется только для принятия решения о proxy host-fallback.
     *
     * @param message текст ошибки (например: "Ошибка финализации сессии: 405").
     * @return HTTP-код или `-1`, если код не обнаружен/не распарсен.
     */
    private int extractLastHttpCode(String message) {
        if (message == null || message.isEmpty()) {
            return -1;
        }
        Matcher matcher = LAST_HTTP_CODE_PATTERN.matcher(message);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /**
     * Выбирает базовый host для auth-flow.
     *
     * Зависимости:
     * - proxy-режим: `http://www.neverlands.ru`
     * - direct-режим: `http://neverlands.ru`
     */
    private String resolveAuthBaseUrl() {
        boolean proxyActive = ProxyRuntimeManager.isRunning();
        String baseUrl = proxyActive ? "http://www.neverlands.ru" : "http://neverlands.ru";
        FileLogger.log("AuthManager: authBaseUrl=" + baseUrl + ", proxyActive=" + proxyActive);
        return baseUrl;
    }

    /**
     * Возвращает альтернативный host для fallback-попытки.
     *
     * Зависимости:
     * - вызывается из `authorize(...)` и `authorizeWithCaptcha(...)` только после
     *   положительного решения `shouldRetryWithAlternateHost(...)`.
     *
     * @param currentBaseUrl текущий host первой попытки.
     * @return зеркальный host (`www` <-> `non-www`) для второй попытки auth-flow.
     */
    private String resolveAlternateAuthBaseUrl(String currentBaseUrl) {
        if (currentBaseUrl != null && currentBaseUrl.contains("://www.neverlands.ru")) {
            return "http://neverlands.ru";
        }
        return "http://www.neverlands.ru";
    }

    /**
     * Собирает итоговый cookie-набор neverlands после успешной авторизации.
     *
     * Зависимости:
     * - основной источник: весь CookieStore (host-only + *.neverlands.ru);
     * - fallback-источник: явные URI `neverlands.ru` и `www.neverlands.ru`.
     */
    private List<HttpCookie> collectNeverlandsCookies(java.net.CookieManager cookieManager) {
        List<HttpCookie> source = cookieManager.getCookieStore().getCookies();
        List<HttpCookie> result = new ArrayList<>();
        Set<String> dedup = new HashSet<>();

        for (HttpCookie cookie : source) {
            if (cookie == null) {
                continue;
            }
            String domain = cookie.getDomain();
            String lowerDomain = domain == null ? "" : domain.toLowerCase(Locale.ROOT);
            boolean hostCookie = lowerDomain.isEmpty();
            boolean neverlandsDomain = lowerDomain.contains("neverlands.ru");
            if (!hostCookie && !neverlandsDomain) {
                continue;
            }

            String path = cookie.getPath() == null ? "/" : cookie.getPath();
            String key = cookie.getName() + "|" + lowerDomain + "|" + path;
            if (dedup.add(key)) {
                result.add(cookie);
            }
        }

        if (result.isEmpty()) {
            List<HttpCookie> fallback = cookieManager.getCookieStore().get(HttpUrl.get("http://neverlands.ru/").uri());
            if (fallback != null) {
                result.addAll(fallback);
            }
            if (result.isEmpty()) {
                List<HttpCookie> fallbackWww = cookieManager.getCookieStore().get(HttpUrl.get("http://www.neverlands.ru/").uri());
                if (fallbackWww != null) {
                    result.addAll(fallbackWww);
                }
            }
        }

        StringBuilder names = new StringBuilder();
        for (HttpCookie cookie : result) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(cookie.getName());
        }
        FileLogger.log("AuthManager: collected cookies count=" + result.size() + " names=[" + names + "]");
        return result;
    }
}

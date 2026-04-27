package ru.neverlands.anclient.manager;

import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import ru.neverlands.anclient.proxy.ProxyRuntimeManager;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;

/**
 * HTTP-клиент внешнего сервиса anti-captcha.com для задачи ImageToTextTask.
 *
 * Назначение:
 * - получить bytes текущей Neverlands captcha из MainActivity;
 * - создать задачу `createTask` с body=base64 PNG/GIF/JPEG;
 * - опрашивать `getTaskResult` до готовности ответа;
 * - вернуть только распознанный текст, а submit captcha оставить существующему popup-контуры.
 *
 * Зависимости и инварианты:
 * - лицензирование и настройка API key живут в AutoFunctionsManager/LicenseRuntime;
 * - User-Agent всегда берётся из AppVars.BROWSER_USER_AGENT, без ANClient/ABClient маркеров;
 * - HTTPS API нельзя отправлять через локальный LocalHttpProxyServer, потому что он обслуживает
 *   HTTP-трафик игры и не реализует CONNECT. Если профильный proxy включён, используем upstream
 *   напрямую через ProxyRuntimeManager.getActiveUpstreamJavaProxyOrNull(); иначе route=direct_external.
 */
public final class AntiCaptchaManager {
    private static final String TAG = "AntiCaptchaManager";
    private static final String API_CREATE_TASK = "https://api.anti-captcha.com/createTask";
    private static final String API_GET_TASK_RESULT = "https://api.anti-captcha.com/getTaskResult";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final int MAX_POLL_COUNT = 24;
    private static final long POLL_DELAY_MS = 3_000L;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private AntiCaptchaManager() {
    }

    public interface Callback {
        void onSolved(String challengeKey, String text);
        void onFailed(String challengeKey, String message);
    }

    public static final class Config {
        public final String clientKey;
        public final boolean phrase;
        public final boolean caseSensitive;
        public final int numeric;
        public final int math;
        public final int minLength;
        public final int maxLength;
        public final String languagePool;

        public Config(String clientKey,
                      boolean phrase,
                      boolean caseSensitive,
                      int numeric,
                      int math,
                      int minLength,
                      int maxLength,
                      String languagePool) {
            this.clientKey = clientKey == null ? "" : clientKey.trim();
            this.phrase = phrase;
            this.caseSensitive = caseSensitive;
            this.numeric = clamp(numeric, 0, 2);
            this.math = clamp(math, 0, 1);
            this.minLength = clamp(minLength, 0, 20);
            this.maxLength = clamp(maxLength, 0, 20);
            String normalizedLanguage = languagePool == null ? "" : languagePool.trim().toLowerCase(java.util.Locale.ROOT);
            this.languagePool = "rn".equals(normalizedLanguage) ? "rn" : "en";
        }

        public boolean hasClientKey() {
            return !clientKey.isEmpty();
        }
    }

    public static void solveImageAsync(byte[] imageBytes,
                                       Config config,
                                       String challengeKey,
                                       Callback callback) {
        final byte[] captchaBytes = imageBytes == null ? null : imageBytes.clone();
        new Thread(() -> solveImage(captchaBytes, config, challengeKey, callback), "anti-captcha-solver").start();
    }

    private static void solveImage(byte[] imageBytes,
                                   Config config,
                                   String challengeKey,
                                   Callback callback) {
        String safeChallengeKey = challengeKey == null ? "" : challengeKey;
        try {
            if (config == null || !config.hasClientKey()) {
                notifyFailed(callback, safeChallengeKey, "API key is empty");
                return;
            }
            if (imageBytes == null || imageBytes.length == 0) {
                notifyFailed(callback, safeChallengeKey, "captcha image is empty");
                return;
            }

            AppLog.i(TAG, "ANTI_CAPTCHA_TRACE create task, bytes=" + imageBytes.length
                    + ", numeric=" + config.numeric
                    + ", min=" + config.minLength
                    + ", max=" + config.maxLength
                    + ", lang=" + config.languagePool);
            long taskId = createTask(imageBytes, config);
            if (taskId <= 0L) {
                notifyFailed(callback, safeChallengeKey, "createTask failed");
                return;
            }

            String text = waitForResult(taskId, config.clientKey);
            if (text == null || text.trim().isEmpty()) {
                notifyFailed(callback, safeChallengeKey, "empty solution");
                return;
            }

            notifySolved(callback, safeChallengeKey, text.trim());
        } catch (Exception e) {
            AppLog.e(TAG, "ANTI_CAPTCHA_TRACE solve failed", e);
            notifyFailed(callback, safeChallengeKey, e.getMessage() == null ? "solve failed" : e.getMessage());
        }
    }

    private static long createTask(byte[] imageBytes, Config config) throws Exception {
        JSONObject task = new JSONObject();
        task.put("type", "ImageToTextTask");
        task.put("body", Base64.encodeToString(imageBytes, Base64.NO_WRAP));
        task.put("phrase", config.phrase);
        task.put("case", config.caseSensitive);
        task.put("numeric", config.numeric);
        task.put("math", config.math);
        if (config.minLength > 0) {
            task.put("minLength", config.minLength);
        }
        if (config.maxLength > 0) {
            task.put("maxLength", config.maxLength);
        }
        if (!config.languagePool.isEmpty()) {
            task.put("languagePool", config.languagePool);
        }

        JSONObject request = new JSONObject();
        request.put("clientKey", config.clientKey);
        request.put("softId", 0);
        request.put("task", task);

        JSONObject response = postJson(API_CREATE_TASK, request);
        int errorId = response.optInt("errorId", -1);
        if (errorId != 0) {
            AppLog.w(TAG, "ANTI_CAPTCHA_TRACE createTask error="
                    + response.optString("errorCode") + ": " + response.optString("errorDescription"));
            return 0L;
        }
        return response.optLong("taskId", 0L);
    }

    private static String waitForResult(long taskId, String clientKey) throws Exception {
        for (int attempt = 0; attempt < MAX_POLL_COUNT; attempt++) {
            if (attempt > 0) {
                Thread.sleep(POLL_DELAY_MS);
            }

            JSONObject request = new JSONObject();
            request.put("clientKey", clientKey);
            request.put("taskId", taskId);
            JSONObject response = postJson(API_GET_TASK_RESULT, request);
            int errorId = response.optInt("errorId", -1);
            if (errorId != 0) {
                AppLog.w(TAG, "ANTI_CAPTCHA_TRACE getTaskResult error="
                        + response.optString("errorCode") + ": " + response.optString("errorDescription"));
                return "";
            }

            String status = response.optString("status", "");
            if ("processing".equalsIgnoreCase(status)) {
                AppLog.d(TAG, "ANTI_CAPTCHA_TRACE task=" + taskId + " processing, attempt=" + attempt);
                continue;
            }
            if ("ready".equalsIgnoreCase(status)) {
                JSONObject solution = response.optJSONObject("solution");
                String text = solution == null ? "" : solution.optString("text", "");
                AppLog.i(TAG, "ANTI_CAPTCHA_TRACE task=" + taskId
                        + " ready, textLen=" + text.length()
                        + ", cost=" + response.optString("cost", ""));
                return text;
            }

            AppLog.w(TAG, "ANTI_CAPTCHA_TRACE unexpected status=" + status + ", task=" + taskId);
            return "";
        }
        AppLog.w(TAG, "ANTI_CAPTCHA_TRACE task=" + taskId + " timeout");
        return "";
    }

    private static JSONObject postJson(String urlString, JSONObject body) throws Exception {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .followRedirects(true);

        java.net.Proxy upstreamProxy = ProxyRuntimeManager.getActiveUpstreamJavaProxyOrNull();
        boolean strictProxyRequired = ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile();
        if (upstreamProxy != null) {
            // Внешний HTTPS route в proxy-профиле: идём сразу через upstream proxy.
            // Локальный 127.0.0.1 proxy здесь запрещён, иначе Android OkHttp отправляет CONNECT
            // в LocalHttpProxyServer и получает 501 Not Implemented.
            clientBuilder.proxy(upstreamProxy);
            String upstreamAuthHeader = ProxyRuntimeManager.getActiveUpstreamBasicAuthHeaderOrEmpty();
            if (!upstreamAuthHeader.isEmpty()) {
                clientBuilder.proxyAuthenticator((route, response) -> {
                    if (response.request().header("Proxy-Authorization") != null) {
                        return null;
                    }
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", upstreamAuthHeader)
                            .build();
                });
            }
            AppLog.i(TAG, "ANTI_CAPTCHA_TRACE route=upstream_proxy, url=" + urlString);
        } else {
            if (strictProxyRequired) {
                throw new IllegalStateException("strict proxy enabled but runtime proxy unavailable");
            }
            // Proxy не включён в профиле: явно задаём NO_PROXY, чтобы системные/старые WebView
            // proxy-overrides не перехватили внешний Anti-Captcha HTTPS API.
            clientBuilder.proxy(java.net.Proxy.NO_PROXY);
            AppLog.i(TAG, "ANTI_CAPTCHA_TRACE route=direct_external, url=" + urlString);
        }

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        Request request = new Request.Builder()
                .url(urlString)
                .post(RequestBody.create(payload, JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .header("User-Agent", AppVars.BROWSER_USER_AGENT)
                .build();

        try (Response response = clientBuilder.build().newCall(request).execute()) {
            int code = response.code();
            ResponseBody responseBody = response.body();
            String responseText = responseBody == null ? "" : responseBody.string();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code + ": " + responseText);
            }
            return new JSONObject(responseText);
        }
    }

    private static void notifySolved(Callback callback, String challengeKey, String text) {
        if (callback != null) {
            callback.onSolved(challengeKey, text);
        }
    }

    private static void notifyFailed(Callback callback, String challengeKey, String message) {
        AppLog.w(TAG, "ANTI_CAPTCHA_TRACE failed: " + message);
        if (callback != null) {
            callback.onFailed(challengeKey, message);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

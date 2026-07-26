package ru.neverlands.anclient.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.webkit.CookieManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.anclient.manager.AntiCaptchaManager;
import ru.neverlands.anclient.manager.AutoFunctionsManager;
import ru.neverlands.anclient.proxy.CookiesManager;
import ru.neverlands.anclient.proxy.ProxyRuntimeManager;

/**
 * Утилиты боевой капчи, не зависящие от состояния Activity.
 *
 * <p>Выделено из {@code MainActivity} в рамках D6. Сюда перенесены только <b>чистые</b>
 * функции: загрузка и декодирование картинки, нормализация и сравнение URL, работа с
 * параметром {@code code}, валидация ответа Anti-Captcha.</p>
 *
 * <p><b>Что намеренно осталось в {@code MainActivity}:</b> весь stateful-контур боевой капчи —
 * сам диалог, авто-обновление картинки, retry Anti-Captcha, системные уведомления и ~25 полей
 * состояния ({@code activeFightCaptcha*}, {@code antiCaptcha*}). Это критичная боевая цепочка
 * (AGENTS п.8/п.9), её вынос требует отдельной итерации с live-проверкой на реальных боях.</p>
 */
public final class FightCaptchaUtils {

    private static final String TAG = "FightCaptchaUtils";

    /**
     * Минимальный размер картинки капчи, ниже которого ответ считается мусором
     * (сервер отдал заглушку/ошибку вместо изображения).
     */
    public static final int CAPTCHA_IMAGE_MIN_USABLE_BYTES = 1024;

    private static final int CAPTCHA_HTTP_TIMEOUT_MS = 10_000;

    private FightCaptchaUtils() {
    }

    // ------------------------------------------------------------------
    // Загрузка и декодирование изображения
    // ------------------------------------------------------------------

    /**
     * Выполняет HTTP GET изображения капчи и возвращает сырые байты.
     *
     * <p>Особенности: браузерный User-Agent, отключённый кэш, cookie текущей игровой сессии
     * из WebView {@link CookieManager} с fallback через {@link CookiesManager}, учёт
     * strict-proxy режима.</p>
     *
     * @return байты изображения либо {@code null} при ошибке/неуспешном HTTP-коде
     */
    public static byte[] downloadCaptchaImageBytes(String captchaUrl) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        ByteArrayOutputStream outputStream = null;
        try {
            String routedCaptchaUrl = GameServerUrls.routeUrlToCurrentServer(captchaUrl);
            URL url = new URL(routedCaptchaUrl);
            java.net.Proxy activeProxy = ProxyRuntimeManager.getActiveJavaProxyOrNull();
            if (activeProxy == null && ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile()) {
                AppLog.e(TAG, "PROXY_FAIL: strict proxy enabled and runtime proxy unavailable, blocking direct captcha download: " + routedCaptchaUrl);
                return null;
            }
            connection = activeProxy != null
                    ? (HttpURLConnection) url.openConnection(activeProxy)
                    : (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CAPTCHA_HTTP_TIMEOUT_MS);
            connection.setReadTimeout(CAPTCHA_HTTP_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("Referer", GameServerUrls.currentGameUrl("/main.php"));
            connection.setRequestProperty("User-Agent", AppVars.BROWSER_USER_AGENT);

            String cookie = CookieManager.getInstance().getCookie(routedCaptchaUrl);
            if (cookie == null || cookie.isEmpty()) {
                cookie = CookieManager.getInstance().getCookie(GameServerUrls.neverlandsCookieUrl());
            }
            if ((cookie == null || cookie.isEmpty()) && url.getHost() != null) {
                cookie = CookiesManager.obtain(url.getHost());
            }
            if (cookie != null && !cookie.isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
                AppLog.d(TAG, "downloadCaptchaImageBytes: using cookie len=" + cookie.length());
            } else {
                AppLog.w(TAG, "downloadCaptchaImageBytes: cookie is empty for " + routedCaptchaUrl);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                AppLog.w(TAG, "downloadCaptchaImageBytes: HTTP " + responseCode + " for " + routedCaptchaUrl);
                return null;
            }

            inputStream = connection.getInputStream();
            outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }

            byte[] data = outputStream.toByteArray();
            AppLog.d(TAG, "downloadCaptchaImageBytes: loaded " + data.length + " bytes from " + routedCaptchaUrl);
            return data.length > 0 ? data : null;
        } catch (Exception e) {
            AppLog.e(TAG, "downloadCaptchaImageBytes: failed for " + captchaUrl, e);
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                    // Ожидаемая ветка cleanup: поток картинки капчи уже закрыт/оборван.
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                    // Ожидаемая ветка cleanup: ByteArrayOutputStream close() не влияет на данные.
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Декодирует байты капчи в {@link Bitmap}, отсекая заведомо непригодные ответы.
     *
     * @param source метка вызывающего кода для логов
     * @return bitmap либо {@code null}, если байты пусты, слишком малы или не декодируются
     */
    public static Bitmap decodeUsableCaptchaBitmap(byte[] captchaBytes, String source) {
        String safeSource = source == null ? "captcha" : source;
        if (captchaBytes == null || captchaBytes.length == 0) {
            AppLog.w(TAG, safeSource + ": captcha image bytes are empty");
            return null;
        }
        if (captchaBytes.length < CAPTCHA_IMAGE_MIN_USABLE_BYTES) {
            AppLog.w(TAG, safeSource + ": captcha image bytes too small, bytes="
                    + captchaBytes.length + ", min=" + CAPTCHA_IMAGE_MIN_USABLE_BYTES);
            return null;
        }
        Bitmap bitmap = BitmapFactory.decodeByteArray(captchaBytes, 0, captchaBytes.length);
        if (bitmap == null) {
            AppLog.w(TAG, safeSource + ": captcha bitmap decode failed, bytes=" + captchaBytes.length);
            return null;
        }
        return bitmap;
    }

    // ------------------------------------------------------------------
    // Работа с URL
    // ------------------------------------------------------------------

    /**
     * Нормализует игровой URL для сравнения: выравнивает http/https и {@code www},
     * отбрасывает фрагмент. Query сохраняется — для капчи token в нём критичен.
     *
     * <p>Заменяет две ранее дублировавшиеся приватные реализации {@code MainActivity}:
     * {@code normalizeCaptchaUrlForCompare} и {@code normalizeFightFinishUrlForCompare}
     * (их тела были идентичны).</p>
     */
    public static String normalizeUrlForCompare(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) {
            return "";
        }
        try {
            String normalized = GameServerUrls.normalizeNeverlandsUrlForCompare(rawUrl);
            int fragmentIndex = normalized.indexOf('#');
            if (fragmentIndex >= 0) {
                normalized = normalized.substring(0, fragmentIndex);
            }
            return normalized;
        } catch (Exception ignored) {
            return "";
        }
    }

    /** Сравнивает два URL после {@link #normalizeUrlForCompare(String)}. */
    public static boolean isSameUrl(String firstUrl, String secondUrl) {
        if (firstUrl == null || firstUrl.isEmpty() || secondUrl == null || secondUrl.isEmpty()) {
            return false;
        }
        String firstNormalized = normalizeUrlForCompare(firstUrl);
        String secondNormalized = normalizeUrlForCompare(secondUrl);
        if (firstNormalized.isEmpty() || secondNormalized.isEmpty()) {
            return false;
        }
        return firstNormalized.equals(secondNormalized);
    }

    /**
     * Добавляет либо заменяет параметр {@code code} в finish-URL, сохраняя остальной query
     * и фрагмент.
     */
    public static String appendOrReplaceCaptchaCode(String finishUrl, String code) {
        String submitUrl = finishUrl == null ? "" : finishUrl;
        String encodedCode = Uri.encode(code == null ? "" : code);
        if (submitUrl.isEmpty()) {
            return "code=" + encodedCode;
        }

        String fragment = "";
        int fragmentIndex = submitUrl.indexOf('#');
        if (fragmentIndex >= 0) {
            fragment = submitUrl.substring(fragmentIndex);
            submitUrl = submitUrl.substring(0, fragmentIndex);
        }

        Pattern codeParamPattern = Pattern.compile("([?&])code=[^&]*");
        Matcher codeMatcher = codeParamPattern.matcher(submitUrl);
        if (codeMatcher.find()) {
            submitUrl = codeMatcher.replaceFirst("$1code=" + encodedCode);
        } else {
            int queryIndex = submitUrl.indexOf('?');
            if (queryIndex >= 0) {
                String base = submitUrl.substring(0, queryIndex);
                String query = submitUrl.substring(queryIndex + 1);
                submitUrl = base + "?code=" + encodedCode + (query.isEmpty() ? "" : "&" + query);
            } else {
                submitUrl = submitUrl + "?code=" + encodedCode;
            }
        }
        return submitUrl + fragment;
    }

    /**
     * true, если finish-URL принадлежит popup Авто-Травника ({@code alchemy_ajax.php act=3}),
     * а не боевой капче.
     */
    public static boolean isAlchemyCaptchaFinishUrl(String finishUrl) {
        return finishUrl != null
                && finishUrl.contains("/gameplay/ajax/alchemy_ajax.php")
                && finishUrl.contains("act=3");
    }

    // ------------------------------------------------------------------
    // Anti-Captcha
    // ------------------------------------------------------------------

    /**
     * true, если ошибку Anti-Captcha имеет смысл повторить (нет свободных воркеров,
     * серверная 5xx, таймаут).
     */
    public static boolean shouldRetryAntiCaptchaFailure(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return normalized.contains("error_no_slot_available")
                || normalized.contains("no idle workers")
                || normalized.contains("http 5")
                || normalized.contains("timeout");
    }

    /**
     * Проверяет ответ Anti-Captcha на соответствие настройкам профиля
     * (только цифры / минимальная и максимальная длина).
     */
    public static boolean isAntiCaptchaSolutionValid(String code, AntiCaptchaManager.Config config) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        if (config != null && config.numeric == AutoFunctionsManager.ANTI_CAPTCHA_NUMERIC_NUMBERS_ONLY
                && !code.matches("\\d+")) {
            return false;
        }
        int minLength = config == null ? 0 : config.minLength;
        int maxLength = config == null ? 0 : config.maxLength;
        if (minLength > 0 && code.length() < minLength) {
            return false;
        }
        return maxLength <= 0 || code.length() <= maxLength;
    }
}

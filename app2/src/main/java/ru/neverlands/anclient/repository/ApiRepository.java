package ru.neverlands.anclient.repository;

import ru.neverlands.anclient.utils.AppLog;

import android.content.Context;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.File;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import ru.neverlands.anclient.model.Contact;
import ru.neverlands.anclient.network.NetworkClient;
import ru.neverlands.anclient.proxy.CookiesManager;
import ru.neverlands.anclient.proxy.ProxyRuntimeManager;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.ContactRenderHelper;
import ru.neverlands.anclient.utils.FileLogger;
import ru.neverlands.anclient.utils.GameServerUrls;
import ru.neverlands.anclient.utils.ParseUtils;

/**
 * Репозиторий для взаимодействия с внешним API игры.
 * Инкапсулирует логику сетевых запросов (OkHttp) и парсинга ответов.
 * Все методы асинхронны и используют интерфейс ApiCallback для возврата результатов.
 */
public class ApiRepository {

    /**
     * Вспомогательный метод для получения единственного экземпляра OkHttpClient.
     * Зависимость: `NetworkClient.getInstance()`
     * @return Синглтон OkHttpClient.
     */
    private static OkHttpClient getClient() {
        return NetworkClient.getInstance();
    }

    private static Request buildSessionAwareGetRequest(String url) {
        String host = extractHost(url);
        boolean isPublicApi = isPublicApiRequest(url);
        String cookie = isPublicApi ? "" : buildBestEffortCookieHeader(url, host);

        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", AppVars.BROWSER_USER_AGENT)
                .header("Accept", "*/*");
        if (!isPublicApi) {
            builder.header("Referer", "http://neverlands.ru/main.php");
        }
        if (cookie != null && !cookie.trim().isEmpty()) {
            builder.header("Cookie", cookie);
            FileLogger.log("SESSION_COOKIE_APPLIED: url=" + url + ", bytes=" + cookie.length());
            AppLog.d("ApiRepository", "SESSION_COOKIE_APPLIED: host=" + host + ", bytes=" + cookie.length());
        } else {
            FileLogger.log("SESSION_COOKIE_APPLIED: url=" + url + ", bytes=0");
            AppLog.w("ApiRepository", "SESSION_COOKIE_APPLIED: host=" + host + ", bytes=0");
        }
        return builder.build();
    }

    private static boolean isPublicApiRequest(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("service.neverlands.ru/info/clans.txt");
    }

    private static String extractHost(String url) {
        try {
            URL parsed = new URL(url);
            return parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String buildBestEffortCookieHeader(String url, String host) {
        LinkedHashMap<String, String> cookieByName = new LinkedHashMap<>();
        addCookiePairs(cookieByName, CookiesManager.obtain(host));
        addCookiePairs(cookieByName, CookiesManager.obtain(GameServerUrls.serverHost(GameServerUrls.currentServerCode())));
        addCookiePairs(cookieByName, CookiesManager.obtain("neverlands.ru"));
        addCookiePairs(cookieByName, CookiesManager.obtain("www.neverlands.ru"));
        try {
            for (String cookieUrl : GameServerUrls.cookieUrls()) {
                addCookiePairs(cookieByName, CookieManager.getInstance().getCookie(cookieUrl));
            }
            addCookiePairs(cookieByName, CookieManager.getInstance().getCookie(url));
            addCookiePairs(cookieByName, CookieManager.getInstance().getCookie("http://neverlands.ru/"));
            addCookiePairs(cookieByName, CookieManager.getInstance().getCookie("http://www.neverlands.ru/"));
            addCookiePairs(cookieByName, CookieManager.getInstance().getCookie("http://neverlands.ru/main.php"));
        } catch (Throwable t) {
            AppLog.d("ApiRepository", "cookie collection failed: " + t.getClass().getSimpleName());
        }
        if (cookieByName.isEmpty()) {
            return "";
        }
        StringBuilder header = new StringBuilder();
        for (Map.Entry<String, String> pair : cookieByName.entrySet()) {
            if (header.length() > 0) {
                header.append("; ");
            }
            header.append(pair.getKey()).append("=").append(pair.getValue());
        }
        return header.toString();
    }

    private static void addCookiePairs(Map<String, String> out, String cookieHeader) {
        if (cookieHeader == null || cookieHeader.trim().isEmpty()) {
            return;
        }
        String[] pairs = cookieHeader.split(";");
        for (String rawPair : pairs) {
            String pair = rawPair == null ? "" : rawPair.trim();
            if (pair.isEmpty()) {
                continue;
            }
            int delimiter = pair.indexOf('=');
            if (delimiter <= 0) {
                continue;
            }
            String name = pair.substring(0, delimiter).trim();
            String value = pair.substring(delimiter + 1).trim();
            if (name.isEmpty()) {
                continue;
            }
            out.put(name, value);
        }
    }

    private static String describeRequestHeaders(Request request) {
        if (request == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < request.headers().size(); index++) {
            if (result.length() > 0) {
                result.append(", ");
            }
            String name = request.headers().name(index);
            String value = request.headers().value(index);
            result.append(name).append(": ");
            if ("Cookie".equalsIgnoreCase(name)) {
                result.append("<redacted, bytes=")
                        .append(value == null ? 0 : value.length())
                        .append(">");
            } else {
                result.append(value);
            }
        }
        return result.toString();
    }

    private static boolean ensureProxyReadyForRequest(String tracePrefix, ApiCallback<?> callback) {
        boolean strictProxyRequired = ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile();
        if (!strictProxyRequired) {
            return true;
        }

        java.net.Proxy activeProxy = ProxyRuntimeManager.getActiveJavaProxyOrNull();
        if (activeProxy != null) {
            FileLogger.log(tracePrefix + "_PROXY_READY: strict=true, started=false, active=true");
            return true;
        }

        Context context = AppVars.getContext();
        boolean started = context != null && ProxyRuntimeManager.ensureStarted(context, AppVars.Profile);
        java.net.Proxy activeAfterStart = ProxyRuntimeManager.getActiveJavaProxyOrNull();
        boolean ready = started && activeAfterStart != null;
        FileLogger.log(tracePrefix + "_PROXY_READY: strict=true, started=" + started + ", active=" + (activeAfterStart != null));
        if (ready) {
            return true;
        }

        String reason = ProxyRuntimeManager.getLastStartError();
        if (reason == null || reason.trim().isEmpty()) {
            reason = "proxy runtime is not ready";
        }
        if (callback != null) {
            callback.onFailure("Proxy runtime error: " + reason);
        }
        return false;
    }

    /**
     * Универсальный интерфейс колбэка для асинхронной обработки результатов API-запросов.
     * @param <T> Тип ожидаемого успешного результата.
     */
    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onFailure(String message);
    }

    /**
     * Шаг 1 в процессе добавления контакта: получает ID персонажа по его нику.
     * @param nick Ник персонажа. Может содержать кириллицу и пробелы.
     * @param callback Колбэк, в который возвращается необработанная строка ответа сервера (playerID|nick).
     */
    public static void getPlayerId(String nick, ApiCallback<String> callback) {
        try {
            if (!ensureProxyReadyForRequest("CONTACTS_GET_ID", callback)) {
                return;
            }
            // Кодирование ника в windows-1251 и замена пробелов на %20 для корректного URL.
            String encodedNick = URLEncoder.encode(nick, "windows-1251");
            encodedNick = encodedNick.replace("+", "%20");
            String url = "http://www.neverlands.ru/modules/api/getid.cgi?" + encodedNick;

            Request request = buildSessionAwareGetRequest(url);

            FileLogger.log("REQUEST_URL: " + request.url());
            FileLogger.log("REQUEST_HEADERS: " + describeRequestHeaders(request));

            // Асинхронный вызов
            getClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    FileLogger.log("RESPONSE_ERROR: " + e.getMessage());
                    callback.onFailure(e.getMessage() != null ? e.getMessage() : "Unknown network error");
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try (Response safeResponse = response) {
                        String responseBody = safeResponse.body() != null ? safeResponse.body().string() : "";
                        FileLogger.log("RESPONSE_CODE: " + safeResponse.code());
                        FileLogger.log("RESPONSE_BODY: " + responseBody);

                        if (!safeResponse.isSuccessful()) {
                            callback.onFailure("Server error: " + safeResponse.code());
                            return;
                        }
                        if (responseBody.isEmpty()) {
                            callback.onFailure("Empty response from getid.cgi");
                            return;
                        }

                        String[] parts = responseBody.split("\\|");
                        if (parts.length >= 1 && !parts[0].isEmpty()) {
                            callback.onSuccess(responseBody); // Возвращаем всю строку "playerID|nick"
                        } else {
                            callback.onFailure("Could not parse playerID from response: " + responseBody);
                        }
                    }
                }
            });
        } catch (Exception e) {
            FileLogger.log("REQUEST_PREPARATION_ERROR: " + e.getMessage());
            callback.onFailure(e.getMessage() != null ? e.getMessage() : "Error during getPlayerId");
        }
    }

    /**
     * Шаг 2: Получает полную информацию о персонаже по его ID и парсит ее в объект Contact.
     * @param playerId Уникальный ID игрока.
     * @param callback Колбэк, в который возвращается готовый объект Contact.
     */
    public static void getPlayerInfo(String playerId, ApiCallback<Contact> callback) {
        try {
            if (!ensureProxyReadyForRequest("CONTACTS_GET_INFO", callback)) {
                return;
            }

            String url = "http://www.neverlands.ru/modules/api/info.cgi?playerid="
                    + playerId + "&info=1&hmu=1&effects=1&slots=1";
            Request request = buildSessionAwareGetRequest(url);

            FileLogger.log("REQUEST_URL: " + request.url());
            FileLogger.log("REQUEST_HEADERS: " + describeRequestHeaders(request));

            getClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    FileLogger.log("RESPONSE_ERROR: " + e.getMessage());
                    callback.onFailure(e.getMessage() != null ? e.getMessage() : "Unknown network error");
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try (Response safeResponse = response) {
                        String responseBody = safeResponse.body() != null ? safeResponse.body().string() : "";
                        FileLogger.log("RESPONSE_CODE: " + safeResponse.code());
                        FileLogger.log("RESPONSE_BODY: " + responseBody);

                        if (!safeResponse.isSuccessful()) {
                            callback.onFailure("Server error: " + safeResponse.code());
                            return;
                        }
                        if (responseBody.isEmpty()) {
                            callback.onFailure("Empty response from info.cgi");
                            return;
                        }

                        try {
                            // Парсинг ответа в объект Contact
                            Contact contact = parseContactInfo(playerId, responseBody);
                            callback.onSuccess(contact);
                        } catch (Exception e) {
                            callback.onFailure("Failed to parse contact info: " + e.getMessage());
                        }
                    }
                }
            });
        } catch (Exception e) {
            FileLogger.log("REQUEST_PREPARATION_ERROR: " + e.getMessage());
            callback.onFailure(e.getMessage() != null ? e.getMessage() : "Error during getPlayerInfo");
        }
    }

    /**
     * Вспомогательный метод для парсинга ответа от info.cgi.
     * @param playerId ID игрока, который был использован в запросе.
     * @param response Строка ответа сервера, разделенная символами "|".
     * @return Заполненный объект Contact.
     */
    private static Contact parseContactInfo(String playerId, String response) {
        Contact contact = new Contact();
        contact.playerID = playerId;
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty info.cgi response");
        }

        String[] rows = response.trim().split("\\r?\\n");
        List<ContactRenderHelper.EffectState> effectStates = parseEffectsFromRows(rows);
        contact.effectStates = ContactRenderHelper.toEffectStatesCsv(effectStates);
        contact.effectIds = ContactRenderHelper.toEffectIdsCsv(ContactRenderHelper.extractEffectIds(effectStates));
        if (rows.length >= 3 && rows[2].startsWith("3|")) {
            String[] parts = rows[2].substring(2).split("\\|", -1);
            if (parts.length < 15) {
                throw new IllegalArgumentException("Invalid multiline info.cgi response format");
            }
            contact.nick = parts[0];
            contact.playerLevel = ParseUtils.parseIntSafe(parts[1], 0);
            contact.inclination = ParseUtils.parseIntSafe(parts[2], 0);
            contact.clanNumber = parts[3];
            contact.clanIco = parts[4];
            contact.clanName = parts[5];
            contact.clanStatus = parts[6];
            contact.gender = ParseUtils.parseIntSafe(parts[7], 0);
            contact.blockStatus = ParseUtils.parseIntSafe(parts[8], 0);
            contact.jailStatus = ParseUtils.parseIntSafe(parts[9], 0);
            contact.muteSeconds = ParseUtils.parseIntSafe(parts[10], 0);
            contact.muteForumSeconds = ParseUtils.parseIntSafe(parts[11], 0);
            contact.onlineStatus = ParseUtils.parseIntSafe(parts[12], 0);
            contact.geoLocation = parts[13];
            contact.warLogNumber = parts[14];
        } else {
            String[] parts = response.split("\\|");
            if (parts.length < 16) {
                throw new IllegalArgumentException("Invalid singleline info.cgi response format");
            }
            contact.nick = parts[1];
            contact.playerLevel = ParseUtils.parseIntSafe(parts[2], 0);
            contact.inclination = ParseUtils.parseIntSafe(parts[3], 0);
            contact.clanNumber = parts[4];
            contact.clanIco = parts[5];
            contact.clanName = parts[6];
            contact.clanStatus = parts[7];
            contact.gender = ParseUtils.parseIntSafe(parts[8], 0);
            contact.blockStatus = ParseUtils.parseIntSafe(parts[9], 0);
            contact.jailStatus = ParseUtils.parseIntSafe(parts[10], 0);
            contact.muteSeconds = ParseUtils.parseIntSafe(parts[11], 0);
            contact.muteForumSeconds = ParseUtils.parseIntSafe(parts[12], 0);
            contact.onlineStatus = ParseUtils.parseIntSafe(parts[13], 0);
            contact.geoLocation = parts[14];
            contact.warLogNumber = parts[15];
        }

        // Преобразование цифрового ID склонности в текстовое название
        switch (contact.inclination) {
            case 4: contact.inclinationName = "Chaos"; break;
            case 3: contact.inclinationName = "Sumers"; break;
            case 2: contact.inclinationName = "Lights"; break;
            case 1: contact.inclinationName = "Darks"; break;
            default: contact.inclinationName = "0"; break;
        }
        AppLog.d("ApiRepository", "CONTACT_INFO_PARSED: nick=" + contact.nick
                + ", playerID=" + contact.playerID
                + ", onlineStatus=" + contact.onlineStatus
                + ", isOnline=" + contact.isOnline()
                + ", hasLocation=" + (contact.geoLocation != null && !contact.geoLocation.trim().isEmpty())
                + ", warLogNumber=" + (contact.warLogNumber == null ? "" : contact.warLogNumber));
        return contact;
    }

    private static List<ContactRenderHelper.EffectState> parseEffectsFromRows(String[] rows) {
        List<ContactRenderHelper.EffectState> result = new ArrayList<>();
        if (rows == null || rows.length < 2) {
            return result;
        }
        String effectsRow = rows[1];
        if (effectsRow == null || !effectsRow.startsWith("2|")) {
            return result;
        }
        String rawPayload = effectsRow.substring(2);
        if (rawPayload.trim().isEmpty()) {
            return result;
        }
        String[] effects = rawPayload.split("@");
        Map<Integer, ContactRenderHelper.EffectState> unique = new LinkedHashMap<>();
        for (String effect : effects) {
            if (effect == null || effect.trim().isEmpty()) {
                continue;
            }
            String[] parts = effect.split("\\.", 4);
            int effectId = ParseUtils.parseIntSafe(parts.length > 0 ? parts[0] : "", 0);
            if (effectId <= 0) {
                continue;
            }
            int effectCount = ParseUtils.parseIntSafe(parts.length > 2 ? parts[2] : "", 1);
            String effectTimeout = parts.length > 3 ? parts[3] : "";
            ContactRenderHelper.EffectState existing = unique.get(effectId);
            if (existing == null) {
                unique.put(effectId, new ContactRenderHelper.EffectState(effectId, effectCount, effectTimeout));
            } else {
                int mergedCount = Math.max(1, existing.count) + Math.max(1, effectCount);
                String mergedTimeout = (existing.timeout == null || existing.timeout.trim().isEmpty())
                        ? effectTimeout
                        : existing.timeout;
                unique.put(effectId, new ContactRenderHelper.EffectState(effectId, mergedCount, mergedTimeout));
            }
        }
        result.addAll(unique.values());
        return result;
    }


    /**
     * Универсальный метод для скачивания файла по URL и сохранения его на диск.
     * @param url URL для скачивания.
     * @param destinationFile Файл, в который нужно сохранить результат.
     * @param callback Колбэк, возвращающий путь к файлу в случае успеха.
     */
    public static void downloadFile(String url, File destinationFile, ApiCallback<String> callback) {
        try {
            if (!ensureProxyReadyForRequest("DOWNLOAD_FILE", callback)) {
                return;
            }

            Request request = buildSessionAwareGetRequest(url);

            FileLogger.log("DOWNLOAD_FILE_URL: " + request.url());
            FileLogger.log("DOWNLOAD_FILE_PROXY_REQUIRED: "
                    + ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile()
                    + ", PROXY_ACTIVE=" + (ProxyRuntimeManager.getActiveJavaProxyOrNull() != null));

            getClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    FileLogger.log("DOWNLOAD_FILE_ERROR: " + e.getMessage());
                    callback.onFailure(e.getMessage() != null ? e.getMessage() : "Unknown network error");
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try (Response safeResponse = response) {
                        if (!safeResponse.isSuccessful() || safeResponse.body() == null) {
                            FileLogger.log("DOWNLOAD_FILE_HTTP_ERROR: code=" + safeResponse.code()
                                    + ", url=" + url);
                            callback.onFailure("Server error or empty response: " + safeResponse.code());
                            return;
                        }
                        try (okhttp3.ResponseBody body = safeResponse.body()) {
                            okio.BufferedSource source = body.source();
                            // Создание родительских директорий, если их нет
                            File parentDir = destinationFile.getParentFile();
                            if (parentDir != null && !parentDir.exists()) {
                                if (!parentDir.mkdirs()) {
                                    callback.onFailure("Failed to create directory: " + parentDir.getPath());
                                    return;
                                }
                            }
                            // Запись файла на диск с использованием эффективной библиотеки Okio
                            try (BufferedSink sink = Okio.buffer(Okio.sink(destinationFile))) {
                                sink.writeAll(source);
                            }
                            callback.onSuccess(destinationFile.getPath());
                        } catch (Exception e) {
                            callback.onFailure("Failed to save file: " + e.getMessage());
                        }
                    }
                }
            });
        } catch (Exception e) {
            callback.onFailure(e.getMessage() != null ? e.getMessage() : "Error during file download");
        }
    }
}

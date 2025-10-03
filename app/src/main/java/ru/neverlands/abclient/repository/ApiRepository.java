package ru.neverlands.abclient.repository;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neverlands.abclient.model.Contact;
import ru.neverlands.abclient.network.NetworkClient;

import ru.neverlands.abclient.utils.CustomDebugLogger;

public class ApiRepository {

    private static OkHttpClient getClient() {
        return NetworkClient.getInstance();
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onFailure(String message);
    }

    /**
     * Шаг 1: Получает ID персонажа по его нику.
     */
    public static void getPlayerId(String nick, ApiCallback<String> callback) {
        try {
            String encodedNick = URLEncoder.encode(nick, "windows-1251");
            String url = "http://www.neverlands.ru/modules/api/getid.cgi?" + encodedNick;

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
                    .header("Referer", "http://www.neverlands.ru/main.php")
                    .build();

            CustomDebugLogger.log("REQUEST_URL: " + request.url());
            CustomDebugLogger.log("REQUEST_HEADERS: " + request.headers().toString());

            getClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    CustomDebugLogger.log("RESPONSE_ERROR: " + e.getMessage());
                    callback.onFailure(e.getMessage() != null ? e.getMessage() : "Unknown network error");
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    CustomDebugLogger.log("RESPONSE_CODE: " + response.code());
                    CustomDebugLogger.log("RESPONSE_BODY: " + responseBody);

                    if (!response.isSuccessful()) {
                        callback.onFailure("Server error: " + response.code());
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
            });
        } catch (Exception e) {
            CustomDebugLogger.log("REQUEST_PREPARATION_ERROR: " + e.getMessage());
            callback.onFailure(e.getMessage() != null ? e.getMessage() : "Error during getPlayerId");
        }
    }

    /**
     * Шаг 2: Получает полную информацию о персонаже по его ID.
     */
    public static void getPlayerInfo(String playerId, ApiCallback<Contact> callback) {
        try {
            String url = "http://www.neverlands.ru/modules/api/info.cgi?playerid=" + playerId + "&info=1";
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
                    .header("Referer", "http://www.neverlands.ru/main.php")
                    .build();

            CustomDebugLogger.log("REQUEST_URL: " + request.url());
            CustomDebugLogger.log("REQUEST_HEADERS: " + request.headers().toString());

            getClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    CustomDebugLogger.log("RESPONSE_ERROR: " + e.getMessage());
                    callback.onFailure(e.getMessage() != null ? e.getMessage() : "Unknown network error");
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    CustomDebugLogger.log("RESPONSE_CODE: " + response.code());
                    CustomDebugLogger.log("RESPONSE_BODY: " + responseBody);

                    if (!response.isSuccessful()) {
                        callback.onFailure("Server error: " + response.code());
                        return;
                    }
                    if (responseBody.isEmpty()) {
                        callback.onFailure("Empty response from info.cgi");
                        return;
                    }

                    try {
                        Contact contact = parseContactInfo(playerId, responseBody);
                        callback.onSuccess(contact);
                    } catch (Exception e) {
                        callback.onFailure("Failed to parse contact info: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            CustomDebugLogger.log("REQUEST_PREPARATION_ERROR: " + e.getMessage());
            callback.onFailure(e.getMessage() != null ? e.getMessage() : "Error during getPlayerInfo");
        }
    }

    private static Contact parseContactInfo(String playerId, String response) {
        String[] parts = response.split("\\|");
        if (parts.length < 16) {
            throw new IllegalArgumentException("Invalid info.cgi response format");
        }

        Contact contact = new Contact();
        contact.playerID = playerId;
        contact.nick = parts[1];
        contact.playerLevel = Integer.parseInt(parts[2]);
        contact.inclination = Integer.parseInt(parts[3]);
        contact.clanNumber = parts[4];
        contact.clanIco = parts[5];
        contact.clanName = parts[6];
        contact.clanStatus = parts[7];
        contact.gender = Integer.parseInt(parts[8]);
        contact.blockStatus = Integer.parseInt(parts[9]);
        contact.jailStatus = Integer.parseInt(parts[10]);
        contact.muteSeconds = Integer.parseInt(parts[11]);
        contact.muteForumSeconds = Integer.parseInt(parts[12]);
        contact.onlineStatus = Integer.parseInt(parts[13]);
        contact.geoLocation = parts[14];
        contact.warLogNumber = parts[15];

        switch (contact.inclination) {
            case 4: contact.inclinationName = "Chaos"; break;
            case 3: contact.inclinationName = "Sumers"; break;
            case 2: contact.inclinationName = "Lights"; break;
            case 1: contact.inclinationName = "Darks"; break;
            default: contact.inclinationName = "0"; break;
        }
        return contact;
    }
}

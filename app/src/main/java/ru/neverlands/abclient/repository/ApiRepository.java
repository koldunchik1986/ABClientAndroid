package ru.neverlands.abclient.repository;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.File;
import java.net.URLEncoder;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import ru.neverlands.abclient.model.Contact;
import ru.neverlands.abclient.network.NetworkClient;
import ru.neverlands.abclient.utils.CustomDebugLogger;

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
            // Кодирование ника в windows-1251 и замена пробелов на %20 для корректного URL.
            String encodedNick = URLEncoder.encode(nick, "windows-1251");
            encodedNick = encodedNick.replace("+", "%20");
            String url = "http://neverlands.ru/modules/api/getid.cgi?" + encodedNick;

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
                    .header("Referer", "http://neverlands.ru/main.php")
                    .build();

            CustomDebugLogger.log("REQUEST_URL: " + request.url());
            CustomDebugLogger.log("REQUEST_HEADERS: " + request.headers().toString());

            // Асинхронный вызов
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
     * Шаг 2: Получает полную информацию о персонаже по его ID и парсит ее в объект Contact.
     * @param playerId Уникальный ID игрока.
     * @param callback Колбэк, в который возвращается готовый объект Contact.
     */
    public static void getPlayerInfo(String playerId, ApiCallback<Contact> callback) {
        try {
            String url = "http://neverlands.ru/modules/api/info.cgi?playerid=" + playerId + "&info=1";
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
                    .header("Referer", "http://neverlands.ru/main.php")
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
                        // Парсинг ответа в объект Contact
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

    /**
     * Вспомогательный метод для парсинга ответа от info.cgi.
     * @param playerId ID игрока, который был использован в запросе.
     * @param response Строка ответа сервера, разделенная символами "|".
     * @return Заполненный объект Contact.
     */
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

        // Преобразование цифрового ID склонности в текстовое название
        switch (contact.inclination) {
            case 4: contact.inclinationName = "Chaos"; break;
            case 3: contact.inclinationName = "Sumers"; break;
            case 2: contact.inclinationName = "Lights"; break;
            case 1: contact.inclinationName = "Darks"; break;
            default: contact.inclinationName = "0"; break;
        }
        return contact;
    }

    /**
     * Универсальный метод для скачивания файла по URL и сохранения его на диск.
     * @param url URL для скачивания.
     * @param destinationFile Файл, в который нужно сохранить результат.
     * @param callback Колбэк, возвращающий путь к файлу в случае успеха.
     */
    public static void downloadFile(String url, File destinationFile, ApiCallback<String> callback) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            getClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    callback.onFailure(e.getMessage() != null ? e.getMessage() : "Unknown network error");
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onFailure("Server error or empty response: " + response.code());
                        return;
                    }
                    try (okhttp3.ResponseBody body = response.body()) {
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
            });
        } catch (Exception e) {
            callback.onFailure(e.getMessage() != null ? e.getMessage() : "Error during file download");
        }
    }
}
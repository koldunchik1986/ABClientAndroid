package ru.neverlands.abclient.manager;

import android.webkit.CookieManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

/**
 * Портирование NeverApi.cs — прямые HTTP-запросы к API Neverlands.
 * Используется для получения информации о игроке (FightLog) и опроса боевого лога.
 *
 * Ключевые методы:
 *   getAll(nick)  — получить UserInfo включая FightLog (ID боя)
 *   getFlog(fid)  — получить HTML боевого лога для проверки окончания боя
 */
public class NeverApi {

    private static final String TAG = "NeverApi";
    private static final int TIMEOUT_MS = 10000;

    // Кэш nick → userId (аналог NameToId в C#)
    private static final Map<String, String> nameToId = new HashMap<>();

    // -----------------------------------------------------------------------
    // Публичный API
    // -----------------------------------------------------------------------

    /**
     * Получить userId по нику (кэшируется).
     * GET http://www.neverlands.ru/modules/api/getid.cgi?{nick}
     * Ответ: "userId|NickName"
     */
    public static String getUserId(String nick) {
        synchronized (nameToId) {
            if (nameToId.containsKey(nick)) return nameToId.get(nick);
        }
        try {
            String encoded = URLEncoder.encode(nick, "windows-1251");
            String data = getInfo("http://www.neverlands.ru/modules/api/getid.cgi?" + encoded);
            if (data == null || data.isEmpty()) return null;
            String[] parts = data.split("\\|");
            if (parts.length != 2) return null;
            String id = parts[0].trim();
            String name = parts[1].trim();
            synchronized (nameToId) {
                nameToId.put(name, id);
            }
            return id;
        } catch (Exception e) {
            android.util.Log.w(TAG, "getUserId failed for " + nick, e);
            return null;
        }
    }

    /**
     * Полная информация об игроке.
     * GET http://www.neverlands.ru/modules/api/info.cgi?playerid={id}&info=1&hmu=1&effects=1&slots=1
     *
     * Ответ — 4 строки через \n, поле sp3[14] = FightLog (ID боя или "0").
     *
     * @return UserInfo или null при ошибке
     */
    public static UserInfo getAll(String nick) {
        String id = getUserId(nick);
        if (id == null) return null;

        String data = getInfo(
            "http://www.neverlands.ru/modules/api/info.cgi?playerid=" + id
            + "&info=1&hmu=1&effects=1&slots=1");
        if (data == null || data.isEmpty()) return null;

        // Формат: строки 1..4 разделены \n, каждая начинается с "N|"
        String[] sp = data.split("\n");
        if (sp.length < 4) return null;

        // Строка 3: "3|Nick|Level|Align|ClanCode|...|FightLog"  (sp3[14])
        String line3 = sp[2];
        if (line3.length() < 2) return null;
        String[] sp3 = line3.substring(2).split("\\|", -1);
        if (sp3.length < 15) return null;

        UserInfo info = new UserInfo();
        info.nick = sp3[0].trim();
        info.fightLog = sp3[14].trim();
        if (info.fightLog.equals("0")) info.fightLog = "";

        android.util.Log.d(TAG, "getAll: nick=" + info.nick + " fightLog=" + info.fightLog);
        return info;
    }

    /**
     * HTML боевого лога для проверки окончания боя.
     * GET http://neverlands.ru/logs.fcg?fid={flog}
     *
     * В ответе ищем "var off = 1;" — если найдено, бой завершён.
     */
    public static String getFlog(String flog) {
        return getInfo("http://neverlands.ru/logs.fcg?fid=" + flog);
    }

    // -----------------------------------------------------------------------
    // Внутренний HTTP-клиент
    // -----------------------------------------------------------------------

    /**
     * Выполняет GET-запрос с куками WebView (аналог CookieAwareWebClient в C#).
     * Декодирует ответ из windows-1251.
     */
    /**
     * Универсальный GET-запрос к API Neverlands с cookies текущей WebView-сессии.
     *
     * Что делает:
     * - открывает {@link HttpURLConnection} с таймаутами,
     * - добавляет Cookie из {@link CookieManager} для текущего URL,
     * - отправляет единый браузерный User-Agent ({@link AppVars#BROWSER_USER_AGENT}),
     * - читает ответ в bytes и декодирует через windows-1251.
     *
     * Метод является базовой сетевой зависимостью для getUserId/getAll/getFlog.
     *
     * @param urlString полный URL API-эндпоинта.
     * @return строка ответа или {@code null} при ошибке/HTTP != 200.
     */
    static String getInfo(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestMethod("GET");

            // Передаём куки сессии из WebView
            String cookie = CookieManager.getInstance().getCookie(urlString);
            if (cookie != null && !cookie.isEmpty()) {
                conn.setRequestProperty("Cookie", cookie);
            }
            conn.setRequestProperty("User-Agent", AppVars.BROWSER_USER_AGENT);

            int code = conn.getResponseCode();
            if (code != 200) {
                android.util.Log.w(TAG, "getInfo: HTTP " + code + " for " + urlString);
                return null;
            }

            // Читаем ответ в bytes, затем декодируем windows-1251
            java.io.InputStream is = conn.getInputStream();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            is.close();

            return Russian.getString(baos.toByteArray());

        } catch (Exception e) {
            android.util.Log.w(TAG, "getInfo failed: " + urlString, e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // -----------------------------------------------------------------------
    // UserInfo (аналог класса UserInfo в C#)
    // -----------------------------------------------------------------------

    public static class UserInfo {
        public String nick = "";
        /** ID боевого лога. Пустая строка = не в бою. */
        public String fightLog = "";
    }
}

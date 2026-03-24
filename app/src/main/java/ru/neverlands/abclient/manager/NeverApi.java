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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.proxy.ProxyRuntimeManager;
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
    private static final int POISON_INDEX = 0;
    private static final int LIGHT_WOUND_INDEX = 1;
    private static final int MEDIUM_WOUND_INDEX = 2;
    private static final int HEAVY_WOUND_INDEX = 3;

    // Кэш nick → userId (аналог NameToId в C#)
    private static final Map<String, String> nameToId = new HashMap<>();

    /**
     * Снимок vitals из pinfo.cgi:
     * - HP: curHp/maxHp
     * - MA: curMa/maxMa
     * - усталость: curTire (0..100)
     * - отравление/травмы: poisonAndWounds ([яд, легк, сред, тяж])
     */
    public static final class PinfoVitals {
        public final Integer curHp;
        public final Integer maxHp;
        public final Integer curMa;
        public final Integer maxMa;
        public final Integer curTire;
        public final int[] poisonAndWounds;

        public PinfoVitals(Integer curHp, Integer maxHp, Integer curMa, Integer maxMa, Integer curTire,
                           int[] poisonAndWounds) {
            this.curHp = curHp;
            this.maxHp = maxHp;
            this.curMa = curMa;
            this.maxMa = maxMa;
            this.curTire = curTire;
            this.poisonAndWounds = normalizePoisonAndWounds(poisonAndWounds);
        }
    }

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

    /**
     * Текущая усталость персонажа через pinfo.cgi.
     *
     * Возвращает проценты текущей усталости (curTire, 0..100) или null,
     * если значение не удалось извлечь.
     */
    public static Integer getCurrentTiedFromPinfo(String nick) {
        PinfoVitals vitals = getPinfoVitalsFromPinfo(nick);
        if (vitals == null) {
            return null;
        }
        return vitals.curTire;
    }

    /**
     * Полная синхронизация vitals через pinfo.cgi.
     */
    public static PinfoVitals getPinfoVitalsFromPinfo(String nick) {
        if (nick == null || nick.trim().isEmpty()) {
            return null;
        }
        try {
            String encoded = URLEncoder.encode(nick.trim(), "windows-1251");
            String html = getInfo("http://neverlands.ru/pinfo.cgi?" + encoded);
            PinfoVitals vitals = parsePinfoVitalsFromPinfoHtml(html);
            if (vitals != null) {
                android.util.Log.d(TAG, "AUTO_BLAZ_TRACE pinfo vitals sync: nick=" + nick
                        + ", hp=" + safeInt(vitals.curHp) + "/" + safeInt(vitals.maxHp)
                        + ", ma=" + safeInt(vitals.curMa) + "/" + safeInt(vitals.maxMa)
                        + ", tied=" + safeInt(vitals.curTire)
                        + ", pw=" + safePoisonAndWounds(vitals.poisonAndWounds));
            } else {
                android.util.Log.w(TAG, "AUTO_BLAZ_TRACE pinfo vitals sync failed: value not found for nick=" + nick);
            }
            return vitals;
        } catch (Exception e) {
            android.util.Log.w(TAG, "AUTO_BLAZ_TRACE pinfo vitals sync error for nick=" + nick, e);
            return null;
        }
    }

    private static Integer parseCurrentTiedFromPinfoHtml(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        try {
            // 1) Предпочтительно: hpmp = [curHp, maxHp, curMa, maxMa, maxTire]
            Matcher hpmpMatcher = Pattern.compile("(?is)\\bhpmp\\b\\s*=\\s*\\[(.*?)\\]").matcher(html);
            if (hpmpMatcher.find()) {
                String[] hpmp = hpmpMatcher.group(1).split(",");
                if (hpmp.length >= 5) {
                    Matcher maxTireDigits = Pattern.compile("(\\d{1,3})").matcher(hpmp[4]);
                    if (maxTireDigits.find()) {
                        int maxTire = Integer.parseInt(maxTireDigits.group(1));
                        return clampPercent(100 - maxTire);
                    }
                }
            }

            // 2) Fallback: текст "Усталость ... NN%"
            Matcher textMatcher = Pattern
                    .compile("(?is)\\u0423\\u0441\\u0442\\u0430\\u043B\\u043E\\u0441\\u0442\\u044C[^\\d]{0,64}(\\d{1,3})\\s*%")
                    .matcher(html);
            if (textMatcher.find()) {
                return clampPercent(Integer.parseInt(textMatcher.group(1)));
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "AUTO_BLAZ_TRACE parseCurrentTiedFromPinfoHtml failed", e);
        }
        return null;
    }

    private static PinfoVitals parsePinfoVitalsFromPinfoHtml(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        try {
            Integer curHp = null;
            Integer maxHp = null;
            Integer curMa = null;
            Integer maxMa = null;
            Integer curTire = null;
            int[] poisonAndWounds = parsePoisonAndWoundsFromPinfoEff(html);

            Matcher hpmpMatcher = Pattern.compile("(?is)\\bhpmp\\b\\s*=\\s*\\[(.*?)\\]").matcher(html);
            if (hpmpMatcher.find()) {
                String[] hpmp = hpmpMatcher.group(1).split(",");
                if (hpmp.length >= 5) {
                    curHp = parseIntToken(hpmp[0]);
                    maxHp = parseIntToken(hpmp[1]);
                    curMa = parseIntToken(hpmp[2]);
                    maxMa = parseIntToken(hpmp[3]);
                    Integer maxTire = parseIntToken(hpmp[4]);
                    if (maxTire != null) {
                        curTire = clampPercent(100 - maxTire);
                    }
                }
            }

            if (curTire == null) {
                Integer fallbackTied = parseCurrentTiedFromPinfoHtml(html);
                if (fallbackTied != null) {
                    curTire = clampPercent(fallbackTied);
                }
            }

            if (curHp == null && maxHp == null && curMa == null && maxMa == null
                    && curTire == null && poisonAndWounds == null) {
                return null;
            }
            return new PinfoVitals(curHp, maxHp, curMa, maxMa, curTire, poisonAndWounds);
        } catch (Exception e) {
            android.util.Log.w(TAG, "AUTO_BLAZ_TRACE parsePinfoVitalsFromPinfoHtml failed", e);
            return null;
        }
    }

    /**
     * Парсит `var eff = [[code,'name'], ...];` из pinfo и возвращает
     * runtime-массив [яд, легк, сред, тяж] (C# parity `PoisonAndWounds`).
     *
     * Важно:
     * - если блок `eff` отсутствует, возвращается `null` (не перезатираем текущее runtime-состояние);
     * - если блок есть, но релевантных эффектов нет, возвращается `[0,0,0,0]`.
     */
    private static int[] parsePoisonAndWoundsFromPinfoEff(String html) {
        try {
            Matcher effMatcher = Pattern.compile("(?is)\\bvar\\s+eff\\s*=\\s*(\\[[\\s\\S]*?\\]);").matcher(html);
            if (!effMatcher.find()) {
                return null;
            }
            String effPayload = effMatcher.group(1);
            int[] poisonAndWounds = new int[] {0, 0, 0, 0};
            Matcher codeMatcher = Pattern.compile("\\[(\\d{1,3})\\s*,").matcher(effPayload);
            while (codeMatcher.find()) {
                int code;
                try {
                    code = Integer.parseInt(codeMatcher.group(1));
                } catch (Exception ignored) {
                    continue;
                }
                switch (code) {
                    case 24:
                        poisonAndWounds[POISON_INDEX]++;
                        break;
                    case 4:
                        poisonAndWounds[LIGHT_WOUND_INDEX]++;
                        break;
                    case 3:
                        poisonAndWounds[MEDIUM_WOUND_INDEX]++;
                        break;
                    case 2:
                        poisonAndWounds[HEAVY_WOUND_INDEX]++;
                        break;
                    default:
                        break;
                }
            }
            return poisonAndWounds;
        } catch (Exception e) {
            android.util.Log.w(TAG, "AUTO_BLAZ_TRACE parsePoisonAndWoundsFromPinfoEff failed", e);
            return null;
        }
    }

    private static Integer parseIntToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Matcher digits = Pattern.compile("(\\d{1,6})").matcher(token);
        if (!digits.find()) {
            return null;
        }
        try {
            return Integer.parseInt(digits.group(1));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safeInt(Integer value) {
        return value == null ? "?" : String.valueOf(value);
    }

    private static int[] normalizePoisonAndWounds(int[] poisonAndWounds) {
        if (poisonAndWounds == null || poisonAndWounds.length < 4) {
            return null;
        }
        return new int[] {
                Math.max(0, poisonAndWounds[POISON_INDEX]),
                Math.max(0, poisonAndWounds[LIGHT_WOUND_INDEX]),
                Math.max(0, poisonAndWounds[MEDIUM_WOUND_INDEX]),
                Math.max(0, poisonAndWounds[HEAVY_WOUND_INDEX])
        };
    }

    private static String safePoisonAndWounds(int[] poisonAndWounds) {
        if (poisonAndWounds == null || poisonAndWounds.length < 4) {
            return "n/a";
        }
        return "[" + poisonAndWounds[POISON_INDEX]
                + "," + poisonAndWounds[LIGHT_WOUND_INDEX]
                + "," + poisonAndWounds[MEDIUM_WOUND_INDEX]
                + "," + poisonAndWounds[HEAVY_WOUND_INDEX] + "]";
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
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
            java.net.Proxy activeProxy = ProxyRuntimeManager.getActiveJavaProxyOrNull();
            if (activeProxy == null && ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile()) {
                android.util.Log.e(TAG, "PROXY_FAIL: strict proxy enabled and runtime proxy unavailable, blocking direct NeverApi call: " + urlString);
                return null;
            }
            android.util.Log.d(TAG, "PROXY_BINDING: NeverApi openConnection via "
                    + (activeProxy != null ? "local proxy" : "direct")
                    + ", url=" + urlString);
            conn = activeProxy != null
                    ? (HttpURLConnection) url.openConnection(activeProxy)
                    : (HttpURLConnection) url.openConnection();
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

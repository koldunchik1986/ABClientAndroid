package ru.neverlands.abclient.manager;


import ru.neverlands.abclient.utils.AppLog;
import android.webkit.CookieManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    // Локальный HTTP-статус последнего sync-запроса внутри текущего потока.
    // Используется как транспорт статуса из getInfo(...) в вызывающий код без изменения сигнатур.
    // ✅ API 21 compatible ThreadLocal initialization (withInitial requires API 26)
    private static final ThreadLocal<Integer> lastHttpStatusCode = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };
    // Последний HTTP-статус именно для pinfo-запроса компаса.
    // Читается в AutoFunctionsManager для адаптивного backoff (535/536).
    private static volatile int lastCompassPinfoHttpStatus = 0;

    // Кэш nick → userId (аналог NameToId в C#)
    private static final Map<String, String> nameToId = new HashMap<>();

    /**
     * @return последний HTTP-код pinfo-запроса компаса.
     * 0 = нет данных, 200 = успешный парсинг, 535/536 = server-side rate-limit/anti-bot throttling,
     * -1 = локальная ошибка запроса до получения валидного кода.
     */
    public static int getLastCompassPinfoHttpStatus() {
        return lastCompassPinfoHttpStatus;
    }

    /**
     * Утилита для быстрых проверок "ограничил ли сервер pinfo".
     * Используется в AutoCompass-контуре для мягкого backoff вместо немедленного stop.
     */
    public static boolean wasLastCompassPinfoRateLimited() {
        int code = lastCompassPinfoHttpStatus;
        return code == 535 || code == 536;
    }

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
        // 0=no wounds, 1=light, 2=medium, 3=heavy, 4=battle
        public final Integer topWoundType;

        public PinfoVitals(Integer curHp, Integer maxHp, Integer curMa, Integer maxMa, Integer curTire,
                           int[] poisonAndWounds) {
            this(curHp, maxHp, curMa, maxMa, curTire, poisonAndWounds, null);
        }

        public PinfoVitals(Integer curHp, Integer maxHp, Integer curMa, Integer maxMa, Integer curTire,
                           int[] poisonAndWounds, Integer topWoundType) {
            this.curHp = curHp;
            this.maxHp = maxHp;
            this.curMa = curMa;
            this.maxMa = maxMa;
            this.curTire = curTire;
            this.poisonAndWounds = normalizePoisonAndWounds(poisonAndWounds);
            this.topWoundType = normalizeTopWoundType(topWoundType);
        }
    }

    /**
     * Снимок PINFO для контура "Компас/Авто-компас".
     * Содержит только то, что нужно для цикла поиска:
     * - ник цели,
     * - текст местоположения (parameters[0][5]),
     * - текущую усталость (0..100),
     * - серверное время получения снимка.
     */
    public static final class PinfoCompassSnapshot {
        public final String nick;
        public final String locationRaw;
        public final String locationRegion;
        public final String locationName;
        public final String clanToken;
        public final String fightFid;
        public final Integer level;
        public final Integer inclination;
        public final boolean offlineOrInvisible;
        public final Integer curTire;
        public final long capturedAtMs;

        public PinfoCompassSnapshot(
                String nick,
                String locationRaw,
                String locationRegion,
                String locationName,
                String clanToken,
                String fightFid,
                Integer level,
                Integer inclination,
                boolean offlineOrInvisible,
                Integer curTire,
                long capturedAtMs) {
            this.nick = nick == null ? "" : nick.trim();
            this.locationRaw = locationRaw == null ? "" : locationRaw.trim();
            this.locationRegion = locationRegion == null ? "" : locationRegion.trim();
            this.locationName = locationName == null ? "" : locationName.trim();
            this.clanToken = clanToken == null ? "" : clanToken.trim();
            this.fightFid = normalizeFightFid(fightFid);
            this.level = level;
            this.inclination = inclination;
            this.offlineOrInvisible = offlineOrInvisible;
            this.curTire = curTire;
            this.capturedAtMs = capturedAtMs;
        }

        public boolean isValid() {
            return !locationName.isEmpty();
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
            String encoded = encodeNeverlandsQueryTail(nick);
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
            AppLog.w(TAG, "getUserId failed for " + nick, e);
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

        AppLog.d(TAG, "getAll: nick=" + info.nick + " fightLog=" + info.fightLog);
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
            String encoded = encodeNeverlandsQueryTail(nick.trim());
            String html = getInfo("http://neverlands.ru/pinfo.cgi?" + encoded);
            PinfoVitals vitals = parsePinfoVitalsFromPinfoHtml(html);
            if (vitals != null) {
                AppLog.d(TAG, "AUTO_BLAZ_TRACE pinfo vitals sync: nick=" + nick
                        + ", hp=" + safeInt(vitals.curHp) + "/" + safeInt(vitals.maxHp)
                        + ", ma=" + safeInt(vitals.curMa) + "/" + safeInt(vitals.maxMa)
                        + ", tied=" + safeInt(vitals.curTire)
                        + ", pw=" + safePoisonAndWounds(vitals.poisonAndWounds)
                        + ", topWound=" + safeWoundType(vitals.topWoundType));
            } else {
                AppLog.w(TAG, "AUTO_BLAZ_TRACE pinfo vitals sync failed: value not found for nick=" + nick);
            }
            return vitals;
        } catch (Exception e) {
            AppLog.w(TAG, "AUTO_BLAZ_TRACE pinfo vitals sync error for nick=" + nick, e);
            return null;
        }
    }

    /**
     * Снимок pinfo для компаса (ник + местоположение + усталость).
     *
     * Назначение:
     * - единая точка, где AutoCompass получает "состояние цели" для принятия решений;
     * - параллельно фиксирует HTTP-статус, который нужен для адаптивного интервала опроса.
     *
     * Зависимости:
     * - `encodeNeverlandsQueryTail(...)` — корректный query-tail в windows-1251 + `%20` вместо `+`;
     * - `getInfo(...)` — транспорт HTTP с anti-detect User-Agent и общим timeout;
     * - `parsePinfoCompassSnapshotFromHtml(...)` — извлечение region/location/tired из JS `var parameters`.
     *
     * Контракт статусов:
     * - при успешном parse выставляется 200, если код не был установлен явно;
     * - при сетевой/парсинг-ошибке сохраняется последний код (или -1 как fallback).
     */
    public static PinfoCompassSnapshot getPinfoCompassSnapshot(String nick) {
        if (nick == null || nick.trim().isEmpty()) {
            return null;
        }
        lastCompassPinfoHttpStatus = 0;
        try {
            String normalizedNick = nick.trim();
            String encoded = encodeNeverlandsQueryTail(normalizedNick);
            String html = getInfo("http://neverlands.ru/pinfo.cgi?" + encoded);
            int lastStatus = lastHttpStatusCode.get() == null ? 0 : lastHttpStatusCode.get();
            if (lastStatus != 0) {
                lastCompassPinfoHttpStatus = lastStatus;
            }
            PinfoCompassSnapshot snapshot = parsePinfoCompassSnapshotFromHtml(normalizedNick, html);
            if (snapshot != null) {
                if (lastCompassPinfoHttpStatus == 0) {
                    lastCompassPinfoHttpStatus = 200;
                }
                AppLog.d(TAG, "AUTO_COMPASS_TRACE snapshot: nick=" + snapshot.nick
                        + ", locationRaw=" + snapshot.locationRaw
                        + ", region=" + snapshot.locationRegion
                        + ", location=" + snapshot.locationName
                        + ", clanToken=" + snapshot.clanToken
                        + ", fightFid=" + snapshot.fightFid
                        + ", level=" + safeInt(snapshot.level)
                        + ", inclination=" + safeInt(snapshot.inclination)
                        + ", offlineOrInvisible=" + snapshot.offlineOrInvisible
                        + ", tied=" + safeInt(snapshot.curTire)
                        + ", capturedAt=" + snapshot.capturedAtMs);
            } else {
                AppLog.w(TAG, "AUTO_COMPASS_TRACE snapshot: parse failed for nick=" + normalizedNick);
            }
            return snapshot;
        } catch (Exception e) {
            if (lastCompassPinfoHttpStatus == 0) {
                lastCompassPinfoHttpStatus = -1;
            }
            AppLog.w(TAG, "AUTO_COMPASS_TRACE snapshot: request failed for nick=" + nick, e);
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
            AppLog.w(TAG, "AUTO_BLAZ_TRACE parseCurrentTiedFromPinfoHtml failed", e);
        }
        return null;
    }

    /**
     * Кодирует ник в формат "query tail" для URL вида `...pinfo.cgi?<nick>` / `...getid.cgi?<nick>`.
     *
     * Важный нюанс сервера Neverlands:
     * - пробел в хвосте query должен быть `%20`, а не `+`;
     * - при `+` сервер периодически возвращает HTTP 536 (воспроизводится на никах с пробелами).
     */
    private static String encodeNeverlandsQueryTail(String value) throws Exception {
        String encoded = URLEncoder.encode(value, "windows-1251");
        return encoded.replace("+", "%20");
    }

    private static PinfoCompassSnapshot parsePinfoCompassSnapshotFromHtml(String nick, String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        try {
            List<String> tupleElements = parsePinfoFirstTupleElements(html);
            String locationRaw = parsePinfoLocationFromParameters(tupleElements);
            String[] locationParts = splitPinfoLocationToRegionAndCell(locationRaw);
            String locationRegion = locationParts[0];
            String locationName = locationParts[1];
            Boolean onlineFlag = parsePinfoOnlineFlagFromParameters(tupleElements);
            String clanToken = parsePinfoClanTokenFromParameters(tupleElements);
            String fightFid = parsePinfoFightFidFromParameters(tupleElements);
            Integer level = parsePinfoLevelFromParameters(tupleElements);
            Integer inclination = parsePinfoInclinationFromParameters(tupleElements);
            boolean offlineOrInvisible = (onlineFlag != null && !onlineFlag)
                    || (locationName == null || locationName.trim().isEmpty());
            Integer tied = parseCurrentTiedFromPinfoHtml(html);
            if ((locationRaw == null || locationRaw.trim().isEmpty())
                    && tied == null
                    && onlineFlag == null
                    && isEmpty(clanToken)
                    && isEmpty(fightFid)
                    && level == null
                    && inclination == null) {
                return null;
            }
            return new PinfoCompassSnapshot(
                    nick,
                    locationRaw,
                    locationRegion,
                    locationName,
                    clanToken,
                    fightFid,
                    level,
                    inclination,
                    offlineOrInvisible,
                    tied,
                    System.currentTimeMillis());
        } catch (Exception e) {
            AppLog.w(TAG, "AUTO_COMPASS_TRACE parsePinfoCompassSnapshotFromHtml failed", e);
            return null;
        }
    }

    private static Boolean parsePinfoOnlineFlagFromParameters(String html) {
        return parsePinfoOnlineFlagFromParameters(parsePinfoFirstTupleElements(html));
    }

    private static Boolean parsePinfoOnlineFlagFromParameters(List<String> tupleElements) {
        if (tupleElements == null || tupleElements.isEmpty()) {
            return null;
        }
        if (tupleElements.size() <= 6) {
            return null;
        }
        Integer onlineFlag = parseIntToken(tupleElements.get(6));
        if (onlineFlag == null) {
            return null;
        }
        return onlineFlag > 0;
    }

    private static String parsePinfoClanTokenFromParameters(List<String> tupleElements) {
        if (tupleElements == null || tupleElements.size() <= 2) {
            return "";
        }
        String token = unwrapJsString(tupleElements.get(2));
        return token == null ? "" : token.trim();
    }

    private static String parsePinfoFightFidFromParameters(List<String> tupleElements) {
        if (tupleElements == null || tupleElements.size() <= 7) {
            return "";
        }
        String fid = unwrapJsString(tupleElements.get(7));
        return normalizeFightFid(fid);
    }

    private static Integer parsePinfoLevelFromParameters(List<String> tupleElements) {
        if (tupleElements == null || tupleElements.size() <= 3) {
            return null;
        }
        // В pinfo var parameters[0]:
        // [0]=nick, [1]=inclination, [2]=clanToken, [3]=level, ...
        Integer level = parseIntToken(tupleElements.get(3));
        if (level == null || level < 0) {
            return null;
        }
        return level;
    }

    private static Integer parsePinfoInclinationFromParameters(List<String> tupleElements) {
        if (tupleElements == null || tupleElements.size() <= 1) {
            return null;
        }
        Integer inclination = parseIntToken(tupleElements.get(1));
        if (inclination == null || inclination < 0) {
            return null;
        }
        return inclination;
    }

    private static String normalizeFightFid(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.isEmpty() || "0".equals(text) || "null".equalsIgnoreCase(text)) {
            return "";
        }
        Matcher digits = Pattern.compile("(\\d{1,16})").matcher(text);
        if (digits.find()) {
            return digits.group(1);
        }
        return text;
    }

    private static List<String> parsePinfoFirstTupleElements(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        String firstTuple = extractFirstParametersTuple(html);
        if (firstTuple == null || firstTuple.isEmpty()) {
            return null;
        }
        return parseTopLevelJsArrayElements(firstTuple);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Достаёт `parameters[0][5]` из pinfo:
     * var parameters = [[...,'Локация',...], [...], [...]];
     */
    private static String parsePinfoLocationFromParameters(String html) {
        return parsePinfoLocationFromParameters(parsePinfoFirstTupleElements(html));
    }

    private static String parsePinfoLocationFromParameters(List<String> tupleElements) {
        if (tupleElements == null || tupleElements.isEmpty()) {
            return null;
        }
        // В оригинальном JS местоположение — 6-е поле (index 5) в parameters[0].
        if (tupleElements.size() <= 5) {
            return null;
        }
        String location = unwrapJsString(tupleElements.get(5));
        if (location == null) {
            return null;
        }
        return location.trim();
    }

    /**
     * Формат pinfo-локации: "Region [CellName]".
     * Для поиска по карте сейчас используем именно CellName (внутри скобок),
     * а Region только сохраняем/логируем для будущей точной привязки.
     */
    private static String[] splitPinfoLocationToRegionAndCell(String locationRaw) {
        String[] result = new String[] {"", ""};
        if (locationRaw == null) {
            return result;
        }
        String value = locationRaw.trim();
        if (value.isEmpty()) {
            return result;
        }
        int bracketOpen = value.indexOf('[');
        int bracketClose = value.lastIndexOf(']');
        if (bracketOpen >= 0 && bracketClose > bracketOpen) {
            result[0] = value.substring(0, bracketOpen).trim();
            result[1] = value.substring(bracketOpen + 1, bracketClose).trim();
            if (result[1].isEmpty()) {
                result[1] = value;
            }
            return result;
        }
        result[1] = value;
        return result;
    }

    /**
     * Извлекает содержимое первого кортежа `parameters[0]` из блока:
     * `var parameters = [[...], [...], ...];`
     *
     * Важно: здесь нельзя использовать простой regex с `.*?`, потому что в
     * строковом поле location встречаются `[` и `]` (например, название замка),
     * и regex обрывает кортеж раньше времени.
     */
    private static String extractFirstParametersTuple(String html) {
        Matcher parametersMatcher = Pattern.compile("(?is)\\bvar\\s+parameters\\s*=\\s*\\[").matcher(html);
        if (!parametersMatcher.find()) {
            return null;
        }

        int outerArrayOpen = parametersMatcher.end() - 1;
        int tupleOpen = -1;
        boolean inSingleQuote = false;
        boolean escaped = false;
        for (int index = outerArrayOpen + 1; index < html.length(); index++) {
            char ch = html.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '\'') {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (inSingleQuote) {
                continue;
            }
            if (ch == '[') {
                tupleOpen = index;
                break;
            }
            if (!Character.isWhitespace(ch)) {
                return null;
            }
        }
        if (tupleOpen < 0) {
            return null;
        }

        int depth = 1;
        inSingleQuote = false;
        escaped = false;
        for (int index = tupleOpen + 1; index < html.length(); index++) {
            char ch = html.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '\'') {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (inSingleQuote) {
                continue;
            }
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    return html.substring(tupleOpen + 1, index);
                }
            }
        }
        return null;
    }

    private static List<String> parseTopLevelJsArrayElements(String source) {
        ArrayList<String> result = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        StringBuilder token = new StringBuilder();
        boolean inSingleQuote = false;
        boolean escaped = false;
        int bracketDepth = 0;

        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (escaped) {
                token.append(ch);
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                token.append(ch);
                escaped = true;
                continue;
            }
            if (ch == '\'') {
                token.append(ch);
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (!inSingleQuote) {
                if (ch == '[') {
                    bracketDepth++;
                    token.append(ch);
                    continue;
                }
                if (ch == ']' && bracketDepth > 0) {
                    bracketDepth--;
                    token.append(ch);
                    continue;
                }
                if (ch == ',' && bracketDepth == 0) {
                    result.add(token.toString().trim());
                    token.setLength(0);
                    continue;
                }
            }
            token.append(ch);
        }

        if (token.length() > 0) {
            result.add(token.toString().trim());
        }
        return result;
    }

    private static String unwrapJsString(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.replace("\\'", "'").replace("\\\\", "\\");
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
            Integer topWoundType = parseTopWoundTypeFromPinfoEff(html);

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
                    && curTire == null && poisonAndWounds == null && topWoundType == null) {
                return null;
            }
            return new PinfoVitals(curHp, maxHp, curMa, maxMa, curTire, poisonAndWounds, topWoundType);
        } catch (Exception e) {
            AppLog.w(TAG, "AUTO_BLAZ_TRACE parsePinfoVitalsFromPinfoHtml failed", e);
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
            AppLog.w(TAG, "AUTO_BLAZ_TRACE parsePoisonAndWoundsFromPinfoEff failed", e);
            return null;
        }
    }

    /**
     * Returns top wound type from pinfo `var eff`:
     * 4=battle, 3=heavy, 2=medium, 1=light, 0=no wounds.
     * Returns null if `eff` block is absent.
     */
    private static Integer parseTopWoundTypeFromPinfoEff(String html) {
        try {
            Matcher effMatcher = Pattern.compile("(?is)\\bvar\\s+eff\\s*=\\s*(\\[[\\s\\S]*?\\]);").matcher(html);
            if (!effMatcher.find()) {
                return null;
            }
            String effPayload = effMatcher.group(1);
            boolean hasBattle = false;
            boolean hasHeavy = false;
            boolean hasMedium = false;
            boolean hasLight = false;

            Matcher codeMatcher = Pattern.compile("\\[(\\d{1,3})\\s*,").matcher(effPayload);
            while (codeMatcher.find()) {
                int code;
                try {
                    code = Integer.parseInt(codeMatcher.group(1));
                } catch (Exception ignored) {
                    continue;
                }
                switch (code) {
                    case 1:
                        hasBattle = true;
                        break;
                    case 2:
                        hasHeavy = true;
                        break;
                    case 3:
                        hasMedium = true;
                        break;
                    case 4:
                        hasLight = true;
                        break;
                    default:
                        break;
                }
            }

            if (hasBattle) {
                return 4;
            }
            if (hasHeavy) {
                return 3;
            }
            if (hasMedium) {
                return 2;
            }
            if (hasLight) {
                return 1;
            }
            return 0;
        } catch (Exception e) {
            AppLog.w(TAG, "AUTO_BLAZ_TRACE parseTopWoundTypeFromPinfoEff failed", e);
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

    private static Integer normalizeTopWoundType(Integer woundType) {
        if (woundType == null) {
            return null;
        }
        int normalized = woundType;
        if (normalized < 0) {
            normalized = 0;
        }
        if (normalized > 4) {
            normalized = 4;
        }
        return normalized;
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

    private static String safeWoundType(Integer woundType) {
        return woundType == null ? "n/a" : String.valueOf(woundType);
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
            lastHttpStatusCode.set(0);
            URL url = new URL(urlString);
            java.net.Proxy activeProxy = ProxyRuntimeManager.getActiveJavaProxyOrNull();
            if (activeProxy == null && ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile()) {
                AppLog.e(TAG, "PROXY_FAIL: strict proxy enabled and runtime proxy unavailable, blocking direct NeverApi call: " + urlString);
                return null;
            }
            AppLog.d(TAG, "PROXY_BINDING: NeverApi openConnection via "
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
            lastHttpStatusCode.set(code);
            if (code != 200) {
                boolean retryable536 = (code == 536) && shouldRetryNeverApiRequest(urlString);
                AppLog.w(TAG, "getInfo: HTTP " + code + " for " + urlString
                        + (retryable536 ? " (retry)" : ""));
                if (retryable536) {
                    sleepRetryQuietly(220L);
                    return getInfoRetryOnce(urlString);
                }
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
            lastHttpStatusCode.set(-1);
            AppLog.w(TAG, "getInfo failed: " + urlString, e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Повторный единичный запрос для кратковременных сбоев pinfo/getid (HTTP 536).
     * Без рекурсии, чтобы не зациклиться при постоянной ошибке сервера.
     */
    private static String getInfoRetryOnce(String urlString) {
        HttpURLConnection conn = null;
        try {
            lastHttpStatusCode.set(0);
            URL url = new URL(urlString);
            java.net.Proxy activeProxy = ProxyRuntimeManager.getActiveJavaProxyOrNull();
            if (activeProxy == null && ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile()) {
                AppLog.e(TAG, "PROXY_FAIL: strict proxy enabled and runtime proxy unavailable, blocking direct NeverApi retry: " + urlString);
                return null;
            }

            conn = activeProxy != null
                    ? (HttpURLConnection) url.openConnection(activeProxy)
                    : (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestMethod("GET");

            String cookie = CookieManager.getInstance().getCookie(urlString);
            if (cookie != null && !cookie.isEmpty()) {
                conn.setRequestProperty("Cookie", cookie);
            }
            conn.setRequestProperty("User-Agent", AppVars.BROWSER_USER_AGENT);

            int code = conn.getResponseCode();
            lastHttpStatusCode.set(code);
            if (code != 200) {
                AppLog.w(TAG, "getInfoRetryOnce: HTTP " + code + " for " + urlString);
                return null;
            }

            java.io.InputStream is = conn.getInputStream();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            is.close();
            return Russian.getString(baos.toByteArray());
        } catch (Exception e) {
            lastHttpStatusCode.set(-1);
            AppLog.w(TAG, "getInfoRetryOnce failed: " + urlString, e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean shouldRetryNeverApiRequest(String urlString) {
        if (urlString == null) {
            return false;
        }
        String lower = urlString.toLowerCase();
        return lower.contains("/pinfo.cgi?")
                || lower.contains("/modules/api/getid.cgi?");
    }

    private static void sleepRetryQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
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

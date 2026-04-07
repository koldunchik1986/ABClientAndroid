package ru.neverlands.abclient.manager;


import ru.neverlands.abclient.utils.AppLog;
import android.content.Intent;
import android.webkit.CookieManager;
import android.util.Xml;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.StringReader;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.FileLogger;
import ru.neverlands.abclient.proxy.ProxyRuntimeManager;
import ru.neverlands.abclient.utils.Russian;
import ru.neverlands.abclient.postfilter.MainPhp;

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
    private static final Object nickIdCacheLock = new Object();
    private static final Map<String, NickIdRecord> nickIdCache = new LinkedHashMap<>();
    private static volatile boolean nickIdCacheLoaded = false;
    private static final String NICK_ID_CACHE_FILE = "nick_id.xml";
    private static final String INFO_SOURCE_DEFAULT = "info_api";
    private static final String INFO_SOURCE_LOGIN_SYNC = "login_sync";
    private static final String INFO_SOURCE_AUTO_BLAZ = "auto_blaz";
    private static final String INFO_SOURCE_MAP_REGION_SYNC = "map_region_sync";

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

    public static final class InfoApiSlot {
        public final int index;
        public final String icon;
        public final String itemName;
        public final String engraving;
        public final Integer minDamage;
        public final Integer maxDamage;
        public final Integer armor;
        public final Integer armorPierce;
        public final Integer hpBonus;
        public final Integer maBonus;
        public final Integer durability;

        public InfoApiSlot(int index,
                           String icon,
                           String itemName,
                           String engraving,
                           Integer minDamage,
                           Integer maxDamage,
                           Integer armor,
                           Integer armorPierce,
                           Integer hpBonus,
                           Integer maBonus,
                           Integer durability) {
            this.index = index;
            this.icon = icon == null ? "" : icon.trim();
            this.itemName = itemName == null ? "" : itemName.trim();
            this.engraving = engraving == null ? "" : engraving.trim();
            this.minDamage = minDamage;
            this.maxDamage = maxDamage;
            this.armor = armor;
            this.armorPierce = armorPierce;
            this.hpBonus = hpBonus;
            this.maBonus = maBonus;
            this.durability = durability;
        }

        public boolean isFilled() {
            return !isSlotPlaceholder(icon, itemName);
        }
    }

    public static final class InfoApiEffect {
        public final int id;
        public final String name;
        public final int count;
        public final String timeout;

        public InfoApiEffect(int id, String name, int count, String timeout) {
            this.id = id;
            this.name = name == null ? "" : name.trim();
            this.count = Math.max(0, count);
            this.timeout = timeout == null ? "" : timeout.trim();
        }
    }

    public static final class InfoApiInfoLine {
        public final String nick;
        public final Integer level;
        public final Integer inclination;
        public final String clanCode;
        public final String clanToken;
        public final String clanName;
        public final String clanStatus;
        public final Integer gender;
        public final Integer blockStatus;
        public final Integer jailStatus;
        public final Integer muteSeconds;
        public final Integer muteForumSeconds;
        public final Integer onlineStatus;
        public final String locationRaw;
        public final String locationRegion;
        public final String locationName;
        public final String fightFid;
        public final String image;

        public InfoApiInfoLine(String nick,
                               Integer level,
                               Integer inclination,
                               String clanCode,
                               String clanToken,
                               String clanName,
                               String clanStatus,
                               Integer gender,
                               Integer blockStatus,
                               Integer jailStatus,
                               Integer muteSeconds,
                               Integer muteForumSeconds,
                               Integer onlineStatus,
                               String locationRaw,
                               String locationRegion,
                               String locationName,
                               String fightFid,
                               String image) {
            this.nick = nick == null ? "" : nick.trim();
            this.level = level;
            this.inclination = inclination;
            this.clanCode = clanCode == null ? "" : clanCode.trim();
            this.clanToken = clanToken == null ? "" : clanToken.trim();
            this.clanName = clanName == null ? "" : clanName.trim();
            this.clanStatus = clanStatus == null ? "" : clanStatus.trim();
            this.gender = gender;
            this.blockStatus = blockStatus;
            this.jailStatus = jailStatus;
            this.muteSeconds = muteSeconds;
            this.muteForumSeconds = muteForumSeconds;
            this.onlineStatus = onlineStatus;
            this.locationRaw = locationRaw == null ? "" : locationRaw.trim();
            this.locationRegion = locationRegion == null ? "" : locationRegion.trim();
            this.locationName = locationName == null ? "" : locationName.trim();
            this.fightFid = normalizeFightFid(fightFid);
            this.image = image == null ? "" : image.trim();
        }
    }

    public static final class InfoApiHmuLine {
        public final Integer curHp;
        public final Integer maxHp;
        public final Integer curMa;
        public final Integer maxMa;
        public final Integer maxTire;
        public final Integer curTire;

        public InfoApiHmuLine(Integer curHp,
                              Integer maxHp,
                              Integer curMa,
                              Integer maxMa,
                              Integer maxTire,
                              Integer curTire) {
            this.curHp = curHp;
            this.maxHp = maxHp;
            this.curMa = curMa;
            this.maxMa = maxMa;
            this.maxTire = maxTire;
            this.curTire = curTire;
        }
    }

    public static final class InfoApiSnapshot {
        public final String sourceModule;
        public final String requestedNick;
        public final String playerId;
        public final List<InfoApiSlot> slots;
        public final Map<Integer, InfoApiSlot> slotsByIndex;
        public final List<InfoApiEffect> effects;
        public final InfoApiInfoLine info;
        public final InfoApiHmuLine hmu;
        public final String rawLine1;
        public final String rawLine2;
        public final String rawLine3;
        public final String rawLine4;
        public final long capturedAtMs;

        public InfoApiSnapshot(String sourceModule,
                               String requestedNick,
                               String playerId,
                               List<InfoApiSlot> slots,
                               Map<Integer, InfoApiSlot> slotsByIndex,
                               List<InfoApiEffect> effects,
                               InfoApiInfoLine info,
                               InfoApiHmuLine hmu,
                               String rawLine1,
                               String rawLine2,
                               String rawLine3,
                               String rawLine4,
                               long capturedAtMs) {
            this.sourceModule = sourceModule == null ? "" : sourceModule.trim();
            this.requestedNick = requestedNick == null ? "" : requestedNick.trim();
            this.playerId = playerId == null ? "" : playerId.trim();
            this.slots = slots == null ? new ArrayList<>() : new ArrayList<>(slots);
            this.slotsByIndex = slotsByIndex == null ? new HashMap<>() : new HashMap<>(slotsByIndex);
            this.effects = effects == null ? new ArrayList<>() : new ArrayList<>(effects);
            this.info = info;
            this.hmu = hmu;
            this.rawLine1 = rawLine1 == null ? "" : rawLine1;
            this.rawLine2 = rawLine2 == null ? "" : rawLine2;
            this.rawLine3 = rawLine3 == null ? "" : rawLine3;
            this.rawLine4 = rawLine4 == null ? "" : rawLine4;
            this.capturedAtMs = capturedAtMs;
        }

        public boolean isValid() {
            return info != null && hmu != null;
        }

        public InfoApiSlot getSlot(int index) {
            return slotsByIndex.get(index);
        }
    }

    private static final class NickIdRecord {
        private final String key;
        private final String nick;
        private final String id;
        private final long updatedAtMs;

        private NickIdRecord(String key, String nick, String id, long updatedAtMs) {
            this.key = key;
            this.nick = nick;
            this.id = id;
            this.updatedAtMs = updatedAtMs;
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
        return resolvePlayerIdByNick(nick, "legacy_get_user_id");
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
     * Возвращает vitals персонажа через новый контур `getid.cgi -> info.cgi`.
     *
     * Зависимости:
     * - {@link #getInfoApiSnapshotByNick(String, String)} — единая точка получения 4-строчного snapshot;
     * - {@link #convertInfoSnapshotToVitals(InfoApiSnapshot)} — совместимый конвертер в текущую модель
     *   {@link PinfoVitals}, чтобы не ломать существующие потребители.
     *
     * Правила:
     * - метод не меняет логику принятия решений в вызывающих модулях;
     * - метод только заменяет источник данных (pinfo.cgi -> info.cgi) для фазовой миграции.
     */
    public static PinfoVitals getPinfoVitalsFromInfoApi(String nick, String sourceModule) {
        InfoApiSnapshot snapshot = getInfoApiSnapshotByNick(nick, sourceModule);
        if (snapshot == null) {
            return null;
        }
        return convertInfoSnapshotToVitals(snapshot);
    }

    /**
     * Совместимый snapshot для контуров карты/компаса на базе `info.cgi`.
     *
     * Назначение:
     * - отдать вызывающему коду прежний тип {@link PinfoCompassSnapshot},
     *   но заполненный из line3/line4 ответа `info.cgi`.
     *
     * Зависимости:
     * - {@link #getInfoApiSnapshotByNick(String, String)} — транспорт и парсинг;
     * - {@link #splitPinfoLocationToRegionAndCell(String)} — единый разбор `Region [CellName]`.
     */
    public static PinfoCompassSnapshot getPinfoCompassSnapshotFromInfoApi(String nick, String sourceModule) {
        if (nick == null || nick.trim().isEmpty()) {
            return null;
        }
        lastCompassPinfoHttpStatus = 0;
        InfoApiSnapshot snapshot = getInfoApiSnapshotByNick(nick, sourceModule);
        int status = lastHttpStatusCode.get() == null ? 0 : lastHttpStatusCode.get();
        if (status != 0) {
            lastCompassPinfoHttpStatus = status;
        }
        if (snapshot == null || snapshot.info == null || snapshot.hmu == null) {
            if (lastCompassPinfoHttpStatus == 0) {
                lastCompassPinfoHttpStatus = -1;
            }
            return null;
        }
        if (lastCompassPinfoHttpStatus == 0) {
            lastCompassPinfoHttpStatus = 200;
        }
        InfoApiInfoLine infoLine = snapshot.info;
        boolean offlineOrInvisible = (infoLine.onlineStatus != null && infoLine.onlineStatus <= 0)
                || isEmpty(infoLine.locationName);
        return new PinfoCompassSnapshot(
                infoLine.nick.isEmpty() ? nick.trim() : infoLine.nick,
                infoLine.locationRaw,
                infoLine.locationRegion,
                infoLine.locationName,
                infoLine.clanToken,
                infoLine.fightFid,
                infoLine.level,
                infoLine.inclination,
                offlineOrInvisible,
                snapshot.hmu.curTire,
                snapshot.capturedAtMs
        );
    }

    /**
     * Полная синхронизация vitals через pinfo.cgi.
     */
    public static PinfoVitals getPinfoVitalsFromPinfo(String nick) {
        String normalizedNick = nick == null ? "" : nick.trim();
        if (normalizedNick.isEmpty()) {
            return null;
        }
        AppLog.d(TAG, "INFO_API_TRACE stage=legacy_adapter_call, adapter=getPinfoVitalsFromPinfo"
                + ", source_module=" + INFO_SOURCE_DEFAULT + ", nick=" + normalizedNick);
        return getPinfoVitalsFromInfoApi(normalizedNick, INFO_SOURCE_DEFAULT);
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
        String normalizedNick = nick == null ? "" : nick.trim();
        if (normalizedNick.isEmpty()) {
            return null;
        }
        AppLog.d(TAG, "INFO_API_TRACE stage=legacy_adapter_call, adapter=getPinfoCompassSnapshot"
                + ", source_module=" + INFO_SOURCE_DEFAULT + ", nick=" + normalizedNick);
        return getPinfoCompassSnapshotFromInfoApi(normalizedNick, INFO_SOURCE_DEFAULT);
    }

    /**
     * Единая точка Phase-1 для чтения данных персонажа через `getid.cgi + info.cgi`.
     *
     * Алгоритм:
     * 1) нормализует nick/source;
     * 2) получает snapshot через внутренний pipeline;
     * 3) пишет trace decision-point в logcat + FileLogger через AppLog.
     *
     * Логирование:
     * - `INFO_API_TRACE ... stage=request/request_ok/request_failed`.
     */
    public static InfoApiSnapshot getInfoApiSnapshotByNick(String nick, String sourceModule) {
        if (nick == null || nick.trim().isEmpty()) {
            return null;
        }
        String normalizedNick = nick.trim();
        String source = normalizeSourceModule(sourceModule);
        AppLog.d(TAG, "INFO_API_TRACE source_module=" + source + ", stage=request, nick=" + normalizedNick);
        try {
            InfoApiSnapshot snapshot = requestInfoApiSnapshotInternal(normalizedNick, source);
            if (snapshot == null) {
                AppLog.w(TAG, "INFO_API_TRACE source_module=" + source + ", stage=request_failed, nick=" + normalizedNick);
                return null;
            }
            AppLog.d(TAG, "INFO_API_TRACE source_module=" + source
                    + ", stage=request_ok, nick=" + normalizedNick
                    + ", id=" + snapshot.playerId
                    + ", level=" + safeInt(snapshot.info == null ? null : snapshot.info.level)
                    + ", tied=" + safeInt(snapshot.hmu == null ? null : snapshot.hmu.curTire)
                    + ", location=" + (snapshot.info == null ? "" : snapshot.info.locationRegion + " [" + snapshot.info.locationName + "]"));
            return snapshot;
        } catch (Exception e) {
            AppLog.w(TAG, "INFO_API_TRACE source_module=" + source + ", stage=request_exception, nick=" + normalizedNick, e);
            return null;
        }
    }

    /**
     * Внутренний pipeline `nick -> playerId -> info.cgi -> DTO`.
     *
     * Зависимости:
     * - {@link #resolvePlayerIdByNick(String, String)} — кэш + getid.cgi;
     * - {@link #fetchInfoApiResponse(String, String)} — HTTP info.cgi;
     * - {@link #parseInfoApiSnapshot(String, String, String, String)} — парсинг 4 строк.
     */
    private static InfoApiSnapshot requestInfoApiSnapshotInternal(String nick, String sourceModule) {
        String playerId = resolvePlayerIdByNick(nick, sourceModule);
        if (isEmpty(playerId)) {
            AppLog.w(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=id_resolve_failed, nick=" + nick);
            return null;
        }

        String response = fetchInfoApiResponse(playerId, sourceModule);
        if (response == null || response.trim().isEmpty()) {
            AppLog.w(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=info_empty, id=" + playerId + ", nick=" + nick);
            return null;
        }

        InfoApiSnapshot snapshot = parseInfoApiSnapshot(response, playerId, nick, sourceModule);
        if (snapshot == null || !snapshot.isValid()) {
            AppLog.w(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=info_parse_failed, id=" + playerId + ", nick=" + nick);
            return null;
        }
        return snapshot;
    }

    /**
     * Конвертер нового snapshot (`info.cgi`) в существующий runtime-формат vitals.
     *
     * Назначение:
     * - сохранить обратную совместимость для модулей, ожидающих {@link PinfoVitals},
     *   без дублирования старого pinfo-парсинга.
     */
    private static PinfoVitals convertInfoSnapshotToVitals(InfoApiSnapshot snapshot) {
        if (snapshot == null || snapshot.hmu == null) {
            return null;
        }
        int[] poisonAndWounds = buildPoisonAndWoundsFromEffects(snapshot.effects);
        Integer topWoundType = resolveTopWoundTypeFromEffects(snapshot.effects);
        return new PinfoVitals(
                snapshot.hmu.curHp,
                snapshot.hmu.maxHp,
                snapshot.hmu.curMa,
                snapshot.hmu.maxMa,
                snapshot.hmu.curTire,
                poisonAndWounds,
                topWoundType
        );
    }

    /**
     * Сборка массива [яд, легк, сред, тяж] из line2/effects `info.cgi`.
     * Индексы и приоритеты соответствуют текущей C#-совместимой модели.
     */
    private static int[] buildPoisonAndWoundsFromEffects(List<InfoApiEffect> effects) {
        int[] values = new int[] {0, 0, 0, 0};
        if (effects == null) {
            return values;
        }
        for (InfoApiEffect effect : effects) {
            if (effect == null) {
                continue;
            }
            int count = Math.max(1, effect.count);
            switch (effect.id) {
                case 24:
                    values[POISON_INDEX] += count;
                    break;
                case 4:
                    values[LIGHT_WOUND_INDEX] += count;
                    break;
                case 3:
                    values[MEDIUM_WOUND_INDEX] += count;
                    break;
                case 2:
                    values[HEAVY_WOUND_INDEX] += count;
                    break;
                default:
                    break;
            }
        }
        return values;
    }

    /**
     * Приоритет травмы для авто-лечения:
     * 4 (боевая) > 3 (тяжёлая) > 2 (средняя) > 1 (лёгкая) > 0 (нет травм).
     */
    private static Integer resolveTopWoundTypeFromEffects(List<InfoApiEffect> effects) {
        if (effects == null) {
            return 0;
        }
        boolean hasBattle = false;
        boolean hasHeavy = false;
        boolean hasMedium = false;
        boolean hasLight = false;
        for (InfoApiEffect effect : effects) {
            if (effect == null) {
                continue;
            }
            switch (effect.id) {
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
    }

    /**
     * Парсит 4 строки ответа `info.cgi` и собирает единый DTO.
     *
     * Критерии валидности:
     * - line1..line4 присутствуют;
     * - line3 (info) и line4 (hmu) распарсены успешно.
     */
    private static InfoApiSnapshot parseInfoApiSnapshot(String response, String playerId, String requestedNick, String sourceModule) {
        try {
            String[] rows = response.split("\\r?\\n");
            String line1 = findInfoApiLine(rows, "1|");
            String line2 = findInfoApiLine(rows, "2|");
            String line3 = findInfoApiLine(rows, "3|");
            String line4 = findInfoApiLine(rows, "4|");

            if (line1 == null || line2 == null || line3 == null || line4 == null) {
                AppLog.w(TAG, "INFO_API_TRACE source_module=" + sourceModule
                        + ", stage=info_parse_fail, reason=missing_line, id=" + playerId
                        + ", has1=" + (line1 != null)
                        + ", has2=" + (line2 != null)
                        + ", has3=" + (line3 != null)
                        + ", has4=" + (line4 != null));
                return null;
            }

            List<InfoApiSlot> slots = parseInfoApiSlotsLine(line1, sourceModule, playerId);
            List<InfoApiEffect> effects = parseInfoApiEffectsLine(line2, sourceModule, playerId);
            InfoApiInfoLine infoLine = parseInfoApiInfoLine(line3, sourceModule, playerId);
            InfoApiHmuLine hmuLine = parseInfoApiHmuLine(line4, sourceModule, playerId);
            if (infoLine == null || hmuLine == null) {
                AppLog.w(TAG, "INFO_API_TRACE source_module=" + sourceModule
                        + ", stage=info_parse_fail, reason=critical_line_parse, id=" + playerId
                        + ", infoOk=" + (infoLine != null)
                        + ", hmuOk=" + (hmuLine != null));
                return null;
            }

            Map<Integer, InfoApiSlot> slotsByIndex = new HashMap<>();
            if (slots != null) {
                for (InfoApiSlot slot : slots) {
                    slotsByIndex.put(slot.index, slot);
                }
            }

            AppLog.d(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=info_parse_ok, id=" + playerId
                    + ", slots=" + (slots == null ? 0 : slots.size())
                    + ", effects=" + (effects == null ? 0 : effects.size())
                    + ", nick=" + infoLine.nick);
            return new InfoApiSnapshot(
                    sourceModule,
                    requestedNick,
                    playerId,
                    slots,
                    slotsByIndex,
                    effects,
                    infoLine,
                    hmuLine,
                    line1,
                    line2,
                    line3,
                    line4,
                    System.currentTimeMillis()
            );
        } catch (Exception e) {
            AppLog.w(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=info_parse_exception, id=" + playerId, e);
            return null;
        }
    }

    /**
     * Извлекает строку по префиксу (`1|`, `2|`, `3|`, `4|`) из multiline-ответа.
     */
    private static String findInfoApiLine(String[] rows, String prefix) {
        if (rows == null || rows.length == 0 || prefix == null) {
            return null;
        }
        for (String row : rows) {
            if (row == null) {
                continue;
            }
            String value = row.trim();
            if (value.startsWith(prefix)) {
                return value;
            }
        }
        return null;
    }

    /**
     * Парсинг line1/slots:
     * - сохраняет все слоты без потерь;
     * - поддерживает доступ по индексам (важно для Fishing/Fight: hand1/hand2/pocket).
     */
    private static List<InfoApiSlot> parseInfoApiSlotsLine(String line, String sourceModule, String playerId) {
        ArrayList<InfoApiSlot> result = new ArrayList<>();
        try {
            String payload = line.length() > 2 ? line.substring(2) : "";
            String[] entries = payload.split("@", -1);
            for (int index = 0; index < entries.length; index++) {
                String entry = entries[index];
                if (entry == null || entry.trim().isEmpty()) {
                    continue;
                }
                InfoApiSlot slot = parseInfoApiSlotEntry(index, entry);
                if (slot != null) {
                    result.add(slot);
                }
            }
            AppLog.d(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=info_parse_ok_line1, id=" + playerId + ", slots=" + result.size());
            return result;
        } catch (Exception e) {
            AppLog.w(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=info_parse_fail_line1, id=" + playerId, e);
            return result;
        }
    }

    /**
     * Парсер одной slot-записи line1:
     * `icon:name:engraving|min|max|armor|pierce|hp|ma|durability`.
     */
    private static InfoApiSlot parseInfoApiSlotEntry(int index, String entry) {
        String[] parts = entry.split(":", 3);
        String icon = parts.length > 0 ? parts[0] : "";
        String itemName = parts.length > 1 ? parts[1] : "";
        String tail = parts.length > 2 ? parts[2] : "";

        String engraving = "";
        Integer minDamage = null;
        Integer maxDamage = null;
        Integer armor = null;
        Integer armorPierce = null;
        Integer hpBonus = null;
        Integer maBonus = null;
        Integer durability = null;

        if (!isEmpty(tail)) {
            int statsPos = tail.indexOf('|');
            if (statsPos >= 0) {
                engraving = tail.substring(0, statsPos);
                String statsRaw = tail.substring(statsPos + 1);
                String[] stats = statsRaw.split("\\|", -1);
                if (stats.length >= 7) {
                    minDamage = parseIntToken(stats[0]);
                    maxDamage = parseIntToken(stats[1]);
                    armor = parseIntToken(stats[2]);
                    armorPierce = parseIntToken(stats[3]);
                    hpBonus = parseIntToken(stats[4]);
                    maBonus = parseIntToken(stats[5]);
                    durability = parseIntToken(stats[6]);
                }
            } else {
                engraving = tail;
            }
        }

        return new InfoApiSlot(
                index,
                icon,
                itemName,
                engraving,
                minDamage,
                maxDamage,
                armor,
                armorPierce,
                hpBonus,
                maBonus,
                durability
        );
    }

    /**
     * Парсинг line2/effects:
     * формат записи `id.name.count.timeout`.
     */
    private static List<InfoApiEffect> parseInfoApiEffectsLine(String line, String sourceModule, String playerId) {
        ArrayList<InfoApiEffect> result = new ArrayList<>();
        try {
            String payload = line.length() > 2 ? line.substring(2) : "";
            if (payload.trim().isEmpty()) {
                AppLog.d(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=info_parse_ok_line2, id=" + playerId + ", effects=0");
                return result;
            }
            String[] entries = payload.split("@", -1);
            for (String entry : entries) {
                if (entry == null || entry.trim().isEmpty()) {
                    continue;
                }
                String[] parts = entry.split("\\.", 4);
                if (parts.length < 2) {
                    continue;
                }
                Integer id = parseIntToken(parts[0]);
                if (id == null) {
                    continue;
                }
                String name = parts[1];
                Integer count = parts.length >= 3 ? parseIntToken(parts[2]) : 0;
                String timeout = parts.length >= 4 ? parts[3] : "";
                result.add(new InfoApiEffect(id, name, count == null ? 0 : count, timeout));
            }
            AppLog.d(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=info_parse_ok_line2, id=" + playerId + ", effects=" + result.size());
            return result;
        } catch (Exception e) {
            AppLog.w(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=info_parse_fail_line2, id=" + playerId, e);
            return result;
        }
    }

    /**
     * Парсинг line3/info:
     * `nick|level|inclination|clanCode|clanToken|...|online|Region [CellName]|fightFid|image`.
     */
    private static InfoApiInfoLine parseInfoApiInfoLine(String line, String sourceModule, String playerId) {
        try {
            String payload = line.length() > 2 ? line.substring(2) : "";
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 16) {
                AppLog.w(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=info_parse_fail_line3, id=" + playerId
                        + ", parts=" + parts.length);
                return null;
            }

            String locationRaw = parts[13];
            String[] locationParts = splitPinfoLocationToRegionAndCell(locationRaw);
            InfoApiInfoLine infoLine = new InfoApiInfoLine(
                    parts[0],
                    parseIntToken(parts[1]),
                    parseIntToken(parts[2]),
                    parts[3],
                    parts[4],
                    parts[5],
                    parts[6],
                    parseIntToken(parts[7]),
                    parseIntToken(parts[8]),
                    parseIntToken(parts[9]),
                    parseIntToken(parts[10]),
                    parseIntToken(parts[11]),
                    parseIntToken(parts[12]),
                    locationRaw,
                    locationParts[0],
                    locationParts[1],
                    parts[14],
                    parts[15]
            );
            AppLog.d(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=info_parse_ok_line3, id=" + playerId
                    + ", nick=" + infoLine.nick + ", fightFid=" + infoLine.fightFid);
            return infoLine;
        } catch (Exception e) {
            AppLog.w(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=info_parse_fail_line3, id=" + playerId, e);
            return null;
        }
    }

    /**
     * Парсинг line4/hmu:
     * `curHP|maxHP|curMA|maxMA|maxTire`, где `curTire = 100 - maxTire`.
     */
    private static InfoApiHmuLine parseInfoApiHmuLine(String line, String sourceModule, String playerId) {
        try {
            String payload = line.length() > 2 ? line.substring(2) : "";
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 5) {
                AppLog.w(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=info_parse_fail_line4, id=" + playerId
                        + ", parts=" + parts.length);
                return null;
            }
            Integer curHp = parseIntToken(parts[0]);
            Integer maxHp = parseIntToken(parts[1]);
            Integer curMa = parseIntToken(parts[2]);
            Integer maxMa = parseIntToken(parts[3]);
            Integer maxTire = parseIntToken(parts[4]);
            Integer curTire = maxTire == null ? null : clampPercent(100 - maxTire);
            InfoApiHmuLine hmu = new InfoApiHmuLine(curHp, maxHp, curMa, maxMa, maxTire, curTire);
            AppLog.d(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=info_parse_ok_line4, id=" + playerId
                    + ", hp=" + safeInt(curHp) + "/" + safeInt(maxHp)
                    + ", ma=" + safeInt(curMa) + "/" + safeInt(maxMa)
                    + ", tire=" + safeInt(curTire));
            return hmu;
        } catch (Exception e) {
            AppLog.w(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=info_parse_fail_line4, id=" + playerId, e);
            return null;
        }
    }

    /**
     * HTTP-запрос `info.cgi` для уже известного `playerId`.
     * Использует общий транспорт {@link #getInfo(String)} (proxy/cookies/user-agent/retry).
     */
    private static String fetchInfoApiResponse(String playerId, String sourceModule) {
        try {
            String url = "http://www.neverlands.ru/modules/api/info.cgi?playerid=" + playerId
                    + "&slots=1&effects=1&info=1&hmu=1";
            String data = getInfo(url);
            int status = lastHttpStatusCode.get() == null ? 0 : lastHttpStatusCode.get();
            AppLog.d(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=info_http, id=" + playerId + ", status=" + status);
            return data;
        } catch (Exception e) {
            AppLog.w(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=info_http_exception, id=" + playerId, e);
            return null;
        }
    }

    /**
     * Получает `playerId` по nick с двухуровневым кэшем:
     * 1) in-memory + disk (`nick_id.xml`);
     * 2) при miss — `getid.cgi`.
     *
     * Логирование:
     * - `id_cache_hit`, `id_cache_hit_memory`, `id_cache_miss`, `id_http_*`, `id_parse_*`.
     */
    private static String resolvePlayerIdByNick(String nick, String sourceModule) {
        if (nick == null || nick.trim().isEmpty()) {
            return null;
        }
        String normalizedNick = nick.trim();
        String key = normalizeNickKey(normalizedNick);
        if (isEmpty(key)) {
            return null;
        }

        ensureNickIdCacheLoaded();

        NickIdRecord cachedRecord;
        synchronized (nickIdCacheLock) {
            cachedRecord = nickIdCache.get(key);
        }
        if (cachedRecord != null && !isEmpty(cachedRecord.id)) {
            synchronized (nameToId) {
                nameToId.put(key, cachedRecord.id);
            }
            AppLog.d(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=id_cache_hit, nick=" + normalizedNick + ", id=" + cachedRecord.id);
            return cachedRecord.id;
        }

        synchronized (nameToId) {
            String memoryId = nameToId.get(key);
            if (!isEmpty(memoryId)) {
                AppLog.d(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=id_cache_hit_memory, nick=" + normalizedNick + ", id=" + memoryId);
                return memoryId;
            }
        }

        AppLog.d(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=id_cache_miss, nick=" + normalizedNick);
        try {
            String encodedNick = encodeNeverlandsQueryTail(normalizedNick);
            String response = getInfo("http://www.neverlands.ru/modules/api/getid.cgi?" + encodedNick);
            int status = lastHttpStatusCode.get() == null ? 0 : lastHttpStatusCode.get();
            if (response == null || response.trim().isEmpty()) {
                AppLog.w(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=id_http_empty, nick=" + normalizedNick + ", status=" + status);
                return null;
            }
            String[] parts = response.split("\\|", -1);
            if (parts.length < 1) {
                AppLog.w(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=id_parse_fail, nick=" + normalizedNick + ", raw=" + response);
                return null;
            }
            String id = parts[0] == null ? "" : parts[0].trim();
            if (isEmpty(id)) {
                AppLog.w(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=id_parse_empty, nick=" + normalizedNick + ", raw=" + response);
                return null;
            }
            String resolvedNick = parts.length > 1 && !isEmpty(parts[1]) ? parts[1].trim() : normalizedNick;
            cacheNickIdRecord(normalizedNick, resolvedNick, id, sourceModule);
            return id;
        } catch (Exception e) {
            AppLog.w(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=id_http_exception, nick=" + normalizedNick, e);
            return null;
        }
    }

    /**
     * Записывает новую/обновлённую пару nick->id в:
     * - runtime map;
     * - `nick_id.xml` (глобальный кэш).
     *
     * Дополнительно:
     * - в Dev-режиме шлёт уведомление в чат о новой записи ID.
     */
    private static void cacheNickIdRecord(String requestNick, String responseNick, String id, String sourceModule) {
        if (isEmpty(requestNick) || isEmpty(id)) {
            return;
        }
        String requestKey = normalizeNickKey(requestNick);
        String responseKey = normalizeNickKey(responseNick);
        long now = System.currentTimeMillis();
        boolean wrote = false;
        synchronized (nickIdCacheLock) {
            ensureNickIdCacheLoadedLocked();
            NickIdRecord current = nickIdCache.get(requestKey);
            if (current == null || !id.equals(current.id) || !safeEquals(current.nick, responseNick)) {
                nickIdCache.put(requestKey, new NickIdRecord(requestKey, responseNick, id, now));
                wrote = true;
            }
            if (!isEmpty(responseKey) && !responseKey.equals(requestKey)) {
                NickIdRecord responseRecord = nickIdCache.get(responseKey);
                if (responseRecord == null || !id.equals(responseRecord.id) || !safeEquals(responseRecord.nick, responseNick)) {
                    nickIdCache.put(responseKey, new NickIdRecord(responseKey, responseNick, id, now));
                    wrote = true;
                }
            }
            if (wrote) {
                writeNickIdCacheLocked();
            }
        }
        synchronized (nameToId) {
            nameToId.put(requestKey, id);
            if (!isEmpty(responseKey)) {
                nameToId.put(responseKey, id);
            }
        }
        if (wrote) {
            AppLog.i(TAG, "INFO_API_TRACE source_module=" + sourceModule + ", stage=id_cache_write, nick=" + responseNick + ", id=" + id);
            postNickIdWriteChatNoticeIfDevMode(sourceModule, responseNick, id);
        }
    }

    /**
     * Публичный lazy-init кэша nick->id.
     */
    private static void ensureNickIdCacheLoaded() {
        synchronized (nickIdCacheLock) {
            ensureNickIdCacheLoadedLocked();
        }
    }

    /**
     * Lazy-init под lock:
     * - читает `files/info/nick_id.xml`;
     * - заполняет memory cache;
     * - подготавливает fallback map `nameToId`.
     */
    private static void ensureNickIdCacheLoadedLocked() {
        if (nickIdCacheLoaded) {
            return;
        }
        nickIdCache.clear();
        File file = resolveNickIdCacheFile();
        if (file != null && file.exists()) {
            readNickIdCacheLocked(file);
        }
        nickIdCacheLoaded = true;
        AppLog.d(TAG, "INFO_API_TRACE stage=id_cache_loaded, size=" + nickIdCache.size());
    }

    /**
     * Читает `nick_id.xml` в memory-кэш.
     * Формат: `<nick_ids><entry key=\"...\" nick=\"...\" id=\"...\" updated=\"...\"/></nick_ids>`.
     */
    private static void readNickIdCacheLocked(File file) {
        try (FileInputStream stream = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(stream, Charset.forName("UTF-8"))) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(reader);
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && "entry".equals(parser.getName())) {
                    String nick = parser.getAttributeValue(null, "nick");
                    String id = parser.getAttributeValue(null, "id");
                    String keyAttr = parser.getAttributeValue(null, "key");
                    String updated = parser.getAttributeValue(null, "updated");
                    String key = isEmpty(keyAttr) ? normalizeNickKey(nick) : normalizeNickKey(keyAttr);
                    if (!isEmpty(key) && !isEmpty(id)) {
                        long updatedAt = parseLongSafe(updated, 0L);
                        nickIdCache.put(key, new NickIdRecord(key, nick == null ? "" : nick.trim(), id.trim(), updatedAt));
                        synchronized (nameToId) {
                            nameToId.put(key, id.trim());
                        }
                    }
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            AppLog.w(TAG, "INFO_API_TRACE stage=id_cache_read_fail, file=" + file.getAbsolutePath(), e);
        }
    }

    /**
     * Атомарно сохраняет memory-кэш в `files/info/nick_id.xml`.
     */
    private static void writeNickIdCacheLocked() {
        File file = resolveNickIdCacheFile();
        if (file == null) {
            return;
        }
        try (FileOutputStream stream = new FileOutputStream(file, false);
             OutputStreamWriter writer = new OutputStreamWriter(stream, Charset.forName("UTF-8"))) {
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(writer);
            serializer.startDocument("UTF-8", true);
            serializer.startTag(null, "nick_ids");
            serializer.attribute(null, "updated", String.valueOf(System.currentTimeMillis()));
            for (NickIdRecord record : nickIdCache.values()) {
                serializer.startTag(null, "entry");
                serializer.attribute(null, "key", record.key);
                serializer.attribute(null, "nick", record.nick);
                serializer.attribute(null, "id", record.id);
                serializer.attribute(null, "updated", String.valueOf(record.updatedAtMs));
                serializer.endTag(null, "entry");
            }
            serializer.endTag(null, "nick_ids");
            serializer.endDocument();
            writer.flush();
        } catch (Exception e) {
            AppLog.w(TAG, "INFO_API_TRACE stage=id_cache_write_fail", e);
        }
    }

    /**
     * Возвращает путь к глобальному кэшу ID:
     * `Context.getFilesDir()/info/nick_id.xml`.
     */
    private static File resolveNickIdCacheFile() {
        if (AppVars.getContext() == null) {
            return null;
        }
        File infoDir = new File(AppVars.getContext().getFilesDir(), "info");
        if (!infoDir.exists() && !infoDir.mkdirs()) {
            AppLog.w(TAG, "INFO_API_TRACE stage=id_cache_dir_fail, path=" + infoDir.getAbsolutePath());
            return null;
        }
        return new File(infoDir, NICK_ID_CACHE_FILE);
    }

    /**
     * Dev-only уведомление о новой записи ID в базу игроков.
     *
     * Условие показа:
     * - включён dev-флаг профиля (сейчас `DoTexLog || DoHttpLog`).
     */
    private static void postNickIdWriteChatNoticeIfDevMode(String sourceModule, String nick, String id) {
        if (!isDevIdCacheNotifyEnabled()) {
            return;
        }
        if (AppVars.getContext() == null) {
            return;
        }
        String source = isEmpty(sourceModule) ? "info_api" : sourceModule;
        String safeNick = escapeHtmlText(nick);
        String safeId = escapeHtmlText(id);
        String safeSource = escapeHtmlText(source);
        String html = MainPhp.buildServerChatTimeHtmlExternal()
                + "<font color=#5D7C91><b>[" + safeSource + "]</b></font> "
                + "Записан ID <b>" + safeId + "</b> для персонажа <b>" + safeNick + "</b> в базу игроков.";
        Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        intent.putExtra("message", html);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
    }

    /**
     * Источник "Dev-режима" для уведомлений ID-кэша.
     */
    private static boolean isDevIdCacheNotifyEnabled() {
        return AppVars.Profile != null && (AppVars.Profile.DoTexLog || AppVars.Profile.DoHttpLog);
    }

    /**
     * Нормализация ключа nick для стабильного кэширования.
     */
    private static String normalizeNickKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Нормализация source-модуля для trace.
     */
    private static String normalizeSourceModule(String sourceModule) {
        if (sourceModule == null) {
            return INFO_SOURCE_DEFAULT;
        }
        String value = sourceModule.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return INFO_SOURCE_DEFAULT;
        }
        return value;
    }

    /**
     * Безопасный парсер long с fallback.
     */
    private static long parseLongSafe(String value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /**
     * Null-safe сравнение строк.
     */
    private static boolean safeEquals(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    /**
     * Минимальный HTML-escape для сообщений в чат.
     */
    private static String escapeHtmlText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Проверка, что slot является "пустым слотом", а не надетым предметом.
     */
    private static boolean isSlotPlaceholder(String icon, String itemName) {
        String iconValue = icon == null ? "" : icon.trim().toLowerCase(Locale.ROOT);
        String nameValue = itemName == null ? "" : itemName.trim().toLowerCase(Locale.ROOT);
        return iconValue.startsWith("sl_") || nameValue.startsWith("слот для ");
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
                || lower.contains("/modules/api/getid.cgi?")
                || lower.contains("/modules/api/info.cgi?");
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

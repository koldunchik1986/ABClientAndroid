package ru.neverlands.abclient.manager;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.abclient.MainActivity;
import ru.neverlands.abclient.utils.Russian;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.EventSounds;

public class RoomManager {
    private static final String TAG = "RoomManager";
    private static final String BG_TRACE_PREFIX = "[BG_TRACE]";
    private static final long AUTO_ATTACK_BLACKLIST_MS = 10_000L;
    private static final String AA_TRACE_PREFIX = "[AA_TRACE]";
    // Временный чёрный список целей авто-нападения (аналог C# `RoomManager.BlackList`).
    // Ключ: ник в нижнем регистре, значение: время добавления в список (мс).
    private static final Map<String, Long> autoAttackBlackList = new ConcurrentHashMap<>();

    // Обработчик списка игроков комнаты (`ch.php?lo=1`).
    // Метод `process(...)` содержит портированную логику разбора списка комнаты.
    public static String process(Context context, String html) {
        Log.d(TAG, BG_TRACE_PREFIX + " process: htmlLen=" + (html == null ? 0 : html.length())
                + ", contextNull=" + (context == null)
                + ", doShowWalkers=" + AppVars.DoShowWalkers);
        FilterProcRoomResult filterResult = FilterProcRoom(html);
        FilterGetWalkers(html, filterResult);
        boolean fightActive = isFightSessionActive();
        Log.d(TAG, AA_TRACE_PREFIX + " room tick: chars=" + filterResult.numCharsInRoom
                + ", enemies=" + buildEnemyCandidatesTrace(filterResult.enemyCandidates)
                + ", selectedEnemy=" + filterResult.enemyAttack
                + ", fastNeed=" + AppVars.FastNeed
                + ", fastId=" + AppVars.FastId
                + ", fastNick=" + AppVars.FastNick
                + ", fightActive=" + fightActive
                + ", fightLink=" + AppVars.FightLink);

        // Авто-нападение по списку комнаты (аналог ветки `RoomManager.Process -> EnemyAttack` в C#).
        // Зависимости:
        // - `AutoFunctionsManager` (флаг AUTO_ATTACK),
        // - `ContactsManager` (`classId`/`toolId` контакта),
        // - `FastActionManager.fastAttackAutoByToolId(...)` (запуск быстрой атаки),
        // - `AppVars.FastNeed` (защита от параллельного цикла быстрой атаки).
        // Конвейер авто-нападения:
        // 1) берём выбранного противника из списка комнаты (`filterResult.enemyAttack`);
        // 2) определяем инструмент атаки с приоритетом `contact.toolId -> AppVars.AutoAttackToolId`;
        // 3) запускаем `FastActionManager.fastAttackAutoByToolId(...)` только если
        //    `AppVars.FastNeed == false`, чтобы не пересекаться с уже активным циклом быстрой атаки.
        if (context == null) {
            Log.d(TAG, AA_TRACE_PREFIX + " auto-attack skipped: context=null");
            return html;
        }

        if (AppVars.FastNeed) {
            Log.d(TAG, AA_TRACE_PREFIX + " auto-attack skipped: fast pipeline active"
                    + ", fastId=" + AppVars.FastId + ", fastNick=" + AppVars.FastNick);
            return html;
        }

        if (isEmpty(filterResult.enemyAttack)) {
            if (filterResult.enemyCandidates.isEmpty()) {
                Log.d(TAG, AA_TRACE_PREFIX + " auto-attack skipped: no hostile contacts in room");
            } else {
                Log.d(TAG, AA_TRACE_PREFIX + " auto-attack skipped: no selected enemy"
                        + ", enemies=" + buildEnemyCandidatesTrace(filterResult.enemyCandidates));
            }
            return html;
        }

        // Критическая защита:
        // Во время активного боя чат продолжает приходить тиками (`ch.php`), и без этой проверки
        // `RoomManager` может повторно запускать авто-нападение по устаревшему списку врагов.
        // Это перезапускает `FastNeed/FastId` в середине боя и конфликтует с циклом ударов автобоя.
        // Итог: конфликт между циклами "авто-нападение" и "цикл ударов автобоя".
        if (fightActive) {
            Log.d(TAG, AA_TRACE_PREFIX + " auto-attack skipped: active fight session"
                    + ", fastNeed=" + AppVars.FastNeed
                    + ", fightLink=" + AppVars.FightLink
                    + ", topUrl=" + AppVars.url_main_top);
            return html;
        }

        boolean autoAttackEnabled = isAutoAttackEnabled(context);
        if (!autoAttackEnabled) {
            Log.d(TAG, AA_TRACE_PREFIX + " auto-attack disabled: enabled=false"
                    + ", globalTool=" + AppVars.AutoAttackToolId);
            return html;
        }

        String enemyNick = stripItalic(filterResult.enemyAttack);
        // Локальная настройка инструмента для конкретного контакта из `contacts.xml`.
        // Значение `0` трактуется как "использовать глобальный инструмент".
        int contactToolId = ContactsManager.getToolIdOfContact(enemyNick);
        // Глобальный инструмент авто-нападения из быстрых настроек (`AppVars.AutoAttackToolId`).
        int globalToolId = AppVars.AutoAttackToolId;
        // Финальный выбор инструмента:
        // - приоритет у настройки контакта (`contactToolId > 0`);
        // - иначе используем глобальное значение.
        // Зависимости: `ContactsManager`, `AppVars`.
        int toolId = (contactToolId > 0) ? contactToolId : globalToolId;
        if (toolId == 0) {
            Log.d(TAG, AA_TRACE_PREFIX + " auto-attack skipped: no tool selected, nick=" + enemyNick
                    + ", contactTool=" + contactToolId + ", globalTool=" + globalToolId);
            return html;
        }

        long blackListRemainingMs = getBlackListRemainingMs(enemyNick);
        Log.d(TAG, AA_TRACE_PREFIX + " auto-attack candidate: nick=" + enemyNick + ", toolId=" + toolId
                + ", contactTool=" + contactToolId
                + ", fastNeedBefore=" + AppVars.FastNeed
                + ", globalTool=" + globalToolId
                + ", blacklistRemainingMs=" + blackListRemainingMs);
        FastActionManager.writeChatMsg("Пытаемся напасть на <b>" + enemyNick + "</b>!");
        boolean started = FastActionManager.fastAttackAutoByToolId(enemyNick, toolId);
        if (!started) {
            Log.w(TAG, AA_TRACE_PREFIX + " auto-attack skipped: unsupported toolId=" + toolId + ", nick=" + enemyNick);
        } else {
            Log.d(TAG, AA_TRACE_PREFIX + " auto-attack started: nick=" + enemyNick + ", toolId=" + toolId
                    + ", fastNeedAfter=" + AppVars.FastNeed + ", fastId=" + AppVars.FastId
                    + ", fastNick=" + AppVars.FastNick);
        }
        return html;
    }

    /**
     * Определяет, что сейчас активна боевая сессия в основном фрейме.
     *
     * Зависимости:
     * - `AppVars.ContentMainPhp`: последний HTML боя, который сохраняет MainPhp;
     * - `AppVars.url_main_top`: текущий URL верхнего фрейма;
     * - `AppVars.FightLink`: ссылка цикла боя/завершения.
     *
     * Почему отдельный метод:
     * - `RoomManager` вызывается из чата (нижний фрейм), где нет прямого доступа к парсеру боя;
     * - поэтому используем агрегированное состояние из `AppVars` как защитную проверку перед авто-нападением.
     */
    private static boolean isFightSessionActive() {
        String mainHtml = AppVars.ContentMainPhp;
        if (mainHtml != null && (mainHtml.contains("var fight_ty") || mainHtml.contains("magic_slots();"))) {
            return true;
        }

        String topUrl = AppVars.url_main_top;
        if (topUrl != null && topUrl.contains("get_id=56&act=10&go=inf")) {
            return true;
        }

        String fightLink = AppVars.FightLink;
        return fightLink != null && fightLink.contains("get_id=61&act=");
    }

    public static void startTracing(MainActivity mainActivity) {
        autoAttackBlackList.clear();
    }

    public static void stopTracing() {
        autoAttackBlackList.clear();
    }

    // Формирует сообщения о входе/выходе игроков в локации (порт C# логики).
    /**
     * Формирует и публикует события перемещения по локации (вход/выход видимых персонажей и невидимок).
     *
     * Зависимости:
     * - `AppVars.DoShowWalkers`: глобальный флаг включения трекинга передвижений;
     * - `AppVars.url_ch_list`: URL текущей комнаты, используется для вычисления ключа координат `r=...`;
     * - `AppVars.myCharsOld`, `AppVars.myCoordOld`, `AppVars.myLocOld`, `AppVars.myNevidsOld`: предыдущее состояние;
     * - `parseVisibleCharsMap(...)`: извлечение видимых никнеймов из `ChatListU`;
     * - `resolveNevidsCount(...)`: расчет количества невидимок как разницы "серверный общий счётчик на клетке - видимые";
     * - `buildWalkersMessage(...)`: сборка человекочитаемого текста для чата;
     * - `FastActionManager.writeChatMsg(...)`: публикация уведомлений в игровой чат;
     * - `EventSounds.playSndMsg()`: звуковой сигнал при входящих событиях.
     *
     * Алгоритм:
     * 1) Проверяет, что трекинг передвижений включен и HTML не пустой.
     * 2) Определяет текущую локацию и набор видимых персонажей.
     * 3) На той же клетке/локации сравнивает прошлый и текущий наборы:
     *    - кто исчез из видимых;
     *    - кто появился в видимых;
     *    - как изменилось число невидимок.
     * 4) Формирует сообщения и отправляет их в чат.
     * 5) Обновляет снимок состояния для следующего тика.
     */
    private static void FilterGetWalkers(String html, FilterProcRoomResult filterResult) {
        if (!AppVars.DoShowWalkers || isEmpty(html)) {
            return;
        }

        String locationNow = extractLocationName(html);
        if (isEmpty(locationNow)) {
            return;
        }

        Map<String, String> charsNow = parseVisibleCharsMap(html);
        int visibleChars = charsNow.size();
        int locationCharsFromServer = parseLocationCharsCount(html);
        AppVars.myNevids = resolveNevidsCount(html, visibleChars);
        Log.d(TAG, AA_TRACE_PREFIX + " FilterGetWalkers: loc=" + locationNow
                + ", coord=" + extractRoomCoordKey(AppVars.url_ch_list)
                + ", visibleChars=" + visibleChars
                + ", locationCharsFromServer=" + locationCharsFromServer
                + ", nevids=" + AppVars.myNevids);

        String roomCoordNow = extractRoomCoordKey(AppVars.url_ch_list);
        boolean sameCoord = roomCoordNow.equals(AppVars.myCoordOld);
        boolean sameLocation = locationNow.equals(AppVars.myLocOld);

        if (sameCoord && sameLocation) {
            Map<String, String> leftChars = new LinkedHashMap<>();
            for (Map.Entry<String, String> oldEntry : AppVars.myCharsOld.entrySet()) {
                String nick = oldEntry.getKey();
                if (!charsNow.containsKey(nick)) {
                    if (isSelfNick(nick)) {
                        FastActionManager.writeChatMsg("<b><font color=#01A9DB>Мы ушли в невид</font></b>");
                    } else {
                        leftChars.put(nick, oldEntry.getValue());
                    }
                }
            }

            Map<String, String> comeChars = new LinkedHashMap<>();
            for (Map.Entry<String, String> nowEntry : charsNow.entrySet()) {
                String nick = nowEntry.getKey();
                if (!AppVars.myCharsOld.containsKey(nick)) {
                    if (isSelfNick(nick)) {
                        FastActionManager.writeChatMsg("<b><font color=#DF0101>Мы вышли из невида!</font></b>");
                    } else {
                        comeChars.put(nick, nowEntry.getValue());
                    }
                }
            }

            int diffNevids = AppVars.myNevids - AppVars.myNevidsOld;
            if (!leftChars.isEmpty() || !comeChars.isEmpty() || diffNevids != 0) {
                int prevTotalChars = AppVars.myCharsOld.size() + Math.max(0, AppVars.myNevidsOld);
                int currTotalChars = locationCharsFromServer >= 0
                        ? locationCharsFromServer
                        : (visibleChars + Math.max(0, AppVars.myNevids));

                String revealFromNevidMsg = buildNevidStateChangeMessage(
                        comeChars,
                        leftChars,
                        diffNevids,
                        prevTotalChars,
                        currTotalChars,
                        false
                );
                String hideToNevidMsg = buildNevidStateChangeMessage(
                        leftChars,
                        comeChars,
                        diffNevids,
                        prevTotalChars,
                        currTotalChars,
                        true
                );

                AppVars.myWalkers1 = !isEmpty(revealFromNevidMsg)
                        ? revealFromNevidMsg
                        : buildWalkersMessage(comeChars, diffNevids, true);
                AppVars.myWalkers2 = !isEmpty(hideToNevidMsg)
                        ? hideToNevidMsg
                        : buildWalkersMessage(leftChars, diffNevids, false);
            }
        }

        AppVars.myCoordOld = roomCoordNow;
        AppVars.myLocOld = locationNow;
        AppVars.myCharsOld.clear();
        AppVars.myCharsOld.putAll(charsNow);
        AppVars.myNevidsOld = AppVars.myNevids;

        if (!isEmpty(AppVars.myWalkers1)) {
            EventSounds.playSndMsg();
            FastActionManager.writeChatMsg(AppVars.myWalkers1);
            AppVars.myWalkers1 = "";
        }

        if (!isEmpty(AppVars.myWalkers2)) {
            FastActionManager.writeChatMsg(AppVars.myWalkers2);
            AppVars.myWalkers2 = "";
        }
    }

    /**
     * Извлекает карту видимых персонажей из JS-массива `ChatListU`.
     *
     * Зависимости:
     * - формат серверного блока `var ChatListU = new Array(...);`;
     * - `parseChatListEntries(...)`: безопасное разбиение массива на записи;
     * - `normalizeChatUserEntry(...)`: нормализация записи к единому `:`-формату.
     *
     * Правила фильтрации:
     * - пропускаются пустые/битые записи;
     * - пропускаются записи с `<i>` (невидимые в списке видимых ников не учитываются);
     * - дубликаты ников отбрасываются, сохраняется первое вхождение.
     *
     * @param html HTML комнаты/чата
     * @return карта `nick -> rawEntry` для всех видимых персонажей на текущей клетке
     */
    private static Map<String, String> parseVisibleCharsMap(String html) {
        Map<String, String> visibleChars = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("var\\s+ChatListU\\s*=\\s*new Array\\((.*?)\\);", Pattern.DOTALL).matcher(html);
        if (!matcher.find()) {
            return visibleChars;
        }

        String[] parsedEntries = parseChatListEntries(matcher.group(1));
        for (String parsedEntry : parsedEntries) {
            String normalized = normalizeChatUserEntry(parsedEntry);
            if (isEmpty(normalized)) {
                continue;
            }
            String[] parts = normalized.split(":");
            if (parts.length < 3) {
                continue;
            }
            String nick = parts[1];
            if (nick.contains("<i>")) {
                continue;
            }
            if (!visibleChars.containsKey(nick)) {
                visibleChars.put(nick, normalized);
            }
        }
        return visibleChars;
    }

    /**
     * Собирает текст уведомления о входе/выходе персонажей и изменении количества невидимок.
     *
     * Зависимости:
     * - `HtmlChar(...)`: рендер никнейма/иконок в HTML-представление;
     * - правила склонения и построения фраз для русского текста.
     *
     * Поведение:
     * - для `incoming=true` и `diffNevids>0` добавляет блок "невидимка/невидимок";
     * - для `incoming=false` и `diffNevids<0` добавляет блок об ушедших невидимках;
     * - добавляет список видимых персонажей из `chars`;
     * - в конце добавляет хвост действия ("приходит/приходят", "покидает/покидают").
     *
     * @param chars карта персонажей, участвующих в конкретном событии
     * @param diffNevids разница `currentNevids - previousNevids`
     * @param incoming true для входа, false для выхода
     * @return готовая строка для чата; пустая строка, если событие нечего публиковать
     */
    private static String buildWalkersMessage(Map<String, String> chars, int diffNevids, boolean incoming) {
        StringBuilder sb = new StringBuilder();
        int count = 0;

        if (incoming && diffNevids > 0) {
            count = 1;
            sb.append("<font color=#5D7C91><b>");
            if (diffNevids == 1) {
                sb.append("Невидимка");
            } else {
                sb.append(diffNevids).append(" невидимок");
            }
            sb.append("</b></font>");
        } else if (!incoming && diffNevids < 0) {
            count = 1;
            int hiddenCount = -diffNevids;
            sb.append("<font color=#5D7C91><b>");
            if (hiddenCount == 1) {
                sb.append("Невидимка");
            } else {
                sb.append(hiddenCount).append(" невидимок");
            }
            sb.append("</b></font>");
        }

        for (String rawChar : chars.values()) {
            if (count > 0) {
                sb.append(", ");
            }
            count++;
            try {
                sb.append(HtmlChar(rawChar));
            } catch (Exception e) {
                Log.w(TAG, "buildWalkersMessage: skip malformed char entry: " + rawChar, e);
            }
        }

        if (count > 0) {
            if (incoming) {
                sb.append(count > 1 ? " приходят в локацию" : " приходит в локацию");
            } else {
                sb.append(count > 1 ? " покидают локацию" : " покидает локацию");
            }
        }
        return sb.toString();
    }

    /**
     * Формирует отдельное сообщение для перехода видимых персонажей в невидимость и обратно,
     * если общее количество на клетке не изменилось.
     *
     * Пример:
     * - было 2/2 (видимых/всего), стало 1/2, пропал ник "N" -> "N перешёл в невидимку".
     */
    private static String buildNevidStateChangeMessage(
            Map<String, String> changedChars,
            Map<String, String> oppositeChangedChars,
            int diffNevids,
            int prevTotalChars,
            int currTotalChars,
            boolean toNevid) {
        if (changedChars == null || changedChars.isEmpty()) {
            return "";
        }
        if (oppositeChangedChars != null && !oppositeChangedChars.isEmpty()) {
            return "";
        }
        if (prevTotalChars < 0 || currTotalChars < 0 || prevTotalChars != currTotalChars) {
            return "";
        }

        int expectedDiff = toNevid ? changedChars.size() : -changedChars.size();
        if (diffNevids != expectedDiff) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String rawChar : changedChars.values()) {
            if (count > 0) {
                sb.append(", ");
            }
            count++;
            try {
                sb.append(HtmlChar(rawChar));
            } catch (Exception e) {
                Log.w(TAG, "buildNevidStateChangeMessage: skip malformed char entry: " + rawChar, e);
            }
        }
        if (count == 0) {
            return "";
        }

        sb.insert(0, "<font color=#5D7C91><b>[Невид]</b></font> ");

        if (toNevid) {
            sb.append(count > 1 ? " перешли в невидимку" : " перешёл в невидимку");
        } else {
            sb.append(count > 1 ? " вышли из невидимки" : " вышел из невидимки");
        }
        return sb.toString();
    }

    private static boolean isSelfNick(String nick) {
        if (AppVars.Profile == null || isEmpty(AppVars.Profile.UserNick)) {
            return false;
        }
        return AppVars.Profile.UserNick.equalsIgnoreCase(stripItalic(nick));
    }

    private static String extractLocationName(String html) {
        String location = extractBetween(html, "<font class=placename><b>", "</b>");
        if (isEmpty(location)) {
            location = extractBetween(html, "<font class=placename><b>", "</b></font>");
        }
        if (isEmpty(location)) {
            return "";
        }
        return location.replace("<br>", " ").trim();
    }

    private static String extractRoomCoordKey(String roomUrl) {
        if (isEmpty(roomUrl)) {
            return "";
        }
        Matcher matcher = Pattern.compile("[?&]r=([^&]+)").matcher(roomUrl);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    /**
     * Вычисляет текущее число невидимок на клетке.
     *
     * Зависимости:
     * - `parseLocationCharsCount(...)`: парсинг серверного общего количества персонажей именно на локации;
     * - `AppVars.myNevids`: резервное значение, если из HTML не удалось извлечь общий счётчик.
     *
     * Формула:
     * - `nevids = max(0, totalCharsOnLocation - visibleChars)`.
     *
     * @param html HTML текущей страницы комнаты
     * @param visibleChars количество видимых ников из `ChatListU`
     * @return количество невидимок на текущей клетке
     */
    private static int resolveNevidsCount(String html, int visibleChars) {
        int totalChars = parseLocationCharsCount(html);
        if (totalChars < 0) {
            return AppVars.myNevids;
        }
        int nevids = totalChars - Math.max(0, visibleChars);
        return Math.max(0, nevids);
    }

    /**
     * Извлекает серверное количество персонажей на текущей локации.
     *
     * Зависимости:
     * - `parseFirstInt(...)`: первичный парсинг по приоритетному шаблону рядом с названием локации;
     * - `parseLastBracketedIntBeforeChatList(...)`: резервный парсинг последнего `[N]` до блока `ChatListU`.
     *
     * Почему два шага:
     * - в разных серверных шаблонах счетчик может находиться в разных местах HTML;
     * - резервный шаг нужен для устойчивости к вариациям верстки.
     *
     * @param html HTML страницы комнаты
     * @return количество персонажей на локации или `-1`, если счетчик не найден
     */
    private static int parseLocationCharsCount(String html) {
        int totalChars = parseFirstInt(html, "(?is)</b>\\s*</font>\\s*</a>\\s*\\[\\s*(\\d+)\\s*\\]");
        if (totalChars >= 0) {
            return totalChars;
        }
        return parseLastBracketedIntBeforeChatList(html);
    }

    /**
     * Резервно ищет последний числовой маркер `[N]` перед определением `var ChatListU`.
     *
     * Зависимости:
     * - серверный инвариант: счетчик "персонажей на клетке" располагается в HTML до чата;
     * - граница поиска по `var ChatListU`, чтобы не зацепить нецелевые счетчики из других блоков.
     *
     * @param html HTML страницы комнаты
     * @return найденное значение `N` или `-1`, если подходящий маркер отсутствует
     */
    private static int parseLastBracketedIntBeforeChatList(String html) {
        if (isEmpty(html)) {
            return -1;
        }
        int chatListPos = html.indexOf("var ChatListU");
        int searchLimit = chatListPos >= 0 ? chatListPos : html.length();
        String prefix = html.substring(0, searchLimit);
        Matcher matcher = Pattern.compile("\\[\\s*(\\d+)\\s*\\]").matcher(prefix);
        int result = -1;
        while (matcher.find()) {
            try {
                result = Integer.parseInt(matcher.group(1));
            } catch (Exception ignored) {
                // Игнорируем повреждённый числовой фрагмент и продолжаем поиск,
                // чтобы не терять корректный `[N]`, который может встретиться позже.
            }
        }
        return result;
    }

    private static int parseFirstInt(String source, String regex) {
        if (isEmpty(source) || isEmpty(regex)) {
            return -1;
        }
        Matcher matcher = Pattern.compile(regex).matcher(source);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static String extractBetween(String text, String start, String end) {
        if (text == null) {
            return "";
        }
        int startIndex = text.indexOf(start);
        if (startIndex < 0) {
            return "";
        }
        startIndex += start.length();
        int endIndex = text.indexOf(end, startIndex);
        if (endIndex < 0) {
            return "";
        }
        return text.substring(startIndex, endIndex);
    }

    private static String HtmlChar(String schar) {
        String[] strArray = schar.split(":");
        String nnSec = strArray[1];
        String login = strArray[1];
        int classId = Integer.parseInt(ContactsManager.getClassIdOfContact(login));

        String color = "#000000";
        if (classId == 1) {
            color = "#008000"; // Зелёный (союзник/нейтральная метка по classId).
        } else if (classId == 2) {
            color = "#FF0000"; // Красный (враждебная метка по classId).
        }

        while (nnSec.contains("+")) {
            nnSec = nnSec.replace("+", "%2B");
        }

        if (login.contains("<i>")) {
            login = login.replace("<i>", "");
            login = login.replace("</i>", "");
            nnSec = nnSec.replace("<i>", "");
            nnSec = nnSec.replace("</i>", "");
        }

        String ss = "";
        String altadd = "";
        if (strArray[3].length() > 1) {
            String[] signArray = strArray[3].split(";");
            if (signArray.length > 2 && signArray[2].length() > 1) {
                altadd = " (" + signArray[2] + ")";
            }

            ss =
                "<img src=http://image.neverlands.ru/signs/" +
                signArray[0] +
                " width=15 height=12 align=absmiddle alt=\"" +
                signArray[1] +
                altadd +
                "\">&nbsp;";
        }

        String sleeps = "";
        if (strArray.length > 4 && strArray[4].length() > 1) {
            sleeps =
                "<img src=http://image.neverlands.ru/signs/molch.gif width=15 height=12 border=0 alt=\"" +
                strArray[4] +
                "\" align=absmiddle>";
        }

        String ign = "";
        if (strArray.length > 5 && strArray[5].equals("1")) {
            ign =
                "<a href=\"javascript:ch_clear_ignor('" +
                login +
                "');\"><img src=http://image.neverlands.ru/signs/ignor/3.gif width=15 height=12 border=0 alt=\"Снять игнорирование\"></a>";
        }

        String inj = "";
        if (strArray.length > 6 && !strArray[6].equals("0")) {
            inj = "<img src=http://image.neverlands.ru/chat/tr4.gif border=0 width=15 height=12 alt=\"" +
                  strArray[6] +
                  "\" align=absmiddle>";

            if (strArray[6].contains("боевая")) {
                strArray[1] = "<font color=\"#666600\">" + strArray[1] + "</font>";
            } else if (strArray[6].contains("тяжелая")) {
                strArray[1] = "<font color=\"#c10000\">" + strArray[1] + "</font>";
            } else if (strArray[6].contains("средняя")) {
                strArray[1] = "<font color=\"#e94c69\">" + strArray[1] + "</font>";
            } else if (strArray[6].contains("легкая")) {
                strArray[1] = "<font color=\"#ef7f94\">" + strArray[1] + "</font>";
            }
        }

        String psg = "";
        if (strArray.length > 7 && !strArray[7].equals("0")) {
            String[] dilers = {"", "Дилер", "", "", "", "", "", "", "", "", "", "Помощник дилера"};
            psg =
                "<img src=http://image.neverlands.ru/signs/d_sm_" +
                strArray[7] +
                ".gif width=15 height=12 align=absmiddle border=0 alt=\"" +
                dilers[Integer.parseInt(strArray[7])] +
                "\">&nbsp;";
        }

        String align = "";
        if (strArray.length > 8 && !strArray[8].equals("0")) {
            String[] signArray = strArray[8].split(";");
            if (signArray.length >= 2) {
                align =
                    "<img src=http://image.neverlands.ru/signs/" +
                    signArray[0] +
                    " width=15 height=12 align=absmiddle border=0 alt=\"" +
                    signArray[1] +
                    "\">&nbsp";
            }
        }

        return
            "<a href=\"#\" onclick=\"top.say_private('" +
            login +
            "');\"><img src=http://image.neverlands.ru/chat/private.gif width=11 height=12 border=0 align=absmiddle></a>&nbsp;" +
            psg +
            align +
            ss +
            "<a class=\"activenick\" href=\"#\" onclick=\"top.say_to('" +
            login +
            "');\"><font class=nickname color=\"" + color + "\"><b>" +
            strArray[1] +
            "</b></a>[" +
            strArray[2] +
            "]</font><a href=\"http://neverlands.ru/pinfo.cgi?" +
            nnSec +
            "\" onclick=\"window.open(this.href);\"><img src=http://image.neverlands.ru/chat/info.gif width=11 height=12 border=0 align=absmiddle></a>" +
            sleeps +
            ign +
            inj;
    }

    // Парсит JS-массив ChatListU и формирует HTML списка игроков.
    private static FilterProcRoomResult FilterProcRoom(String html) {
        FilterProcRoomResult result = new FilterProcRoomResult();

        Pattern pattern = Pattern.compile("var\\s+ChatListU\\s*=\\s*new Array\\((.*?)\\);", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            String chatListU = matcher.group(1);
            String[] par = parseChatListEntries(chatListU);
            result.numCharsInRoom = par.length;

            StringBuilder sb = new StringBuilder();
            StringBuilder chatListUBuilder = new StringBuilder();
            List<String> enemyAttack = new ArrayList<>();
            for (int i = 0; i < par.length; i++) {
                String rawEntry = normalizeChatUserEntry(par[i]);
                if (rawEntry.isEmpty()) {
                    continue;
                }

                String nick = extractNick(rawEntry);
                if (!nick.isEmpty() && isEnemyContact(nick)) {
                    enemyAttack.add(nick);
                    Log.d(TAG, AA_TRACE_PREFIX + " enemy detected in room: " + buildEnemyTrace(nick));
                }

                try {
                    sb.append(HtmlChar(rawEntry));
                } catch (Exception htmlCharError) {
                    Log.w(TAG, "FilterProcRoom: skip malformed ChatListU entry: " + rawEntry, htmlCharError);
                    continue;
                }
                chatListUBuilder.append("\"" + rawEntry + "\"");
                if (i < par.length - 1) {
                    chatListUBuilder.append(",");
                }
            }
            result.html = sb.toString();
            result.chatListU = chatListUBuilder.toString();
            result.enemyCandidates = enemyAttack;
            result.enemyAttack = pickEnemyForAutoAttack(enemyAttack);
            Log.d(TAG, "FilterProcRoom: chars=" + result.numCharsInRoom
                    + ", enemies=" + enemyAttack.size()
                    + ", enemyAttack=" + result.enemyAttack);
        }

        return result;
    }

    /**
     * Разбирает содержимое `new Array(...)` для `ChatListU`.
     *
     * Почему отдельный парсер:
     * - сервер может присылать переносы/пробелы между элементами (`",\r\n"`),
     * - простой split по `","` в таком случае может вернуть один элемент.
     *
     * Возвращает массив строк формата `nickLow:nick:level:...`.
     */
    private static String[] parseChatListEntries(String chatListU) {
        if (chatListU == null || chatListU.trim().isEmpty()) {
            return new String[0];
        }

        // Основной путь: split по разделителю элементов массива.
        String[] splitByComma = chatListU.split("\"\\s*,\\s*\"");
        if (splitByComma.length > 1) {
            return splitByComma;
        }
        Log.d(TAG, "[AA_TRACE] parseChatListEntries: splitByComma failed, fallback regex. rawLen=" + chatListU.length());

        // Резервный путь: извлекаем все элементы в двойных кавычках,
        // если сервер прислал нестандартные разделители/переносы.
        List<String> quoted = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(chatListU);
        while (matcher.find()) {
            quoted.add(matcher.group(1));
        }
        if (!quoted.isEmpty()) {
            Log.d(TAG, "[AA_TRACE] parseChatListEntries: fallback extracted=" + quoted.size());
            return quoted.toArray(new String[0]);
        }

        // Последний резерв: возвращаем исходную строку как один элемент,
        // чтобы внешняя логика могла безопасно обработать деградированный формат.
        Log.w(TAG, "[AA_TRACE] parseChatListEntries: no quoted entries, using raw source");
        return new String[]{chatListU};
    }

    /**
     * Добавляет ник в blacklist авто-нападения на короткое время.
     *
     * Аналог C# `RoomManager.CharAddToBlackList`.
     * Используется при ошибке "Нельзя вмешаться в закрытый бой", чтобы не спамить повторами.
     */
    public static void charAddToBlackList(String nick) {
        if (isEmpty(nick)) {
            return;
        }
        String key = normalizeNickKey(nick);
        autoAttackBlackList.put(key, System.currentTimeMillis());
        Log.d(TAG, AA_TRACE_PREFIX + " blacklist add: " + key + ", ttlMs=" + AUTO_ATTACK_BLACKLIST_MS
                + ", size=" + autoAttackBlackList.size());
    }

    private static boolean isCharInBlackList(String nick) {
        if (isEmpty(nick)) {
            return false;
        }
        long remainingMs = getBlackListRemainingMs(nick);
        if (remainingMs <= 0L) {
            return false;
        }
        String key = normalizeNickKey(nick);
        Log.d(TAG, AA_TRACE_PREFIX + " blacklist hit: " + key + ", remainingMs=" + remainingMs);
        return true;
    }

    private static boolean isAutoAttackEnabled(Context context) {
        try {
            boolean enabled = AutoFunctionsManager.getInstance(context).isAutoAttackEnabled();
            Log.d(TAG, BG_TRACE_PREFIX + " isAutoAttackEnabled: " + enabled);
            return enabled;
        } catch (Exception e) {
            Log.w(TAG, "isAutoAttackEnabled failed", e);
            return false;
        }
    }

    private static String pickEnemyForAutoAttack(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        List<String> filtered = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        for (String nick : candidates) {
            long remainingMs = getBlackListRemainingMs(nick);
            if (remainingMs <= 0L) {
                filtered.add(nick);
            } else {
                blocked.add(stripItalic(nick) + "(" + remainingMs + "ms)");
            }
        }
        Log.d(TAG, AA_TRACE_PREFIX + " pickEnemyForAutoAttack: total=" + candidates.size()
                + ", available=" + filtered.size()
                + ", blocked=" + blocked.size()
                + ", blockedList=" + blocked);

        if (filtered.isEmpty() && !blocked.isEmpty()) {
            Log.d(TAG, AA_TRACE_PREFIX + " pickEnemyForAutoAttack: all candidates are blacklisted,"
                    + " fallback to full list for compatibility");
        }

        List<String> source = filtered.isEmpty() ? candidates : filtered;
        String selected = source.get(ThreadLocalRandom.current().nextInt(source.size()));
        Log.d(TAG, AA_TRACE_PREFIX + " pickEnemyForAutoAttack: total=" + candidates.size()
                + ", filtered=" + filtered.size() + ", selected=" + selected);
        return selected;
    }

    private static boolean isEnemyContact(String nick) {
        try {
            return Integer.parseInt(ContactsManager.getClassIdOfContact(nick)) == 1;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String extractNick(String chatListEntry) {
        if (isEmpty(chatListEntry)) {
            return "";
        }
        String[] parts = chatListEntry.split(":");
        if (parts.length < 2) {
            return "";
        }
        return stripItalic(parts[1]);
    }

    private static String normalizeChatUserEntry(String rawEntry) {
        if (rawEntry == null) {
            return "";
        }
        String normalized = rawEntry.trim();
        if (normalized.startsWith("\"")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("\"")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        // Разэкраниваем JS-последовательности, если запись получена из fallback-парсера.
        normalized = normalized.replace("\\\"", "\"").replace("\\\\", "\\");
        return normalized.trim();
    }

    private static String stripItalic(String nick) {
        if (nick == null) {
            return "";
        }
        return nick.replace("<i>", "").replace("</i>", "").trim();
    }

    private static String normalizeNickKey(String nick) {
        return stripItalic(nick).toLowerCase(Locale.ROOT);
    }

    private static long getBlackListRemainingMs(String nick) {
        if (isEmpty(nick)) {
            return 0L;
        }
        String key = normalizeNickKey(nick);
        Long insertedAt = autoAttackBlackList.get(key);
        if (insertedAt == null) {
            return 0L;
        }
        long ageMs = System.currentTimeMillis() - insertedAt;
        long remainingMs = AUTO_ATTACK_BLACKLIST_MS - ageMs;
        if (remainingMs <= 0L) {
            autoAttackBlackList.remove(key);
            Log.d(TAG, AA_TRACE_PREFIX + " blacklist expire: " + key + ", ageMs=" + ageMs);
            return 0L;
        }
        return remainingMs;
    }

    private static String buildEnemyTrace(String nick) {
        String cleanNick = stripItalic(nick);
        String classId = ContactsManager.getClassIdOfContact(cleanNick);
        int contactTool = ContactsManager.getToolIdOfContact(cleanNick);
        long remainingMs = getBlackListRemainingMs(cleanNick);
        return cleanNick + "{classId=" + classId
                + ", contactTool=" + contactTool
                + ", blacklisted=" + (remainingMs > 0L)
                + ", blacklistRemainingMs=" + remainingMs + "}";
    }

    private static String buildEnemyCandidatesTrace(List<String> enemies) {
        if (enemies == null || enemies.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < enemies.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(buildEnemyTrace(enemies.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class MenuItem {
        public String title;
    }

    // Вспомогательный контейнер результата парсинга списка комнаты.
    private static class FilterProcRoomResult {
        int numCharsInRoom;
        String enemyAttack;
        String html;
        String chatListU;
        List<String> enemyCandidates = new ArrayList<>();
    }
}

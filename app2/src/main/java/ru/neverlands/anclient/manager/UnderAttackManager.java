package ru.neverlands.anclient.manager;

import ru.neverlands.anclient.utils.AppLog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.anclient.model.LezBotsClass;
import ru.neverlands.anclient.model.LezBotsClassCollection;
import ru.neverlands.anclient.model.LezBotsGroup;
import ru.neverlands.anclient.model.LezSayType;
import ru.neverlands.anclient.model.UserConfig;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.Chat;
import ru.neverlands.anclient.utils.HelperStrings;

/**
 * Аналог `ANClient/UnderAttack.cs`.
 *
 * Назначение:
 * - при старте нового боя сформировать анонс "кто на кого напал",
 * - отправить этот анонс в нужный канал чата по настройке `LezSay`:
 *   общий / клан / pair / отключено.
 *
 * Зависимости:
 * - вызывается из `MainPhp.mainPhpFight(...)` при смене `LogBoi`,
 * - использует `Chat.sendMessageToServer(...)` для отправки в чат,
 * - читает профиль из `AppVars.Profile`.
 */
public final class UnderAttackManager {
    private static final String TAG = "UnderAttackManager";
    private static final String TRACE_CHAIN = "UNDER_ATTACK_TRACE";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Pattern FIGHT_PAIR_FROM_LOGS = Pattern.compile(
            "\\\"Бой между\\\"\\s*,\\s*\\[1\\s*,\\s*1\\s*,\\s*\\\"([^\\\"]+)\\\"[^\\]]*\\]\\s*,\\s*\\\"\\s*и\\s*\\\"\\s*,\\s*\\[1\\s*,\\s*2\\s*,\\s*\\\"([^\\\"]+)\\\"",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static volatile String lastFightLogId = "";
    private static volatile boolean isHumanFight = false;
    private static volatile boolean isMeAttacker = false;

    private UnderAttackManager() {
    }

    /**
     * Асинхронный парсинг HTML боя.
     * Безопасно вызывать часто: есть дедуп по `fight_ty[8]`.
     */
    public static void parseAsync(String html) {
        if (html == null || html.isEmpty()) return;
        AppLog.d(TRACE_CHAIN, TAG, "parseAsync queued: htmlLen=" + html.length()
                + ", hasFightTy=" + html.contains("var fight_ty = [")
                + ", hasParamEn=" + html.contains("var param_en = [")
                + ", hasSlotsEn=" + html.contains("var slots_en = [")
                + ", hasLivesG1=" + html.contains("var lives_g1 = [")
                + ", hasLivesG2=" + html.contains("var lives_g2 = ["));
        EXECUTOR.execute(() -> parseInternal(html));
    }

    public static boolean isHumanFight() {
        return isHumanFight;
    }

    public static boolean isMeAttacker() {
        return isMeAttacker;
    }

    private static void parseInternal(String html) {
        UserConfig profile = AppVars.Profile;
        if (profile == null) {
            AppLog.d(TRACE_CHAIN, TAG, "parse skip: profile is null");
            return;
        }

        String fightTy = HelperStrings.subString(html, "var fight_ty = [", "];");
        if (fightTy == null || fightTy.isEmpty()) {
            AppLog.d(TRACE_CHAIN, TAG, "parse skip: fight_ty not found");
            return;
        }
        String[] fightTyParts = fightTy.split(",");
        if (fightTyParts.length < 9) {
            AppLog.d(TRACE_CHAIN, TAG, "parse skip: fight_ty invalid, parts=" + fightTyParts.length);
            return;
        }
        String fightLogId = stripQuotes(fightTyParts[8]);
        if (fightLogId.isEmpty()) {
            AppLog.d(TRACE_CHAIN, TAG, "parse skip: empty fightLogId");
            return;
        }
        if (fightLogId.equals(lastFightLogId)) {
            AppLog.d(TRACE_CHAIN, TAG, "parse skip: duplicate fightLogId=" + fightLogId);
            return;
        }

        FightParticipants participants = extractParticipants(html, profile);
        if (participants == null) {
            AppLog.d(TRACE_CHAIN, TAG, "parse skip: participants not resolved, logId=" + fightLogId);
            return;
        }

        String nick1 = participants.nick1;
        String nick2 = participants.nick2;
        isMeAttacker = participants.isMeAttacker;

        String fightType = HelperStrings.subString(html, " начался (", ")");
        if (fightType == null || fightType.isEmpty()) {
            fightType = "обычное нападение";
        }
        isHumanFight = !"нападение бота".equalsIgnoreCase(fightType);
        if (!isHumanFight) {
            AppLog.d(TRACE_CHAIN, TAG, "parse skip: non-human fight, type=" + fightType + ", logId=" + fightLogId);
            return;
        }

        // Канал сообщения о нападении теперь выбирается по конкретной группе противника (как просили в UI),
        // а не по глобальному profile.LezSay.
        // Зависимость:
        // - resolveAttackSayType() использует тот же порядок/критерии групп, что и LezFight.SelectFoeGroup().
        LezSayType attackSayType = resolveAttackSayType(profile, html, nick2);
        if (attackSayType == null || attackSayType == LezSayType.No) {
            AppLog.d(TRACE_CHAIN, TAG, "parse skip: attackSayType=" + attackSayType
                    + ", logId=" + fightLogId
                    + ", foe=" + nick2);
            return;
        }

        String channelPrefix = buildChannelPrefix(attackSayType);
        String locationSuffix = buildLocationSuffixByRegNum();
        String message;
        if (isMeAttacker) {
            message = channelPrefix + " я нападаю на перса «" + nick2 + "»"
                    + locationSuffix
                    + ", [[[" + fightLogId + "]]] (" + fightType + ")!";
        } else {
            if ("невидимка".equalsIgnoreCase(nick1)) {
                message = channelPrefix + " на меня напал невидимка"
                        + locationSuffix
                        + ", [[[" + fightLogId + "]]] (" + fightType + ")!";
            } else {
                message = channelPrefix + " на меня напал перс «" + nick1 + "»"
                        + locationSuffix
                        + ", [[[" + fightLogId + "]]] (" + fightType + ")!";
            }
        }

        AppLog.d(TRACE_CHAIN, TAG, "announce: logId=" + fightLogId
                + ", say=" + attackSayType
                + ", meAttacker=" + isMeAttacker
                + ", foe=" + nick2);
        AppLog.d(TAG, "announce: " + message);
        Chat.sendMessageToServer(message);
        lastFightLogId = fightLogId;
        AppLog.d(TRACE_CHAIN, TAG, "announce sent: logId=" + fightLogId);
    }

    private static FightParticipants extractParticipants(String html, UserConfig profile) {
        String myNick = profile != null && profile.UserNick != null ? profile.UserNick.trim() : "";

        String livesG1 = HelperStrings.subString(html, "var lives_g1 = [", "];");
        String livesG2 = HelperStrings.subString(html, "var lives_g2 = [", "];");
        if (livesG1 != null && !livesG1.isEmpty() && livesG2 != null && !livesG2.isEmpty()) {
            String nick1 = extractNickFromLives(livesG1);
            String nick2 = extractNickFromLives(livesG2);
            boolean meAttacker = myNick.isEmpty() || !livesG2.contains(myNick);
            return new FightParticipants(nick1, nick2, meAttacker, "lives");
        }

        String[] pairFromLogs = parseFightPairFromLogs(html);
        if (pairFromLogs != null) {
            String nick1 = pairFromLogs[0];
            String nick2 = pairFromLogs[1];
            boolean meAttacker = true;
            if (!myNick.isEmpty()) {
                if (myNick.equalsIgnoreCase(nick1)) {
                    meAttacker = true;
                } else if (myNick.equalsIgnoreCase(nick2)) {
                    meAttacker = false;
                }
            }
            return new FightParticipants(nick1, nick2, meAttacker, "logs");
        }

        String nickOw = extractNickFromParam(html, "var param_ow = [");
        String nickEn = extractNickFromParam(html, "var param_en = [");
        if (!nickOw.isEmpty() && !nickEn.isEmpty()) {
            boolean meAttacker = true;
            if (!myNick.isEmpty()) {
                if (myNick.equalsIgnoreCase(nickOw)) {
                    meAttacker = true;
                } else if (myNick.equalsIgnoreCase(nickEn)) {
                    meAttacker = false;
                }
            }
            return new FightParticipants(nickOw, nickEn, meAttacker, "params");
        }

        return null;
    }

    private static String extractNickFromLives(String lives) {
        if (lives == null || lives.isEmpty()) {
            return "невидимка";
        }
        String[] parts = lives.split(",");
        if (parts.length > 2 && !lives.startsWith("[4")) {
            return stripQuotes(parts[1]);
        }
        return "невидимка";
    }

    private static String[] parseFightPairFromLogs(String html) {
        String logsRaw = HelperStrings.subString(html, "var logs = ", ";");
        if (logsRaw == null || logsRaw.isEmpty()) {
            return null;
        }

        String normalized = logsRaw.replace("\\\"", "\"");
        Matcher matcher = FIGHT_PAIR_FROM_LOGS.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }

        String nick1 = stripQuotes(matcher.group(1));
        String nick2 = stripQuotes(matcher.group(2));
        if (nick1.isEmpty() || nick2.isEmpty()) {
            return null;
        }
        return new String[]{nick1, nick2};
    }

    private static String extractNickFromParam(String html, String marker) {
        String[] param = parseJsArray(html, marker);
        if (param == null || param.length < 1) {
            return "";
        }
        return stripQuotes(param[0]);
    }

    private static String buildChannelPrefix(LezSayType sayType) {
        if (sayType == null) return "";
        switch (sayType) {
            case Clan:
                return "%clan%";
            case Pair:
                return "%pair%";
            case Chat:
            case No:
            default:
                return "";
        }
    }

    private static String buildLocationSuffix() {
        // В Android-порте нет отдельного Profile.MapLocation как в C#.
        // Используем последнее известное значение локации комнаты, если оно есть.
        String location = AppVars.myLocOld;
        if (location == null || location.trim().isEmpty()) {
            return "";
        }
        return ", клетка " + location.trim();
    }

    private static String buildLocationSuffixByRegNum() {
        String regNum = null;

        if (AppVars.Profile != null && AppVars.Profile.MapLocation != null) {
            String candidate = AppVars.Profile.MapLocation.trim();
            if (candidate.matches("\\d{1,4}-\\d{1,5}")) {
                regNum = candidate;
            }
        }

        if ((regNum == null || regNum.isEmpty()) && AppVars.AutoMovingDestinaton != null) {
            String candidate = AppVars.AutoMovingDestinaton.trim();
            if (candidate.matches("\\d{1,4}-\\d{1,5}")) {
                regNum = candidate;
            }
        }

        if ((regNum == null || regNum.isEmpty()) && AppVars.myLocOld != null) {
            String candidate = AppVars.myLocOld.trim();
            if (candidate.matches("\\d{1,4}-\\d{1,5}")) {
                regNum = candidate;
            }
        }

        if (regNum == null || regNum.isEmpty()) {
            return "";
        }
        return ", клетка № " + regNum;
    }

    private static String stripQuotes(String value) {
        if (value == null) return "";
        return value.replace("\"", "").replace("'", "").trim();
    }

    /**
     * Выбирает канал сообщения о нападении по группе противника.
     * Логика приоритета групп 1:1 совпадает с LezFight.SelectFoeGroup():
     * first-match по отсортированному profile.LezGroups (Id DESC, MinimalLevel DESC).
     */
    private static LezSayType resolveAttackSayType(UserConfig profile, String html, String foeNameHint) {
        if (profile == null || profile.LezGroups == null || profile.LezGroups.isEmpty()) {
            return profile != null ? profile.LezSay : LezSayType.No;
        }

        String[] paramEn = parseJsArray(html, "var param_en = [");
        String[] slotsEn = parseJsArray(html, "var slots_en = [");
        if (paramEn == null || paramEn.length < 6 || slotsEn == null || slotsEn.length < 1) {
            String fallbackName = foeNameHint == null ? "" : foeNameHint.trim();
            if (!fallbackName.isEmpty() && isBossName(fallbackName)) {
                LezSayType bossSay = resolveBossAttackSayType(profile);
                AppLog.d(TRACE_CHAIN, TAG, "resolveAttackSayType: boss fallback, foe=" + fallbackName
                        + ", say=" + bossSay);
                return bossSay;
            }
            return profile.LezSay;
        }

        String foeName = stripQuotes(paramEn[0]);
        int foeLevel;
        try {
            foeLevel = Integer.parseInt(stripQuotes(paramEn[5]));
        } catch (Exception ignore) {
            foeLevel = 33;
        }

        String foeImage = stripQuotes(slotsEn[0]).toLowerCase();
        if (!foeImage.startsWith("bot") && !foeImage.startsWith("_xneto") && !foeImage.startsWith("_xsilf")) {
            foeName = "Человек";
        }

        for (LezBotsGroup group : profile.LezGroups) {
            if (group == null) continue;
            boolean match = false;
            switch (group.Id) {
                case 1:
                    match = true;
                    break;
                case 10:
                    match = "Человек".equalsIgnoreCase(foeName) && foeLevel >= group.MinimalLevel;
                    break;
                case 20:
                    match = !"Человек".equalsIgnoreCase(foeName) && foeLevel >= group.MinimalLevel;
                    break;
                case 21:
                    // Группа `Id=21` (Боссы) больше не зависит от hardcode-списков имён.
                    // Зависимости/переменные:
                    // - `foeName` -> имя противника из текущего fight-кадра,
                    // - `isBossName(foeName)` -> lookup в `LezBotsClassCollection` (runtime bottypes.xml).
                    match = isBossName(foeName);
                    break;
                default:
                    LezBotsClass foeClass = LezBotsClassCollection.getClass(group.Id);
                    String className = foeClass != null ? foeClass.name : null;
                    match = className != null
                            && className.equalsIgnoreCase(foeName)
                            && foeLevel >= group.MinimalLevel;
                    break;
            }
            if (match) {
                LezSayType resolved = group.AttackSay != null ? group.AttackSay : profile.LezSay;
                AppLog.d(TRACE_CHAIN, TAG, "resolveAttackSayType: matched groupId=" + group.Id
                        + ", minLevel=" + group.MinimalLevel
                        + ", foe=" + foeName
                        + ", level=" + foeLevel
                        + ", say=" + resolved);
                return resolved;
            }
        }

        AppLog.d(TRACE_CHAIN, TAG, "resolveAttackSayType: no group match, fallback profile.LezSay=" + profile.LezSay
                + ", foe=" + foeName
                + ", level=" + foeLevel);
        return profile.LezSay;
    }

    private static LezSayType resolveBossAttackSayType(UserConfig profile) {
        if (profile == null || profile.LezGroups == null) {
            return LezSayType.No;
        }
        for (LezBotsGroup group : profile.LezGroups) {
            if (group == null) continue;
            if (group.Id == 21) {
                LezSayType resolved = group.AttackSay != null ? group.AttackSay : profile.LezSay;
                AppLog.d(TRACE_CHAIN, TAG, "resolveBossAttackSayType: group21 found, say=" + resolved);
                return resolved;
            }
        }
        AppLog.d(TRACE_CHAIN, TAG, "resolveBossAttackSayType: group21 missing, fallback profile.LezSay=" + profile.LezSay);
        return profile.LezSay;
    }

    /**
     * Парсит JS-массив вида `var name = [ ... ];` в сырой список элементов.
     */
    private static String[] parseJsArray(String html, String marker) {
        String jsArray = HelperStrings.subString(html, marker, "];");
        if (jsArray == null || jsArray.trim().isEmpty()) {
            return null;
        }
        return jsArray.split(",");
    }

    /**
     * Аналог C# IsBossName() для выбора группы Id=21 (Боссы).
     *
     * Важно:
     * - единый источник классификации = `LezBotsClassCollection`;
     * - изменение типа (`bot`/`boss`) производится в `bottypes.xml`, без правки Java-кода.
     */
    private static boolean isBossName(String name) {
        return LezBotsClassCollection.isBossName(name);
    }

    private static final class FightParticipants {
        final String nick1;
        final String nick2;
        final boolean isMeAttacker;

        FightParticipants(String nick1, String nick2, boolean isMeAttacker, String source) {
            this.nick1 = nick1;
            this.nick2 = nick2;
            this.isMeAttacker = isMeAttacker;
            AppLog.d(TRACE_CHAIN, TAG, "participants resolved: source=" + source
                    + ", nick1=" + nick1
                    + ", nick2=" + nick2
                    + ", meAttacker=" + isMeAttacker);
        }
    }
}

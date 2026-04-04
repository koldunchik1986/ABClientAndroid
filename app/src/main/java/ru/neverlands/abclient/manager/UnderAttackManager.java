package ru.neverlands.abclient.manager;

import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.neverlands.abclient.model.LezBotsClassCollection;
import ru.neverlands.abclient.model.LezBotsGroup;
import ru.neverlands.abclient.model.LezSayType;
import ru.neverlands.abclient.model.UserConfig;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Chat;
import ru.neverlands.abclient.utils.HelperStrings;

/**
 * Аналог `ABClient/UnderAttack.cs`.
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
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

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
            return;
        }

        String fightTy = HelperStrings.subString(html, "var fight_ty = [", "];");
        if (fightTy == null || fightTy.isEmpty()) {
            return;
        }
        String[] fightTyParts = fightTy.split(",");
        if (fightTyParts.length < 9) {
            return;
        }
        String fightLogId = stripQuotes(fightTyParts[8]);
        if (fightLogId.isEmpty() || fightLogId.equals(lastFightLogId)) {
            return;
        }
        lastFightLogId = fightLogId;

        String livesG1 = HelperStrings.subString(html, "var lives_g1 = [", "];");
        if (livesG1 == null || livesG1.isEmpty()) {
            return;
        }
        String[] lives1Parts = livesG1.split(",");
        String nick1 = (lives1Parts.length > 2 && !livesG1.startsWith("[4"))
                ? stripQuotes(lives1Parts[1])
                : "невидимка";

        String livesG2 = HelperStrings.subString(html, "var lives_g2 = [", "];");
        if (livesG2 == null || livesG2.isEmpty()) {
            return;
        }
        String[] lives2Parts = livesG2.split(",");
        String nick2 = (lives2Parts.length > 2 && !livesG2.startsWith("[4"))
                ? stripQuotes(lives2Parts[1])
                : "невидимка";

        // Полная синхронизация с C# логикой:
        // если мой ник НЕ найден в группе 2, считаем что я атакующий.
        String myNick = profile.UserNick == null ? "" : profile.UserNick;
        isMeAttacker = !livesG2.contains(myNick);

        String fightType = HelperStrings.subString(html, " начался (", ")");
        if (fightType == null || fightType.isEmpty()) {
            fightType = "обычное нападение";
        }
        isHumanFight = !"нападение бота".equalsIgnoreCase(fightType);
        if (!isHumanFight) {
            return;
        }

        // Канал сообщения о нападении теперь выбирается по конкретной группе противника (как просили в UI),
        // а не по глобальному profile.LezSay.
        // Зависимость:
        // - resolveAttackSayType() использует тот же порядок/критерии групп, что и LezFight.SelectFoeGroup().
        LezSayType attackSayType = resolveAttackSayType(profile, html);
        if (attackSayType == null || attackSayType == LezSayType.No) {
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

        Log.d(TAG, "announce: " + message);
        Chat.sendMessageToServer(message);
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
    private static LezSayType resolveAttackSayType(UserConfig profile, String html) {
        if (profile == null || profile.LezGroups == null || profile.LezGroups.isEmpty()) {
            return profile != null ? profile.LezSay : LezSayType.No;
        }

        String[] paramEn = parseJsArray(html, "var param_en = [");
        String[] slotsEn = parseJsArray(html, "var slots_en = [");
        if (paramEn == null || paramEn.length < 6 || slotsEn == null || slotsEn.length < 1) {
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
                    match = isBossName(foeName);
                    break;
                default:
                    String className = LezBotsClassCollection.getClass(group.Id).name;
                    match = className != null
                            && className.equalsIgnoreCase(foeName)
                            && foeLevel >= group.MinimalLevel;
                    break;
            }
            if (match) {
                return group.AttackSay != null ? group.AttackSay : profile.LezSay;
            }
        }

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
     */
    private static boolean isBossName(String name) {
        if (name == null) return false;
        return "Королева Змей".equalsIgnoreCase(name)
                || "Хранитель Леса".equalsIgnoreCase(name)
                || "Громлех Синезубый".equalsIgnoreCase(name)
                || "Выползень".equalsIgnoreCase(name);
    }
}

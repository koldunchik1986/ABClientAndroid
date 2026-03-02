package ru.neverlands.abclient.manager;

import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        if (profile == null || profile.LezSay == LezSayType.No) {
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

        String channelPrefix = buildChannelPrefix(profile.LezSay);
        String locationSuffix = buildLocationSuffix();
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

    private static String stripQuotes(String value) {
        if (value == null) return "";
        return value.replace("\"", "").replace("'", "").trim();
    }
}

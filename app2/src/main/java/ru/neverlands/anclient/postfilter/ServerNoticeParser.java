package ru.neverlands.anclient.postfilter;

import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import ru.neverlands.anclient.manager.AutoCutManager;
import ru.neverlands.anclient.manager.AutoFunctionsManager;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.HelperStrings;

public class ServerNoticeParser {
    private static final String TAG = "ServerNoticeParser";
    static final long SERVER_NOTICE_CHAT_DEDUP_MS = 1500L;
    static volatile long lastServerNoticeAtMs = 0L;
    static volatile String lastServerNoticeKey = "";

    static String extractServerNoticeFromMainHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        String direct = HelperStrings.subString(
                html,
                "<font class=nickname><font color=#cc0000><b>",
                "<br><br></b></font></font>");
        if (direct != null && !direct.trim().isEmpty()) {
            return direct.trim();
        }

        Matcher redBoldMatcher = Pattern.compile(
                "(?is)<font[^>]*color\\s*=\\s*['\\\"]?#?cc0000['\\\"]?[^>]*>\\s*<b>(.*?)<br\\s*/?>\\s*<br\\s*/?>\\s*</b>\\s*</font>")
                .matcher(html);
        if (redBoldMatcher.find()) {
            String candidate = redBoldMatcher.group(1);
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }

        Matcher redBoldSingleBrMatcher = Pattern.compile(
                "(?is)<font[^>]*color\\s*=\\s*['\\\"]?#?cc0000['\\\"]?[^>]*>\\s*<b>(.*?)<br\\s*/?>\\s*</b>\\s*</font>")
                .matcher(html);
        if (redBoldSingleBrMatcher.find()) {
            String candidate = redBoldSingleBrMatcher.group(1);
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }

        Matcher alertMatcher = Pattern.compile("(?is)alert\\s*\\(\\s*['\\\"](.*?)['\\\"]\\s*\\)")
                .matcher(html);
        if (alertMatcher.find()) {
            String candidate = alertMatcher.group(1);
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }
        return "";
    }

    static String extractServerNoticeFromPlainText(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }
        String normalized = plainText
                .replace('\u00A0', ' ')
                .replace("\r", "\n")
            .replaceAll("[\t\u000B\f]+", " ")
                .replaceAll(" +", " ")
                .trim();
        if (normalized.isEmpty()) {
            return "";
        }

        String[] lines = normalized.split("\n+");
        for (String line : lines) {
            String candidate = line == null ? "" : line.trim().replaceAll(" +", " ");
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.length() < 8) {
                continue;
            }
            if (candidate.startsWith("Деньги:")
                    || candidate.startsWith("Сила:")
                    || candidate.startsWith("Ловкость:")
                    || candidate.startsWith("Удача:")
                    || candidate.startsWith("Здоровье:")
                    || candidate.startsWith("Знания:")
                    || candidate.startsWith("Мудрость:")
                    || candidate.startsWith("Побед:")
                    || candidate.startsWith("Поражений:")
                    || candidate.startsWith("Масса Вашего инвентаря:")
                    || candidate.startsWith("Использовать \"")
                    || candidate.startsWith("Кому:")
                    || candidate.startsWith("На кого:")
                    || candidate.startsWith("Ваш персонаж")
                    || candidate.startsWith("Инвентарь")
                    || candidate.startsWith("Вернуться")) {
                continue;
            }
            if (candidate.contains("Ошибка")
                    || candidate.contains("Нельзя")
                    || candidate.contains("Нет такого игрока")
                    || candidate.contains("не можете")
                    || candidate.contains("слишком часто")
                    || candidate.contains("лимит")
                    || candidate.contains("невозможно")
                    || candidate.contains("не выполнено")
                    || candidate.contains("уже действует")
                    || candidate.contains("достигнут")) {
                return candidate;
            }
        }
        return "";
    }

    public static String extractServerNoticeForUi(String html) {
        return extractServerNoticeForUi(html, "");
    }

    public static String extractServerNoticeForUi(String html, String plainText) {
        String htmlNotice = extractServerNoticeFromMainHtml(html);
        if (htmlNotice != null && !htmlNotice.trim().isEmpty()) {
            return htmlNotice.trim();
        }
        return extractServerNoticeFromPlainText(plainText);
    }

    public static void postServerNotificationToChat(String messageText, String sourceTag, String addressHint) {
        if (AppVars.getContext() == null) {
            return;
        }
        String normalized = normalizeServerNotificationText(messageText);
        if (normalized.isEmpty()) {
            return;
        }
        if (shouldSuppressAutoFishPopupChatNotice(normalized, sourceTag)) {
            String msgSkip = "SERVER_NOTICE_TRACE skip duplicate auto-fish popup: source="
                    + sourceTag + ", text=" + normalized;
            AppLog.d(TAG, msgSkip);
            return;
        }
        String type = resolveServerNotificationType(normalized, sourceTag, addressHint);
        boolean appendAutoCureTarget = shouldAppendAutoCureTarget(sourceTag, addressHint);
        if (appendAutoCureTarget) {
            type = "\u0410\u0432\u0442\u043e-\u041b\u0435\u0447\u0435\u043d\u0438\u0435";
            String cureTarget = resolveAutoCureNoticeTargetNick();
            if (!cureTarget.isEmpty()) {
                normalized = normalized + " на игрока '" + cureTarget + "'";
            }
        }
        String dedupKey = type + "|" + normalized;
        long nowMs = System.currentTimeMillis();
        if (dedupKey.equals(lastServerNoticeKey) && (nowMs - lastServerNoticeAtMs) < SERVER_NOTICE_CHAT_DEDUP_MS) {
            String msgDup = "SERVER_NOTICE_TRACE dedup: key=" + dedupKey + ", source=" + sourceTag;
            AppLog.d(TAG, msgDup);
            return;
        }
        lastServerNoticeKey = dedupKey;
        lastServerNoticeAtMs = nowMs;
        maybeRequestGarbageCleanupFromNotice(normalized, sourceTag);

        String messageHtml = FightAuto.buildServerChatTimeHtml()
                + "<font color=#333399><b>["
                + escapeHtmlText(type)
                + "]</b>:</font> "
                + escapeHtmlText(normalized);
        Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        msgIntent.putExtra("message", messageHtml);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(msgIntent);

        String msg = "SERVER_NOTICE_TRACE post: type=" + type
                + ", source=" + sourceTag
                + ", address=" + addressHint
                + ", text=" + normalized;
        AppLog.d(TAG, msg);
        if (appendAutoCureTarget) {
            AppVars.CureNickDone = "";
        }
    }

    private static void maybeRequestGarbageCleanupFromNotice(String normalized, String sourceTag) {
        if (AppVars.getContext() == null || normalized == null || normalized.isEmpty()) {
            return;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!lower.contains("случайно найдено")
                || !lower.contains("предмет")
                || !lower.contains(AutoCutManager.GARBAGE_ITEM_NAME.toLowerCase(Locale.ROOT))) {
            return;
        }
        try {
            if (!AutoFunctionsManager.getInstance(AppVars.getContext()).isAutoCutLikeEnabled()) {
                AppLog.d(AutoCutManager.TRACE_CHAIN, TAG,
                        "garbage notice ignored: AutoCut disabled, source=" + sourceTag);
                return;
            }
            AutoCutManager.getInstance(AppVars.getContext())
                    .requestGarbageCleanupAfterCut("server_notice:" + sourceTag);
        } catch (Exception error) {
            AppLog.w(AutoCutManager.TRACE_CHAIN, TAG,
                    "garbage notice cleanup request failed, source=" + sourceTag, error);
        }
    }

    static boolean shouldSuppressAutoFishPopupChatNotice(String normalized, String sourceTag) {
        String lowerSource = sourceTag == null ? "" : sourceTag.toLowerCase(Locale.ROOT);
        String lowerText = normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
        if (!containsAny(lowerSource, "map_bridge_popup", "popup", "bridge")) {
            return false;
        }
        return (lowerText.contains("доход за рыбалку") || lowerText.contains("потери за рыбалку"))
                && lowerText.contains("умелка");
    }

    static boolean shouldAppendAutoCureTarget(String sourceTag, String addressHint) {
        String cureTarget = resolveAutoCureNoticeTargetNick();
        if (cureTarget.isEmpty()) {
            return false;
        }
        String lowerSource = sourceTag == null ? "" : sourceTag.toLowerCase(Locale.ROOT);
        String lowerAddress = addressHint == null ? "" : addressHint.toLowerCase(Locale.ROOT);
        return containsAny(lowerSource, "main_php_sys_message", "auto_cure", "cure")
                || containsAny(lowerAddress, "wca=85", "doctorform", "wca=27", "cure", "med");
    }

    static String resolveAutoCureNoticeTargetNick() {
        String fromDone = AppVars.CureNickDone == null ? "" : AppVars.CureNickDone.trim();
        if (!fromDone.isEmpty()) {
            return fromDone;
        }
        String fromCurrent = AppVars.CureNick == null ? "" : AppVars.CureNick.trim();
        if (!fromCurrent.isEmpty()) {
            return fromCurrent;
        }
        return "";
    }

    static String normalizeServerNotificationText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text
                .replace('\u00A0', ' ')
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() > 700) {
            normalized = normalized.substring(0, 700) + "...";
        }
        return normalized;
    }

    static String resolveServerNotificationType(String messageText, String sourceTag, String addressHint) {
        String lowerMessage = messageText == null ? "" : messageText.toLowerCase(Locale.ROOT);
        String lowerSource = sourceTag == null ? "" : sourceTag.toLowerCase(Locale.ROOT);
        String lowerAddress = addressHint == null ? "" : addressHint.toLowerCase(Locale.ROOT);

        if (containsAny(lowerMessage, "травм", "леч", "исцел", "аптеч", "отравлен")) {
            return "Авто-лечение";
        }
        if (containsAny(lowerMessage, "рыбал", "удоч", "снаст", "приманк")) {
            return "Авто-рыбалка";
        }
        if (containsAny(lowerAddress, "wca=85", "doctorform", "im=6", "cure", "med")) {
            return "Авто-лечение";
        }
        if (containsAny(lowerAddress, "get_id=43", "get_id=17", "wca=28", "wca=27")) {
            return "Быстрое действие";
        }
        if (containsAny(lowerSource, "popup", "bridge")) {
            return "Системное окно";
        }
        return "Системное сообщение";
    }

    static boolean containsAny(String value, String... tokens) {
        if (value == null || value.isEmpty() || tokens == null) {
            return false;
        }
        for (String token : tokens) {
            if (token != null && !token.isEmpty() && value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    static String escapeHtmlText(String value) {
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
}

package ru.neverlands.abclient.postfilter;

import android.content.Intent;
import android.util.Log;

import java.util.Locale;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

public class MapActAjaxPhp {
    private static final String TAG = "MapActAjaxPhp";
    private static final String NEED_SHOVEL_MARKER = "\u043d\u0443\u0436\u043d\u0430 \u043b\u043e\u043f\u0430\u0442\u0430";
    private static final String NEED_SHOVEL_ACTION_MARKER = "\u0447\u0442\u043e\u0431\u044b \u043a\u043e\u043f\u0430\u0442\u044c";
    private static final String NEED_SHOVEL_WORD_MARKER = "\u043b\u043e\u043f\u0430\u0442";
    private static final long DIG_RESULT_DEDUP_WINDOW_MS = 2000L;
    private static volatile String lastDigResultMessage = "";
    private static volatile long lastDigResultAtMs = 0L;

    public static byte[] process(byte[] array) {
        if (array == null || array.length == 0) {
            return array;
        }
        try {
            String html = Russian.getString(array);
            if (html == null || html.isEmpty()) {
                return array;
            }
            boolean autoTreasureActive = AppVars.DoSearchBox
                    || AppVars.AutoMoving
                    || (AppVars.Profile != null && AppVars.Profile.AutoDig);
            if (!autoTreasureActive) {
                return array;
            }
            String resoMessage = extractFirstResoMessage(html);
            String sourceForNeedShovel = (resoMessage != null && !resoMessage.isEmpty()) ? resoMessage : html;
            if (containsNeedShovelPopup(sourceForNeedShovel)) {
                AppVars.AutoTreasureShovelReady = false;
                AppVars.AutoTreasureShovelReadyOption = "";
                AppVars.AutoTreasureDigPendingInventory = true;
                AppVars.TreasureDigPauseNonCombatAutoFunctions = true;
                Log.w(TAG, "AUTO_SEARCH_BOX_TRACE dig flow: server requires shovel, retry equip");
            }
            if (isTreasureDigResultMessage(resoMessage)) {
                postTreasureDigResultToChat(resoMessage);
            }
        } catch (Exception e) {
            Log.w(TAG, "AUTO_SEARCH_BOX_TRACE dig flow: map_act parse failed", e);
        }
        return array;
    }

    private static boolean containsNeedShovelPopup(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        if (lower.contains(NEED_SHOVEL_MARKER)) {
            return true;
        }
        return lower.contains("reso@[")
                && lower.contains(NEED_SHOVEL_ACTION_MARKER)
                && lower.contains(NEED_SHOVEL_WORD_MARKER);
    }

    private static boolean isTreasureDigResultMessage(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("\u0432\u044b \u0432\u044b\u043a\u043e\u043f\u0430\u043b\u0438")
                || lower.contains("\u0432\u044b \u043e\u0442\u043a\u043e\u043f\u0430\u043b\u0438")
                || lower.contains("\u043d\u0438\u0447\u0435\u0433\u043e \u043d\u0435 \u043d\u0430\u0448\u043b\u0438")
                || lower.contains("\u043a\u043b\u0430\u0434")
                || lower.contains("\u043f\u0440\u0435\u0434\u043c\u0435\u0442:");
    }

    private static void postTreasureDigResultToChat(String rawMessage) {
        if (rawMessage == null) {
            return;
        }
        String message = rawMessage.trim();
        if (message.isEmpty()) {
            return;
        }
        if (containsNeedShovelPopup(message)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (message.equals(lastDigResultMessage) && (now - lastDigResultAtMs) < DIG_RESULT_DEDUP_WINDOW_MS) {
            return;
        }
        if (AppVars.getContext() == null) {
            return;
        }
        lastDigResultMessage = message;
        lastDigResultAtMs = now;

        String messageHtml = MainPhp.buildServerChatTimeHtmlExternal()
                + "<font color=#004bbb>Авто-Клад: " + message + "</font>";
        Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        intent.putExtra("message", messageHtml);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
        Log.d(TAG, "AUTO_SEARCH_BOX_TRACE dig result message posted to chat");
    }

    private static String extractFirstResoMessage(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        int resoPos = html.indexOf("RESO@[");
        if (resoPos == -1) {
            return null;
        }
        int quoteStart = html.indexOf('"', resoPos);
        if (quoteStart == -1) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = quoteStart + 1; i < html.length(); i++) {
            char ch = html.charAt(i);
            if (escaped) {
                switch (ch) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case '"':
                    case '\\':
                    case '/':
                        sb.append(ch);
                        break;
                    case 'u':
                        if (i + 4 < html.length()) {
                            String hex = html.substring(i + 1, i + 5);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                                break;
                            } catch (Exception ignored) {
                                sb.append('u');
                                break;
                            }
                        }
                        sb.append('u');
                        break;
                    default:
                        sb.append(ch);
                        break;
                }
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                return sb.toString();
            }
            sb.append(ch);
        }
        return null;
    }
}

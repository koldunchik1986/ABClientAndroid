package ru.neverlands.abclient.postfilter;

import android.util.Log;

import ru.neverlands.abclient.utils.Russian;

/**
 * Порт postfilter для /js/hp.js из ПК-версии (ABClient/PostFilter/HpJs.cs).
 *
 * Зависимости и контракт:
 * - Вызывается из {@link Filter#process} для URL "/js/hp.js".
 * - Подменяет строку обновления `hbar` на вызов Android-бриджа
 *   `window.external.ShowHpMaTimers(...)`.
 * - Через {@code WebAppInterface.showHpMaTimers(...)} синхронизируются интервалы регена
 *   `AppVars.PersIntHP/AppVars.PersIntMA`, которые используются в LezFight/MainPhp
 *   для расчёта состояний `Restoring/Timeout`.
 */
public class HpJs {
    private static final String TAG = "HpJs";
    private static final String TARGET_EXPRESSION =
            "s.substring(0, s.lastIndexOf(':')+1) + \"[<font color=#bb0000><b>\" + Math.round(curHP)+\"</b>/<b>\"+maxHP+\"</b></font> | <font color=#336699><b>\"+Math.round(curMA)+\"</b>/<b>\"+maxMA+\"</b></font>]\"";
    private static final String BRIDGE_EXPRESSION =
            "window.external.ShowHpMaTimers(s,curHP,maxHP,intHP,curMA,maxMA,intMA)";

    private HpJs() {
        // Utility class.
    }

    public static byte[] process(byte[] array) {
        if (array == null || array.length == 0) {
            return array;
        }

        String html = Russian.getString(array);
        int beforeTargetCount = countOccurrences(html, TARGET_EXPRESSION);
        int beforeBridgeCount = countOccurrences(html, BRIDGE_EXPRESSION);
        Log.d(TAG, "HPJS_TRACE before: bytes=" + array.length
                + ", targetCount=" + beforeTargetCount
                + ", bridgeCount=" + beforeBridgeCount);

        // 1:1 C# Filter.HpJs(): точечная замена выражения hbar на bridge-вызов.
        String processed = html.replace(TARGET_EXPRESSION, BRIDGE_EXPRESSION);
        int afterTargetCount = countOccurrences(processed, TARGET_EXPRESSION);
        int afterBridgeCount = countOccurrences(processed, BRIDGE_EXPRESSION);
        Log.d(TAG, "HPJS_TRACE after: replaced=" + (!processed.equals(html))
                + ", targetCount=" + afterTargetCount
                + ", bridgeCount=" + afterBridgeCount);

        return Russian.getBytes(processed);
    }

    private static int countOccurrences(String source, String token) {
        if (source == null || source.isEmpty() || token == null || token.isEmpty()) {
            return 0;
        }
        int count = 0;
        int from = 0;
        while (true) {
            int idx = source.indexOf(token, from);
            if (idx < 0) {
                break;
            }
            count++;
            from = idx + token.length();
        }
        return count;
    }
}

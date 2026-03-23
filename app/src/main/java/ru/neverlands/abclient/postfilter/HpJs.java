package ru.neverlands.abclient.postfilter;

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

    private HpJs() {
        // Utility class.
    }

    public static byte[] process(byte[] array) {
        if (array == null || array.length == 0) {
            return array;
        }

        String html = Russian.getString(array);

        // 1:1 C# Filter.HpJs(): точечная замена выражения hbar на bridge-вызов.
        html = html.replace(
                "s.substring(0, s.lastIndexOf(':')+1) + \"[<font color=#bb0000><b>\" + Math.round(curHP)+\"</b>/<b>\"+maxHP+\"</b></font> | <font color=#336699><b>\"+Math.round(curMA)+\"</b>/<b>\"+maxMA+\"</b></font>]\"",
                "window.external.ShowHpMaTimers(s,curHP,maxHP,intHP,curMA,maxMA,intMA)"
        );

        return Russian.getBytes(html);
    }
}

package ru.neverlands.abclient.utils;

import android.util.Log;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.FileLogger;
import ru.neverlands.abclient.utils.HelperStrings;

/**
 * Парсит текущую одежду (снаряжение) из HTML ответа сервера.
 * Эквивалент C# класса ParsedDressed из ABClient/TInvUd.cs
 *
 * Парсит slots_inv() или slots_pla() JavaScript функции, которые содержат информацию о:
 * - Text текущей одежды в руке 1 и руке 2
 * - Статус: одета ли удочка или свободно место (Слот для...)
 * - wid, vcode для снятия текущей одежды
 *
 * Структура slots_inv():
 *   params[2] -> array главных слотов (@-разделённый), index 2 = Hand1, index 12 = Hand2
 *   params[3] -> array wid'ов, index 2 = Wid текущей одежды в руке 1
 *   params[4] -> array vcode'ов
 *   params[5] -> array durability значений (@-разделённых)
 */
public class GearParser {
    private static final String TAG = "GearParser";

    public String hand1;          // Название одежды в руке 1 или "Слот для..."
    public String hand2;          // Название одежды в руке 2 или "Слот для..."
    public boolean empty1;        // true если рука 1 пуста (Слот для...)
    public boolean empty2;        // true если рука 2 пуста
    public String wid;            // wid текущей одежды (для снятия)
    public String vcode;          // vcode для снятия одежды
    public String durability1;    // "текущее/максимум" для Hand1
    public String durability2;    // "текущее/максимум" для Hand2
    public boolean isValid;

    public GearParser(String html) {
        this.isValid = false;
        this.hand1 = "";
        this.hand2 = "";
        this.empty1 = true;
        this.empty2 = true;
        this.wid = "";
        this.vcode = "";
        this.durability1 = "";
        this.durability2 = "";

        if (html == null || html.isEmpty()) {
            return;
        }

        try {
            // Пытаемся парсить slots_inv() - обычный инвентарь
            if (parseSlots(html, "slots_inv")) {
                return;
            }

            // Если slots_inv не сработал, пытаемся slots_pla() - для плеера в боях
            parseSlots(html, "slots_pla");
        } catch (Exception e) {
            AppLog.w(TAG, "GearParser parse failed: " + e.getMessage(), e);
        }
    }

    /**
     * Парсит slots_inv(...) или slots_pla(...) из HTML
     * @return true если успешно спарсен, false иначе
     */
    private boolean parseSlots(String html, String slotsFunctionName) {
        String slotStr = HelperStrings.subString(html, slotsFunctionName + "(", ");");
        if (slotStr == null || slotStr.isEmpty()) {
            return false;
        }

        String[] params = slotStr.split(",");
        if (params.length < 6) {
            Log.d(TAG, "GearParser: not enough params in " + slotsFunctionName);
            return false;
        }

        try {
            // params[2] - основные слоты (одежда, оружие и т.д.)
            String[] mainSlots = params[2].split("@");
            if (mainSlots.length < 13) {
                return false;
            }

            // params[3] - wid'ы
            String[] wids = params[3].split("@");
            if (wids.length < 3) {
                return false;
            }
            this.wid = wids[2];

            // params[4] - vcode'ы
            String[] vcodes = params[4].split("@");
            if (vcodes.length < 3) {
                return false;
            }
            this.vcode = vcodes[2];

            // params[5] - durability
            String[] durabilityParams = params[5].split("@");

            // Парсим Hand1 (индекс 2)
            parseHand(mainSlots[2], durabilityParams.length > 2 ? durabilityParams[2] : "", 1);

            // Парсим Hand2 (индекс 12)
            if (mainSlots.length > 12) {
                parseHand(mainSlots[12], durabilityParams.length > 12 ? durabilityParams[12] : "", 2);
            }

            this.isValid = true;
            String msg = String.format(
                "✅ GearParser: hand1='%s' (empty=%b, dur=%s), hand2='%s' (empty=%b, dur=%s)",
                hand1, empty1, durability1, hand2, empty2, durability2
            );
            AppLog.d(TAG, TAG, msg);
            return true;

        } catch (Exception e) {
            Log.w(TAG, "GearParser parseSlots error", e);
            return false;
        }
    }

    /**
     * Парсит одну руку из mainSlot строки вида "Название:param1|param2|param3|..."
     */
    private void parseHand(String mainSlot, String durabilityStr, int handNum) {
        if (mainSlot == null || mainSlot.isEmpty()) {
            return;
        }

        String[] parts = mainSlot.split(":", -1);
        if (parts.length < 2) {
            return;
        }

        String itemName = parts[1];
        boolean isEmpty = itemName.toLowerCase().startsWith("слот");

        if (handNum == 1) {
            this.hand1 = itemName;
            this.empty1 = isEmpty;
            this.durability1 = parseItemDurability(parts);
            if (!durabilityStr.isEmpty()) {
                this.durability1 = durabilityStr;
            }
        } else if (handNum == 2) {
            this.hand2 = itemName;
            this.empty2 = isEmpty;
            this.durability2 = parseItemDurability(parts);
            if (!durabilityStr.isEmpty()) {
                this.durability2 = durabilityStr;
            }
        }
    }

    /**
     * Парсит durability из item params вида "param1|param2|...|durability|..."
     * Durability обычно на позиции 7 (индекс 7 в split результате)
     */
    private String parseItemDurability(String[] itemParts) {
        if (itemParts.length < 2) {
            return "";
        }
        // itemParts[2] содержит что-то вида "параметры:24|25|35|0|60|0|40"
        // где index 7 = max durability
        // Но это будут числа, нам нужен вид "current/max"
        // На самом деле durability приходит в отдельном поле params[5],
        // поэтому здесь мы просто возвращаем пусто
        return "";
    }

    /**
     * Проверяет, одета ли удочка в руке (независимо от её названия)
     * @param handNum 1 или 2
     * @return true если в руке одета удочка/спиннинг, false если пусто
     */
    public boolean isRodWorn(int handNum) {
        String hand = (handNum == 1) ? hand1 : hand2;
        boolean isEmpty = (handNum == 1) ? empty1 : empty2;

        if (isEmpty) {
            return false;
        }

        String lowerHand = hand.toLowerCase();
        return lowerHand.contains("удочка") || lowerHand.contains("спиннинг");
    }

    /**
     * Проверяет есть ли вообще что-то в руках
     */
    public boolean hasAnythingWorn() {
        return !empty1 || !empty2;
    }

    /**
     * Возвращает описание текущего состояния для логирования
     */
    public String getStatusString() {
        return String.format(
            "Hand1: '%s' (%s, dur=%s), Hand2: '%s' (%s, dur=%s)",
            hand1, empty1 ? "empty" : "worn", durability1,
            hand2, empty2 ? "empty" : "worn", durability2
        );
    }
}

package ru.neverlands.abclient.model;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.HelperStrings;

/**
 * Порт C# `ParsedDressed` из `ABClient/TInvUd.cs`.
 *
 * Назначение:
 * - разобрать состояние экипировки по JS-данным `slots_inv(...)` / `slots_pla(...)`;
 * - определить, что надето в руках;
 * - предоставить C#-совместимые проверки `IsWear1/IsWear2/IsWearKnife`
 *   с теми же побочными эффектами обновления `AppVars`.
 *
 * Зависимости:
 * - `AppVars.Profile`: настройки авто-рыбалки/авто-охоты;
 * - `AppVars.AutoFishHand*`, `AppVars.AutoSkinHand*`: runtime-состояние экипировки;
 * - `AppVars.ACTION_ADD_CHAT_MESSAGE`: уведомление в чат при смене ножа.
 */
public class ParsedDressed {
    private static final String TAG = "ParsedDressed";

    public boolean Valid;
    public String Wid;
    public String Vcod;
    public boolean Empty1;
    public boolean Empty2;
    public boolean InRightSlot;
    public String Hand1;
    public String Hand2;

    private final List<String> slist = new ArrayList<>();
    private final List<String> dlist = new ArrayList<>();
    /**
     * Полный список надетых предметов по всем слотам экипировки (не только руки).
     *
     * Зачем нужен:
     * - режим AutoFury должен понимать, надет ли "Свиток Удар Ярости"/"Снежок" в любом слоте;
     * - проверка только `slist` (слоты рук) давала ложный `armed=false` и вызывала цикл повторного надевания.
     *
     * Зависимости:
     * - заполняется методом `collectEquippedSlots(...)` при разборе `slots_inv(...)` и `slots_pla(...)`;
     * - читается в `IsWearFuryScroll()`.
     */
    private final List<String> equippedNameList = new ArrayList<>();
    /**
     * Состояние/долговечность для `equippedNameList` в том же индексе (`current/max`).
     *
     * Зависимости:
     * - формируется в `collectEquippedSlots(...)` (с использованием `safeGet` и `extractMaxDolg`);
     * - используется в `IsWearFuryScroll()` для заполнения `AppVars.AutoFuryHandD`.
     */
    private final List<String> equippedDolgList = new ArrayList<>();

    private static final String[] SKIN_KNIFE_NAMES = new String[]{
            "Малый Разделочный Нож",
            "Охотничий Нож",
            "Вороненый Охотничий Нож",
            "Разделочный Топорик",
            "Нож Мастера-охотника"
    };
    private static final String[] FURY_SCROLL_NAMES = new String[]{
            "Свиток Удар Ярости",
            "Снежок"
    };

    public ParsedDressed(String html) {
        Valid = false;
        Wid = "";
        Vcod = "";
        Hand1 = "";
        Hand2 = "";

        AppVars.AutoFishHand1 = "";
        AppVars.AutoFishHand2 = "";
        AppVars.AutoFishHand1D = "";
        AppVars.AutoFishHand2D = "";

        String slotsInv = HelperStrings.subString(html, "slots_inv(", ");");
        if (isNullOrEmpty(slotsInv)) {
            parseSlotsPla(html);
            return;
        }
        parseSlotsInv(slotsInv);
    }

    private void parseSlotsPla(String html) {
        String slotsPla = HelperStrings.subString(html, "slots_pla(", ");");
        if (isNullOrEmpty(slotsPla)) {
            return;
        }

        String[] farg = slotsPla.split(",", -1);
        if (farg.length < 5) {
            return;
        }

        String[] fmain = farg[2].split("@", -1);
        if (fmain.length < 13) {
            return;
        }

        String[] fdo = farg[3].split("@", -1);
        if (fdo.length < 13) {
            return;
        }
        collectEquippedSlots(fmain, fdo);

        String[] fhand1 = fmain[2].split(":", -1);
        if (fhand1.length < 2) {
            return;
        }

        Hand1 = fhand1[1];
        Empty1 = startsWithSlot(Hand1);
        String fcurdlg1 = "";
        String fmaxdlg1 = "";
        if (!Empty1) {
            fcurdlg1 = safeGet(fdo, 2);
            fmaxdlg1 = extractMaxDolg(fhand1);
        }

        String[] fhand2 = fmain[12].split(":", -1);
        if (fhand2.length < 2) {
            return;
        }

        Hand2 = fhand2[1];
        Empty2 = startsWithSlot(Hand2);
        String fcurdlg2 = "";
        String fmaxdlg2 = "";
        if (!Empty2) {
            fcurdlg2 = safeGet(fdo, 12);
            fmaxdlg2 = extractMaxDolg(fhand2);
        }

        if (!Empty1) {
            slist.add(Hand1);
            dlist.add(fcurdlg1 + "/" + fmaxdlg1);
        }
        if (!Empty2) {
            slist.add(Hand2);
            dlist.add(fcurdlg2 + "/" + fmaxdlg2);
        }

        Valid = true;
    }

    private void parseSlotsInv(String slotsInv) {
        String[] pslots = slotsInv.split(",", -1);
        if (pslots.length < 6) {
            return;
        }

        String[] slmain = pslots[2].split("@", -1);
        if (slmain.length < 13) {
            return;
        }

        String[] slwid = pslots[3].split("@", -1);
        if (slwid.length < 3) {
            return;
        }
        Wid = slwid[2];

        String[] slvcod = pslots[4].split("@", -1);
        if (slvcod.length < 3) {
            return;
        }
        Vcod = slvcod[2];

        String[] sldlg = pslots[5].split("@", -1);
        if (sldlg.length < 13) {
            return;
        }
        collectEquippedSlots(slmain, sldlg);

        String[] slhand1 = slmain[2].split(":", -1);
        if (slhand1.length < 2) {
            return;
        }

        Hand1 = slhand1[1];
        Empty1 = startsWithSlot(Hand1);
        String curdlg1 = "";
        String maxdlg1 = "";
        if (!Empty1) {
            curdlg1 = safeGet(sldlg, 2);
            maxdlg1 = extractMaxDolg(slhand1);
        }

        String[] slhand2 = slmain[12].split(":", -1);
        if (slhand2.length < 2) {
            return;
        }

        Hand2 = slhand2[1];
        Empty2 = startsWithSlot(Hand2);
        String curdlg2 = "";
        String maxdlg2 = "";
        if (!Empty2) {
            curdlg2 = safeGet(sldlg, 12);
            maxdlg2 = extractMaxDolg(slhand2);
        }

        if (!Empty1) {
            slist.add(Hand1);
            dlist.add(curdlg1 + "/" + maxdlg1);
        }
        if (!Empty2) {
            slist.add(Hand2);
            dlist.add(curdlg2 + "/" + maxdlg2);
        }

        Valid = true;
    }

    /**
     * C# parity: проверка первой удочки + побочный эффект обновления AppVars.AutoFishHand1/AutoFishHand1D.
     */
    public boolean IsWear1() {
        boolean isWear1 = false;
        InRightSlot = false;

        if (AppVars.Profile == null
                || !AppVars.Profile.FishAutoWear
                || equalsIgnoreCase(AppVars.Profile.FishHandOne, "нет")) {
            isWear1 = true;
        } else {
            if (equalsIgnoreCase(AppVars.Profile.FishHandOne, "Любая удочка")) {
                if ((slist.size() > 0) && (containsIgnoreCase(slist.get(0), "удочка") || containsIgnoreCase(slist.get(0), "спиннинг"))) {
                    isWear1 = true;
                    AppVars.AutoFishHand1 = slist.get(0);
                    AppVars.AutoFishHand1D = dlist.get(0);
                    slist.remove(0);
                    dlist.remove(0);
                } else {
                    // Сохранена C#-семантика (включая исходный индекс в проверке "спиннинг").
                    if ((slist.size() > 1) && (containsIgnoreCase(slist.get(1), "удочка") || containsIgnoreCase(slist.get(0), "спиннинг"))) {
                        isWear1 = true;
                        InRightSlot = true;
                        AppVars.AutoFishHand1 = slist.get(1);
                        AppVars.AutoFishHand1D = dlist.get(1);
                        slist.remove(1);
                        dlist.remove(1);
                    }
                }
            } else {
                if ((slist.size() > 0) && containsIgnoreCase(slist.get(0), AppVars.Profile.FishHandOne)) {
                    isWear1 = true;
                    AppVars.AutoFishHand1 = slist.get(0);
                    AppVars.AutoFishHand1D = dlist.get(0);
                    slist.remove(0);
                    dlist.remove(0);
                } else {
                    if ((slist.size() > 1) && containsIgnoreCase(slist.get(1), AppVars.Profile.FishHandOne)) {
                        isWear1 = true;
                        InRightSlot = true;
                        AppVars.AutoFishHand1 = slist.get(1);
                        AppVars.AutoFishHand1D = dlist.get(1);
                        slist.remove(1);
                        dlist.remove(1);
                    }
                }
            }
        }

        return isWear1;
    }

    /**
     * C# parity: проверка второй удочки + побочный эффект обновления AppVars.AutoFishHand2/AutoFishHand2D.
     */
    public boolean IsWear2() {
        boolean isWear2 = false;

        if (AppVars.Profile == null
                || !AppVars.Profile.FishAutoWear
                || equalsIgnoreCase(AppVars.Profile.FishHandTwo, "нет")) {
            isWear2 = true;
        } else {
            if (equalsIgnoreCase(AppVars.Profile.FishHandTwo, "Любая удочка")) {
                if ((slist.size() > 0) && (containsIgnoreCase(slist.get(0), "удочка") || containsIgnoreCase(slist.get(0), "спиннинг"))) {
                    AppVars.AutoFishHand2 = slist.get(0);
                    AppVars.AutoFishHand2D = dlist.get(0);
                    isWear2 = true;
                } else {
                    // Сохранена C#-семантика (включая исходный индекс в проверке "спиннинг").
                    if ((slist.size() > 1) && (containsIgnoreCase(slist.get(1), "удочка") || containsIgnoreCase(slist.get(0), "спиннинг"))) {
                        AppVars.AutoFishHand2 = slist.get(1);
                        AppVars.AutoFishHand2D = dlist.get(1);
                        isWear2 = true;
                    }
                }
            } else {
                if ((slist.size() > 0) && containsIgnoreCase(slist.get(0), AppVars.Profile.FishHandTwo)) {
                    AppVars.AutoFishHand2 = slist.get(0);
                    AppVars.AutoFishHand2D = dlist.get(0);
                    isWear2 = true;
                } else {
                    if ((slist.size() > 1) && containsIgnoreCase(slist.get(1), AppVars.Profile.FishHandTwo)) {
                        AppVars.AutoFishHand2 = slist.get(1);
                        AppVars.AutoFishHand2D = dlist.get(1);
                        isWear2 = true;
                    }
                }
            }
        }

        return isWear2;
    }

    /**
     * C# parity: проверка, надет ли разделочный нож, + обновление AutoSkin runtime-полей.
     */
    public boolean IsWearKnife() {
        for (int i = 0; i < slist.size(); i++) {
            for (String knifeName : SKIN_KNIFE_NAMES) {
                if (containsIgnoreCase(slist.get(i), knifeName)) {
                    if (AppVars.Profile != null
                            && AppVars.Profile.SkinAuto
                            && (!equalsExact(AppVars.AutoSkinHand, slist.get(i))
                            || !equalsExact(AppVars.AutoSkinHandD, dlist.get(i)))) {
                        sendKnifeChangedChatMessage(slist.get(i), dlist.get(i));
                    }

                    AppVars.AutoSkinHand = slist.get(i);
                    AppVars.AutoSkinHandD = dlist.get(i);
                    return true;
                }
            }
        }

        return false;
    }

    public static String[] getSkinKnifeNames() {
        return SKIN_KNIFE_NAMES.clone();
    }

    /**
     * Проверка, надет ли целевой свиток режима осады (`Свиток Удар Ярости`/`Снежок`).
     *
     * Назначение:
     * - используется MainPhp в ветке авто-надевания свитка перед первым ударом.
     *
     * Побочный эффект:
     * - обновляет AppVars.AutoFuryHand / AppVars.AutoFuryHandD при обнаружении свитка.
     */
    /**
     * Проверяет, надет ли целевой свиток режима осады по всем экипированным слотам.
     *
     * Почему отдельный метод:
     * - MainPhp использует его как единую проверку перед auto-wear;
     * - метод должен работать даже если свиток не в руке, а в другом слоте экипировки.
     *
     * Зависимости:
     * - `equippedNameList`/`equippedDolgList`, заполненные через `collectEquippedSlots(...)`;
     * - словарь целей `FURY_SCROLL_NAMES`;
     * - runtime-поля `AppVars.AutoFuryHand` и `AppVars.AutoFuryHandD`.
     *
     * Побочный эффект:
     * - при обнаружении свитка обновляет `AppVars.AutoFuryHand*`, чтобы в логах/состоянии
     *   был виден фактически найденный предмет.
     */
    public boolean IsWearFuryScroll() {
        for (int i = 0; i < equippedNameList.size(); i++) {
            for (String scrollName : FURY_SCROLL_NAMES) {
                if (containsIgnoreCase(equippedNameList.get(i), scrollName)) {
                    AppVars.AutoFuryHand = equippedNameList.get(i);
                    AppVars.AutoFuryHandD = i < equippedDolgList.size() ? equippedDolgList.get(i) : "";
                    return true;
                }
            }
        }
        return false;
    }

    public static String[] getFuryScrollNames() {
        return FURY_SCROLL_NAMES.clone();
    }

    private void sendKnifeChangedChatMessage(String knifeName, String knifeDolg) {
        try {
            Context context = AppVars.getContext();
            if (context == null) return;
            String message = "Разделочный нож: <span style=\"color:#009933;font-weight:bold;\">«"
                    + knifeName + " " + knifeDolg + "»</span>";
            Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
            intent.putExtra("message", message);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        } catch (Exception e) {
            Log.w(TAG, "sendKnifeChangedChatMessage failed", e);
        }
    }

    private static boolean startsWithSlot(String value) {
        if (value == null) return true;
        return value.regionMatches(true, 0, "Слот", 0, "Слот".length());
    }

    private static String extractMaxDolg(String[] handParts) {
        if (handParts == null || handParts.length < 3) {
            return "";
        }
        String[] details = handParts[2].split("\\|", -1);
        if (details.length <= 7) {
            return "";
        }
        return details[7];
    }

    /**
     * Собирает список всех надетых предметов из массива слотов экипировки.
     *
     * Вход:
     * - `slotMain`: данные слотов (имя + meta) из `slots_inv` или `slots_pla`;
     * - `slotDolg`: текущие значения износа/заряда по индексам слотов.
     *
     * Фильтрация:
     * - пропускает пустые слоты (`Слот...`);
     * - пропускает повреждённые записи, где невозможно извлечь имя предмета.
     *
     * Зависимости:
     * - использует `startsWithSlot`, `safeGet`, `extractMaxDolg`;
     * - заполняет `equippedNameList` и `equippedDolgList` синхронно по одному индексу.
     *
     * Важно:
     * - метод не трогает `slist/dlist`, чтобы не ломать C#-паритет для логики рук (`IsWear1/IsWear2/IsWearKnife`).
     */
    private void collectEquippedSlots(String[] slotMain, String[] slotDolg) {
        if (slotMain == null) {
            return;
        }

        for (int i = 0; i < slotMain.length; i++) {
            String[] slotParts = slotMain[i].split(":", -1);
            if (slotParts.length < 2) {
                continue;
            }

            String slotName = slotParts[1];
            if (startsWithSlot(slotName)) {
                continue;
            }

            String currentDolg = safeGet(slotDolg, i);
            String maxDolg = extractMaxDolg(slotParts);
            equippedNameList.add(slotName);
            equippedDolgList.add(currentDolg + "/" + maxDolg);
        }
    }

    private static boolean containsIgnoreCase(String source, String token) {
        if (source == null || token == null) return false;
        return source.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        return left.equalsIgnoreCase(right);
    }

    private static boolean equalsExact(String left, String right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        return left.equals(right);
    }

    private static String safeGet(String[] source, int index) {
        if (source == null || index < 0 || index >= source.length) {
            return "";
        }
        return source[index];
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }
}

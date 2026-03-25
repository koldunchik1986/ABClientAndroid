package ru.neverlands.abclient.postfilter;

import android.util.Log;

import java.util.Locale;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

public class MapActAjaxPhp {
    private static final String TAG = "MapActAjaxPhp";
    private static final String NEED_SHOVEL_MARKER = "\u043d\u0443\u0436\u043d\u0430 \u043b\u043e\u043f\u0430\u0442\u0430";
    private static final String NEED_SHOVEL_ACTION_MARKER = "\u0447\u0442\u043e\u0431\u044b \u043a\u043e\u043f\u0430\u0442\u044c";
    private static final String NEED_SHOVEL_WORD_MARKER = "\u043b\u043e\u043f\u0430\u0442";

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
            if (containsNeedShovelPopup(html)) {
                AppVars.AutoTreasureShovelReady = false;
                AppVars.AutoTreasureShovelReadyOption = "";
                AppVars.AutoTreasureDigPendingInventory = true;
                AppVars.TreasureDigPauseNonCombatAutoFunctions = true;
                Log.w(TAG, "AUTO_SEARCH_BOX_TRACE dig flow: server requires shovel, retry equip");
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
}
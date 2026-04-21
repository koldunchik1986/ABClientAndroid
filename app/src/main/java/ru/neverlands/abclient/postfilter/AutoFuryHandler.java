package ru.neverlands.abclient.postfilter;

import java.util.List;

import ru.neverlands.abclient.model.ParsedDressed;
import ru.neverlands.abclient.utils.AppLog;
import ru.neverlands.abclient.utils.AppVars;

public class AutoFuryHandler {

    private static final String TAG = "AutoFuryHandler";

    static boolean isAutoFuryEnabledByPreference() {
        if (AppVars.Profile != null) {
            boolean enabled = AppVars.Profile.hasAnyLezFuryGroup();
            AppVars.Profile.LezDoFury = enabled;
            if (!enabled) {
                AppVars.DoFury = false;
            }
            return enabled;
        }
        return AppVars.DoFury;
    }

    static boolean mainPhpArmedFuryScroll(String html) {
        ParsedDressed parsedDressed = new ParsedDressed(html);
        if (!parsedDressed.Valid) {
            return false;
        }
        return parsedDressed.IsWearFuryScroll();
    }

    static String mainPhpWearFuryScroll(String html) {
        ParsedDressed dressed = new ParsedDressed(html);
        if (!dressed.Valid) {
            return null;
        }
        boolean isWear = dressed.IsWearFuryScroll();
        if (!isWear) {
            List<InventoryParser.WearInvEntry> invList = InventoryParser.getWearInvList(html);
            String[] scrollNames = ParsedDressed.getFuryScrollNames();
            for (InventoryParser.WearInvEntry thing : invList) {
                if (thing.name == null || thing.wearLink == null || thing.wearLink.isEmpty()) {
                    continue;
                }
                for (String scrollName : scrollNames) {
                    if (InventoryParser.containsIgnoreCase(thing.name, scrollName)) {
                        AppLog.d(TAG, "AUTO_FURY_TRACE mainPhpWearFuryScroll: wear " + thing.name
                                + ", link=" + thing.wearLink);
                        return MainPhp.buildRedirectHtml("Одеваем " + thing.name, thing.wearLink);
                    }
                }
            }
        }
        AppVars.AutoFuryArmedScroll = false;
        return null;
    }
}

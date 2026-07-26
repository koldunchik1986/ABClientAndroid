package ru.neverlands.anclient.postfilter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.anclient.manager.NeverApi;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.ExtMap;
import ru.neverlands.anclient.utils.HelperStrings;
import ru.neverlands.anclient.utils.ParseUtils;

final class InventoryEngravingResolver {
    private static final String TAG = "InventoryEngravingResolver";
    private static final long MISS_CACHE_TTL_MS = 5L * 60L * 1000L;
    private static final Pattern COORDINATE_ENGRAVING_PATTERN = Pattern.compile(
            "(Гравировка:\\s*(?:&nbsp;|\\s|<[^>]{1,64}>)*)(\\d{3,4})\\s*,\\s*(\\d{3,4})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Map<String, String> placeNameCache = new ConcurrentHashMap<>();
    private static final Map<String, Long> missCacheAtMs = new ConcurrentHashMap<>();

    private InventoryEngravingResolver() {
    }

    static String replaceCoordinateEngravings(String html) {
        if (html == null || html.isEmpty() || !html.contains("Гравировка")) {
            return html;
        }

        Matcher matcher = COORDINATE_ENGRAVING_PATTERN.matcher(html);
        StringBuffer result = new StringBuffer(html.length());
        boolean changed = false;
        while (matcher.find()) {
            int x = ParseUtils.parseIntSafe(matcher.group(2));
            int y = ParseUtils.parseIntSafe(matcher.group(3));
            if (x <= 0 || y <= 0) {
                continue;
            }
            String placeName = resolvePlaceName(x, y);
            if (placeName.isEmpty()) {
                continue;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1) + escapeHtml(placeName)));
            changed = true;
            AppLog.d(TAG, "INV_ENGRAVING_TRACE replaced " + x + "," + y + " -> " + placeName);
        }
        if (!changed) {
            return html;
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String resolvePlaceName(int x, int y) {
        String roomKey = buildRoomKey(x, y);
        String cached = normalizePlaceName(placeNameCache.get(roomKey));
        if (!cached.isEmpty()) {
            return cached;
        }

        String mapName = normalizePlaceName(ExtMap.resolveCellNameForCoordinates(x, y));
        if (!mapName.isEmpty()) {
            placeNameCache.put(roomKey, mapName);
            return mapName;
        }

        if (isRecentMiss(roomKey)) {
            return "";
        }

        String roomHtml = NeverApi.getRoomHtml(roomKey, "inventory_engraving");
        String placeName = extractPlaceName(roomHtml);
        if (placeName.isEmpty()) {
            missCacheAtMs.put(roomKey, System.currentTimeMillis());
            AppLog.d(TAG, "INV_ENGRAVING_TRACE placename miss, room=" + roomKey);
            return "";
        }

        placeNameCache.put(roomKey, placeName);
        missCacheAtMs.remove(roomKey);
        if (AppVars.Profile != null && AppVars.Profile.MapRebuildFromPinfo) {
            boolean synced = ExtMap.syncCellLabelFromCoordinates(x, y, placeName);
            AppLog.d(TAG, "INV_ENGRAVING_TRACE map sync room=" + roomKey
                    + ", placename=" + placeName
                    + ", synced=" + synced);
        }
        return placeName;
    }

    private static String extractPlaceName(String roomHtml) {
        if (roomHtml == null || roomHtml.isEmpty()) {
            return "";
        }
        String value = HelperStrings.subString(roomHtml, "<font class=placename><b>", "</b>");
        if (value == null || value.isEmpty()) {
            value = HelperStrings.subString(roomHtml, "<font class=placename><b>", "</b></font>");
        }
        return normalizePlaceName(value);
    }

    private static boolean isRecentMiss(String roomKey) {
        Long missAt = missCacheAtMs.get(roomKey);
        if (missAt == null) {
            return false;
        }
        long age = System.currentTimeMillis() - missAt;
        if (age <= MISS_CACHE_TTL_MS) {
            return true;
        }
        missCacheAtMs.remove(roomKey);
        return false;
    }

    private static String buildRoomKey(int x, int y) {
        return "m_" + x + "_" + y;
    }


    private static String normalizePlaceName(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("<br>", " ")
                .replace("<BR>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

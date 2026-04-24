package ru.neverlands.abclient.postfilter;

import java.util.Locale;

import ru.neverlands.abclient.manager.CharacterVitalsManager;
import ru.neverlands.abclient.utils.AppLog;

final class MainPhpVitals {

    private static final String TAG = "MainPhp";

    private MainPhpVitals() {
    }

    static void mainPhpInsHp(String html) {
        try {
            MainPhp.InsHpSnapshot snapshot = parseInsHpSnapshot(html);
            if (snapshot == null) return;
            CharacterVitalsManager.updateFromInsHpSnapshot(
                    snapshot.curHp,
                    snapshot.maxHp,
                    snapshot.curMa,
                    snapshot.maxMa,
                    snapshot.intHp,
                    snapshot.intMa,
                    "MainPhp.mainPhpInsHp"
            );
            String msg = "mainPhpInsHp: parsed hpInt=";
            AppLog.d(TAG, msg);
        } catch (Exception e) {
            String msg = "mainPhpInsHp error";
            AppLog.e(TAG, msg, e);
        }
    }

    static MainPhp.InsHpSnapshot parseInsHpSnapshot(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        String htmlLower = html.toLowerCase(Locale.ROOT);
        int varPos = htmlLower.indexOf("var inshp");
        if (varPos != -1) {
            int bracketStart = html.indexOf('[', varPos);
            int bracketEnd = html.indexOf("];", bracketStart);
            if (bracketStart != -1 && bracketEnd != -1 && bracketEnd > bracketStart) {
                MainPhp.InsHpSnapshot fromVar = parseInsHpSnapshotArgs(html.substring(bracketStart + 1, bracketEnd));
                if (fromVar != null) {
                    return fromVar;
                }
            }
        }
        int start = htmlLower.indexOf("ins_hp(");
        if (start == -1) {
            return null;
        }
        start += "ins_hp(".length();
        int end = html.indexOf(')', start);
        if (end == -1 || end <= start) {
            return null;
        }
        return parseInsHpSnapshotArgs(html.substring(start, end));
    }

    static MainPhp.InsHpSnapshot parseInsHpSnapshotArgs(String args) {
        if (args == null || args.isEmpty()) {
            return null;
        }
        String[] parts = args.split(",");
        if (parts.length != 6) {
            String msg = "parseInsHpSnapshot: unexpected args count=";
            AppLog.d(TAG, msg);
            return null;
        }
        Double curHpRaw = tryParseDoubleInvariant(parts[0]);
        Double maxHpRaw = tryParseDoubleInvariant(parts[1]);
        Double curMaRaw = tryParseDoubleInvariant(parts[2]);
        Double maxMaRaw = tryParseDoubleInvariant(parts[3]);
        Double intHpRaw = tryParseDoubleInvariant(parts[4]);
        Double intMaRaw = tryParseDoubleInvariant(parts[5]);
        if (curHpRaw == null || maxHpRaw == null || curMaRaw == null || maxMaRaw == null
                || intHpRaw == null || intMaRaw == null) {
            return null;
        }
        MainPhp.InsHpSnapshot snapshot = new MainPhp.InsHpSnapshot();
        snapshot.curHp = (int) Math.round(curHpRaw);
        snapshot.maxHp = (int) Math.round(maxHpRaw);
        snapshot.curMa = (int) Math.round(curMaRaw);
        snapshot.maxMa = (int) Math.round(maxMaRaw);
        snapshot.intHp = intHpRaw;
        snapshot.intMa = intMaRaw;
        return snapshot;
    }

    static Double tryParseDoubleInvariant(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim()
                .replace("\"", "")
                .replace("'", "")
                .replace('\u00A0', ' ')
                .replace(" ", "")
                .replace(",", ".");
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

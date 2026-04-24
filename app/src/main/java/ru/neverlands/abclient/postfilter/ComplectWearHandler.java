package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.AppLog;

final class ComplectWearHandler {

    private static final String TAG = "MainPhp";

    private ComplectWearHandler() {
    }

    static String mainPhpWearComplect(String html, String complectName) {
        if (html == null || html.isEmpty() || complectName == null || complectName.isEmpty()) {
            return null;
        }

        int startIdx = 0;
        while (true) {
            final String marker = "compl_view(\"";
            startIdx = html.indexOf(marker, startIdx);
            if (startIdx == -1) break;

            startIdx += marker.length();
            int endNameIdx = html.indexOf("\"", startIdx);
            if (endNameIdx == -1) break;

            String parsedComplectName = html.substring(startIdx, endNameIdx);

            final String uidMarker = "\",\"";
            int uidStartIdx = html.indexOf(uidMarker, endNameIdx);
            if (uidStartIdx == -1) break;

            uidStartIdx += uidMarker.length();
            int uidEndIdx = html.indexOf("\"", uidStartIdx);
            if (uidEndIdx == -1) break;

            String uid = html.substring(uidStartIdx, uidEndIdx);

            int vcodeStartIdx = html.indexOf(uidMarker, uidEndIdx);
            if (vcodeStartIdx == -1) break;

            vcodeStartIdx += uidMarker.length();
            int vcodeEndIdx = html.indexOf("\"", vcodeStartIdx);
            if (vcodeEndIdx == -1) break;

            String vcode = html.substring(vcodeStartIdx, vcodeEndIdx);

            if (parsedComplectName.equalsIgnoreCase(complectName)) {
                String wearUrl = "main.php?get_id=57&uid=" + uid + "&s=2&vcode=" + vcode;
                String msgWear = "COMPLECT_TIMER_PARSE_TRACE: found complect=\"" + parsedComplectName + "\", uid=" + uid;
                AppLog.d(TAG, msgWear);
                return MainPhp.buildRedirectHtml("Таймер комплекта: одеваем " + parsedComplectName, wearUrl);
            }

            startIdx = vcodeEndIdx;
        }

        String msgNotFound = "COMPLECT_TIMER_PARSE_TRACE: complect not found \"" + complectName + "\"";
        AppLog.d(TAG, msgNotFound);
        return null;
    }
}

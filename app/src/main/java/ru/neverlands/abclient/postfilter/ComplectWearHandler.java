package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.AppLog;
import ru.neverlands.abclient.utils.AppVars;

/**
 * Handler таймерного надевания комплектов из main.php.
 *
 * Источник выноса: блок `AppVars.WearComplect` из MainPhp.process() и parser `mainPhpWearComplect(...)`.
 * Runtime-состояние: AppVars.WearComplect содержит имя комплекта, который выставил таймер FormMainTimers-порта.
 * Handler не работает в бою: флаги isFightFrame/isFightTopFrame должны быть false.
 */
final class ComplectWearHandler {

    private static final String TAG = "MainPhp";

    private ComplectWearHandler() {
    }

    /**
     * Оркестрирует один шаг таймерного надевания комплекта.
     *
     * Зависимости и переменные:
     * - html: текущая main.php страница, где ожидается JS `compl_view("name","uid","vcode")`.
     * - isFightFrame/isFightTopFrame: защита от запуска non-combat redirect в бою.
     * - AppVars.WearComplect: имя целевого комплекта; очищается и при успехе, и при not found.
     * - mainPhpWearComplect(html, AppVars.WearComplect): возвращает redirect-HTML или null.
     *
     * Возврат: redirect-HTML на надевание комплекта или null, если флаг пуст/бой/комплект не найден.
     */
    static String processWearComplectStep(String html, boolean isFightFrame, boolean isFightTopFrame) {
        if (!AppVars.WearComplect.isEmpty() && !isFightFrame && !isFightTopFrame) {
            String msgStart = "COMPLECT_TIMER_TRACE start wear, AppVars.WearComplect=\"" + AppVars.WearComplect + "\"";
            AppLog.d(TAG, msgStart);
            String complectWearHtml = mainPhpWearComplect(html, AppVars.WearComplect);
            if (complectWearHtml != null && !complectWearHtml.isEmpty()) {
                String msgComplect = "COMPLECT_TIMER_TRACE redirect to wear complect, name=" + AppVars.WearComplect;
                AppLog.d(TAG, msgComplect);
                AppVars.WearComplect = "";
                return complectWearHtml;
            } else {
                String msgComplectNotFound = "COMPLECT_TIMER_TRACE complect not found: \"" + AppVars.WearComplect + "\"";
                AppLog.d(TAG, msgComplectNotFound);
                AppVars.WearComplect = "";
            }
        }
        return null;
    }

    /**
     * Парсит список комплектов из `compl_view(...)` и строит wear URL.
     *
     * Важные локальные переменные:
     * - marker: начало JS-вызова `compl_view("`.
     * - parsedComplectName: имя комплекта из HTML, сравнивается с complectName без учёта регистра.
     * - uid: id комплекта, попадает в query `uid=`.
     * - vcode: защитный код из HTML, попадает в query `vcode=`.
     * - wearUrl: итоговый `main.php?get_id=57&uid=<uid>&s=2&vcode=<vcode>`.
     */
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

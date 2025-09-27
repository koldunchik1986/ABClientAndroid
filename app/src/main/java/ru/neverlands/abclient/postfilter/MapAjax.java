package ru.neverlands.abclient.postfilter;

import java.util.Locale;

import ru.neverlands.abclient.repository.MapRepository;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.HelperStrings;
import ru.neverlands.abclient.utils.MapPath;

public class MapAjax {
    public static String process(String html) {
        final String patternVarMap = "var map = [[";
        int posVarMap = html.indexOf(patternVarMap);
        if (posVarMap == -1) return html;

        posVarMap += patternVarMap.length();
        int posComma = html.indexOf(',', posVarMap);
        if (posComma == -1) return html;

        String stringOurLocationX = html.substring(posVarMap, posComma);
        posComma++;
        int posNextComma = html.indexOf(',', posComma);
        if (posNextComma == -1) return html;

        String stringOurLocationY = html.substring(posComma, posNextComma);
        
        // This part depends on a fully implemented MapRepository, using placeholders for now
        /*
        String positionOurLocation = String.format(Locale.US, "%s/%s_%s", stringOurLocationY, stringOurLocationX, stringOurLocationY);
        MapRepository mapRepo = MapRepository.getInstance();
        if (mapRepo.hasLocation(positionOurLocation)) {
            String regNum = mapRepo.getRegNum(positionOurLocation);
            AppVars.Profile.MapLocation = regNum;
            // TODO: Update UI: AppVars.MainForm.UpdateLocationSafe(regNum);
        }
        */

        posComma = posNextComma + 1;
        posNextComma = html.indexOf(',', posComma);
        if (posNextComma == -1) return html;

        String movingTime = html.substring(posComma, posNextComma);
        if (movingTime != null && !movingTime.isEmpty()) {
            AppVars.MovingTime = movingTime;
        }

        // TODO: Port MovableCells parsing

        if (AppVars.AutoMoving) {
            // TODO: Port full auto-navigation logic
            // This requires MapPath and a functional MapRepository
        }

        return html;
    }
}

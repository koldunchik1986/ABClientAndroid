package ru.neverlands.abclient.postfilter;

import java.util.Collections;
import java.util.List;

import ru.neverlands.abclient.model.Position;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.ExtMap;
import ru.neverlands.abclient.utils.MapPath;
import android.util.Log;

public class MapAjax {
    private static final String TAG = "MapAjax";

    public static String process(String html) {
        final String patternVarMap = "var map = [[";
        int posVarMap = html.indexOf(patternVarMap);
        if (posVarMap == -1) return html;

        posVarMap += patternVarMap.length();
        int posComma = html.indexOf(',', posVarMap);
        if (posComma == -1) return html;

        String stringX = html.substring(posVarMap, posComma).trim();
        posComma++;
        int posNextComma = html.indexOf(',', posComma);
        if (posNextComma == -1) return html;
        String stringY = html.substring(posComma, posNextComma).trim();

        String positionOurLocation = stringY + "/" + stringX + "_" + stringY;
        Position ourPos = ExtMap.Location.get(positionOurLocation);
        if (ourPos != null) {
            String regNum = ourPos.RegNum;
            if (AppVars.Profile != null) {
                AppVars.Profile.MapLocation = regNum;
            }
        }

        posComma = posNextComma + 1;
        posNextComma = html.indexOf(',', posComma);
        if (posNextComma != -1) {
            String movingTime = html.substring(posComma, posNextComma).trim();
            if (!movingTime.isEmpty()) {
                AppVars.MovingTime = movingTime;
            }
        }

        ExtMap.MovableCells.clear();
        final String patternDoubleBrackets = ",[[";
        int posOpenBrackets = html.indexOf(patternDoubleBrackets, posVarMap);
        if (posOpenBrackets != -1) {
            int posOpenBracket = posOpenBrackets + patternDoubleBrackets.length();
            while (posOpenBracket != -1) {
                int posCloseBracket = html.indexOf(']', posOpenBracket);
                if (posCloseBracket == -1) break;
                String insideBrackets = html.substring(posOpenBracket, posCloseBracket);
                if (insideBrackets.indexOf(';') != -1) return html;
                String[] parsInsideBrackets = insideBrackets.split(",");
                if (parsInsideBrackets.length == 3) {
                    String sCoordX = parsInsideBrackets[0].trim();
                    String sCoordY = parsInsideBrackets[1].trim();
                    String position = sCoordY + "/" + sCoordX + "_" + sCoordY;
                    String magicCode = parsInsideBrackets[2].trim().replace("\"", "");
                    ExtMap.MovableCells.put(position, magicCode);
                }
                posOpenBracket = html.indexOf('[', posCloseBracket);
                if (posOpenBracket == -1) break;
                posOpenBracket++;
            }
        }

        if (!AppVars.AutoMoving) return html;

        String mapLocation = AppVars.Profile != null ? AppVars.Profile.MapLocation : null;
        if (mapLocation == null) return html;

        if (mapLocation.equals(AppVars.AutoMovingDestinaton)) {
            AppVars.AutoMoving = false;
            AppVars.AutoMovingMapPath = null;
            AppVars.AutoMovingNextJump = null;
            AppVars.AutoMovingJumps = 0;
            android.util.Log.i(TAG, "AutoMoving: destination reached at " + mapLocation);
            return html;
        }

        if (AppVars.AutoMovingMapPath == null || !AppVars.AutoMovingMapPath.canUseExistingPath(mapLocation, AppVars.AutoMovingDestinaton)) {
            List<String> dest = Collections.singletonList(AppVars.AutoMovingDestinaton);
            AppVars.AutoMovingMapPath = new MapPath(mapLocation, dest);
        }

        if (!AppVars.AutoMovingMapPath.pathExists) return html;

        AppVars.AutoMovingNextJump = AppVars.AutoMovingMapPath.nextJump;
        AppVars.AutoMovingJumps = AppVars.AutoMovingMapPath.jumps;
        AppVars.AutoMovingCityGate = AppVars.AutoMovingMapPath.cityGate;

        if (AppVars.AutoMovingMapPath.isNextTeleport) {
            String newhtml = mainPhpFindEnter(html);
            if (newhtml != null && !newhtml.isEmpty()) return newhtml;
        } else if (AppVars.AutoMovingMapPath.isNextCity) {
            String newhtml = mainPhpFindEnter(html);
            if (newhtml != null && !newhtml.isEmpty()) return newhtml;
        } else {
            String nextJump = AppVars.AutoMovingMapPath.nextJump;
            if (nextJump != null) {
                String coorn = ExtMap.InvLocation.get(nextJump);
                if (coorn != null) {
                    Position position = ExtMap.Location.get(coorn);
                    if (position != null) {
                        String callMove = "moveMapTo(" + position.X + ", " + position.Y + ", map[0][2]);";
                        final String patternViewMap = "view_map();";
                        int poscript = html.toLowerCase().indexOf(patternViewMap.toLowerCase());
                        if (poscript != -1) {
                            poscript += patternViewMap.length();
                            html = html.substring(0, poscript) + callMove + html.substring(poscript);
                        }
                    }
                }
            }
        }

        return html;
    }

    private static String mainPhpFindEnter(String html) {
        final String patternEnter = "[\"dep\",\"\u0412\u043e\u0439\u0442\u0438\",\"";
        int pos = html.indexOf(patternEnter);
        if (pos == -1) return null;
        pos += patternEnter.length();
        int posEnd = html.indexOf('"', pos);
        if (posEnd == -1) return null;
        String vcode = html.substring(pos, posEnd);
        String link = "main.php?get_id=56&act=10&go=dep&vcode=" + vcode;
        return Filter.buildRedirectString("\u0412\u0445\u043e\u0434", link);
    }
}

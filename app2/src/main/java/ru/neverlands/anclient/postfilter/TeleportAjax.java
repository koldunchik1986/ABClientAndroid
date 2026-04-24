package ru.neverlands.anclient.postfilter;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import ru.neverlands.anclient.model.Position;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.ExtMap;
import ru.neverlands.anclient.utils.MapPath;

public class TeleportAjax {

    public static String process(String html) {
        if (!AppVars.AutoMoving) return null;
        if (AppVars.Profile == null) return null;

        String mapLocation = AppVars.Profile.MapLocation;
        String destination = AppVars.AutoMovingDestinaton;
        if (mapLocation == null || destination == null) return null;

        if (AppVars.AutoMovingMapPath == null ||
                !AppVars.AutoMovingMapPath.canUseExistingPath(mapLocation, destination)) {
            List<String> dest = Collections.singletonList(destination);
            AppVars.AutoMovingMapPath = new MapPath(mapLocation, dest);
        }

        AppVars.AutoMovingNextJump = AppVars.AutoMovingMapPath.nextJump;
        AppVars.AutoMovingJumps = AppVars.AutoMovingMapPath.jumps;
        AppVars.AutoMovingCityGate = AppVars.AutoMovingMapPath.cityGate;

        if (AppVars.AutoMovingJumps == 0) {
            AppVars.AutoMoving = false;
            AppVars.AutoMovingMapPath = null;
            AppVars.AutoMovingNextJump = null;
            AppVars.AutoMovingJumps = 0;
            return null;
        }

        String nextJump = AppVars.AutoMovingNextJump;
        if (nextJump == null) return null;

        String telep = subString(html, "var telep = [[", "]];");
        if (telep != null && !telep.isEmpty()) {
            String[] stelep = telep.split(Pattern.quote("],["));
            for (String etelep : stelep) {
                String[] pars = etelep.split(",");
                if (pars.length < 5) continue;
                int x, y;
                try { x = Integer.parseInt(pars[0].trim()); } catch (NumberFormatException e) { continue; }
                try { y = Integer.parseInt(pars[1].trim()); } catch (NumberFormatException e) { continue; }
                String coor = ExtMap.makePosition(x, y);
                Position pos = ExtMap.Location.get(coor);
                if (pos == null) continue;
                String regnum = pos.RegNum;
                if (regnum == null || !regnum.equals(nextJump)) continue;
                String pr = pars[3].trim();
                String vcode = pars[4].trim().replace("\"", "");
                String name = pars[2].trim().replace("\"", "");
                String link = "main.php?get_id=16&act=1&x=" + x + "&y=" + y + "&pr=" + pr + "&vcode=" + vcode;
                return Filter.buildRedirectString("\u0422\u0435\u043b\u0435\u043f\u043e\u0440\u0442 " + name, link);
            }
        }

        String build = subString(html, "var vcode = [[", "]];");
        if (build != null && !build.isEmpty()) {
            String[] sbuild = build.split(Pattern.quote("],["));
            if (sbuild.length >= 3) {
                String[] pars = sbuild[2].split(",");
                if (pars.length >= 2) {
                    String vcodex = pars[1].trim().replace("\"", "");
                    String linkx = "main.php?get_id=56&act=10&go=up&vcode=" + vcodex;
                    return Filter.buildRedirectString("\u0412\u044b\u0445\u043e\u0434\u0438\u043c \u0438\u0437 \u0442\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0430", linkx);
                }
            }
        }

        return null;
    }

    private static String subString(String html, String start, String end) {
        int pos = html.indexOf(start);
        if (pos == -1) return null;
        pos += start.length();
        int posEnd = html.indexOf(end, pos);
        if (posEnd == -1) return null;
        return html.substring(pos, posEnd);
    }
}

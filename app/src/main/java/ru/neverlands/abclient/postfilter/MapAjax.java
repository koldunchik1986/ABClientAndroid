package ru.neverlands.abclient.postfilter;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ru.neverlands.abclient.model.Position;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.ExtMap;
import ru.neverlands.abclient.utils.MapPath;
import android.util.Log;

public class MapAjax {
    private static final String TAG = "MapAjax";
    private static final long SEARCH_BOX_VISITED_TTL_MS = 24L * 60L * 60L * 1000L;

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
            markSearchBoxVisited(regNum);
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

        if (AppVars.AutoMovingDestinaton == null || AppVars.AutoMovingDestinaton.isEmpty()) {
            Log.w(TAG, "AUTO_MOVING_TRACE: destination is empty while AutoMoving=true");
            return html;
        }

        if (mapLocation.equals(AppVars.AutoMovingDestinaton)) {
            if (!AppVars.DoSearchBox) {
                AppVars.AutoMoving = false;
                AppVars.AutoMovingMapPath = null;
                AppVars.AutoMovingNextJump = null;
                AppVars.AutoMovingJumps = 0;
                android.util.Log.i(TAG, "AutoMoving: destination reached at " + mapLocation);
                return html;
            }

            String nextSearchDestination = findNextDestForBox(mapLocation);
            if (nextSearchDestination == null || nextSearchDestination.isEmpty()) {
                Log.i(TAG, "AUTO_SEARCH_BOX_TRACE: no next destination from " + mapLocation);
                return html;
            }

            AppVars.AutoMovingDestinaton = nextSearchDestination;
            AppVars.AutoMovingMapPath = null;
            AppVars.AutoMovingNextJump = null;
            AppVars.AutoMovingJumps = 0;
            Log.i(TAG, "AUTO_SEARCH_BOX_TRACE: rotate destination to " + nextSearchDestination);
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

    /**
     * C# parity (`FormMainNavigator.FindNextDestForBox`):
     * находит ближайшую клетку, которую не посещали >= 1 суток.
     */
    public static String findNextDestForBox(String sourceLocation) {
        String source = sourceLocation;
        if ((source == null || source.isEmpty()) && AppVars.Profile != null) {
            source = AppVars.Profile.MapLocation;
        }
        if (source == null || source.isEmpty() || !ExtMap.Cells.containsKey(source)) {
            return null;
        }

        int[] idx = new int[] {0, 0, -1, 1, -1, 1, -1, 1};
        int[] idy = new int[] {-1, 1, 0, 0, -1, -1, 1, 1};

        Set<String> visited = new HashSet<>();
        ArrayDeque<String> frontier = new ArrayDeque<>();
        visited.add(source);
        frontier.add(source);

        long nowMs = System.currentTimeMillis();
        while (!frontier.isEmpty()) {
            int batch = frontier.size();
            for (int k = 0; k < batch; k++) {
                String current = frontier.poll();
                if (current == null || current.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < idx.length; i++) {
                    String next = moveMapCell(current, idx[i], idy[i]);
                    if (next == null || next.isEmpty() || visited.contains(next)) {
                        continue;
                    }
                    visited.add(next);
                    frontier.add(next);

                    if (isSearchBoxCandidate(next, nowMs)) {
                        return next;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSearchBoxCandidate(String regNum, long nowMs) {
        Long visitedAt = AppVars.SearchBoxVisited.get(regNum);
        if (visitedAt == null || visitedAt <= 0L) {
            return true;
        }
        return (nowMs - visitedAt) >= SEARCH_BOX_VISITED_TTL_MS;
    }

    private static String moveMapCell(String regNum, int dx, int dy) {
        String location = ExtMap.InvLocation.get(regNum);
        if (location == null || location.isEmpty()) {
            return null;
        }
        String[] parts = location.split("[/_]");
        if (parts.length < 2) {
            return null;
        }
        int y;
        int x;
        try {
            y = Integer.parseInt(parts[0]);
            x = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }

        String position = ExtMap.makePosition(x + dx, y + dy);
        Position pos = ExtMap.Location.get(position);
        if (pos == null || pos.RegNum == null || pos.RegNum.isEmpty()) {
            return null;
        }
        return ExtMap.Cells.containsKey(pos.RegNum) ? pos.RegNum : null;
    }

    private static void markSearchBoxVisited(String regNum) {
        if (regNum == null || regNum.isEmpty()) {
            return;
        }
        AppVars.SearchBoxVisited.put(regNum, System.currentTimeMillis());
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

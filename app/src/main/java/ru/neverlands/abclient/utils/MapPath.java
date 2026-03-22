package ru.neverlands.abclient.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import ru.neverlands.abclient.model.Cell;
import ru.neverlands.abclient.model.CityGateType;
import ru.neverlands.abclient.model.Position;
import ru.neverlands.abclient.utils.ExtMap;

public class MapPath {
    public boolean pathExists = false;
    public String[] path;
    public int cost;
    public boolean hasTeleport;
    public int botLevel;
    public String destination;
    public int jumps;
    public String nextJump;
    public boolean isNextTeleport;
    public boolean isNextCity;
    public boolean isIslandRequired;
    public CityGateType cityGate = CityGateType.None;

    private static final String[] ISLAND_CELLS = {
        "11-396","11-397","11-398","11-426","11-427","11-428",
        "11-456","11-457","11-458","11-487","11-488"
    };

    private static final Random DICE = new Random();

    private final Map<String, MapPathNode> matrix = new HashMap<>();
    private final Map<String, Object> destinations = new HashMap<>();
    private final List<MapPathNode> bestPathes = new ArrayList<>();
    private boolean added;

    public MapPath(String sourceCellNumber, List<String> destinationCellNumberList) {
        pathExists = false;
        if (destinationCellNumberList == null) return;
        if (destinationCellNumberList.size() == 1 && sourceCellNumber.equals(destinationCellNumberList.get(0))) return;

        boolean flag = false;
        for (String destCell : destinationCellNumberList) {
            flag = !isIslandCell(sourceCellNumber) && isIslandCell(destCell);
        }
        if (flag) sourceCellNumber = "11-398";

        matrix.put(sourceCellNumber, new MapPathNode(sourceCellNumber));
        for (String destCell : destinationCellNumberList) {
            destinations.put(destCell, null);
        }

        int iteration = 1;
        do {
            added = false;
            List<String> currentList = new ArrayList<>();
            for (Map.Entry<String, MapPathNode> entry : matrix.entrySet()) {
                if (entry.getValue().cellNumbers.length == iteration) {
                    currentList.add(entry.getKey());
                }
            }
            if (currentList.isEmpty()) break;

            for (String cellNumber : currentList) {
                MapPathNode node = matrix.get(cellNumber);
                String h = ExtMap.InvLocation.get(cellNumber);
                if (h == null) continue;

                String[] scp = h.split("[/_]");
                if (scp.length < 2) continue;
                int y, x;
                try {
                    y = Integer.parseInt(scp[0]);
                    x = Integer.parseInt(scp[1]);
                } catch (NumberFormatException e) {
                    continue;
                }

                int[] idx = {0, 0, -1, 1, -1, 1, -1, 1};
                int[] idy = {-1, 1, 0, 0, -1, -1, 1, 1};
                for (int i = 0; i < idx.length; i++) {
                    int xnew = x + idx[i];
                    int ynew = y + idy[i];
                    String hnew = ExtMap.makePosition(xnew, ynew);
                    Position pos = ExtMap.Location.get(hnew);
                    if (pos == null) continue;
                    String nearCellNumber = pos.RegNum;
                    if (!ExtMap.Cells.containsKey(nearCellNumber)) continue;
                    MapPathNode nextNode = node.addCell(nearCellNumber, false, false);
                    if (nextNode != null) addNextNode(nextNode);
                }

                String[] gateTargets;
                switch (cellNumber) {
                    case "8-259": gateTargets = new String[]{"8-294"}; break;
                    case "8-294": gateTargets = new String[]{"8-259"}; break;
                    case "12-428": gateTargets = new String[]{"12-494", "12-521"}; break;
                    case "12-494": gateTargets = new String[]{"12-428", "12-521"}; break;
                    case "12-521": gateTargets = new String[]{"12-428", "12-494"}; break;
                    default: gateTargets = new String[0]; break;
                }
                for (String gateCellNumber : gateTargets) {
                    if (!ExtMap.Cells.containsKey(gateCellNumber)) continue;
                    MapPathNode nextNode = node.addCell(gateCellNumber, true, false);
                    if (nextNode != null) addNextNode(nextNode);
                }

                boolean allowTeleports = AppVars.Profile == null || AppVars.Profile.NavigatorAllowTeleports;
                if (allowTeleports && !node.hasTeleport && ExtMap.Teleports.containsKey(node.getCellNumber())) {
                    for (String teleportCellNumber : ExtMap.Teleports.keySet()) {
                        if (teleportCellNumber.equals(node.getCellNumber())) continue;
                        if (!ExtMap.Cells.containsKey(teleportCellNumber)) continue;
                        MapPathNode nextNode = node.addCell(teleportCellNumber, false, true);
                        if (nextNode != null) addNextNode(nextNode);
                    }
                }
            }
            iteration++;
        } while (added);

        if (bestPathes.isEmpty()) return;

        int index = DICE.nextInt(bestPathes.size());
        pathExists = true;
        path = bestPathes.get(index).cellNumbers;
        destination = bestPathes.get(index).getCellNumber();
        cost = bestPathes.get(index).getCost();
        hasTeleport = bestPathes.get(index).hasTeleport;
        botLevel = bestPathes.get(index).botLevel;
        isIslandRequired = flag;
        canUseExistingPath(sourceCellNumber, destination);
    }

    private void addNextNode(MapPathNode nextNode) {
        if (!bestPathes.isEmpty() && nextNode.cellNumbers.length > bestPathes.get(0).cellNumbers.length + 10) return;
        String cellNumber = nextNode.getCellNumber();
        if (!matrix.containsKey(cellNumber)) {
            matrix.put(cellNumber, nextNode);
            added = true;
        } else {
            MapPathNode oldNode = matrix.get(cellNumber);
            if (nextNode.cellNumbers.length >= oldNode.cellNumbers.length) {
                if (nextNode.compareTo(oldNode) == -1) {
                    matrix.put(cellNumber, nextNode);
                    added = true;
                }
            }
        }

        if (destinations.containsKey(nextNode.getCellNumber())) {
            if (bestPathes.isEmpty()) {
                bestPathes.add(nextNode);
            } else {
                int result = nextNode.compareTo(bestPathes.get(0));
                if (result <= 0) {
                    if (result < 0) bestPathes.clear();
                    bestPathes.add(nextNode);
                }
            }
        }
    }

    public boolean canUseExistingPath(String source, String dest) {
        if (source == null || source.isEmpty() || dest == null || dest.isEmpty()) return false;
        if (!pathExists || path == null || path.length < 2) return false;
        if (source.equals(dest)) return false;
        if (!dest.equals(destination)) return false;

        int index = -1;
        for (int i = 0; i < path.length; i++) {
            if (path[i].equals(source)) { index = i; break; }
        }
        if (index < 0) return false;

        jumps = path.length - 1 - index;
        String currentCell = path[index];
        nextJump = path[index + 1];
        isNextTeleport = false;
        isNextCity = false;
        cityGate = CityGateType.None;

        if (ExtMap.Teleports.containsKey(currentCell) && ExtMap.Teleports.containsKey(nextJump)) {
            isNextTeleport = true;
            return true;
        }

        if ("8-259".equals(currentCell) && "8-294".equals(nextJump)) {
            isNextCity = true; cityGate = CityGateType.ForpostLeftToRightGate; return true;
        }
        if ("8-294".equals(currentCell) && "8-259".equals(nextJump)) {
            isNextCity = true; cityGate = CityGateType.ForpostRightToLeftGate; return true;
        }
        if ("12-428".equals(currentCell) && "12-494".equals(nextJump)) {
            isNextCity = true; cityGate = CityGateType.OktalLeftToRightGate; return true;
        }
        if ("12-494".equals(currentCell) && "12-428".equals(nextJump)) {
            isNextCity = true; cityGate = CityGateType.OktalRightToLeftGate; return true;
        }
        if ("12-428".equals(currentCell) && "12-521".equals(nextJump)) {
            isNextCity = true; cityGate = CityGateType.OktalLeftToBottomGate; return true;
        }
        if ("12-494".equals(currentCell) && "12-521".equals(nextJump)) {
            isNextCity = true; cityGate = CityGateType.OktalRightToBottomGate; return true;
        }
        if ("12-521".equals(currentCell) && "12-428".equals(nextJump)) {
            isNextCity = true; cityGate = CityGateType.OktalBottomToLeftGate; return true;
        }
        if ("12-521".equals(currentCell) && "12-494".equals(nextJump)) {
            isNextCity = true; cityGate = CityGateType.OktalBottomToRightGate; return true;
        }
        return true;
    }

    private static boolean isIslandCell(String cellNumber) {
        for (String c : ISLAND_CELLS) {
            if (c.equals(cellNumber)) return true;
        }
        return false;
    }
}

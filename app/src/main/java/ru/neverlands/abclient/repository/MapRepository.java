package ru.neverlands.abclient.repository;

import java.util.HashMap;
import java.util.Map;

import ru.neverlands.abclient.model.Cell;
import ru.neverlands.abclient.model.Position;

public class MapRepository {
    private static final MapRepository instance = new MapRepository();

    private final Map<String, Position> location = new HashMap<>();
    private final Map<String, String> invLocation = new HashMap<>();
    private final Map<String, Cell> cells = new HashMap<>();
    private final Map<String, String> teleports = new HashMap<>();

    private MapRepository() {
        // Private constructor for singleton
        // TODO: Load data from JSON assets here
    }

    public static MapRepository getInstance() {
        return instance;
    }

    public Cell getCell(String regnum) {
        return cells.get(regnum);
    }

    public boolean hasCell(String regnum) {
        return cells.containsKey(regnum);
    }

    public String getInvLocation(String regnum) {
        return invLocation.get(regnum);
    }

    public boolean isTeleport(String regnum) {
        return teleports.containsKey(regnum);
    }

    public Map<String, String> getTeleports() {
        return teleports;
    }

    public String getRegNum(int x, int y) {
        // TODO: Implement this logic
        return null;
    }
}

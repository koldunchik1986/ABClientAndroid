package ru.neverlands.abclient.utils;

public class MapPathNode implements Comparable<MapPathNode> {
    public String[] cellNumbers;
    public String cellNumber;
    public int cost;
    public boolean hasTeleport;
    public int botLevel;

    public MapPathNode(String cellNumber) {
        this.cellNumber = cellNumber;
        this.cellNumbers = new String[]{cellNumber};
        // TODO: Initialize other fields
    }

    public MapPathNode AddCell(String newCellNumber, boolean isCity, boolean isTeleport) {
        // TODO: Port logic from MapPathNode.cs
        return null;
    }

    @Override
    public int compareTo(MapPathNode other) {
        // TODO: Port comparison logic
        return 0;
    }
}

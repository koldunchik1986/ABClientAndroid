package ru.neverlands.abclient.model;

import java.util.ArrayList;
import java.util.List;

public class Cell {
    public String CellNumber;
    public int Cost;
    public boolean HasFish;
    public boolean HasWater;
    public String HerbGroup;
    public String Name;
    public String Tooltip;
    public String Updated;
    public String NameUpdated;
    public String CostUpdated;
    public List<Object> Bots = new ArrayList<>(); // Using Object for now for MapBot

    // TODO: Add methods like AddMapBot, etc.
}

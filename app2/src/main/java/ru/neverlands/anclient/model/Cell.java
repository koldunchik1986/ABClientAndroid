package ru.neverlands.anclient.model;

import java.util.ArrayList;
import java.util.List;

public class Cell {
    /**
     * Подробная запись о ботах клетки из `map.xml`.
     *
     * Зависимости:
     * - заполняется в `ExtMap.parseCellNode(...)` из дочерних `<bots .../>`;
     * - используется `WebAppInterface.CurrentCellFullInfo()` для modern-блока под картой;
     * - старые поля `MinBotLevel`/`MaxBotLevel` сохраняются как агрегат для компактного overlay.
     */
    public static final class BotInfo {
        public final String Name;
        public final int MinLevel;
        public final int MaxLevel;
        public final String ColorCode;
        public final String DangerCode;

        public BotInfo(String name, int minLevel, int maxLevel, String colorCode, String dangerCode) {
            this.Name = name == null ? "" : name.trim();
            this.MinLevel = Math.max(0, minLevel);
            this.MaxLevel = Math.max(0, maxLevel);
            this.ColorCode = colorCode == null ? "" : colorCode.trim();
            this.DangerCode = dangerCode == null ? "" : dangerCode.trim();
        }
    }

    public String CellNumber;
    public int Cost;
    public boolean HasFish;
    public boolean HasWater;
    public String HerbGroup;
    public String Name;
    public String Region;
    public String Tooltip;
    public String Updated;
    public String NameUpdated;
    public String CostUpdated;
    /** Legacy-compatible список; фактически содержит `Cell.BotInfo`. */
    public List<Object> Bots = new ArrayList<>();
    public int MinBotLevel = 0;
    public int MaxBotLevel = 0;
}

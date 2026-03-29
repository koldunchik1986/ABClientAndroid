package ru.neverlands.abclient.utils;

import ru.neverlands.abclient.model.Cell;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.ExtMap;

public class MapPathNode implements Comparable<MapPathNode> {
    public String[] cellNumbers;
    public int[] costs;
    public boolean hasTeleport;
    public int botLevel;
    public int jumps;

    public String getCellNumber() {
        return cellNumbers == null || cellNumbers.length == 0 ? null : cellNumbers[cellNumbers.length - 1];
    }

    public int getCost() {
        return costs == null || costs.length == 0 ? 0 : costs[costs.length - 1];
    }

    private MapPathNode() {}

    public MapPathNode(String sourceCellNumber) {
        cellNumbers = new String[]{sourceCellNumber};
        hasTeleport = false;
        botLevel = 0;
        jumps = 0;
        Cell cell = ExtMap.Cells.get(sourceCellNumber);
        if (cell == null) {
            costs = new int[]{0};
            return;
        }
        costs = new int[]{0};
        botLevel = cell.MaxBotLevel;
    }

    public MapPathNode addCell(String cellNumber, boolean isGate, boolean isTeleport) {
        for (String cn : cellNumbers) {
            if (cn.equals(cellNumber)) return null;
        }
        int cost = getCost();
        int newJumps = jumps;
        if (!isGate && !isTeleport) {
            Cell currentCell = ExtMap.Cells.get(getCellNumber());
            if (currentCell == null) return null;
            cost += currentCell.Cost;
            newJumps++;
        }
        boolean newHasTeleport = hasTeleport || isTeleport;
        Cell cell = ExtMap.Cells.get(cellNumber);
        if (cell == null) return null;
        int maxBotLevel = Math.max(botLevel, cell.MaxBotLevel);

        MapPathNode node = new MapPathNode();
        node.cellNumbers = new String[cellNumbers.length + 1];
        node.costs = new int[costs.length + 1];
        System.arraycopy(cellNumbers, 0, node.cellNumbers, 0, cellNumbers.length);
        System.arraycopy(costs, 0, node.costs, 0, costs.length);
        node.cellNumbers[cellNumbers.length] = cellNumber;
        node.costs[costs.length] = cost;
        node.hasTeleport = newHasTeleport;
        node.botLevel = maxBotLevel;
        node.jumps = newJumps;
        return node;
    }

    /**
     * Сравнение кандидатов пути по приоритетам навигатора.
     *
     * Порядок критериев:
     * 1) итоговая стоимость пути;
     * 2) длина пути (количество клеток);
     * 3) приоритет `visited` ближайшего шага;
     * 4) максимальный уровень ботов по маршруту;
     * 5) наличие телепорта (как самый поздний tie-break).
     *
     * Примечание:
     * - `visited` поднят выше `botLevel`, чтобы при равных по цене/длине маршрутах
     *   гарантированно выбирать более "старую" (или ещё не отмеченную) соседнюю клетку.
     */
    @Override
    public int compareTo(MapPathNode other) {
        int result = Integer.compare(getCost(), other.getCost());
        if (result != 0) return result;
        result = Integer.compare(cellNumbers.length, other.cellNumbers.length);
        if (result != 0) return result;
        result = Long.compare(getVisitedPriorityKey(), other.getVisitedPriorityKey());
        if (result != 0) return result;
        result = Integer.compare(botLevel, other.botLevel);
        if (result != 0) return result;
        result = Boolean.compare(hasTeleport, other.hasTeleport);
        return result;
    }

    /**
     * Ключ приоритета по `visited` ближайшей соседней клетки маршрута.
     *
     * Логика:
     * - используется именно первая соседняя клетка (индекс 1), т.к. она определяет "ближайший шаг";
     * - отсутствующая метка `visited` трактуется как самая старая (максимальный приоритет);
     * - более старый timestamp => выше приоритет в compareTo (меньший ключ).
     */
    private long getVisitedPriorityKey() {
        if (cellNumbers == null || cellNumbers.length < 2) {
            return Long.MAX_VALUE;
        }
        Long visited = AppVars.SearchBoxVisited.get(cellNumbers[1]);
        if (visited == null || visited <= 0L) {
            return Long.MIN_VALUE;
        }
        return visited;
    }
}

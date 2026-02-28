package ru.neverlands.abclient.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatStats {
    private static long totalXp = 0;
    private static long totalFights = 0;
    private static final List<String> lootLog = new ArrayList<>();

    public static synchronized void addXp(long xp) {
        if (xp <= 0) return;
        totalXp += xp;
    }

    public static synchronized long getTotalXp() {
        return totalXp;
    }

    public static synchronized void addFight() {
        totalFights++;
    }

    public static synchronized long getTotalFights() {
        return totalFights;
    }

    public static synchronized void addLoot(String time, List<String> items) {
        if (items == null || items.isEmpty()) return;
        String prefix = (time == null || time.isEmpty()) ? "" : (time + " ");
        for (String item : items) {
            if (item == null || item.isEmpty()) continue;
            lootLog.add(prefix + item);
        }
    }

    public static synchronized List<String> getLootLog() {
        return Collections.unmodifiableList(new ArrayList<>(lootLog));
    }

    public static synchronized void reset() {
        totalXp = 0;
        totalFights = 0;
        lootLog.clear();
    }
}

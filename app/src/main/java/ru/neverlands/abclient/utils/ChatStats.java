package ru.neverlands.abclient.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatStats {
    private static final String TAG = "ChatStats";
    private static final SimpleDateFormat STAT_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd", Locale.US);
    private static long totalXp = 0;
    private static long totalFights = 0;
    private static final List<String> lootLog = new ArrayList<>();
    private static boolean loaded = false;
    private static String loadedDate = null;
    private static File statFile = null;

    public static synchronized void addXp(long xp) {
        ensureLoaded();
        if (xp <= 0) return;
        totalXp += xp;
        saveInternal();
    }

    public static synchronized long getTotalXp() {
        ensureLoaded();
        return totalXp;
    }

    public static synchronized void addFight() {
        ensureLoaded();
        totalFights++;
        saveInternal();
    }

    public static synchronized long getTotalFights() {
        ensureLoaded();
        return totalFights;
    }

    public static synchronized void addLoot(String time, List<String> items) {
        ensureLoaded();
        if (items == null || items.isEmpty()) return;
        String prefix = (time == null || time.isEmpty()) ? "" : (time + " ");
        for (String item : items) {
            if (item == null || item.isEmpty()) continue;
            lootLog.add(prefix + item);
        }
        saveInternal();
    }

    public static synchronized List<String> getLootLog() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(lootLog));
    }

    public static synchronized void reset() {
        ensureLoaded();
        totalXp = 0;
        totalFights = 0;
        lootLog.clear();
        saveInternal();
    }

    private static void ensureLoaded() {
        if (AppVars.getContext() == null) {
            return;
        }
        String currentDate = STAT_DATE_FORMAT.format(new Date());
        if (!loaded || !currentDate.equals(loadedDate)) {
            loadedDate = currentDate;
            loadFromFile(currentDate);
            loaded = true;
        }
    }

    private static void loadFromFile(String date) {
        totalXp = 0;
        totalFights = 0;
        lootLog.clear();
        statFile = resolveStatFile(date);
        if (statFile == null || !statFile.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(statFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("XP=")) {
                    totalXp = parseLongSafe(line.substring(3));
                } else if (line.startsWith("FIGHTS=")) {
                    totalFights = parseLongSafe(line.substring(7));
                } else if (line.startsWith("LOOT=")) {
                    String value = line.substring(5);
                    if (!value.isEmpty()) {
                        lootLog.add(value);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "loadFromFile failed", e);
        }
    }

    private static void saveInternal() {
        if (AppVars.getContext() == null) return;
        String currentDate = STAT_DATE_FORMAT.format(new Date());
        if (!currentDate.equals(loadedDate)) {
            loadedDate = currentDate;
            loadFromFile(currentDate);
        }
        if (statFile == null) {
            statFile = resolveStatFile(currentDate);
        }
        if (statFile == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("DATE=").append(currentDate).append("\n");
        sb.append("XP=").append(totalXp).append("\n");
        sb.append("FIGHTS=").append(totalFights).append("\n");
        for (String loot : lootLog) {
            sb.append("LOOT=").append(loot).append("\n");
        }
        try (FileOutputStream fos = new FileOutputStream(statFile, false)) {
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "saveInternal failed", e);
        }
    }

    private static File resolveStatFile(String date) {
        File baseLogs = AppVars.getLogsDir();
        if (baseLogs == null && AppVars.getContext() != null) {
            baseLogs = new File(AppVars.getContext().getFilesDir(), "Logs");
        }
        if (baseLogs == null) return null;
        if (!baseLogs.exists()) baseLogs.mkdirs();
        return new File(baseLogs, date + "_stat.txt");
    }

    private static long parseLongSafe(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }
}

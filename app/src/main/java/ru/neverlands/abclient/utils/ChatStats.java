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
    // Статистика хранится отдельным файлом на день: Logs/YYYYMMDD_stat.txt
    private static final SimpleDateFormat STAT_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd", Locale.US);
    private static long totalXp = 0;
    private static long totalFights = 0;
    private static final List<String> lootLog = new ArrayList<>();
    // Состояние загрузки/кеша статистики за текущую дату.
    private static boolean loaded = false;
    private static String loadedDate = null;
    private static File statFile = null;

    // Увеличение опыта — сразу сохраняем в файл.
    public static synchronized void addXp(long xp) {
        ensureLoaded();
        if (xp <= 0) return;
        totalXp += xp;
        saveInternal();
    }

    // Чтение накопленного опыта (гарантирует загрузку из файла).
    public static synchronized long getTotalXp() {
        ensureLoaded();
        return totalXp;
    }

    // Увеличение числа боёв — сразу сохраняем в файл.
    public static synchronized void addFight() {
        ensureLoaded();
        totalFights++;
        Log.d(TAG, "addFight: totalFights=" + totalFights + ", date=" + loadedDate);
        saveInternal();
    }

    // Чтение числа боёв (гарантирует загрузку из файла).
    public static synchronized long getTotalFights() {
        ensureLoaded();
        return totalFights;
    }

    // Добавление лута (с таймштампом) — сразу сохраняем.
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

    // Лог лута (копия списка, чтобы не отдавать внутреннюю коллекцию).
    public static synchronized List<String> getLootLog() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(lootLog));
    }

    // Сброс статистики — очищаем данные и сохраняем пустое состояние.
    public static synchronized void reset() {
        ensureLoaded();
        totalXp = 0;
        totalFights = 0;
        lootLog.clear();
        saveInternal();
    }

    // Гарантирует загрузку статистики для текущей даты.
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

    // Загружает статистику из файла Logs/YYYYMMDD_stat.txt.
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

    // Сохраняет статистику в файл Logs/YYYYMMDD_stat.txt.
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

    // Определяет путь Logs/YYYYMMDD_stat.txt.
    private static File resolveStatFile(String date) {
        File baseLogs = AppVars.getLogsDir();
        if (baseLogs == null && AppVars.getContext() != null) {
            baseLogs = new File(AppVars.getContext().getFilesDir(), "Logs");
        }
        if (baseLogs == null) return null;
        if (!baseLogs.exists()) baseLogs.mkdirs();
        return new File(baseLogs, date + "_stat.txt");
    }

    // Безопасный парсинг long из строки.
    private static long parseLongSafe(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }
}

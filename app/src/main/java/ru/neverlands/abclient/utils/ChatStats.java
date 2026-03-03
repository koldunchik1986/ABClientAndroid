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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatStats {
    private static final String TAG = "ChatStats";
    // Статистика хранится отдельным файлом на день: Logs/YYYYMMDD_stat.txt
    private static final SimpleDateFormat STAT_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd", Locale.US);
    /**
     * Шаблон денежного дропа в формате игры: "22 NV", "1 234 NV".
     *
     * Зависимости:
     * - используется только в {@link #parseMoneyNv(String)} для выделения суммы;
     * - результат влияет на распределение дропа в {@link #addLoot(String, List)}:
     *   денежный дроп идёт в totalNv, предметный — в itemCountByName.
     */
    private static final Pattern NV_AMOUNT_PATTERN = Pattern.compile("(?i)(\\d[\\d\\s]*)\\s*NV");
    /**
     * Шаблон ресурсного дропа в килограммах: "Клык (0.55 кг)".
     *
     * Зависимости:
     * - строка такого формата приходит из {@link ru.neverlands.abclient.utils.ChatFilter} после разбора loot-фрагмента;
     * - используется в {@link #parseResourceKg(String)} для накопления ресурсов по типам и общего веса.
     */
    private static final Pattern KG_RESOURCE_PATTERN = Pattern.compile(
            "^(.+?)\\s*\\((\\d+(?:[\\.,]\\d+)?)\\s*[кК][гГ]\\)$");
    private static long totalXp = 0;
    private static long totalFights = 0;
    /**
     * Накопленная за текущую дату сумма денежного дропа (NV).
     *
     * Зависимости:
     * - увеличивается в {@link #addLoot(String, List)} при разборе "NNN NV";
     * - читается UI-слоем в {@link ru.neverlands.abclient.ui.QuickButtonsPanel#buildStatsText()};
     * - сериализуется в файл статистики как ключ NV= в {@link #saveInternal()} и
     *   восстанавливается в {@link #loadFromFile(String)}.
     */
    private static long totalNv = 0;
    /**
     * Общий вес ресурсного дропа (в килограммах) за текущую дату.
     *
     * Зависимости:
     * - увеличивается в {@link #addLoot(String, List)} при успешном разборе строки формата "(x.xx кг)";
     * - отображается в UI-статистике через {@link ru.neverlands.abclient.ui.QuickButtonsPanel#buildStatsText()};
     * - сохраняется в статистический файл как `KG_TOTAL=...` и восстанавливается в {@link #loadFromFile(String)}.
     */
    private static double totalResourceKg = 0;
    /**
     * Накопление ресурсного дропа по типам (например: "Клык" -> 12.40 кг).
     *
     * Зависимости:
     * - пополняется в {@link #addLoot(String, List)} на основе {@link #parseResourceKg(String)};
     * - используется UI-слоем для показа детальной разбивки в окне статистики.
     */
    private static final Map<String, Double> resourceKgByType = new LinkedHashMap<>();
    /**
     * Накопление предметного дропа по названиям в штуках.
     *
     * Пример:
     * - "Золотой ключ" -> 3
     *
     * Зависимости:
     * - пополняется в {@link #addLoot(String, List)} для non-NV/non-KG записей;
     * - отображается в окне статистики через {@link ru.neverlands.abclient.ui.QuickButtonsPanel#buildStatsText()};
     * - сохраняется как `ITEM_COUNT=<name>\t<count>` и восстанавливается в {@link #loadFromFile(String)}.
     */
    private static final Map<String, Long> itemCountByName = new LinkedHashMap<>();
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

    /**
     * Возвращает текущую накопленную сумму денежного дропа NV.
     *
     * Зависимости:
     * - гарантирует lazy-load дневной статистики через {@link #ensureLoaded()};
     * - используется окном "Статистика" для строки "Денежные средства (NV)".
     */
    public static synchronized long getTotalNv() {
        ensureLoaded();
        return totalNv;
    }

    /**
     * Возвращает накопленный общий вес ресурсного дропа (кг) за текущую дату.
     */
    public static synchronized double getTotalResourceKg() {
        ensureLoaded();
        return totalResourceKg;
    }

    /**
     * Возвращает разбивку ресурсного дропа по типам: "название ресурса" -> "накопленный вес (кг)".
     */
    public static synchronized Map<String, Double> getResourceKgByType() {
        ensureLoaded();
        return Collections.unmodifiableMap(new LinkedHashMap<>(resourceKgByType));
    }

    /**
     * Возвращает разбивку предметного дропа по названиям: "название предмета" -> "количество (шт.)".
     */
    public static synchronized Map<String, Long> getItemCountByName() {
        ensureLoaded();
        return Collections.unmodifiableMap(new LinkedHashMap<>(itemCountByName));
    }

    // Добавление лута (с таймштампом) — сразу сохраняем.
    public static synchronized void addLoot(String time, List<String> items) {
        ensureLoaded();
        if (items == null || items.isEmpty()) return;
        for (String item : items) {
            if (item == null || item.isEmpty()) continue;
            // Разделяем денежный и предметный дроп:
            // - "NNN NV" -> суммируем в totalNv;
            // - "Название (x.xx кг)" -> суммируем как ресурс в кг;
            // - остальной дроп -> суммируем как предмет в штуках.
            long nv = parseMoneyNv(item);
            if (nv > 0) {
                totalNv += nv;
                continue;
            }
            ResourceKgEntry resourceKgEntry = parseResourceKg(item);
            if (resourceKgEntry != null) {
                totalResourceKg += resourceKgEntry.kilograms;
                double current = resourceKgByType.containsKey(resourceKgEntry.resourceName)
                        ? resourceKgByType.get(resourceKgEntry.resourceName) : 0d;
                resourceKgByType.put(resourceKgEntry.resourceName, current + resourceKgEntry.kilograms);
                continue;
            }
            String normalizedItem = item.trim();
            if (!normalizedItem.isEmpty()) {
                long currentCount = itemCountByName.containsKey(normalizedItem)
                        ? itemCountByName.get(normalizedItem) : 0L;
                itemCountByName.put(normalizedItem, currentCount + 1L);
            }
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
        totalNv = 0;
        totalResourceKg = 0;
        resourceKgByType.clear();
        itemCountByName.clear();
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
        totalNv = 0;
        totalResourceKg = 0;
        resourceKgByType.clear();
        itemCountByName.clear();
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
                } else if (line.startsWith("NV=")) {
                    totalNv = parseLongSafe(line.substring(3));
                } else if (line.startsWith("KG_TOTAL=")) {
                    totalResourceKg = parseDoubleSafe(line.substring(9));
                } else if (line.startsWith("KG_ITEM=")) {
                    String value = line.substring(8);
                    int splitPos = value.lastIndexOf('\t');
                    if (splitPos > 0 && splitPos < value.length() - 1) {
                        String resourceName = value.substring(0, splitPos).trim();
                        double kilograms = parseDoubleSafe(value.substring(splitPos + 1));
                        if (!resourceName.isEmpty() && kilograms > 0d) {
                            resourceKgByType.put(resourceName, kilograms);
                        }
                    }
                } else if (line.startsWith("ITEM_COUNT=")) {
                    String value = line.substring(11);
                    int splitPos = value.lastIndexOf('\t');
                    if (splitPos > 0 && splitPos < value.length() - 1) {
                        String itemName = value.substring(0, splitPos).trim();
                        long count = parseLongSafe(value.substring(splitPos + 1));
                        if (!itemName.isEmpty() && count > 0L) {
                            itemCountByName.put(itemName, count);
                        }
                    }
                } else if (line.startsWith("LOOT=")) {
                    String value = line.substring(5);
                    if (!value.isEmpty()) {
                        lootLog.add(value);
                        // Миграция старого формата: LOOT-строки (последние находки) конвертируем в поштучную статистику.
                        String migratedItem = migrateLegacyLootEntryToItemName(value);
                        if (migratedItem != null && !migratedItem.isEmpty()) {
                            long currentCount = itemCountByName.containsKey(migratedItem)
                                    ? itemCountByName.get(migratedItem) : 0L;
                            itemCountByName.put(migratedItem, currentCount + 1L);
                        }
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
        sb.append("NV=").append(totalNv).append("\n");
        sb.append("KG_TOTAL=").append(totalResourceKg).append("\n");
        for (Map.Entry<String, Double> entry : resourceKgByType.entrySet()) {
            sb.append("KG_ITEM=").append(entry.getKey()).append("\t").append(entry.getValue()).append("\n");
        }
        for (Map.Entry<String, Long> entry : itemCountByName.entrySet()) {
            sb.append("ITEM_COUNT=").append(entry.getKey()).append("\t").append(entry.getValue()).append("\n");
        }
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

    // Безопасный парсинг double из строки с поддержкой запятой и пробелов.
    private static double parseDoubleSafe(String value) {
        try {
            String normalized = value == null ? "" : value.trim().replace(" ", "").replace(',', '.');
            if (normalized.isEmpty()) {
                return 0d;
            }
            return Double.parseDouble(normalized);
        } catch (Exception ignored) {
            return 0d;
        }
    }

    /**
     * Миграция legacy-строки `LOOT=` в имя предмета для новой поштучной статистики.
     *
     * Старый формат мог быть:
     * - `HH:mm:ss Название предмета`
     * - `Название предмета`
     */
    private static String migrateLegacyLootEntryToItemName(String lootValue) {
        if (lootValue == null) return null;
        String trimmed = lootValue.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.matches("^\\d{2}:\\d{2}:\\d{2}\\s+.+$")) {
            int splitPos = trimmed.indexOf(' ');
            if (splitPos > 0 && splitPos < trimmed.length() - 1) {
                return trimmed.substring(splitPos + 1).trim();
            }
        }
        return trimmed;
    }

    /**
     * Парсит денежный дроп из текстового элемента обыска.
     *
     * Примеры входа:
     * - "22 NV"
     * - "1 234 NV"
     *
     * Возврат:
     * - сумма NV как long, если строка соответствует денежному формату;
     * - 0, если это не денежный дроп (предмет/иной текст).
     *
     * Зависимости:
     * - использует {@link #NV_AMOUNT_PATTERN} для распознавания формата;
     * - использует {@link #parseLongSafe(String)} для безопасного преобразования.
     */
    private static long parseMoneyNv(String item) {
        if (item == null) return 0;
        String normalized = item.replace('\u00A0', ' ').trim();
        Matcher matcher = NV_AMOUNT_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return 0;
        }
        String amount = matcher.group(1);
        if (amount == null || amount.isEmpty()) {
            return 0;
        }
        return parseLongSafe(amount.replace(" ", ""));
    }

    /**
     * Парсит ресурсный дроп в формате "Название (x.xx кг)".
     *
     * Возврат:
     * - {@link ResourceKgEntry}, если строка соответствует формату ресурса в кг;
     * - `null`, если строка не ресурсная.
     */
    private static ResourceKgEntry parseResourceKg(String item) {
        if (item == null) return null;
        String normalized = item.replace('\u00A0', ' ').trim();
        Matcher matcher = KG_RESOURCE_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        String resourceName = matcher.group(1);
        String kilogramsRaw = matcher.group(2);
        if (resourceName == null || resourceName.trim().isEmpty()) {
            return null;
        }
        double kilograms = parseDoubleSafe(kilogramsRaw);
        if (kilograms <= 0d) {
            return null;
        }
        return new ResourceKgEntry(resourceName.trim(), kilograms);
    }

    // DTO внутреннего разбора ресурсной записи из чата.
    private static final class ResourceKgEntry {
        private final String resourceName;
        private final double kilograms;

        private ResourceKgEntry(String resourceName, double kilograms) {
            this.resourceName = resourceName;
            this.kilograms = kilograms;
        }
    }
}

package ru.neverlands.anclient.utils;

import ru.neverlands.anclient.utils.AppLog;

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

// ParseUtils используется в одном пакете, но импортируем явно для ясности
public class ChatStats {
    private static final String TAG = "ChatStats";
    // Формат даты для служебных полей статистики (RESET_DATE/DATE).
    private static final SimpleDateFormat STAT_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd", Locale.US);
    // Персистентный файл статистики текущего профиля (без автосброса по датам).
    private static final String STAT_FILE_SUFFIX = "_stat.txt";
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
     * - строка такого формата приходит из {@link ru.neverlands.anclient.utils.ChatFilter} после разбора loot-фрагмента;
     * - используется в {@link #parseResourceKg(String)} для накопления ресурсов по типам и общего веса.
     */
    private static final Pattern KG_RESOURCE_PATTERN = Pattern.compile(
            "^(.+?)\\s*\\((\\d+(?:[\\.,]\\d+)?)\\s*[кК][гГ]\\)$");
    private static long totalXp = 0;
    private static long totalFights = 0;
    // Временная точка старта окна статистики (epoch milliseconds).
    // Устанавливается при первом создании статистики и при ручном/автоматическом сбросе.
    private static long statsStartAtMs = 0L;
    // Дата (yyyyMMdd), от которой считается текущая сессия статистики.
    private static String statsResetDateYmd = "";
    /**
     * Накопленная за текущую дату сумма денежного дропа (NV).
     *
     * Зависимости:
     * - увеличивается в {@link #addLoot(String, List)} при разборе "NNN NV";
     * - читается UI-слоем в {@link ru.neverlands.anclient.ui.QuickButtonsPanel#buildStatsText()};
     * - сериализуется в файл статистики как ключ NV= в {@link #saveInternal()} и
     *   восстанавливается в {@link #loadFromFile(String)}.
     */
    private static long totalNv = 0;
    /**
     * Общий вес ресурсного дропа (в килограммах) за текущую дату.
     *
     * Зависимости:
     * - увеличивается в {@link #addLoot(String, List)} при успешном разборе строки формата "(x.xx кг)";
     * - отображается в UI-статистике через {@link ru.neverlands.anclient.ui.QuickButtonsPanel#buildStatsText()};
     * - сохраняется в статистический файл как `KG_TOTAL=...` и восстанавливается в {@link #loadFromFile(String)}.
     */
    private static double totalResourceKg = 0;
    // Финансовый результат рыбалки за текущую сессию статистики (NV).
    private static double totalFishNv = 0d;
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
     * - отображается в окне статистики через {@link ru.neverlands.anclient.ui.QuickButtonsPanel#buildStatsText()};
     * - сохраняется как `ITEM_COUNT=<name>\t<count>` и восстанавливается в {@link #loadFromFile(String)}.
     */
    private static final Map<String, Long> itemCountByName = new LinkedHashMap<>();
    // Количество пойманной рыбы по типам: "тип рыбы" -> "шт.".
    private static final Map<String, Long> fishCountByType = new LinkedHashMap<>();
    private static final List<String> lootLog = new ArrayList<>();
    // Состояние загрузки/кеша статистики.
    private static boolean loaded = false;
    private static String loadedProfileKey = null;
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
        AppLog.d(TAG, "addFight: totalFights=" + totalFights + ", resetDate=" + statsResetDateYmd);
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

    // Суммарный результат рыбалки в NV за текущую сессию статистики.
    public static synchronized double getTotalFishNv() {
        ensureLoaded();
        return totalFishNv;
    }

    // Количество пойманной рыбы по типам.
    public static synchronized Map<String, Long> getFishCountByType() {
        ensureLoaded();
        return Collections.unmodifiableMap(new LinkedHashMap<>(fishCountByType));
    }

    // Добавление шага рыбалки в статистику (доход + количество рыбы по типу).
    public static synchronized void addFishCatch(String fishName, int fishCount, double incomeNv) {
        ensureLoaded();
        totalFishNv += incomeNv;
        String normalizedFishName = fishName == null ? "" : fishName.trim();
        if (!normalizedFishName.isEmpty() && fishCount > 0) {
            long current = fishCountByType.containsKey(normalizedFishName)
                    ? fishCountByType.get(normalizedFishName) : 0L;
            fishCountByType.put(normalizedFishName, current + fishCount);
        }
        saveInternal();
    }

    /**
     * Возвращает длительность текущего окна статистики в миллисекундах.
     *
     * Логика:
     * - отсчёт начинается с {@link #statsStartAtMs};
     * - при сбросе статистики ({@link #reset()}) стартовая точка переносится на "сейчас";
     * - значение никогда не возвращается отрицательным.
     */
    public static synchronized long getStatsElapsedMs() {
        ensureLoaded();
        long now = System.currentTimeMillis();
        if (statsStartAtMs <= 0L || statsStartAtMs > now) {
            statsStartAtMs = now;
            saveInternal();
            return 0L;
        }
        return now - statsStartAtMs;
    }

    /**
     * Добавляет лут в статистику за текущий день.
     *
     * Приоритет разбора (как единый конвейер):
     * 1) денежный дроп `NNN NV` -> `totalNv`,
     * 2) ресурсный дроп `Название (x.xx кг)` -> `totalResourceKg` + `resourceKgByType`,
     * 3) остальной дроп -> `itemCountByName` (`+1 шт.`).
     *
     * Зависимости:
     * - входной список формируется в `ChatFilter` из строки "Результат обыска бота";
     * - метод работает под `synchronized`, чтобы избежать гонок между потоками фильтра/интерцептора;
     * - после любого изменения вызывает `saveInternal()` для немедленной персистентности.
     */
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

    /**
     * Прямое добавление дельты ресурсов (кг) в статистику.
     *
     * Используется AutoSkin-веткой (`MainPhp.mainPhpGetSkinRes`) для случаев,
     * когда дельта уже вычислена как разница "до/после" и не должна повторно
     * проходить через строковый парсер `addLoot(...)`.
     *
     * Зависимости:
     * - читает/обновляет `totalResourceKg` и `resourceKgByType`;
     * - сохраняет состояние через `saveInternal()` при наличии изменений;
     * - потокобезопасность обеспечивается `synchronized`, как и в остальных mutating-методах.
     */
    public static synchronized void addResourceDeltaKg(Map<String, Double> deltaByResource) {
        ensureLoaded();
        if (deltaByResource == null || deltaByResource.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (Map.Entry<String, Double> entry : deltaByResource.entrySet()) {
            if (entry == null) {
                continue;
            }
            String resourceName = entry.getKey();
            Double delta = entry.getValue();
            if (resourceName == null || resourceName.trim().isEmpty() || delta == null || delta <= 0d) {
                continue;
            }
            String key = resourceName.trim();
            totalResourceKg += delta;
            double current = resourceKgByType.containsKey(key) ? resourceKgByType.get(key) : 0d;
            resourceKgByType.put(key, current + delta);
            changed = true;
        }
        if (changed) {
            AppLog.d(TAG, "addResourceDeltaKg: totalResourceKg=" + totalResourceKg
                    + ", resourceTypes=" + resourceKgByType.size());
            saveInternal();
        }
    }

    /**
     * Legacy-журнал "последних находок" (совместимость со старым форматом статистики).
     *
     * Зависимости:
     * - в новой логике UI предметная сводка строится из `itemCountByName`;
     * - `lootLog` остаётся для обратной совместимости и миграции старых `LOOT=` файлов.
     */
    public static synchronized List<String> getLootLog() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(lootLog));
    }

    // Сброс статистики — очищаем данные и сохраняем пустое состояние.
    public static synchronized void reset() {
        ensureLoaded();
        resetStateLocked(System.currentTimeMillis(), getCurrentDateYmd());
        saveInternal();
    }

    // Гарантирует загрузку статистики для текущего профиля.
    private static void ensureLoaded() {
        if (AppVars.getContext() == null) {
            return;
        }
        String profileKey = getCurrentProfileStatsKey();
        if (!loaded || loadedProfileKey == null || !loadedProfileKey.equals(profileKey)) {
            loadedProfileKey = profileKey;
            loadFromFile();
            loaded = true;
        }
        maybeApplyMidnightResetLocked();
    }

    /**
     * Загружает статистику из персистентного файла профиля `Logs/<profile>_stat.txt`.
     *
     * Поддерживаемые ключи:
     * - `START_MS=`, `RESET_DATE=`, `XP=`, `FIGHTS=`, `NV=`,
     * - `KG_TOTAL=`, `KG_ITEM=`,
     * - `ITEM_COUNT=` (новый формат поштучной статистики),
     * - `LOOT=` (legacy, используется для миграции старых данных).
     */
    private static void loadFromFile() {
        String currentDate = getCurrentDateYmd();
        resetStateLocked(System.currentTimeMillis(), currentDate);
        statFile = resolveStatFile();
        File sourceFile = statFile;
        if (sourceFile == null || !sourceFile.exists()) {
            sourceFile = resolveLegacyProfileStatFile();
        }
        if (sourceFile == null || !sourceFile.exists()) {
            sourceFile = resolveLegacyDailyStatFile(currentDate);
        }
        if (sourceFile == null || !sourceFile.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(sourceFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("XP=")) {
                    totalXp = ParseUtils.parseLongSafe(line.substring(3));
                } else if (line.startsWith("FIGHTS=")) {
                    totalFights = ParseUtils.parseLongSafe(line.substring(7));
                } else if (line.startsWith("START_MS=")) {
                    long value = ParseUtils.parseLongSafe(line.substring(9));
                    if (value > 0L) {
                        statsStartAtMs = value;
                    }
                } else if (line.startsWith("RESET_DATE=")) {
                    String value = line.substring(11).trim();
                    if (!value.isEmpty()) {
                        statsResetDateYmd = value;
                    }
                } else if (line.startsWith("NV=")) {
                    totalNv = ParseUtils.parseLongSafe(line.substring(3));
                } else if (line.startsWith("FISH_NV=")) {
                    totalFishNv = ParseUtils.parseDoubleSafe(line.substring(8));
                } else if (line.startsWith("KG_TOTAL=")) {
                    totalResourceKg = ParseUtils.parseDoubleSafe(line.substring(9));
                } else if (line.startsWith("KG_ITEM=")) {
                    String value = line.substring(8);
                    int splitPos = value.lastIndexOf('\t');
                    if (splitPos > 0 && splitPos < value.length() - 1) {
                        String resourceName = value.substring(0, splitPos).trim();
                        double kilograms = ParseUtils.parseDoubleSafe(value.substring(splitPos + 1));
                        if (!resourceName.isEmpty() && kilograms > 0d) {
                            resourceKgByType.put(resourceName, kilograms);
                        }
                    }
                } else if (line.startsWith("ITEM_COUNT=")) {
                    String value = line.substring(11);
                    int splitPos = value.lastIndexOf('\t');
                    if (splitPos > 0 && splitPos < value.length() - 1) {
                        String itemName = value.substring(0, splitPos).trim();
                        long count = ParseUtils.parseLongSafe(value.substring(splitPos + 1));
                        if (!itemName.isEmpty() && count > 0L) {
                            itemCountByName.put(itemName, count);
                        }
                    }
                } else if (line.startsWith("FISH_ITEM=")) {
                    String value = line.substring(10);
                    int splitPos = value.lastIndexOf('\t');
                    if (splitPos > 0 && splitPos < value.length() - 1) {
                        String fishName = value.substring(0, splitPos).trim();
                        long count = ParseUtils.parseLongSafe(value.substring(splitPos + 1));
                        if (!fishName.isEmpty() && count > 0L) {
                            fishCountByType.put(fishName, count);
                        }
                    }
                } else if (line.startsWith("LOOT=")) {
                    String value = line.substring(5);
                    if (!value.isEmpty()) {
                        lootLog.add(value);
                        // Миграция старого формата:
                        // `LOOT=` строки конвертируем в `itemCountByName`,
                        // чтобы пользователю не потерять предметную статистику после обновления формата.
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
            AppLog.e(TAG, "loadFromFile failed", e);
        }
        if (statsResetDateYmd == null || statsResetDateYmd.isEmpty()) {
            statsResetDateYmd = currentDate;
        }
        // Миграция legacy-дневного файла в новый профильный файл.
        if (statFile != null && sourceFile != null && !statFile.equals(sourceFile)) {
            AppLog.i(TAG, "loadFromFile: migrate legacy daily stats -> profile stats file");
            saveInternal();
        }
    }

    /**
     * Сохраняет статистику в персистентный файл профиля `Logs/<profile>_stat.txt`.
     *
     * Зависимости:
     * - вызывается после каждого изменения счётчиков (xp/fights/nv/kg/items);
     * - пишет одновременно новый формат `ITEM_COUNT=` и legacy `LOOT=`,
     *   чтобы сохранить совместимость с ранее накопленными данными.
     */
    private static void saveInternal() {
        if (AppVars.getContext() == null) return;
        String currentDate = getCurrentDateYmd();
        File resolvedDayStatFile = resolveStatFile();
        if (resolvedDayStatFile != null) {
            statFile = resolvedDayStatFile;
        }
        if (statFile == null) return;
        StringBuilder sb = buildStatContent(currentDate);
        try (FileOutputStream fos = new FileOutputStream(statFile, false)) {
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            AppLog.e(TAG, "saveInternal failed", e);
        }
    }

    // Разбиение saveInternal: построение содержимого файла
    private static StringBuilder buildStatContent(String currentDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("DATE=").append(currentDate).append("\n");
        if (statsStartAtMs <= 0L) {
            statsStartAtMs = System.currentTimeMillis();
        }
        if (statsResetDateYmd == null || statsResetDateYmd.isEmpty()) {
            statsResetDateYmd = currentDate;
        }
        sb.append("START_MS=").append(statsStartAtMs).append("\n");
        sb.append("RESET_DATE=").append(statsResetDateYmd).append("\n");
        sb.append("XP=").append(totalXp).append("\n");
        sb.append("FIGHTS=").append(totalFights).append("\n");
        sb.append("NV=").append(totalNv).append("\n");
        sb.append("FISH_NV=").append(totalFishNv).append("\n");
        sb.append("KG_TOTAL=").append(totalResourceKg).append("\n");
        appendResourceKgItems(sb);
        appendItemCountEntries(sb);
        appendFishCountEntries(sb);
        appendLootLog(sb);
        return sb;
    }

    private static void appendResourceKgItems(StringBuilder sb) {
        for (Map.Entry<String, Double> entry : resourceKgByType.entrySet()) {
            sb.append("KG_ITEM=").append(entry.getKey()).append("\t").append(entry.getValue()).append("\n");
        }
    }

    private static void appendItemCountEntries(StringBuilder sb) {
        for (Map.Entry<String, Long> entry : itemCountByName.entrySet()) {
            sb.append("ITEM_COUNT=").append(entry.getKey()).append("\t").append(entry.getValue()).append("\n");
        }
    }

    private static void appendFishCountEntries(StringBuilder sb) {
        for (Map.Entry<String, Long> entry : fishCountByType.entrySet()) {
            sb.append("FISH_ITEM=").append(entry.getKey()).append("\t").append(entry.getValue()).append("\n");
        }
    }

    private static void appendLootLog(StringBuilder sb) {
        for (String loot : lootLog) {
            sb.append("LOOT=").append(loot).append("\n");
        }
    }

    // Определяет путь Logs/<profile>_stat.txt.
    private static File resolveStatFile() {
        File baseLogs = AppVars.getLogsDir();
        if (baseLogs == null && AppVars.getContext() != null) {
            baseLogs = new File(AppVars.getContext().getFilesDir(), "Logs");
        }
        if (baseLogs == null) return null;
        if (!baseLogs.exists()) baseLogs.mkdirs();
        String safeNick = getCurrentProfileLogDirName();
        File userDir = new File(baseLogs, safeNick);
        if (!userDir.exists()) userDir.mkdirs();
        return new File(userDir, getCurrentDateYmd() + STAT_FILE_SUFFIX);
    }

    private static File resolveLegacyProfileStatFile() {
        File baseLogs = AppVars.getLogsDir();
        if (baseLogs == null && AppVars.getContext() != null) {
            baseLogs = new File(AppVars.getContext().getFilesDir(), "Logs");
        }
        if (baseLogs == null) return null;
        if (!baseLogs.exists()) baseLogs.mkdirs();
        String profileKey = getCurrentProfileStatsKey();
        return new File(baseLogs, profileKey + STAT_FILE_SUFFIX);
    }

    // Legacy fallback: старый дневной путь Logs/YYYYMMDD_stat.txt.
    private static File resolveLegacyDailyStatFile(String dateYmd) {
        File baseLogs = AppVars.getLogsDir();
        if (baseLogs == null && AppVars.getContext() != null) {
            baseLogs = new File(AppVars.getContext().getFilesDir(), "Logs");
        }
        if (baseLogs == null) return null;
        if (!baseLogs.exists()) baseLogs.mkdirs();
        return new File(baseLogs, dateYmd + "_stat.txt");
    }

    // Унифицированная очистка/инициализация состояния счётчиков.
    private static void resetStateLocked(long startMs, String resetDateYmd) {
        statsStartAtMs = startMs;
        statsResetDateYmd = resetDateYmd;
        totalXp = 0;
        totalFights = 0;
        totalNv = 0;
        totalResourceKg = 0;
        totalFishNv = 0d;
        resourceKgByType.clear();
        itemCountByName.clear();
        fishCountByType.clear();
        lootLog.clear();
    }

    private static String getCurrentDateYmd() {
        return STAT_DATE_FORMAT.format(new Date());
    }

    private static boolean isMidnightResetEnabled() {
        return AppVars.Profile != null && AppVars.Profile.StatsResetAtMidnight;
    }

    // Проверка дневного автосброса (если включен в профиле).
    private static void maybeApplyMidnightResetLocked() {
        String currentDate = getCurrentDateYmd();
        if (statsResetDateYmd == null || statsResetDateYmd.isEmpty()) {
            statsResetDateYmd = currentDate;
            saveInternal();
            return;
        }
        if (!isMidnightResetEnabled()) {
            return;
        }
        if (!currentDate.equals(statsResetDateYmd)) {
            AppLog.i(TAG, "maybeApplyMidnightResetLocked: reset by midnight, from="
                    + statsResetDateYmd + " to=" + currentDate);
            resetStateLocked(System.currentTimeMillis(), currentDate);
            saveInternal();
        }
    }

    private static String getCurrentProfileStatsKey() {
        if (AppVars.Profile != null && AppVars.Profile.UserNick != null) {
            String nick = AppVars.Profile.UserNick.trim();
            if (!nick.isEmpty()) {
                return nick.replaceAll("[^A-Za-z0-9._-]", "_");
            }
        }
        return "default";
    }

    private static String getCurrentProfileLogDirName() {
        String nick = "unknown";
        if (AppVars.Profile != null && AppVars.Profile.UserNick != null) {
            String candidate = AppVars.Profile.UserNick.trim();
            if (!candidate.isEmpty()) {
                nick = candidate;
            }
        }
        return nick.replaceAll("[/\\\\:*?\"<>|]", "_");
    }

    /**
     * Конвертирует legacy-строку `LOOT=` в имя предмета для новой поштучной статистики.
     *
     * Поддерживаемые legacy-варианты:
     * - `HH:mm:ss Название предмета` (время отрезается),
     * - `Название предмета` (используется как есть).
     *
     * Зависимости:
     * - используется только в `loadFromFile(...)` как одноразовая миграция старых файлов.
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
        return ParseUtils.parseLongSafe(amount.replace(" ", ""));
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
        double kilograms = ParseUtils.parseDoubleSafe(kilogramsRaw);
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

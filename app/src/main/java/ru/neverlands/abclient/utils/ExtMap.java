package ru.neverlands.abclient.utils;

import android.content.Context;
import android.content.res.AssetManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import ru.neverlands.abclient.model.Cell;
import ru.neverlands.abclient.model.Position;

public class ExtMap {
    private static final String TAG = "ExtMap";
    private static final SimpleDateFormat[] ABC_VISITED_FORMATS = new SimpleDateFormat[] {
            new SimpleDateFormat("M/d/yyyy h:mm:ss a", Locale.US),
            new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US),
            new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    };
    private static final SimpleDateFormat ABC_VISITED_OUTPUT_FORMAT =
            new SimpleDateFormat("M/d/yyyy h:mm:ss a", Locale.US);
    private static final String ABC_VISITED_ZERO = "1/1/0001 12:00:00 AM";
    private static final long VISITED_PERSIST_THROTTLE_MS = 1500L;
    private static final String MAP_XML_ASSET_NAME = "map.xml";
    private static final String MAP_XML_RUNTIME_FILE_NAME = "map.xml";
    // Формат даты в runtime `map.xml` совпадает с историческим шаблоном карты.
    private static final String MAP_META_UPDATED_PATTERN = "dd.MM.yyyy HH:mm:ss";

    public static final Map<String, Position> Location = new HashMap<>();
    public static final Map<String, String> InvLocation = new HashMap<>();
    public static final Map<String, Cell> Cells = new HashMap<>();
    public static final Map<String, String> Teleports = new HashMap<>();
    public static final Map<String, String> MovableCells = new HashMap<>();

    private static boolean initialized = false;
    private static Context appContext;
    private static long lastVisitedPersistAtMs = 0L;
    private static boolean visitedPersistPending = false;
    private static String lastVisitedPersistRegNum = "";
    /**
     * Отложенные обновления названий клеток (`label`) для персиста в `abcells.xml`.
     *
     * Зависимости:
     * - заполняется из {@link #syncCellLabelFromServer(String, String)};
     * - сбрасывается в том же цикле персиста, что и `visited` (`persistVisitedToExternalFile(...)`);
     * - используется как "грязный буфер" на случай временной ошибки записи файла.
     */
    private static final Map<String, String> pendingLabelUpdates = new HashMap<>();
    /**
     * Runtime-индекс "id региона -> текстовое имя региона" из pinfo/карты.
     *
     * Нужен для двух задач:
     * 1) быстрый region-aware поиск одинаковых названий клеток в Авто-Компасе;
     * 2) дозапись атрибута `region` в `abcells.xml` даже для клеток,
     *    где region ещё не был явно проставлен в исходном XML.
     */
    private static final Map<String, String> regionNameByRegionId = new HashMap<>();

    /**
     * Возвращает путь к рабочему runtime-файлу карты (`.../files/map.xml`).
     *
     * Зависимости:
     * - `Context.getExternalFilesDir(null)` — корневая writable-директория профиля приложения;
     * - `MAP_XML_RUNTIME_FILE_NAME` — единая константа имени runtime-карты.
     */
    private static File getRuntimeMapFile(Context context) {
        if (context == null) {
            return null;
        }
        File externalDir = context.getExternalFilesDir(null);
        if (externalDir == null) {
            return null;
        }
        return new File(externalDir, MAP_XML_RUNTIME_FILE_NAME);
    }

    /**
     * Гарантирует наличие runtime `map.xml`:
     * - если файла нет, копирует шаблон из assets (`map.xml`);
     * - если файл уже есть, оставляет как есть.
     *
     * Зависимости:
     * - `copyAssetToFile(...)` — атомарное копирование шаблона карты;
     * - `MAP_XML_ASSET_NAME` — источник шаблона в assets.
     */
    private static File ensureRuntimeMapFile(Context context) {
        File runtimeFile = getRuntimeMapFile(context);
        if (runtimeFile == null) {
            return null;
        }
        if (!runtimeFile.exists()) {
            try {
                copyAssetToFile(context, MAP_XML_ASSET_NAME, runtimeFile);
                android.util.Log.d(TAG, "runtimeMap: created from asset template, file=" + runtimeFile.getAbsolutePath());
            } catch (Exception e) {
                android.util.Log.e(TAG, "runtimeMap: cannot create runtime map from template", e);
                return null;
            }
        }
        return runtimeFile;
    }

    /**
     * Открывает входной поток карты для чтения:
     * 1) приоритет — writable runtime `.../files/map.xml`,
     * 2) fallback — assets `map.xml`.
     *
     * Важно:
     * - метод не кэширует stream; вызывающий код обязан закрыть поток;
     * - используется как единая точка входа для `ExtMap.loadMap(...)` и UI-индексов (`Navigator`).
     */
    public static InputStream openRuntimeMapInputStream(Context context) throws Exception {
        File runtimeFile = ensureRuntimeMapFile(context);
        if (runtimeFile != null && runtimeFile.exists()) {
            return new FileInputStream(runtimeFile);
        }
        if (context == null) {
            throw new IllegalStateException("Context is null for map input stream");
        }
        return context.getAssets().open(MAP_XML_ASSET_NAME);
    }

    public static synchronized void init(Context context) {
        if (initialized) return;
        initialized = true;
        appContext = context.getApplicationContext();
        buildRegions();
        loadMap(context);
        loadAbcMap(context);
        loadTeleports(context);
    }

    public static String makePosition(int x, int y) {
        return y + "/" + x + "_" + y;
    }

    public static String makeRegNum(String region, int number) {
        return region + "-" + String.format(Locale.US, "%03d", number);
    }

    private static String extractRegionId(String regNum) {
        if (regNum == null) {
            return "";
        }
        String value = regNum.trim();
        if (value.isEmpty()) {
            return "";
        }
        int dash = value.indexOf('-');
        if (dash <= 0) {
            return "";
        }
        return value.substring(0, dash).trim();
    }

    private static String normalizeRegionLabel(String label) {
        if (label == null) {
            return "";
        }
        return label.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static void rememberRegionMapping(String regNum, String regionLabel) {
        String normalizedRegion = normalizeRegionLabel(regionLabel);
        if (normalizedRegion.isEmpty()) {
            return;
        }
        String regionId = extractRegionId(regNum);
        if (regionId.isEmpty()) {
            return;
        }
        regionNameByRegionId.put(regionId, normalizedRegion);
        Cell cell = Cells.get(regNum);
        if (cell != null) {
            cell.Region = normalizedRegion;
        }
    }

    public static synchronized String resolveRegionLabelForRegNum(String regNum) {
        if (regNum == null) {
            return "";
        }
        String normalizedReg = regNum.trim();
        if (normalizedReg.isEmpty()) {
            return "";
        }
        Cell cell = Cells.get(normalizedReg);
        if (cell != null) {
            String fromCell = normalizeRegionLabel(cell.Region);
            if (!fromCell.isEmpty()) {
                return fromCell;
            }
        }
        String regionId = extractRegionId(normalizedReg);
        if (regionId.isEmpty()) {
            return "";
        }
        return normalizeRegionLabel(regionNameByRegionId.get(regionId));
    }

    private static void buildRegions() {
        addRegion("1",  952, 954);
        addRegion("2",  982, 954);
        addRegion("3",  1012, 954);
        addRegion("13", 1042, 954);
        addRegion("4",  952, 973);
        addRegion("5",  982, 973);
        addRegion("6",  1012, 973);
        addRegion("14", 1042, 973);
        addRegion("7",  952, 992);
        addRegion("8",  982, 992);
        addRegion("9",  1012, 992);
        addRegion("15", 1042, 992);
        addRegion("10", 952, 1011);
        addRegion("11", 982, 1011);
        addRegion("12", 1012, 1011);
        addRegion("16", 1042, 1011);
        addRegion("17", 922, 954);
        addRegion("18", 922, 973);
        addRegion("19", 922, 992);
        addRegion("20", 922, 1011);
        addRegion("21", 922, 1030);
        addRegion("22", 922, 1049);
        addRegion("23", 952, 1030);
        addRegion("24", 952, 1049);
        addRegion("25", 982, 1030);
        addRegion("26", 982, 1049);
        addRegion("27", 1012, 1030);
        addRegion("28", 1012, 1049);
        addRegion("29", 1042, 1030);
        addRegion("30", 1042, 1049);
        addRegion("31", 1072, 954);
        addRegion("32", 1072, 973);
        addRegion("33", 1072, 992);
        addRegion("34", 1072, 1011);
        addRegion("35", 1072, 1030);
        addRegion("36", 1072, 1049);
    }

    private static void addRegion(String region, int xmin, int ymin) {
        int number = 1;
        int xmax = xmin + 29;
        int ymax = ymin + 18;
        for (int y = ymin; y <= ymax; y++) {
            for (int x = xmin; x <= xmax; x++) {
                String h = makePosition(x, y);
                String regNum = makeRegNum(region, number);
                Position p = new Position();
                p.RegNum = regNum;
                p.X = x;
                p.Y = y;
                Location.put(h, p);
                InvLocation.put(regNum, h);
                number++;
            }
        }
    }

    private static void loadMap(Context context) {
        Cells.clear();
        regionNameByRegionId.clear();
        String source = "assets/" + MAP_XML_ASSET_NAME;
        File runtimeFile = getRuntimeMapFile(context);
        if (runtimeFile != null && runtimeFile.exists()) {
            source = runtimeFile.getAbsolutePath();
        }
        try (InputStream in = openRuntimeMapInputStream(context)) {
            int parsed = parseMapXml(in);
            android.util.Log.d(TAG, "loadMap: source=" + source + ", parsedCells=" + parsed);
        } catch (Exception e) {
            android.util.Log.w(TAG, "loadMap: runtime source failed, fallback to assets template", e);
            try (InputStream in = context.getAssets().open(MAP_XML_ASSET_NAME)) {
                int parsed = parseMapXml(in);
                android.util.Log.d(TAG, "loadMap: fallback source=assets/" + MAP_XML_ASSET_NAME + ", parsedCells=" + parsed);
            } catch (Exception fallbackError) {
                android.util.Log.e(TAG, "loadMap error: fallback parse failed", fallbackError);
            }
        }
    }

    private static int parseMapXml(InputStream inputStream) throws Exception {
        int parsedCells = 0;
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(false);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(inputStream, "UTF-8");
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("cell".equalsIgnoreCase(tag)) {
                    Cell cell = parseCellNode(parser);
                    if (cell != null) {
                        Cells.put(cell.CellNumber, cell);
                        if (!cell.Region.isEmpty()) {
                            rememberRegionMapping(cell.CellNumber, cell.Region);
                        }
                        parsedCells++;
                    }
                }
            }
            event = parser.next();
        }
        return parsedCells;
    }

    private static Cell parseCellNode(XmlPullParser parser) throws Exception {
        String cellNumber = parser.getAttributeValue(null, "cellNumber");
        if (cellNumber == null) {
            return null;
        }
        String normalizedCellNumber = cellNumber.trim();
        if (normalizedCellNumber.isEmpty()) {
            return null;
        }

        Cell cell = new Cell();
        cell.CellNumber = normalizedCellNumber;
        String costStr = parser.getAttributeValue(null, "cost");
        if (costStr != null) {
            try { cell.Cost = Integer.parseInt(costStr.trim()); } catch (NumberFormatException ignored) {}
        }
        String hasFish = parser.getAttributeValue(null, "hasFish");
        cell.HasFish = "true".equalsIgnoreCase(hasFish);
        String hasWater = parser.getAttributeValue(null, "hasWater");
        cell.HasWater = "true".equalsIgnoreCase(hasWater);
        String herbGroup = parser.getAttributeValue(null, "herbGroup");
        cell.HerbGroup = herbGroup != null ? herbGroup.trim() : "";
        String name = parser.getAttributeValue(null, "name");
        cell.Name = name != null ? name.trim() : "";
        String tooltip = parser.getAttributeValue(null, "tooltip");
        cell.Tooltip = tooltip != null ? tooltip.trim() : "";
        String region = parser.getAttributeValue(null, "region");
        cell.Region = region != null ? normalizeRegionLabel(region) : "";

        int startDepth = parser.getDepth();
        int innerEvent = parser.next();
        while (!(innerEvent == XmlPullParser.END_TAG
                && parser.getDepth() == startDepth
                && "cell".equalsIgnoreCase(parser.getName()))) {
            if (innerEvent == XmlPullParser.END_DOCUMENT) {
                break;
            }
            if (innerEvent == XmlPullParser.START_TAG && "bots".equalsIgnoreCase(parser.getName())) {
                int minLevel = parsePositiveInt(parser.getAttributeValue(null, "minLevel"));
                int maxLevel = parsePositiveInt(parser.getAttributeValue(null, "maxLevel"));
                if (minLevel > 0) {
                    if (cell.MinBotLevel == 0 || minLevel < cell.MinBotLevel) {
                        cell.MinBotLevel = minLevel;
                    }
                    if (cell.MaxBotLevel == 0 || minLevel > cell.MaxBotLevel) {
                        cell.MaxBotLevel = minLevel;
                    }
                }
                if (maxLevel > 0) {
                    if (cell.MaxBotLevel == 0 || maxLevel > cell.MaxBotLevel) {
                        cell.MaxBotLevel = maxLevel;
                    }
                    if (cell.MinBotLevel == 0) {
                        cell.MinBotLevel = maxLevel;
                    }
                }
            }
            innerEvent = parser.next();
        }
        if (cell.MinBotLevel > 0 && cell.MaxBotLevel > 0 && cell.MinBotLevel > cell.MaxBotLevel) {
            int swap = cell.MinBotLevel;
            cell.MinBotLevel = cell.MaxBotLevel;
            cell.MaxBotLevel = swap;
        }
        return cell;
    }

    private static int parsePositiveInt(String value) {
        if (value == null) {
            return 0;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(0, parsed);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static void loadAbcMap(Context context) {
        AssetManager assets = context.getAssets();
        Map<String, Integer> abcCosts = new HashMap<>();
        Map<String, String> abcLabels = new HashMap<>();
        Map<String, String> abcRegions = new HashMap<>();
        int loadedVisitedCount = 0;
        AppVars.SearchBoxVisited.clear();
        InputStream input = null;
        String source = "assets";
        try {
            File externalFile = null;
            if (context.getExternalFilesDir(null) != null) {
                externalFile = new File(context.getExternalFilesDir(null), "abcells.xml");
            }
            if (externalFile != null && externalFile.exists()) {
                input = new FileInputStream(externalFile);
                source = externalFile.getAbsolutePath();
            } else {
                input = assets.open("abcells.xml");
            }
        } catch (Exception openError) {
            android.util.Log.e(TAG, "loadAbcMap source open error: " + openError.getMessage());
        }

        if (input == null) {
            return;
        }

        try (InputStream in = input) {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(in, "UTF-8");
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "cell".equalsIgnoreCase(parser.getName())) {
                    String regnum = parser.getAttributeValue(null, "regnum");
                    if (regnum != null) {
                        regnum = regnum.trim();
                        String label = parser.getAttributeValue(null, "label");
                        abcLabels.put(regnum, label != null ? label : "");
                        String region = parser.getAttributeValue(null, "region");
                        String normalizedRegion = normalizeRegionLabel(region);
                        if (!normalizedRegion.isEmpty()) {
                            abcRegions.put(regnum, normalizedRegion);
                        }
                        String visited = parser.getAttributeValue(null, "visited");
                        long visitedMs = parseAbcVisitedMs(visited);
                        if (visitedMs > 0L) {
                            AppVars.SearchBoxVisited.put(regnum, visitedMs);
                            loadedVisitedCount++;
                        }
                        String costStr = parser.getAttributeValue(null, "cost");
                        int cost = 0;
                        if (costStr != null) {
                            try { cost = Integer.parseInt(costStr.trim()); } catch (NumberFormatException ignored) {}
                        }
                        if (cost == 0 && Cells.containsKey(regnum)) {
                            int jcost = Cells.get(regnum).Cost;
                            if (jcost == 21) cost = 30;
                            else if (jcost == 28) cost = 40;
                            else if (jcost == 43) cost = 60;
                        }
                        abcCosts.put(regnum, cost);
                    }
                }
                event = parser.next();
            }
            android.util.Log.d(TAG, "loadAbcMap: source=" + source
                    + ", cells=" + abcCosts.size()
                    + ", visitedLoaded=" + loadedVisitedCount);
        } catch (Exception e) {
            android.util.Log.e(TAG, "loadAbcMap error: " + e.getMessage());
        }

        for (String regnum : new java.util.ArrayList<>(Cells.keySet())) {
            if (!abcCosts.containsKey(regnum)) {
                Cells.remove(regnum);
            }
        }
        for (Map.Entry<String, Integer> e : abcCosts.entrySet()) {
            String regnum = e.getKey();
            int cost = e.getValue();
            if (Cells.containsKey(regnum)) {
                Cells.get(regnum).Cost = cost;
                if (abcLabels.containsKey(regnum)) {
                    Cells.get(regnum).Name = abcLabels.get(regnum);
                }
                String region = abcRegions.get(regnum);
                if (!normalizeRegionLabel(region).isEmpty()) {
                    Cells.get(regnum).Region = normalizeRegionLabel(region);
                    rememberRegionMapping(regnum, region);
                }
            } else {
                Cell cell = new Cell();
                cell.CellNumber = regnum;
                cell.Cost = cost;
                cell.Name = abcLabels.containsKey(regnum) ? abcLabels.get(regnum) : "";
                cell.Region = normalizeRegionLabel(abcRegions.get(regnum));
                Cells.put(regnum, cell);
                if (!cell.Region.isEmpty()) {
                    rememberRegionMapping(regnum, cell.Region);
                }
            }
        }
    }

    private static long parseAbcVisitedMs(String rawValue) {
        if (rawValue == null) {
            return 0L;
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return 0L;
        }
        if ("1/1/0001 12:00:00 AM".equals(value)
                || "01.01.0001 00:00:00".equals(value)
                || "0001-01-01 00:00:00".equals(value)) {
            return 0L;
        }
        for (SimpleDateFormat format : ABC_VISITED_FORMATS) {
            try {
                Date parsed = format.parse(value);
                if (parsed != null) {
                    return parsed.getTime();
                }
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    /**
     * Регистрирует посещение клетки в runtime-кэше и персистит обновлённые `visited` в рабочий
     * `abcells.xml` (external files), чтобы метки времени переживали перезапуск приложения.
     *
     * Зависимости:
     * - {@link AppVars#SearchBoxVisited} — источник runtime-значений `regnum -> timestamp`.
     * - `getExternalFilesDir()/abcells.xml` — рабочий файл карты (не assets).
     * - throttle `VISITED_PERSIST_THROTTLE_MS` — защита от избыточной записи на каждом тике.
     */
    public static synchronized void markCellVisited(String regNum) {
        if (regNum == null) {
            return;
        }
        String normalized = regNum.trim();
        if (normalized.isEmpty()) {
            return;
        }
        AppVars.SearchBoxVisited.put(normalized, System.currentTimeMillis());
        visitedPersistPending = true;
        lastVisitedPersistRegNum = normalized;
        persistVisitedIfNeeded(false);
    }

    /**
     * Принудительно сбрасывает отложенные изменения `visited` в `abcells.xml`.
     * Можно вызывать при выключении авто-режимов/закрытии экрана, чтобы не терять хвост.
     */
    public static synchronized void flushVisitedToDisk() {
        persistVisitedIfNeeded(true);
    }

    /**
     * Синхронизирует отображаемое имя клетки с серверным именем из `/ch.php`.
     *
     * Что делает:
     * 1) Нормализует `regNum` и `serverLabel`;
     * 2) Сравнивает текущее имя клетки в runtime (`ExtMap.Cells`) с серверным;
     * 3) При отличии обновляет runtime и персистит `label` в рабочий `abcells.xml`.
     *
     * Зависимости:
     * - `ExtMap.Cells` — источник/цель текущего названия для `CellDivText`;
     * - `pendingLabelUpdates` — буфер отложенной записи в `abcells.xml`;
     * - `persistVisitedToExternalFile(...)` — единый атомарный персист `visited + label`.
     * - `WebAppInterface.shortLabel(...)` — рендер карты берёт сначала `Tooltip`, затем `Name`,
     *   поэтому метод также синхронизирует `Tooltip` через `syncTooltipLabel(...)`.
     *
     * @param regNum номер клетки (`8-330` и т.п.)
     * @param serverLabel имя клетки, полученное из ответа `/ch.php`
     * @return предыдущее название клетки, если было реальное изменение; иначе `null`
     */
    public static synchronized String syncCellLabelFromServer(String regNum, String serverLabel) {
        if (regNum == null || serverLabel == null) {
            return null;
        }
        String normalizedReg = regNum.trim();
        if (normalizedReg.isEmpty()) {
            return null;
        }
        String normalizedServerLabel = normalizeCellLabel(serverLabel);
        if (normalizedServerLabel.isEmpty()) {
            return null;
        }

        Cell cell = Cells.get(normalizedReg);
        if (cell == null) {
            return null;
        }

        String oldLabel = normalizeCellLabel(cell.Name);
        if (normalizedServerLabel.equals(oldLabel)) {
            return null;
        }

        cell.Name = normalizedServerLabel;
        cell.Tooltip = syncTooltipLabel(cell.Tooltip, oldLabel, normalizedServerLabel);
        pendingLabelUpdates.put(normalizedReg, normalizedServerLabel);

        Context context = appContext != null ? appContext : AppVars.getContext();
        if (context == null) {
            return null;
        }
        boolean persisted = persistVisitedToExternalFile(context);
        if (persisted) {
            pendingLabelUpdates.remove(normalizedReg);
            visitedPersistPending = false;
            lastVisitedPersistAtMs = System.currentTimeMillis();
            persistRuntimeMapCellMeta(context, normalizedReg, normalizedServerLabel, resolveRegionLabelForRegNum(normalizedReg));
            android.util.Log.d(TAG, "persistLabel: saved, reg=" + normalizedReg
                    + ", old=" + oldLabel + ", new=" + normalizedServerLabel);
        } else {
            android.util.Log.w(TAG, "persistLabel: write failed, reg=" + normalizedReg
                    + ", old=" + oldLabel + ", new=" + normalizedServerLabel);
        }
        return oldLabel;
    }

    /**
     * Синхронизирует мета-данные клетки из собственного pinfo:
     * - `region` (текстовый регион из `parameters[0][5]`);
     * - `label` (имя клетки внутри квадратных скобок).
     *
     * Метод расширяет существующий конвейер, не создавая дубликаты логики:
     * - обновление label делается через `syncCellLabelFromServer(...)`;
     * - персист выполняется тем же `persistVisitedToExternalFile(...)`,
     *   который уже поддерживает атомарную запись `abcells.xml`.
     *
     * @return `true`, если изменился region и/или label.
     */
    public static synchronized boolean syncCellMetaFromPinfo(String regNum,
                                                             String pinfoRegion,
                                                             String pinfoCellName) {
        if (regNum == null) {
            return false;
        }
        String normalizedReg = regNum.trim();
        if (normalizedReg.isEmpty() || !Cells.containsKey(normalizedReg)) {
            return false;
        }

        boolean regionChanged = false;
        String normalizedRegion = normalizeRegionLabel(pinfoRegion);
        if (!normalizedRegion.isEmpty()) {
            Cell currentCell = Cells.get(normalizedReg);
            String oldRegionOnCell = normalizeRegionLabel(currentCell != null ? currentCell.Region : "");
            // Важно: сравниваем именно с region текущей клетки, а не с region-id cache.
            // Иначе при совпадении регионов по id (`3-XXX`) поле `region` может не попасть
            // в runtime map.xml для конкретной клетки, хотя в кэше регион уже известен.
            if (!normalizedRegion.equals(oldRegionOnCell)) {
                rememberRegionMapping(normalizedReg, normalizedRegion);
                regionChanged = true;
            }
        }

        boolean labelChanged = false;
        String normalizedCellName = normalizeCellLabel(pinfoCellName);
        if (!normalizedCellName.isEmpty()) {
            String oldLabel = syncCellLabelFromServer(normalizedReg, normalizedCellName);
            labelChanged = oldLabel != null;
        }

        if (regionChanged && !labelChanged) {
            Context context = appContext != null ? appContext : AppVars.getContext();
            if (context != null) {
                boolean persisted = persistVisitedToExternalFile(context);
                if (persisted) {
                    visitedPersistPending = false;
                    lastVisitedPersistAtMs = System.currentTimeMillis();
                    persistRuntimeMapCellMeta(context, normalizedReg, "", resolveRegionLabelForRegNum(normalizedReg));
                }
            }
        }

        return regionChanged || labelChanged;
    }

    private static void persistVisitedIfNeeded(boolean force) {
        if (!visitedPersistPending) {
            return;
        }
        Context context = appContext != null ? appContext : AppVars.getContext();
        if (context == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && (now - lastVisitedPersistAtMs) < VISITED_PERSIST_THROTTLE_MS) {
            return;
        }
        boolean ok = persistVisitedToExternalFile(context);
        if (ok) {
            visitedPersistPending = false;
            lastVisitedPersistAtMs = now;
            android.util.Log.d(TAG, "persistVisited: saved, entries=" + AppVars.SearchBoxVisited.size()
                    + ", last=" + lastVisitedPersistRegNum);
        }
    }

    private static boolean persistVisitedToExternalFile(Context context) {
        File externalDir = context.getExternalFilesDir(null);
        if (externalDir == null) {
            android.util.Log.w(TAG, "persistVisited: external dir unavailable");
            return false;
        }
        File targetFile = new File(externalDir, "abcells.xml");
        if (!targetFile.exists()) {
            try {
                copyAssetToFile(context, "abcells.xml", targetFile);
            } catch (Exception e) {
                android.util.Log.e(TAG, "persistVisited: cannot create target abcells.xml", e);
                return false;
            }
        }
        File tmpFile = new File(externalDir, "abcells.xml.tmp");
        try (InputStream in = new FileInputStream(targetFile);
             FileOutputStream out = new FileOutputStream(tmpFile, false)) {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(in, null);

            XmlSerializer serializer = factory.newSerializer();
            serializer.setOutput(out, "UTF-8");
            serializer.startDocument("UTF-8", true);

            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    String namespace = parser.getNamespace();
                    if (namespace != null && namespace.isEmpty()) {
                        namespace = null;
                    }
                    serializer.startTag(namespace, tag);

                    int attrCount = parser.getAttributeCount();
                    String regnum = null;
                    for (int i = 0; i < attrCount; i++) {
                        String attrName = parser.getAttributeName(i);
                        if ("regnum".equalsIgnoreCase(attrName)) {
                            String value = parser.getAttributeValue(i);
                            regnum = value != null ? value.trim() : null;
                            break;
                        }
                    }

                    boolean hasVisitedAttr = false;
                    boolean hasLabelAttr = false;
                    boolean hasRegionAttr = false;
                    String pendingLabel = regnum != null ? pendingLabelUpdates.get(regnum) : null;
                    String resolvedRegion = regnum != null ? resolveRegionLabelForRegNum(regnum) : "";
                    for (int i = 0; i < attrCount; i++) {
                        String attrNamespace = parser.getAttributeNamespace(i);
                        if (attrNamespace != null && attrNamespace.isEmpty()) {
                            attrNamespace = null;
                        }
                        String attrName = parser.getAttributeName(i);
                        String attrValue = parser.getAttributeValue(i);
                        if ("visited".equalsIgnoreCase(attrName)) {
                            hasVisitedAttr = true;
                            Long visitedMs = regnum != null ? AppVars.SearchBoxVisited.get(regnum) : null;
                            if (visitedMs != null && visitedMs > 0L) {
                                attrValue = formatVisitedForAbc(visitedMs);
                            }
                        } else if ("label".equalsIgnoreCase(attrName)) {
                            hasLabelAttr = true;
                            if (pendingLabel != null && !pendingLabel.isEmpty()) {
                                attrValue = pendingLabel;
                            }
                        } else if ("region".equalsIgnoreCase(attrName)) {
                            hasRegionAttr = true;
                            if (!resolvedRegion.isEmpty()) {
                                attrValue = resolvedRegion;
                            }
                        }
                        serializer.attribute(attrNamespace, attrName, attrValue != null ? attrValue : "");
                    }

                    if ("cell".equalsIgnoreCase(tag) && !hasVisitedAttr && regnum != null) {
                        Long visitedMs = AppVars.SearchBoxVisited.get(regnum);
                        if (visitedMs != null && visitedMs > 0L) {
                            serializer.attribute(null, "visited", formatVisitedForAbc(visitedMs));
                        }
                    }
                    if ("cell".equalsIgnoreCase(tag) && !hasLabelAttr && pendingLabel != null && !pendingLabel.isEmpty()) {
                        serializer.attribute(null, "label", pendingLabel);
                    }
                    if ("cell".equalsIgnoreCase(tag) && !hasRegionAttr && !resolvedRegion.isEmpty()) {
                        serializer.attribute(null, "region", resolvedRegion);
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    String tag = parser.getName();
                    String namespace = parser.getNamespace();
                    if (namespace != null && namespace.isEmpty()) {
                        namespace = null;
                    }
                    serializer.endTag(namespace, tag);
                } else if (event == XmlPullParser.TEXT) {
                    serializer.text(parser.getText());
                }
                event = parser.next();
            }
            serializer.endDocument();
            serializer.flush();
        } catch (Exception e) {
            android.util.Log.e(TAG, "persistVisited: write failed", e);
            //noinspection ResultOfMethodCallIgnored
            tmpFile.delete();
            return false;
        }

        try {
            if (targetFile.exists() && !targetFile.delete()) {
                android.util.Log.w(TAG, "persistVisited: cannot delete old abcells.xml");
                return false;
            }
            if (!tmpFile.renameTo(targetFile)) {
                try (InputStream in = new FileInputStream(tmpFile);
                     FileOutputStream out = new FileOutputStream(targetFile, false)) {
                    copyStream(in, out);
                }
                //noinspection ResultOfMethodCallIgnored
                tmpFile.delete();
            }
            return true;
        } catch (Exception e) {
            android.util.Log.e(TAG, "persistVisited: replace failed", e);
            return false;
        }
    }

    /**
     * Персистит обновлённые `name`/`region` в runtime `map.xml`.
     *
     * Назначение:
     * - переводит `map.xml` из "read-only assets" в рабочий runtime-шаблон, который клиент может
     *   дополнять в режиме проверки/пересоздания карты;
     * - сохраняет регион и фактическое название клетки прямо в основном источнике карты.
     *
     * Зависимости:
     * - `ensureRuntimeMapFile(...)` — создаёт writable map.xml из шаблона assets при первом запуске;
     * - `syncCellLabelFromServer(...)`/`syncCellMetaFromPinfo(...)` — вызывающие ветки;
     * - `Cell.cellNumber` в XML — идентификатор целевой клетки.
     */
    private static boolean persistRuntimeMapCellMeta(Context context,
                                                     String regNum,
                                                     String label,
                                                     String region) {
        if (context == null || regNum == null) {
            return false;
        }
        String normalizedReg = regNum.trim();
        if (normalizedReg.isEmpty()) {
            return false;
        }
        String normalizedLabel = normalizeCellLabel(label);
        String normalizedRegion = normalizeRegionLabel(region);
        if (normalizedLabel.isEmpty() && normalizedRegion.isEmpty()) {
            return false;
        }

        File targetFile = ensureRuntimeMapFile(context);
        if (targetFile == null || !targetFile.exists()) {
            return false;
        }
        File parentDir = targetFile.getParentFile();
        if (parentDir == null) {
            return false;
        }
        File tmpFile = new File(parentDir, targetFile.getName() + ".tmp");
        final String metaUpdatedTs = new SimpleDateFormat(MAP_META_UPDATED_PATTERN, Locale.US)
                .format(new Date());

        boolean changed = false;
        try (InputStream in = new FileInputStream(targetFile);
             FileOutputStream out = new FileOutputStream(tmpFile, false)) {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(in, null);

            XmlSerializer serializer = factory.newSerializer();
            serializer.setOutput(out, "UTF-8");
            serializer.startDocument("UTF-8", true);

            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    String namespace = parser.getNamespace();
                    if (namespace != null && namespace.isEmpty()) {
                        namespace = null;
                    }
                    serializer.startTag(namespace, tag);

                    int attrCount = parser.getAttributeCount();
                    String cellNumber = null;
                    for (int i = 0; i < attrCount; i++) {
                        if ("cellNumber".equalsIgnoreCase(parser.getAttributeName(i))) {
                            String rawValue = parser.getAttributeValue(i);
                            cellNumber = rawValue != null ? rawValue.trim() : "";
                            break;
                        }
                    }

                    boolean isTargetCell = "cell".equalsIgnoreCase(tag) && normalizedReg.equals(cellNumber);
                    boolean hasNameAttr = false;
                    boolean hasRegionAttr = false;
                    boolean hasUpdatedAttr = false;
                    boolean hasNameUpdatedAttr = false;
                    String currentNameAttr = "";
                    String currentRegionAttr = "";
                    if (isTargetCell) {
                        for (int i = 0; i < attrCount; i++) {
                            String attrName = parser.getAttributeName(i);
                            String attrValue = parser.getAttributeValue(i);
                            if ("name".equalsIgnoreCase(attrName)) {
                                hasNameAttr = true;
                                currentNameAttr = normalizeCellLabel(attrValue);
                            } else if ("region".equalsIgnoreCase(attrName)) {
                                hasRegionAttr = true;
                                currentRegionAttr = normalizeRegionLabel(attrValue);
                            } else if ("updated".equalsIgnoreCase(attrName)) {
                                hasUpdatedAttr = true;
                            } else if ("nameUpdated".equalsIgnoreCase(attrName)) {
                                hasNameUpdatedAttr = true;
                            }
                        }
                    }
                    boolean shouldUpdateName = isTargetCell
                            && !normalizedLabel.isEmpty()
                            && (!hasNameAttr || !normalizedLabel.equals(currentNameAttr));
                    boolean shouldUpdateRegion = isTargetCell
                            && !normalizedRegion.isEmpty()
                            && (!hasRegionAttr || !normalizedRegion.equals(currentRegionAttr));
                    boolean metaChangedForTarget = shouldUpdateName || shouldUpdateRegion;

                    for (int i = 0; i < attrCount; i++) {
                        String attrNamespace = parser.getAttributeNamespace(i);
                        if (attrNamespace != null && attrNamespace.isEmpty()) {
                            attrNamespace = null;
                        }
                        String attrName = parser.getAttributeName(i);
                        String attrValue = parser.getAttributeValue(i);

                        if (isTargetCell && "name".equalsIgnoreCase(attrName) && shouldUpdateName) {
                            attrValue = normalizedLabel;
                        } else if (isTargetCell && "region".equalsIgnoreCase(attrName) && shouldUpdateRegion) {
                            attrValue = normalizedRegion;
                        } else if (isTargetCell && metaChangedForTarget
                                && ("updated".equalsIgnoreCase(attrName) || "nameUpdated".equalsIgnoreCase(attrName))) {
                            // При изменении `name/region` обязательно поднимаем timestamp мета-обновления.
                            attrValue = metaUpdatedTs;
                        }

                        serializer.attribute(attrNamespace, attrName, attrValue != null ? attrValue : "");
                    }

                    if (isTargetCell && !hasNameAttr && !normalizedLabel.isEmpty()) {
                        serializer.attribute(null, "name", normalizedLabel);
                    }
                    if (isTargetCell && !hasRegionAttr && !normalizedRegion.isEmpty()) {
                        serializer.attribute(null, "region", normalizedRegion);
                    }
                    if (isTargetCell && metaChangedForTarget) {
                        if (!hasUpdatedAttr) {
                            serializer.attribute(null, "updated", metaUpdatedTs);
                        }
                        if (!hasNameUpdatedAttr) {
                            serializer.attribute(null, "nameUpdated", metaUpdatedTs);
                        }
                        changed = true;
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    String tag = parser.getName();
                    String namespace = parser.getNamespace();
                    if (namespace != null && namespace.isEmpty()) {
                        namespace = null;
                    }
                    serializer.endTag(namespace, tag);
                } else if (event == XmlPullParser.TEXT) {
                    serializer.text(parser.getText());
                }
                event = parser.next();
            }
            serializer.endDocument();
            serializer.flush();
        } catch (Exception e) {
            android.util.Log.e(TAG, "persistRuntimeMapCellMeta: write failed, reg=" + normalizedReg, e);
            //noinspection ResultOfMethodCallIgnored
            tmpFile.delete();
            return false;
        }

        if (!changed) {
            //noinspection ResultOfMethodCallIgnored
            tmpFile.delete();
            return false;
        }

        try {
            if (targetFile.exists() && !targetFile.delete()) {
                android.util.Log.w(TAG, "persistRuntimeMapCellMeta: cannot delete old runtime map");
                //noinspection ResultOfMethodCallIgnored
                tmpFile.delete();
                return false;
            }
            if (!tmpFile.renameTo(targetFile)) {
                try (InputStream in = new FileInputStream(tmpFile);
                     FileOutputStream out = new FileOutputStream(targetFile, false)) {
                    copyStream(in, out);
                }
                //noinspection ResultOfMethodCallIgnored
                tmpFile.delete();
            }
            android.util.Log.d(TAG, "persistRuntimeMapCellMeta: saved reg=" + normalizedReg
                    + ", name=" + normalizedLabel + ", region=" + normalizedRegion);
            return true;
        } catch (Exception e) {
            android.util.Log.e(TAG, "persistRuntimeMapCellMeta: replace failed", e);
            return false;
        }
    }

    private static String formatVisitedForAbc(long visitedMs) {
        if (visitedMs <= 0L) {
            return ABC_VISITED_ZERO;
        }
        synchronized (ABC_VISITED_OUTPUT_FORMAT) {
            return ABC_VISITED_OUTPUT_FORMAT.format(new Date(visitedMs));
        }
    }

    /**
     * Нормализует подпись клетки для корректного сравнения строк из разных источников:
     * - убирает NBSP (`\u00A0`),
     * - схлопывает повторные пробелы,
     * - обрезает пробелы по краям.
     *
     * Важно:
     * - эта нормализация используется в цепочке `syncCellLabelFromServer(...)` и `syncTooltipLabel(...)`;
     * - эквивалентная логика должна сохраняться в `RoomManager.normalizeCellLabel(...)`.
     */
    private static String normalizeCellLabel(String label) {
        if (label == null) {
            return "";
        }
        return label.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    /**
     * Синхронизирует `Tooltip` клетки с новым названием, чтобы карта не показывала устаревший текст.
     *
     * Почему это важно:
     * - в рендере карты приоритет у `Tooltip` (см. `WebAppInterface.shortLabel(...)`);
     * - если обновить только `Name`, пользователь может продолжать видеть старую подпись.
     *
     * Алгоритм обновления:
     * 1) Если tooltip пустой — подставляем `newLabel`.
     * 2) Если tooltip целиком равен старому имени — полностью заменяем на `newLabel`.
     * 3) Если tooltip имеет форму `prefix, oldLabel` — заменяем только suffix после запятой.
     * 4) Иначе пытаемся точечную замену `oldLabel` внутри строки.
     * 5) Если ни одно правило не подошло — оставляем tooltip без изменений.
     */
    private static String syncTooltipLabel(String tooltip, String oldLabel, String newLabel) {
        if (newLabel == null || newLabel.isEmpty()) {
            return tooltip;
        }
        if (tooltip == null || tooltip.trim().isEmpty()) {
            return newLabel;
        }

        String normalizedOld = normalizeCellLabel(oldLabel);
        if (normalizedOld.isEmpty()) {
            return tooltip;
        }

        String normalizedTooltip = normalizeCellLabel(tooltip);
        if (normalizedTooltip.equals(normalizedOld)) {
            return newLabel;
        }

        int comma = tooltip.indexOf(',');
        if (comma >= 0 && comma + 1 < tooltip.length()) {
            String prefix = tooltip.substring(0, comma + 1);
            String suffix = tooltip.substring(comma + 1);
            if (normalizeCellLabel(suffix).equals(normalizedOld)) {
                return prefix + " " + newLabel;
            }
        }

        if (tooltip.contains(oldLabel)) {
            return tooltip.replace(oldLabel, newLabel);
        }
        return tooltip;
    }

    private static void copyAssetToFile(Context context, String assetName, File outputFile) throws Exception {
        try (InputStream in = context.getAssets().open(assetName);
             FileOutputStream out = new FileOutputStream(outputFile, false)) {
            copyStream(in, out);
        }
    }

    private static void copyStream(InputStream in, FileOutputStream out) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        out.flush();
    }

    private static void loadTeleports(Context context) {
        AssetManager assets = context.getAssets();
        try (InputStream in = assets.open("abteleports.xml")) {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(in, "UTF-8");
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "teleport".equalsIgnoreCase(parser.getName())) {
                    String regnum = parser.getAttributeValue(null, "regnum");
                    String name = parser.getAttributeValue(null, "name");
                    if (regnum != null) {
                        Teleports.put(regnum.trim(), name != null ? name.trim() : "");
                    }
                }
                event = parser.next();
            }
        } catch (Exception e) {
            android.util.Log.e("ExtMap", "loadTeleports error: " + e.getMessage());
        }
    }
}

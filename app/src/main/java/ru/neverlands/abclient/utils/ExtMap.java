package ru.neverlands.abclient.utils;

import android.content.Context;
import android.content.res.AssetManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
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

    public static final Map<String, Position> Location = new HashMap<>();
    public static final Map<String, String> InvLocation = new HashMap<>();
    public static final Map<String, Cell> Cells = new HashMap<>();
    public static final Map<String, String> Teleports = new HashMap<>();
    public static final Map<String, String> MovableCells = new HashMap<>();

    private static boolean initialized = false;

    public static synchronized void init(Context context) {
        if (initialized) return;
        initialized = true;
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
        AssetManager assets = context.getAssets();
        try (InputStream in = assets.open("map.xml")) {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(in, "UTF-8");
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("cell".equalsIgnoreCase(tag)) {
                        String cellNumber = parser.getAttributeValue(null, "cellNumber");
                        if (cellNumber != null) {
                            cellNumber = cellNumber.trim();
                            Cell cell = new Cell();
                            cell.CellNumber = cellNumber;
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
                            Cells.put(cellNumber, cell);
                        }
                    }
                }
                event = parser.next();
            }
        } catch (Exception e) {
            android.util.Log.e("ExtMap", "loadMap error: " + e.getMessage());
        }
    }

    private static void loadAbcMap(Context context) {
        AssetManager assets = context.getAssets();
        Map<String, Integer> abcCosts = new HashMap<>();
        Map<String, String> abcLabels = new HashMap<>();
        int loadedVisitedCount = 0;
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
            } else {
                Cell cell = new Cell();
                cell.CellNumber = regnum;
                cell.Cost = cost;
                cell.Name = abcLabels.containsKey(regnum) ? abcLabels.get(regnum) : "";
                Cells.put(regnum, cell);
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

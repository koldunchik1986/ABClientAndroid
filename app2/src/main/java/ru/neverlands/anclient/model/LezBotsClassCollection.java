package ru.neverlands.anclient.model;

import android.content.Context;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;

/**
 * Коллекция классов существ для автобоя.
 * Портировано из LezBotsClassCollection.cs.
 */
public class LezBotsClassCollection {
    private static final String TAG = "LezBotsClassCollection";
    private static final String BOT_TYPES_ASSET_PATH = "info/bottypes.xml";
    private static final String BOT_TYPES_DIR_NAME = "info";
    private static final String BOT_TYPES_FILE_NAME = "bottypes.xml";
    private static final int DYNAMIC_CLASS_ID_START = 10_000;

    private static final Object LOCK = new Object();
    private static final Map<Integer, LezBotsClass> classesById = new TreeMap<>();
    private static final Map<String, LezBotsClass> classesByName = new HashMap<>();
    private static boolean loaded = false;
    private static File runtimeFile;

    private LezBotsClassCollection() {
    }

    public static void init(Context context) {
        ensureLoaded(context);
    }

    public static LezBotsClass getClass(int id) {
        ensureLoaded(AppVars.getContext());
        synchronized (LOCK) {
            LezBotsClass botClass = classesById.get(id);
            if (botClass != null) {
                return botClass;
            }
            return new LezBotsClass(0, String.valueOf(id), String.valueOf(id), "bot");
        }
    }

    public static List<LezBotsClass> listForComboBox() {
        ensureLoaded(AppVars.getContext());
        synchronized (LOCK) {
            return new ArrayList<>(classesById.values());
        }
    }

    public static boolean isBossName(String foeName) {
        ensureLoaded(AppVars.getContext());
        String key = normalizeNameKey(foeName);
        if (key.isEmpty()) {
            return false;
        }
        synchronized (LOCK) {
            LezBotsClass botClass = classesByName.get(key);
            return botClass != null && "boss".equals(botClass.kind);
        }
    }

    public static void registerEncounteredFoeName(String foeName) {
        registerEncounteredFoeName(foeName, "bot");
    }

    public static void registerEncounteredFoeName(String foeName, String defaultKind) {
        ensureLoaded(AppVars.getContext());
        String normalizedName = safeTrim(foeName);
        if (normalizedName.isEmpty() || "Человек".equalsIgnoreCase(normalizedName)) {
            return;
        }
        // Runtime auto-upsert неизвестных имён:
        // - `normalizedName` -> ключ противника из боя (без изменений регистра/символов);
        // - `normalizedKind` -> только из whitelist (`all/human/bot/boss`), иначе fallback `bot`;
        // - `classesByName` -> защита от дублей по `normalizeNameKey(name)`;
        // - `dynamicId` -> выдаётся через `nextDynamicIdLocked()` начиная с `DYNAMIC_CLASS_ID_START`.
        // После вставки обязательно пишем runtime XML (`persistRuntimeFile()`), чтобы новое имя
        // стало доступно в следующих сессиях и в фильтрах `LezFight` / `UnderAttackManager`.
        String normalizedKind = normalizeKind(defaultKind);
        boolean inserted = false;
        synchronized (LOCK) {
            String nameKey = normalizeNameKey(normalizedName);
            if (nameKey.isEmpty() || classesByName.containsKey(nameKey)) {
                return;
            }
            int dynamicId = nextDynamicIdLocked();
            addClassLocked(new LezBotsClass(dynamicId, normalizedName, normalizedName, normalizedKind));
            inserted = true;
        }
        if (inserted) {
            boolean persisted = persistRuntimeFile();
            AppLog.i(TAG, "BOT_TYPES_RUNTIME_UPSERT name=" + normalizedName
                    + ", kind=" + normalizedKind
                    + ", persisted=" + persisted);
        }
    }

    private static void ensureLoaded(Context context) {
        if (loaded) {
            return;
        }
        synchronized (LOCK) {
            if (loaded) {
                return;
            }
            classesById.clear();
            classesByName.clear();

            if (context == null) {
                AppLog.w(TAG, "BOT_TYPES_INIT context is null, using minimal fallback");
                if (classesById.isEmpty()) {
                    seedMinimalFallbackLocked();
                }
                return;
            }

            // Порядок инициализации canonical-источника bottypes:
            // 1) `runtimeFile = resolveRuntimeFile(context)` -> `<files>/info/bottypes.xml`;
            // 2) `ensureRuntimeFileExistsFromAssetsLocked(...)` -> bootstrap из `assets/info/bottypes.xml`;
            // 3) `loadFromRuntimeFileLocked()` -> приоритет runtime-версии;
            // 4) fallback `loadFromAssetLocked(context)` если runtime ещё невалиден;
            // 5) при пустом результате -> `seedMinimalFallbackLocked()` (Все/Человек/Бот/Босс).
            // Это обеспечивает data-driven поведение без hardcode по именам боссов.
            runtimeFile = resolveRuntimeFile(context);
            boolean bootstrapped = ensureRuntimeFileExistsFromAssetsLocked(context);
            boolean loadedRuntime = loadFromRuntimeFileLocked();
            boolean loadedAsset = false;
            if (!loadedRuntime) {
                loadedAsset = loadFromAssetLocked(context);
                if (loadedAsset) {
                    persistRuntimeFile();
                }
            }
            if (classesById.isEmpty()) {
                seedMinimalFallbackLocked();
            }
            loaded = true;
            AppLog.i(TAG, "BOT_TYPES_INIT classes=" + classesById.size()
                    + ", runtimeLoaded=" + loadedRuntime
                    + ", assetLoaded=" + loadedAsset
                    + ", bootstrapped=" + bootstrapped
                    + ", runtimeFile=" + (runtimeFile == null ? "null" : runtimeFile.getAbsolutePath()));
        }
    }

    private static boolean loadFromRuntimeFileLocked() {
        if (runtimeFile == null || !runtimeFile.exists()) {
            return false;
        }
        try (FileInputStream inputStream = new FileInputStream(runtimeFile)) {
            return parseBotTypesXmlLocked(inputStream, runtimeFile.getAbsolutePath());
        } catch (Exception e) {
            AppLog.e(TAG, "BOT_TYPES_RUNTIME_LOAD_FAIL file=" + runtimeFile.getAbsolutePath(), e);
            return false;
        }
    }

    private static boolean loadFromAssetLocked(Context context) {
        try (InputStream inputStream = context.getAssets().open(BOT_TYPES_ASSET_PATH)) {
            return parseBotTypesXmlLocked(inputStream, "assets/" + BOT_TYPES_ASSET_PATH);
        } catch (Exception e) {
            AppLog.e(TAG, "BOT_TYPES_ASSET_LOAD_FAIL asset=" + BOT_TYPES_ASSET_PATH, e);
            return false;
        }
    }

    private static boolean parseBotTypesXmlLocked(InputStream inputStream, String sourceLabel) {
        int parsedCount = 0;
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && "class".equalsIgnoreCase(parser.getName())) {
                    int id = parseIntSafe(parser.getAttributeValue(null, "id"));
                    String name = safeTrim(parser.getAttributeValue(null, "name"));
                    String plural = safeTrim(parser.getAttributeValue(null, "plural"));
                    String type = parser.getAttributeValue(null, "type");
                    if (type == null || type.trim().isEmpty()) {
                        type = parser.getAttributeValue(null, "kind");
                    }
                    if (id > 0 && !name.isEmpty()) {
                        if (plural.isEmpty()) {
                            plural = name;
                        }
                        addClassLocked(new LezBotsClass(id, name, plural, normalizeKind(type)));
                        parsedCount++;
                    }
                }
                eventType = parser.next();
            }
            AppLog.d(TAG, "BOT_TYPES_PARSE_OK source=" + sourceLabel + ", parsed=" + parsedCount);
            return parsedCount > 0;
        } catch (Exception e) {
            AppLog.e(TAG, "BOT_TYPES_PARSE_FAIL source=" + sourceLabel, e);
            return false;
        }
    }

    private static boolean ensureRuntimeFileExistsFromAssetsLocked(Context context) {
        if (runtimeFile == null || runtimeFile.exists()) {
            return false;
        }
        File parent = runtimeFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            AppLog.w(TAG, "BOT_TYPES_BOOTSTRAP_FAIL cannot create dir: " + parent.getAbsolutePath());
            return false;
        }
        try (InputStream inputStream = context.getAssets().open(BOT_TYPES_ASSET_PATH);
             FileOutputStream outputStream = new FileOutputStream(runtimeFile, false)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
            AppLog.i(TAG, "BOT_TYPES_BOOTSTRAP_OK file=" + runtimeFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            AppLog.e(TAG, "BOT_TYPES_BOOTSTRAP_FAIL file=" + runtimeFile.getAbsolutePath(), e);
            return false;
        }
    }

    private static boolean persistRuntimeFile() {
        synchronized (LOCK) {
            if (runtimeFile == null) {
                return false;
            }
            File parent = runtimeFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                AppLog.w(TAG, "BOT_TYPES_PERSIST_FAIL cannot create dir: " + parent.getAbsolutePath());
                return false;
            }
            File tempFile = new File(parent, BOT_TYPES_FILE_NAME + ".tmp");
            try (FileOutputStream outputStream = new FileOutputStream(tempFile, false);
                 OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                XmlSerializer serializer = Xml.newSerializer();
                serializer.setOutput(writer);
                serializer.startDocument("UTF-8", true);
                serializer.startTag(null, "bottypes");
                serializer.attribute(null, "version", "1");

                for (LezBotsClass botClass : classesById.values()) {
                    serializer.startTag(null, "class");
                    serializer.attribute(null, "id", String.valueOf(botClass.id));
                    serializer.attribute(null, "name", safeTrim(botClass.name));
                    serializer.attribute(null, "plural", safeTrim(botClass.plural));
                    serializer.attribute(null, "type", normalizeKind(botClass.kind));
                    serializer.endTag(null, "class");
                }

                serializer.endTag(null, "bottypes");
                serializer.endDocument();
                writer.flush();
            } catch (Exception e) {
                AppLog.e(TAG, "BOT_TYPES_PERSIST_FAIL temp=" + tempFile.getAbsolutePath(), e);
                return false;
            }

            if (runtimeFile.exists() && !runtimeFile.delete()) {
                AppLog.w(TAG, "BOT_TYPES_PERSIST_WARN cannot delete old file: " + runtimeFile.getAbsolutePath());
            }
            if (!tempFile.renameTo(runtimeFile)) {
                try (FileInputStream inputStream = new FileInputStream(tempFile);
                     FileOutputStream outputStream = new FileOutputStream(runtimeFile, false)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, read);
                    }
                    outputStream.flush();
                    if (!tempFile.delete()) {
                        AppLog.w(TAG, "BOT_TYPES_PERSIST_WARN temp delete failed: " + tempFile.getAbsolutePath());
                    }
                } catch (Exception e) {
                    AppLog.e(TAG, "BOT_TYPES_PERSIST_FAIL rename/copy fallback", e);
                    return false;
                }
            }
            return true;
        }
    }

    private static File resolveRuntimeFile(Context context) {
        File root = context.getExternalFilesDir(null);
        if (root == null) {
            root = context.getFilesDir();
            AppLog.w(TAG, "BOT_TYPES_INIT external files dir unavailable, fallback to internal");
        }
        File infoDir = new File(root, BOT_TYPES_DIR_NAME);
        if (!infoDir.exists() && !infoDir.mkdirs()) {
            AppLog.w(TAG, "BOT_TYPES_INIT cannot create info dir: " + infoDir.getAbsolutePath());
        }
        return new File(infoDir, BOT_TYPES_FILE_NAME);
    }

    private static void seedMinimalFallbackLocked() {
        addClassLocked(new LezBotsClass(1, "Все", "Все", "all"));
        addClassLocked(new LezBotsClass(10, "Человек", "Люди", "human"));
        addClassLocked(new LezBotsClass(20, "Бот", "Боты", "bot"));
        addClassLocked(new LezBotsClass(21, "Босс", "Боссы", "boss"));
    }

    private static void addClassLocked(LezBotsClass botClass) {
        if (botClass == null || botClass.id <= 0) {
            return;
        }
        if (classesById.containsKey(botClass.id)) {
            return;
        }
        classesById.put(botClass.id, botClass);
        String key = normalizeNameKey(botClass.name);
        if (!key.isEmpty() && !classesByName.containsKey(key)) {
            classesByName.put(key, botClass);
        }
    }

    private static int nextDynamicIdLocked() {
        int maxId = DYNAMIC_CLASS_ID_START - 1;
        for (Integer id : classesById.keySet()) {
            if (id != null && id > maxId) {
                maxId = id;
            }
        }
        return Math.max(DYNAMIC_CLASS_ID_START, maxId + 1);
    }

    private static int parseIntSafe(String value) {
        try {
            return Integer.parseInt(safeTrim(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String normalizeNameKey(String value) {
        String normalized = safeTrim(value).replace('\u00A0', ' ');
        if (normalized.isEmpty()) {
            return "";
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeKind(String value) {
        String normalized = safeTrim(value).toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "all":
            case "human":
            case "bot":
            case "boss":
                return normalized;
            default:
                return "bot";
        }
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}

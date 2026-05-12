package ru.neverlands.anclient.manager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ru.neverlands.anclient.model.KaznaItemDetails;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.GameInfoStorage;

/**
 * UTF-8 кеш UID-деталей казны в `info/<profile nick>/kazna/uids.txt`.
 *
 * Назначение после доработок:
 * - хранит свойства и image URL, которые сервер отдаёт в inventory-card
 *   `main.php?get_id=57&uid=...`, но не отдаёт в строках казны;
 * - пополняется из `InventoryParser.mainPhpInv(...)` и cache-only входов
 *   `InventoryParser.syncKaznaItemDetailsCacheFromHtml(...)`;
 * - читается `KaznaManager.loadItemDetailsByUid()` и передаётся в
 *   `KaznaItemAdapter.submitDetails(...)`;
 * - не является источником action UID для `Взять/Пожертвовать/Собрать`.
 *
 * Decision points:
 * - кеш пополняется только из уже загруженного HTML инвентаря;
 * - merge не затирает ранее известные поля пустыми значениями из неполной карточки;
 * - файл отделён от `info/<profile nick>/kazna/kazna.txt`, чтобы snapshot казны
 *   оставался только серверным списком;
 * - путь привязан к текущему профилю, чтобы детали вещей разных персонажей не
 *   смешивались между сессиями.
 */
public final class KaznaItemDetailsCache {
    private static final String TAG = "KaznaUidDetailsCache";
    private static final String TRACE = "KAZNA_TRACE";
    private static final String KAZNA_DIR = "kazna";
    private static final String UID_DETAILS_FILE = "uids.txt";
    private static final Object LOCK = new Object();

    private KaznaItemDetailsCache() {
    }

    public static File getCacheFile() {
        return getCacheFile(currentProfileNickForStorage());
    }

    public static File getCacheFile(String profileNick) {
        File dir = getKaznaDir(profileNick);
        return dir == null ? null : new File(dir, UID_DETAILS_FILE);
    }

    public static Map<String, KaznaItemDetails> loadAll() {
        return loadAll(currentProfileNickForStorage());
    }

    public static Map<String, KaznaItemDetails> loadAll(String profileNick) {
        synchronized (LOCK) {
            return loadAllLocked(profileNick);
        }
    }

    public static void mergeFromInventoryDetails(List<KaznaItemDetails> details) {
        mergeFromInventoryDetails(details, currentProfileNickForStorage());
    }

    public static void mergeFromInventoryDetails(List<KaznaItemDetails> details, String profileNick) {
        if (details == null || details.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            Map<String, KaznaItemDetails> current = loadAllLocked(profileNick);
            int parsed = 0;
            int changed = 0;
            for (KaznaItemDetails detail : details) {
                if (detail == null || detail.uid.isEmpty()) {
                    continue;
                }
                parsed++;
                KaznaItemDetails merged = merge(current.get(detail.uid), detail);
                if (!same(current.get(detail.uid), merged)) {
                    current.put(detail.uid, merged);
                    changed++;
                }
            }
            if (changed > 0) {
                saveAllLocked(current, profileNick);
                File file = getCacheFile(profileNick);
                AppLog.i(TAG, TRACE + " uid details updated: parsed=" + parsed
                        + ", changed=" + changed
                        + ", file=" + (file == null ? "" : file.getAbsolutePath()));
            }
        }
    }

    private static KaznaItemDetails merge(KaznaItemDetails oldValue, KaznaItemDetails newValue) {
        if (oldValue == null) {
            return new KaznaItemDetails(
                    newValue.uid,
                    newValue.name,
                    newValue.imageUrl,
                    newValue.propertiesText,
                    System.currentTimeMillis());
        }
        return new KaznaItemDetails(
                firstNonEmpty(newValue.uid, oldValue.uid),
                firstNonEmpty(newValue.name, oldValue.name),
                firstNonEmpty(newValue.imageUrl, oldValue.imageUrl),
                firstNonEmpty(newValue.propertiesText, oldValue.propertiesText),
                System.currentTimeMillis());
    }

    private static Map<String, KaznaItemDetails> loadAllLocked(String profileNick) {
        LinkedHashMap<String, KaznaItemDetails> result = new LinkedHashMap<>();
        File file = getCacheFile(profileNick);
        if (file == null || !file.exists()) {
            return result;
        }
        String json = readUtf8(file);
        if (json.isEmpty()) {
            return result;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONArray items = root.optJSONArray("items");
            if (items == null) {
                return result;
            }
            for (int index = 0; index < items.length(); index++) {
                JSONObject item = items.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                KaznaItemDetails details = new KaznaItemDetails(
                        item.optString("uid", ""),
                        item.optString("name", ""),
                        item.optString("imageUrl", ""),
                        item.optString("propertiesText", ""),
                        item.optLong("updatedAtMs", 0L));
                if (!details.uid.isEmpty()) {
                    result.put(details.uid, details);
                }
            }
        } catch (Exception e) {
            AppLog.w(TAG, TRACE + " uid details read failed", e);
        }
        return result;
    }

    private static void saveAllLocked(Map<String, KaznaItemDetails> detailsByUid, String profileNick) {
        File file = getCacheFile(profileNick);
        if (file == null) {
            AppLog.w(TAG, TRACE + " uid details save skipped: info dir unavailable");
            return;
        }
        try {
            JSONObject root = new JSONObject();
            root.put("generatedAtMs", System.currentTimeMillis());
            JSONArray items = new JSONArray();
            for (KaznaItemDetails detail : detailsByUid.values()) {
                if (detail == null || detail.uid.isEmpty()) {
                    continue;
                }
                JSONObject item = new JSONObject();
                item.put("uid", detail.uid);
                item.put("name", detail.name);
                item.put("imageUrl", detail.imageUrl);
                item.put("propertiesText", detail.propertiesText);
                item.put("updatedAtMs", detail.updatedAtMs);
                items.put(item);
            }
            root.put("items", items);
            writeUtf8(file, root.toString(2));
        } catch (Exception e) {
            AppLog.e(TAG, TRACE + " uid details save failed", e);
        }
    }

    private static File getKaznaDir(String profileNick) {
        File dir = GameInfoStorage.resolveUserInfoSubDir(profileNick, KAZNA_DIR);
        if (dir == null && AppVars.getContext() != null) {
            File infoDir = new File(AppVars.getContext().getFilesDir(), "info");
            dir = new File(new File(infoDir, GameInfoStorage.sanitizeProfileName(profileNick)), KAZNA_DIR);
        }
        if (dir == null) {
            return null;
        }
        if (!dir.exists() && !dir.mkdirs()) {
            AppLog.w(TAG, TRACE + " failed to create uid details dir: " + dir.getAbsolutePath());
            return null;
        }
        return dir;
    }

    private static String currentProfileNickForStorage() {
        if (AppVars.Profile != null && AppVars.Profile.UserNick != null) {
            String nick = AppVars.Profile.UserNick.trim();
            if (!nick.isEmpty()) {
                return nick;
            }
        }
        String runtimeNick = AppVars.RuntimeAuthUserNick == null ? "" : AppVars.RuntimeAuthUserNick.trim();
        return runtimeNick.isEmpty() ? "profile" : runtimeNick;
    }

    private static String readUtf8(File file) {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        } catch (Exception e) {
            AppLog.w(TAG, TRACE + " uid details utf8 read failed: " + file.getAbsolutePath(), e);
            return "";
        }
    }

    private static void writeUtf8(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create directory: " + parent.getAbsolutePath());
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content == null ? "" : content);
        }
    }

    private static String firstNonEmpty(String primary, String fallback) {
        return primary == null || primary.trim().isEmpty() ? safe(fallback) : primary.trim();
    }

    private static boolean same(KaznaItemDetails left, KaznaItemDetails right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return safe(left.uid).equals(safe(right.uid))
                && safe(left.name).equals(safe(right.name))
                && safe(left.imageUrl).equals(safe(right.imageUrl))
                && safe(left.propertiesText).equals(safe(right.propertiesText));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

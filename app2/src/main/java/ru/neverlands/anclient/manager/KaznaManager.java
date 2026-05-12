package ru.neverlands.anclient.manager;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ru.neverlands.anclient.model.KaznaCategory;
import ru.neverlands.anclient.model.KaznaItemDetails;
import ru.neverlands.anclient.model.KaznaItem;
import ru.neverlands.anclient.model.KaznaSet;
import ru.neverlands.anclient.model.KaznaSnapshot;
import ru.neverlands.anclient.parser.KaznaParser;
import ru.neverlands.anclient.postfilter.InventoryParser;
import ru.neverlands.anclient.repository.ApiRepository;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.FileLogger;
import ru.neverlands.anclient.utils.GameInfoStorage;
import ru.neverlands.anclient.utils.Russian;
import ru.neverlands.anclient.utils.SessionManager;

/**
 * Координатор модуля `Казна`.
 *
 * Сделанные правки и зависимости по именам:
 * - `KaznaActivity` вызывает только публичные методы этого класса, а не работает
 *   напрямую с `ApiRepository`, файлами кеша или `SessionManager`;
 * - `KaznaParser.BASE_KAZNA_URL` содержит reset-параметр `wfo=1`, найденный в
 *   ПК-эталоне `ANClient/PostFilter/MainPhp.cs` как `ClanKaznaUrl`; это исправляет
 *   кнопку категории `Все`, когда сервер иначе оставляет предыдущий `wca`-фильтр;
 * - все файлы казны хранятся в `info/<profile nick>/kazna/`, чтобы snapshot,
 *   raw HTML, локальные комплекты и UID-детали не смешивались между профилями;
 * - `KAZNA_CACHE_FILE` (`kazna.txt`) хранит последний snapshot любого scope,
 *   а `KAZNA_ALL_CACHE_FILE` (`kazna_all.txt`) хранит только полный reset-scope;
 * - `KAZNA_SETS_FILE` (`kazna_sets.json`) хранит локальные комплекты UID и не
 *   смешивается с серверным snapshot;
 * - `KaznaItemDetailsCache` (`uids.txt`) подключается как источник картинки/свойств
 *   предмета и fallback UID для локальных комплектов, но не участвует в сетевых
 *   action-ссылках казны.
 *
 * Responsibilities:
 * - сетевой слой переиспользует `ApiRepository.downloadFile(...)`, чтобы не
 *   создавать второй cookie/proxy/User-Agent контур;
 * - парсинг делегирован `KaznaParser`, чтобы Activity не знала HTML-детали;
 * - кеш `info/<profile nick>/kazna/kazna.txt` хранит последний распарсенный snapshot;
 * - локальные комплекты хранятся отдельно в `info/<profile nick>/kazna/kazna_sets.json`.
 */
public final class KaznaManager {
    private static final String TAG = "KaznaManager";
    private static final String TRACE = "KAZNA_TRACE";

    private static final String KAZNA_CACHE_FILE = "kazna.txt";
    private static final String KAZNA_ALL_CACHE_FILE = "kazna_all.txt";
    private static final String KAZNA_SETS_FILE = "kazna_sets.json";
    private static final String KAZNA_RAW_FILE = "kazna_raw.html";
    private static final String KAZNA_INVENTORY_RAW_FILE = "kazna_inventory_raw.html";
    private static final long COLLECT_SET_TAKE_DELAY_MS = 500L;
    private static final int COLLECT_SET_TAKE_MAX_ATTEMPTS = 2;

    private static volatile KaznaManager instance;

    private interface KaznaActionObserver {
        void onParsed(String html, KaznaSnapshot snapshot);
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object snapshotLock = new Object();
    private KaznaSnapshot cachedSnapshot;
    private String cachedSnapshotProfileKey = "";

    private KaznaManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static KaznaManager getInstance(Context context) {
        if (instance == null) {
            synchronized (KaznaManager.class) {
                if (instance == null) {
                    instance = new KaznaManager(context);
                }
            }
        }
        return instance;
    }

    public File getKaznaCacheFile() {
        return new File(getKaznaDir(), KAZNA_CACHE_FILE);
    }

    public File getKaznaAllCacheFile() {
        return new File(getKaznaDir(), KAZNA_ALL_CACHE_FILE);
    }

    public File getKaznaSetsFile() {
        return new File(getKaznaDir(), KAZNA_SETS_FILE);
    }

    private File getKaznaCacheFile(String profileNick) {
        return new File(getKaznaDir(profileNick), KAZNA_CACHE_FILE);
    }

    private File getKaznaAllCacheFile(String profileNick) {
        return new File(getKaznaDir(profileNick), KAZNA_ALL_CACHE_FILE);
    }

    public Map<String, KaznaItemDetails> loadItemDetailsByUid() {
        return KaznaItemDetailsCache.loadAll();
    }

    /**
     * Принимает HTML казны, уже полученный штатным WebView/postfilter-потоком.
     *
     * Так HTML-инъекция в `MainPhp` переиспользует существующий parser/cache-контур
     * `KaznaManager`, не создавая второй сетевой слой для прямого открытия страницы
     * `main.php?useaction=clan-action&addid=3`.
     *
     * Зависимости и порядок:
     * - `sourceUrl` нормализуется тем же `KaznaParser.normalizeMainUrl(...)`, что и
     *   сетевые refresh/action методы, чтобы `wca` и `wfo=1` одинаково попадали в snapshot;
     * - `saveSnapshot(...)` пишет `kazna.txt`, а для полного reset-scope ещё и `kazna_all.txt`;
     * - `snapshotLock` и `cachedSnapshotProfileKey` обновляются синхронно, чтобы `KaznaActivity`
     *   после HTML-инъекции сразу увидела актуальную казну;
     * - при ошибке записи на диск HTML-renderer всё равно получает parsed snapshot и не ломает
     *   ручной просмотр страницы.
     */
    public KaznaSnapshot acceptPostfilterHtml(String html, String sourceUrl) {
        String requestUrl = TextUtils.isEmpty(sourceUrl) ? KaznaParser.BASE_KAZNA_URL : KaznaParser.normalizeMainUrl(sourceUrl);
        String profileNick = currentProfileNickForStorage();
        KaznaSnapshot snapshot = KaznaParser.parse(html, requestUrl);
        try {
            saveSnapshot(snapshot, profileNick);
        } catch (Exception e) {
            AppLog.w(TAG, TRACE + " postfilter snapshot save failed", e);
        }
        synchronized (snapshotLock) {
            cachedSnapshot = snapshot;
            cachedSnapshotProfileKey = storageKey(profileNick);
        }
        AppLog.i(TAG, TRACE + " postfilter parsed: items=" + snapshot.items.size()
                + ", categories=" + snapshot.categories.size()
                + ", currentWca=" + snapshot.currentWca
                + ", source=" + requestUrl);
        FileLogger.trace(TRACE, "postfilter parsed items=" + snapshot.items.size()
                + ", categories=" + snapshot.categories.size()
                + ", source=" + requestUrl);
        return snapshot;
    }

    public KaznaSnapshot getCachedSnapshot() {
        String profileKey = currentProfileStorageKey();
        synchronized (snapshotLock) {
            if (cachedSnapshot == null || !profileKey.equals(cachedSnapshotProfileKey)) {
                cachedSnapshot = readSnapshotFromDisk();
                cachedSnapshotProfileKey = profileKey;
            }
            return cachedSnapshot;
        }
    }

    /**
     * Возвращает только валидный полный кеш.
     *
     * Защита от регрессии `Все -> последняя wca`: старый или ошибочно записанный
     * `kazna_all.txt` в профиле игнорируется, если source URL не является
     * reset-запросом `wfo=1` без `wca`. Это оставляет профильный `kazna.txt`
     * как last-cache, но не даёт ему подменять полный список для вкладок
     * `Арты/Рары/Обычные`.
     */
    public KaznaSnapshot getCachedAllSnapshot() {
        synchronized (snapshotLock) {
            KaznaSnapshot snapshot = readSnapshotFromDisk(getKaznaAllCacheFile());
            if (snapshot != null && !isAllScopeSnapshot(snapshot)) {
                AppLog.w(TAG, TRACE + " all-cache ignored: source=" + snapshot.sourceUrl
                        + ", currentWca=" + snapshot.currentWca
                        + ", items=" + snapshot.items.size());
                return null;
            }
            return snapshot;
        }
    }

    /**
     * Загружает казну или выбранную серверную категорию.
     *
     * После каждого успешного ответа:
     * - HTML декодируется как windows-1251;
     * - VCode синхронизируется в `SessionManager`;
     * - результат парсинга сохраняется в `info/<profile nick>/kazna/kazna.txt`.
     *
     * Имена зависимостей:
     * - `category == null` означает UI-кнопку `Все` и ведёт на
     *   `KaznaParser.BASE_KAZNA_URL` (`main.php?wfo=1&useaction=clan-action&addid=3`);
     * - `category.href` - серверная категория `wca=...` из `KaznaParser.parseCategories(...)`;
     * - callback всегда возвращается на main thread через `postSuccess/postFailure`.
     */
    public void refreshKazna(KaznaCategory category, ApiRepository.ApiCallback<KaznaSnapshot> callback) {
        String url = category == null || TextUtils.isEmpty(category.href)
                ? KaznaParser.BASE_KAZNA_URL
                : KaznaParser.normalizeMainUrl(category.href);
        refreshKaznaUrl(url, callback);
    }

    public void refreshKaznaUrl(String url, ApiRepository.ApiCallback<KaznaSnapshot> callback) {
        String requestUrl = TextUtils.isEmpty(url) ? KaznaParser.BASE_KAZNA_URL : KaznaParser.normalizeMainUrl(url);
        String profileNick = currentProfileNickForStorage();
        AppLog.i(TAG, TRACE + " refresh: url=" + requestUrl);
        FileLogger.trace(TRACE, "refresh url=" + requestUrl);
        File rawFile = new File(getKaznaDir(profileNick), KAZNA_RAW_FILE);
        ApiRepository.downloadFile(requestUrl, rawFile, new ApiRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    KaznaSnapshot snapshot = parseRawFileAndSave(rawFile, requestUrl, "kazna_refresh", profileNick);
                    postSuccess(callback, snapshot);
                } catch (Exception e) {
                    AppLog.e(TAG, TRACE + " refresh parse failed", e);
                    postFailure(callback, "Ошибка парсинга казны: " + safeMessage(e));
                }
            }

            @Override
            public void onFailure(String message) {
                AppLog.w(TAG, TRACE + " refresh failed: " + message);
                postFailure(callback, message);
            }
        });
    }

    public void takeItem(KaznaItem item, ApiRepository.ApiCallback<KaznaSnapshot> callback) {
        if (item == null || TextUtils.isEmpty(item.takeUrl)) {
            postFailure(callback, "Для предмета нет ссылки `Взять из казны`");
            return;
        }
        performKaznaAction(item.takeUrl, "kazna_take", callback);
    }

    public void donateItem(KaznaItem item, ApiRepository.ApiCallback<KaznaSnapshot> callback) {
        if (item == null || TextUtils.isEmpty(item.donateUrl)) {
            postFailure(callback, "Для предмета нет ссылки `Пожертвовать`");
            return;
        }
        performKaznaAction(item.donateUrl, "kazna_donate", callback);
    }

    /**
     * Последовательно берёт предметы комплекта, каждый раз используя свежий
     * snapshot после предыдущего ответа. Это важно, потому что сервер может
     * перевыпустить `vcode` и ссылки после каждого успешного взятия.
     *
     * Успешность отдельного `Взять из казны` определяется по тексту серверного
     * ответа `Вы успешно взяли вещь.` (см. `kazna_take.har`), а не по тяжёлому
     * открытию инвентаря и не по stale-метке `В-инвентаре` в старом snapshot.
     */
    public void collectSet(KaznaSet set, ApiRepository.ApiCallback<KaznaSnapshot> callback) {
        if (set == null || set.itemUids.isEmpty()) {
            postFailure(callback, "В комплекте нет UID предметов");
            return;
        }
        collectSetNext(set.name, set.copyItemUids(), 0, getCachedSnapshot(), new ArrayList<>(), 1, callback);
    }

    /**
     * Надевает UID комплекта без предварительной проверки результата `Собрать`.
     * Маркер `Вы успешно взяли вещь.` нужен только collect-цепочке, чтобы понимать,
     * подтвердил ли сервер взятие из казны. `Надеть` всегда пытается обработать все
     * UID комплекта и пропускает только те, для которых в уже открытом inventory HTML
     * нет wear-link.
     */
    public void wearSet(KaznaSet set, ApiRepository.ApiCallback<String> callback) {
        if (set == null || set.itemUids.isEmpty()) {
            postFailure(callback, "В комплекте нет UID предметов");
            return;
        }
        List<String> targetUids = set.copyItemUids();
        AppLog.i(TAG, TRACE + " wear set start: name=" + set.name
                + ", targetUids=" + targetUids.size()
                + ", requestedUids=" + set.itemUids.size());
        FileLogger.trace(TRACE, "wear set start name=" + set.name
                + ", targetUids=" + targetUids.size());
        loadInventoryHtml("kazna_set_wear_inventory", new ApiRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String html) {
                wearSetNext(set.name, targetUids, 0, html, 0, 0, callback);
            }

            @Override
            public void onFailure(String message) {
                postFailure(callback, message);
            }
        });
    }

    public List<KaznaSet> loadSets() {
        File file = getKaznaSetsFile();
        String json = readUtf8(file);
        List<KaznaSet> result = new ArrayList<>();
        if (TextUtils.isEmpty(json)) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                JSONArray uidArray = item.optJSONArray("itemUids");
                ArrayList<String> uids = new ArrayList<>();
                if (uidArray != null) {
                    for (int uidIndex = 0; uidIndex < uidArray.length(); uidIndex++) {
                        String uid = uidArray.optString(uidIndex, "");
                        if (!TextUtils.isEmpty(uid)) {
                            uids.add(uid);
                        }
                    }
                }
                String name = item.optString("name", "").trim();
                if (!name.isEmpty()) {
                    result.add(new KaznaSet(name, uids));
                }
            }
        } catch (Exception e) {
            AppLog.w(TAG, TRACE + " load sets failed", e);
        }
        return result;
    }

    public boolean addItemToSet(String setName, KaznaItem item) {
        return item != null && addItemToSet(setName, item.uid);
    }

    public boolean addItemToSet(String setName, String uid) {
        if (TextUtils.isEmpty(uid) || TextUtils.isEmpty(setName)) {
            return false;
        }
        String safeUid = uid.trim();
        String safeName = setName.trim();
        List<KaznaSet> sets = loadSets();
        ArrayList<KaznaSet> updated = new ArrayList<>();
        boolean found = false;
        boolean changed = false;
        for (KaznaSet set : sets) {
            if (set.name.equalsIgnoreCase(safeName)) {
                ArrayList<String> uids = new ArrayList<>(set.itemUids);
                if (!uids.contains(safeUid)) {
                    uids.add(safeUid);
                    changed = true;
                }
                updated.add(new KaznaSet(set.name, uids));
                found = true;
            } else {
                updated.add(set);
            }
        }
        if (!found) {
            ArrayList<String> uids = new ArrayList<>();
            uids.add(safeUid);
            updated.add(new KaznaSet(safeName, uids));
            changed = true;
        }
        saveSets(updated);
        return true;
    }

    public boolean addSet(String setName) {
        if (TextUtils.isEmpty(setName)) {
            return false;
        }
        String safeName = setName.trim();
        List<KaznaSet> sets = loadSets();
        for (KaznaSet set : sets) {
            if (set.name.equalsIgnoreCase(safeName)) {
                return true;
            }
        }
        ArrayList<KaznaSet> updated = new ArrayList<>(sets);
        updated.add(new KaznaSet(safeName, new ArrayList<>()));
        saveSets(updated);
        return true;
    }

    public boolean removeItemFromSet(KaznaSet target, String uid) {
        if (target == null || TextUtils.isEmpty(uid)) {
            return false;
        }
        List<KaznaSet> sets = loadSets();
        ArrayList<KaznaSet> updated = new ArrayList<>();
        boolean changed = false;
        String safeUid = uid.trim();
        for (KaznaSet set : sets) {
            if (!set.name.equalsIgnoreCase(target.name)) {
                updated.add(set);
                continue;
            }
            ArrayList<String> uids = new ArrayList<>(set.itemUids);
            changed = uids.remove(safeUid) || changed;
            updated.add(new KaznaSet(set.name, uids));
        }
        if (changed) {
            saveSets(updated);
        }
        return changed;
    }

    public void deleteSet(KaznaSet target) {
        if (target == null) {
            return;
        }
        List<KaznaSet> sets = loadSets();
        ArrayList<KaznaSet> updated = new ArrayList<>();
        for (KaznaSet set : sets) {
            if (!set.name.equalsIgnoreCase(target.name)) {
                updated.add(set);
            }
        }
        saveSets(updated);
    }

    private void collectSetNext(
            String setName,
            List<String> uids,
            int index,
            KaznaSnapshot latest,
            List<String> collectedUids,
            int attempt,
            ApiRepository.ApiCallback<KaznaSnapshot> callback) {
        if (index >= uids.size()) {
            AppLog.i(TAG, TRACE + " collect finished: set=" + setName
                    + ", collected=" + collectedUids.size()
                    + ", requested=" + uids.size());
            FileLogger.trace(TRACE, "collect finished set=" + setName
                    + ", collected=" + collectedUids.size()
                    + ", requested=" + uids.size());
            postSuccess(callback, latest == null ? getCachedSnapshot() : latest);
            return;
        }
        String uid = uids.get(index);
        KaznaSnapshot snapshot = latest == null ? getCachedSnapshot() : latest;
        KaznaItem item = snapshot == null ? null : snapshot.findItemByUid(uid);
        if (item == null || TextUtils.isEmpty(item.takeUrl)) {
            AppLog.w(TAG, TRACE + " collect: skip unavailable uid=" + uid);
            collectSetNext(setName, uids, index + 1, snapshot, collectedUids, 1, callback);
            return;
        }
        AppLog.i(TAG, TRACE + " collect delay before take: uid=" + uid
                + ", attempt=" + attempt
                + ", delayMs=" + COLLECT_SET_TAKE_DELAY_MS);
        FileLogger.trace(TRACE, "collect delay before take uid=" + uid
                + ", attempt=" + attempt
                + ", delayMs=" + COLLECT_SET_TAKE_DELAY_MS);
        boolean[] confirmed = {false};
        mainHandler.postDelayed(() -> performKaznaAction(item.takeUrl, "kazna_collect_set", (html, result) -> {
            if (isSuccessfulKaznaTakeResponse(html)) {
                confirmed[0] = true;
                collectedUids.add(uid);
                AppLog.i(TAG, TRACE + " collect success by server response: uid=" + uid);
                FileLogger.trace(TRACE, "collect success uid=" + uid);
            } else {
                AppLog.w(TAG, TRACE + " collect response without success marker: uid=" + uid);
            }
        }, new ApiRepository.ApiCallback<KaznaSnapshot>() {
            @Override
            public void onSuccess(KaznaSnapshot result) {
                if (!confirmed[0] && attempt < COLLECT_SET_TAKE_MAX_ATTEMPTS) {
                    int nextAttempt = attempt + 1;
                    AppLog.w(TAG, TRACE + " collect retry after missing success marker: uid=" + uid
                            + ", nextAttempt=" + nextAttempt);
                    FileLogger.trace(TRACE, "collect retry uid=" + uid
                            + ", nextAttempt=" + nextAttempt);
                    collectSetNext(setName, uids, index, result, collectedUids, nextAttempt, callback);
                    return;
                }
                collectSetNext(setName, uids, index + 1, result, collectedUids, 1, callback);
            }

            @Override
            public void onFailure(String message) {
                AppLog.w(TAG, TRACE + " collect stopped after failure: set=" + setName
                        + ", collected=" + collectedUids.size()
                        + ", reason=" + message);
                FileLogger.trace(TRACE, "collect stopped set=" + setName
                        + ", collected=" + collectedUids.size()
                        + ", reason=" + message);
                postFailure(callback, message);
            }
        }), COLLECT_SET_TAKE_DELAY_MS);
    }

    private boolean isSuccessfulKaznaTakeResponse(String html) {
        return html != null
                && html.replace('\u00A0', ' ')
                .toLowerCase(Locale.ROOT)
                .contains("вы успешно взяли вещь");
    }

    private void loadInventoryHtml(String actionName, ApiRepository.ApiCallback<String> callback) {
        String profileNick = currentProfileNickForStorage();
        String vcode = SessionManager.getInstance().getValidVCodeForAction(actionName);
        if (TextUtils.isEmpty(vcode)) {
            AppLog.w(TAG, TRACE + " inventory load skip: no vcode, action=" + actionName);
            postFailure(callback, "Нет актуального кода защиты для открытия инвентаря");
            return;
        }
        String url = "http://neverlands.ru/main.php?get_id=56&act=10&go=inv&vcode="
                + vcode + "&an_kazna_set=1&r=" + System.currentTimeMillis();
        File rawFile = new File(getKaznaDir(profileNick), KAZNA_INVENTORY_RAW_FILE);
        AppLog.i(TAG, TRACE + " inventory load: action=" + actionName + ", url=" + url);
        FileLogger.trace(TRACE, "inventory load action=" + actionName + ", url=" + url);
        ApiRepository.downloadFile(url, rawFile, new ApiRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    String html = readWindows1251(rawFile);
                    handleProtectionErrorIfNeeded(html, url, actionName);
                    SessionManager.getInstance().parseVCodeFromHtml(html, actionName);
                    InventoryParser.syncKaznaItemDetailsCacheFromHtml(html, actionName, profileNick);
                    postStringSuccess(callback, html);
                } catch (Exception e) {
                    AppLog.e(TAG, TRACE + " inventory load parse failed", e);
                    postFailure(callback, "Ошибка чтения инвентаря: " + safeMessage(e));
                }
            }

            @Override
            public void onFailure(String message) {
                AppLog.w(TAG, TRACE + " inventory load failed: " + message);
                postFailure(callback, message);
            }
        });
    }

    private void wearSetNext(
            String setName,
            List<String> uids,
            int index,
            String inventoryHtml,
            int wornCount,
            int skippedCount,
            ApiRepository.ApiCallback<String> callback) {
        if (index >= uids.size()) {
            postStringSuccess(callback, "Надевание комплекта `" + setName + "` завершено: надето "
                    + wornCount + ", пропущено " + skippedCount);
            return;
        }
        String uid = uids.get(index);
        InventoryParser.WearInvEntry entry = findWearEntryByUid(inventoryHtml, uid);
        if (entry == null || TextUtils.isEmpty(entry.wearLink)) {
            AppLog.w(TAG, TRACE + " wear skip: no inventory wear-link for uid=" + uid);
            wearSetNext(setName, uids, index + 1, inventoryHtml, wornCount, skippedCount + 1, callback);
            return;
        }
        performInventoryWearAction(entry, new ApiRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String htmlAfterWear) {
                String nextHtml = TextUtils.isEmpty(htmlAfterWear) ? inventoryHtml : htmlAfterWear;
                if (InventoryParser.getWearInvList(nextHtml).isEmpty() && index + 1 < uids.size()) {
                    loadInventoryHtml("kazna_set_wear_inventory_after_action", new ApiRepository.ApiCallback<String>() {
                        @Override
                        public void onSuccess(String reloadedHtml) {
                            wearSetNext(setName, uids, index + 1, reloadedHtml, wornCount + 1, skippedCount, callback);
                        }

                        @Override
                        public void onFailure(String message) {
                            postFailure(callback, message);
                        }
                    });
                } else {
                    wearSetNext(setName, uids, index + 1, nextHtml, wornCount + 1, skippedCount, callback);
                }
            }

            @Override
            public void onFailure(String message) {
                postFailure(callback, message);
            }
        });
    }

    private InventoryParser.WearInvEntry findWearEntryByUid(String inventoryHtml, String uid) {
        if (TextUtils.isEmpty(inventoryHtml) || TextUtils.isEmpty(uid)) {
            return null;
        }
        for (InventoryParser.WearInvEntry entry : InventoryParser.getWearInvList(inventoryHtml)) {
            if (entry != null && uid.equals(entry.uid)) {
                return entry;
            }
        }
        return null;
    }

    private void performInventoryWearAction(InventoryParser.WearInvEntry entry, ApiRepository.ApiCallback<String> callback) {
        String profileNick = currentProfileNickForStorage();
        String requestUrl = prepareProtectedActionUrl(entry.wearLink, "kazna_set_wear_item");
        if (TextUtils.isEmpty(requestUrl)) {
            postFailure(callback, "Нет актуального кода защиты для надевания uid=" + entry.uid);
            return;
        }
        AppLog.i(TAG, TRACE + " wear action: uid=" + entry.uid + ", name=" + entry.name + ", url=" + requestUrl);
        FileLogger.trace(TRACE, "wear action uid=" + entry.uid + ", name=" + entry.name + ", url=" + requestUrl);
        File rawFile = new File(getKaznaDir(profileNick), KAZNA_INVENTORY_RAW_FILE);
        ApiRepository.downloadFile(requestUrl, rawFile, new ApiRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    String html = readWindows1251(rawFile);
                    handleProtectionErrorIfNeeded(html, requestUrl, "kazna_set_wear_item");
                    SessionManager.getInstance().parseVCodeFromHtml(html, "kazna_set_wear_item");
                    InventoryParser.syncKaznaItemDetailsCacheFromHtml(html, "kazna_set_wear_item", profileNick);
                    postStringSuccess(callback, html);
                } catch (Exception e) {
                    AppLog.e(TAG, TRACE + " wear action parse failed", e);
                    postFailure(callback, "Ошибка надевания uid=" + entry.uid + ": " + safeMessage(e));
                }
            }

            @Override
            public void onFailure(String message) {
                AppLog.w(TAG, TRACE + " wear action failed: uid=" + entry.uid + ", reason=" + message);
                postFailure(callback, message);
            }
        });
    }

    private String currentUserNick() {
        if (AppVars.Profile != null && AppVars.Profile.UserNick != null) {
            String nick = AppVars.Profile.UserNick.trim();
            if (!nick.isEmpty()) {
                return nick;
            }
        }
        return AppVars.RuntimeAuthUserNick == null ? "" : AppVars.RuntimeAuthUserNick.trim();
    }

    /**
     * Выполняет защищённое действие казны по уже найденной серверной ссылке.
     *
     * Используется для:
     * - `kazna_take` (`get_id=29`, кнопка `Взять из казны`);
     * - `kazna_donate` (`get_id=18`, кнопка `Пожертвовать`);
     * - `kazna_collect_set` (последовательный сбор `KaznaSet`).
     *
     * Важно: здесь не синтезируется UID и не строится новый endpoint. Метод работает
     * только с actionUrl, который сервер отдал в HTML казны. Это сохраняет VCode,
     * cookie/proxy/User-Agent контур `ApiRepository.downloadFile(...)` и не создаёт
     * параллельную реализацию ручных HTML-действий.
     */
    private void performKaznaAction(String actionUrl, String actionName, ApiRepository.ApiCallback<KaznaSnapshot> callback) {
        performKaznaAction(actionUrl, actionName, null, callback);
    }

    private void performKaznaAction(
            String actionUrl,
            String actionName,
            KaznaActionObserver observer,
            ApiRepository.ApiCallback<KaznaSnapshot> callback) {
        String profileNick = currentProfileNickForStorage();
        String requestUrl = prepareProtectedActionUrl(actionUrl, actionName);
        if (TextUtils.isEmpty(requestUrl)) {
            postFailure(callback, "Нет актуального кода защиты для действия: " + actionName);
            return;
        }
        AppLog.i(TAG, TRACE + " action: " + actionName + ", url=" + requestUrl);
        FileLogger.trace(TRACE, "action=" + actionName + ", url=" + requestUrl);

        File rawFile = new File(getKaznaDir(profileNick), KAZNA_RAW_FILE);
        ApiRepository.downloadFile(requestUrl, rawFile, new ApiRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    String html = readWindows1251(rawFile);
                    handleProtectionErrorIfNeeded(html, requestUrl, actionName);
                    SessionManager.getInstance().parseVCodeFromHtml(html, actionName);
                    KaznaSnapshot snapshot = KaznaParser.parse(html, requestUrl);
                    saveSnapshot(snapshot, profileNick);
                    synchronized (snapshotLock) {
                        cachedSnapshot = snapshot;
                        cachedSnapshotProfileKey = storageKey(profileNick);
                    }
                    if (observer != null) {
                        observer.onParsed(html, snapshot);
                    }
                    postSuccess(callback, snapshot);
                } catch (Exception e) {
                    AppLog.e(TAG, TRACE + " action parse failed: " + actionName, e);
                    postFailure(callback, "Ошибка обработки действия казны: " + safeMessage(e));
                }
            }

            @Override
            public void onFailure(String message) {
                AppLog.w(TAG, TRACE + " action failed: " + actionName + ", reason=" + message);
                postFailure(callback, message);
            }
        });
    }

    /**
     * Готовит URL действия с учётом `SessionManager`.
     *
     * Если серверная ссылка уже содержит `vcode`, она считается первичным источником.
     * Если `vcode` в ссылке отсутствует, используем
     * `SessionManager.getValidVCodeForAction(actionName)`. При `null` действие
     * пропускается, потому новый код не должен читать или писать глобальный VCode из `AppVars`.
     */
    private String prepareProtectedActionUrl(String actionUrl, String actionName) {
        String normalized = KaznaParser.normalizeMainUrl(actionUrl);
        String linkVCode = extractQueryParam(normalized, "vcode");
        String sessionVCode = SessionManager.getInstance().getValidVCodeForAction(actionName);
        if (TextUtils.isEmpty(linkVCode)) {
            if (TextUtils.isEmpty(sessionVCode)) {
                AppLog.w(TAG, TRACE + " action skip: no vcode in link and SessionManager returned null, action=" + actionName);
                return "";
            }
            return appendQueryParam(normalized, "vcode", sessionVCode);
        }
        if (TextUtils.isEmpty(sessionVCode)) {
            AppLog.w(TAG, TRACE + " action continues with server link vcode; SessionManager returned null, action=" + actionName);
        }
        return normalized;
    }

    private KaznaSnapshot parseRawFileAndSave(File rawFile, String sourceUrl, String sourceName, String profileNick) throws Exception {
        String html = readWindows1251(rawFile);
        SessionManager.getInstance().parseVCodeFromHtml(html, sourceName);
        KaznaSnapshot snapshot = KaznaParser.parse(html, sourceUrl);
        saveSnapshot(snapshot, profileNick);
        synchronized (snapshotLock) {
            cachedSnapshot = snapshot;
            cachedSnapshotProfileKey = storageKey(profileNick);
        }
        AppLog.i(TAG, TRACE + " parsed: items=" + snapshot.items.size()
                + ", categories=" + snapshot.categories.size()
                + ", currentWca=" + snapshot.currentWca
                + ", source=" + sourceUrl);
        return snapshot;
    }

    private void handleProtectionErrorIfNeeded(String html, String actionUrl, String actionName) {
        if (html == null) {
            return;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        if (lower.contains("неверный код защиты") || lower.contains("неверный код")) {
            String vcode = extractQueryParam(actionUrl, "vcode");
            if (!TextUtils.isEmpty(vcode)) {
                SessionManager.getInstance().onInvalidProtectionCodeError(vcode, actionName);
            }
        }
    }

    /**
     * Сохраняет snapshot в два разных кеша с разными контрактами.
     *
     * - `info/<profile nick>/kazna/kazna.txt` - последний ответ казны любого scope,
     *   нужен для восстановления UI и поиска свежих action-link после действий;
     * - `info/<profile nick>/kazna/kazna_all.txt` - только полный reset-scope,
     *   нужен кнопке категории `Все`.
     */
    private void saveSnapshot(KaznaSnapshot snapshot, String profileNick) throws Exception {
        writeUtf8(getKaznaCacheFile(profileNick), snapshotToJson(snapshot).toString(2));
        if (isAllScopeSnapshot(snapshot)) {
            writeUtf8(getKaznaAllCacheFile(profileNick), snapshotToJson(snapshot).toString(2));
            AppLog.i(TAG, TRACE + " all-cache saved: items=" + snapshot.items.size()
                    + ", source=" + snapshot.sourceUrl);
        }
    }

    private KaznaSnapshot readSnapshotFromDisk() {
        return readSnapshotFromDisk(getKaznaCacheFile());
    }

    private KaznaSnapshot readSnapshotFromDisk(File file) {
        String json = readUtf8(file);
        if (TextUtils.isEmpty(json)) {
            return null;
        }
        try {
            return snapshotFromJson(new JSONObject(json));
        } catch (Exception e) {
            AppLog.w(TAG, TRACE + " cache read failed", e);
            return null;
        }
    }

    /**
     * Проверка полного scope казны. `currentWca` сам по себе не считается достаточным:
     * после категорийного запроса сервер мог держать фильтр в состоянии сессии. Поэтому
     * all-cache разрешён только при source URL с явным `wfo=1`.
     */
    private boolean isAllScopeSnapshot(KaznaSnapshot snapshot) {
        return snapshot != null
                && TextUtils.isEmpty(snapshot.currentWca)
                && !snapshot.categories.isEmpty()
                && KaznaParser.isAllKaznaResetUrl(snapshot.sourceUrl);
    }

    private JSONObject snapshotToJson(KaznaSnapshot snapshot) throws Exception {
        JSONObject root = new JSONObject();
        root.put("generatedAtMs", snapshot.generatedAtMs);
        root.put("sourceUrl", snapshot.sourceUrl);
        root.put("currentWca", snapshot.currentWca);
        root.put("currentCategoryTitle", snapshot.currentCategoryTitle);

        JSONArray categories = new JSONArray();
        for (KaznaCategory category : snapshot.categories) {
            JSONObject item = new JSONObject();
            item.put("wca", category.wca);
            item.put("title", category.title);
            item.put("iconUrl", category.iconUrl);
            item.put("href", category.href);
            categories.put(item);
        }
        root.put("categories", categories);

        JSONArray items = new JSONArray();
        for (KaznaItem kaznaItem : snapshot.items) {
            JSONObject item = new JSONObject();
            item.put("uid", kaznaItem.uid);
            item.put("rowIndex", kaznaItem.rowIndex);
            item.put("displayName", kaznaItem.displayName);
            item.put("baseName", kaznaItem.baseName);
            item.put("owner", kaznaItem.owner);
            item.put("durabilityText", kaznaItem.durabilityText);
            item.put("currentDurability", kaznaItem.currentDurability);
            item.put("maxDurability", kaznaItem.maxDurability);
            item.put("status", kaznaItem.status);
            item.put("free", kaznaItem.free);
            item.put("artifactCoefficient", kaznaItem.artifactCoefficient);
            item.put("takeUrl", kaznaItem.takeUrl);
            item.put("donateUrl", kaznaItem.donateUrl);
            item.put("sourceUrl", kaznaItem.sourceUrl);
            item.put("categoryWca", kaznaItem.categoryWca);
            item.put("categoryTitle", kaznaItem.categoryTitle);
            item.put("rowHtml", kaznaItem.rowHtml);
            items.put(item);
        }
        root.put("items", items);
        return root;
    }

    private KaznaSnapshot snapshotFromJson(JSONObject root) {
        ArrayList<KaznaCategory> categories = new ArrayList<>();
        JSONArray categoryArray = root.optJSONArray("categories");
        if (categoryArray != null) {
            for (int index = 0; index < categoryArray.length(); index++) {
                JSONObject item = categoryArray.optJSONObject(index);
                if (item != null) {
                    categories.add(new KaznaCategory(
                            item.optString("wca", ""),
                            item.optString("title", ""),
                            item.optString("iconUrl", ""),
                            item.optString("href", "")));
                }
            }
        }

        ArrayList<KaznaItem> items = new ArrayList<>();
        JSONArray itemArray = root.optJSONArray("items");
        if (itemArray != null) {
            for (int index = 0; index < itemArray.length(); index++) {
                JSONObject item = itemArray.optJSONObject(index);
                if (item != null) {
                    items.add(new KaznaItem(
                            item.optString("uid", ""),
                            item.optInt("rowIndex", -1),
                            item.optString("displayName", ""),
                            item.optString("baseName", ""),
                            item.optString("owner", ""),
                            item.optString("durabilityText", ""),
                            item.optInt("currentDurability", -1),
                            item.optInt("maxDurability", -1),
                            item.optString("status", ""),
                            item.optBoolean("free", false),
                            item.optString("artifactCoefficient", ""),
                            item.optString("takeUrl", ""),
                            item.optString("donateUrl", ""),
                            item.optString("sourceUrl", ""),
                            item.optString("categoryWca", ""),
                            item.optString("categoryTitle", ""),
                            item.optString("rowHtml", "")));
                }
            }
        }

        return new KaznaSnapshot(
                root.optLong("generatedAtMs", 0L),
                root.optString("sourceUrl", ""),
                root.optString("currentWca", ""),
                root.optString("currentCategoryTitle", ""),
                categories,
                items);
    }

    private void saveSets(List<KaznaSet> sets) {
        try {
            JSONArray array = new JSONArray();
            for (KaznaSet set : sets) {
                JSONObject item = new JSONObject();
                item.put("name", set.name);
                JSONArray uids = new JSONArray();
                for (String uid : set.itemUids) {
                    uids.put(uid);
                }
                item.put("itemUids", uids);
                array.put(item);
            }
            writeUtf8(getKaznaSetsFile(), array.toString(2));
        } catch (Exception e) {
            AppLog.e(TAG, TRACE + " save sets failed", e);
        }
    }

    private File getKaznaDir() {
        return getKaznaDir(currentProfileNickForStorage());
    }

    private File getKaznaDir(String profileNick) {
        File dir = GameInfoStorage.resolveUserInfoSubDir(profileNick, "kazna");
        if (dir == null) {
            File infoDir = new File(appContext.getFilesDir(), "info");
            dir = new File(new File(infoDir, GameInfoStorage.sanitizeProfileName(profileNick)), "kazna");
        }
        if (!dir.exists() && !dir.mkdirs()) {
            AppLog.w(TAG, TRACE + " failed to create profile kazna dir: " + dir.getAbsolutePath());
        }
        return dir;
    }

    private String currentProfileNickForStorage() {
        String profileNick = currentUserNick();
        if (!TextUtils.isEmpty(profileNick)) {
            return profileNick;
        }
        String runtimeNick = AppVars.RuntimeAuthUserNick == null ? "" : AppVars.RuntimeAuthUserNick.trim();
        return TextUtils.isEmpty(runtimeNick) ? "profile" : runtimeNick;
    }

    private String currentProfileStorageKey() {
        return storageKey(currentProfileNickForStorage());
    }

    private String storageKey(String profileNick) {
        return GameInfoStorage.sanitizeProfileName(profileNick);
    }

    private String readWindows1251(File file) throws Exception {
        byte[] bytes = readAllBytes(file);
        return Russian.getString(bytes);
    }

    private byte[] readAllBytes(File file) throws Exception {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private String readUtf8(File file) {
        if (file == null || !file.exists()) {
            return "";
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        } catch (Exception e) {
            AppLog.w(TAG, TRACE + " read utf8 failed: " + file.getAbsolutePath(), e);
            return "";
        }
    }

    private void writeUtf8(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create directory: " + parent.getAbsolutePath());
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content == null ? "" : content);
        }
    }

    private String appendQueryParam(String url, String key, String value) {
        String delimiter = url.contains("?") ? "&" : "?";
        return url + delimiter + key + "=" + value;
    }

    private String extractQueryParam(String url, String key) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(key)) {
            return "";
        }
        String[] parts = url.replace("&amp;", "&").split("[?&]");
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (key.equalsIgnoreCase(part.substring(0, eq))) {
                return part.substring(eq + 1);
            }
        }
        return "";
    }

    private void postSuccess(ApiRepository.ApiCallback<KaznaSnapshot> callback, KaznaSnapshot snapshot) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onSuccess(snapshot));
    }

    private void postStringSuccess(ApiRepository.ApiCallback<String> callback, String message) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onSuccess(message));
    }

    private void postFailure(ApiRepository.ApiCallback<?> callback, String message) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onFailure(message == null ? "Unknown error" : message));
    }

    private String safeMessage(Throwable error) {
        return error == null || error.getMessage() == null ? "unknown" : error.getMessage();
    }
}

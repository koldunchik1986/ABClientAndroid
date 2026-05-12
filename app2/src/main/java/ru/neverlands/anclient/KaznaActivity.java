package ru.neverlands.anclient;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ru.neverlands.anclient.adapter.KaznaItemAdapter;
import ru.neverlands.anclient.adapter.KaznaSetAdapter;
import ru.neverlands.anclient.manager.KaznaManager;
import ru.neverlands.anclient.model.KaznaCategory;
import ru.neverlands.anclient.model.KaznaItem;
import ru.neverlands.anclient.model.KaznaItemDetails;
import ru.neverlands.anclient.model.KaznaSet;
import ru.neverlands.anclient.model.KaznaSnapshot;
import ru.neverlands.anclient.parser.KaznaParser;
import ru.neverlands.anclient.repository.ApiRepository;
import ru.neverlands.anclient.utils.AppLog;

/**
 * Экран `Казна` вместо старой навигации в `Инвентарь`.
 *
 * Что было добавлено в рамках портирования и последующих исправлений:
 * - `MainActivity.onNavigationItemSelected(...)` открывает именно этот экран из
 *   пункта `R.id.nav_inventory`, поэтому `KaznaActivity` является входной точкой
 *   нового Android-модуля казны;
 * - layout `activity_kazna.xml` задаёт `TabLayout`, horizontal category bar,
 *   статус, empty-state и `RecyclerView`, а rows рендерятся через
 *   `KaznaItemAdapter` и `KaznaSetAdapter`;
 * - `KaznaManager` остаётся единственным источником сетевых refresh/action
 *   операций, profile-scoped кешей `info/<profile nick>/kazna/*.txt/*.html/*.json`
 *   и доступа к UID-деталям из `KaznaItemDetailsCache`;
 * - `KaznaParser` и `KaznaSnapshot` отделяют HTML-разбор от UI: Activity хранит
 *   только выбранную вкладку, выбранную серверную категорию и актуальный snapshot;
 * - `KaznaItemDetails` из inventory-кеша добавляются в adapter отдельно, чтобы
 *   картинка/свойства отображались без нового HTTP-запроса к инвентарю.
 *
 * UI decision points:
 * - вкладки фильтруют уже распарсенный snapshot, а не создают новые URL;
 * - category bar делает серверный запрос `wca=...`, потому что категория в
 *   исходном HTML не привязана к каждой строке предмета;
 * - category `Все` использует отдельный all-snapshot и затем обновляет базовый URL,
 *   чтобы вкладки `Арты/Рары/Обычные` показывали полный список своего типа;
 * - long-click добавляет UID предмета в локальный комплект: сначала серверный UID
 *   строки, затем безопасный fallback из уже существующего inventory-кеша.
 */
public class KaznaActivity extends AppCompatActivity
        implements KaznaItemAdapter.Listener, KaznaSetAdapter.Listener {

    private static final String TAG = "KaznaActivity";
    private static final int TAB_ALL = 0;
    private static final int TAB_ARTS = 1;
    private static final int TAB_RARES = 2;
    private static final int TAB_ORDINARY = 3;
    private static final int TAB_SETS = 4;

    private KaznaManager manager;
    private TabLayout tabLayout;
    private HorizontalScrollView categoriesScroll;
    private LinearLayout categoriesLayout;
    private LinearLayout setActionsLayout;
    private MaterialButton refreshButton;
    private MaterialButton addSetButton;
    private MaterialButton deleteSetButton;
    private TextView statusView;
    private TextView emptyView;
    private RecyclerView recyclerView;
    private KaznaItemAdapter itemAdapter;
    private KaznaSetAdapter setAdapter;

    /**
     * Snapshot, который сейчас отрисован на экране. Это может быть полный список,
     * отдельная серверная категория `wca=...` или результат действия `get_id=29/18`.
     */
    private KaznaSnapshot currentSnapshot;

    /**
     * Отдельный полный snapshot без `wca`. Он нужен для кнопки категории `Все`:
     * после выбора конкретной серверной категории список вкладки `Арты/Рары/Обычные`
     * должен вернуться к полному набору своего типа, а не к последней `wca`.
     */
    private KaznaSnapshot allItemsSnapshot;

    /** Выбранная серверная категория казны. `null` означает кнопку `Все`. */
    private KaznaCategory selectedCategory;

    /** UID-свойства из `info/<profile nick>/kazna/uids.txt`, которые adapter использует только для UI. */
    private Map<String, KaznaItemDetails> itemDetailsByUid = new LinkedHashMap<>();

    /**
     * Базовая часть статуса без счётчика. Количество добавляется в `renderCurrentTab()`
     * после локальной фильтрации, чтобы вкладки `Арты/Рары/Обычные` показывали свой
     * filtered-count, а не общий `snapshot.items.size()`.
     */
    private String statusBaseText = "";
    private int selectedTab = TAB_ALL;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kazna);

        manager = KaznaManager.getInstance(this);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Казна");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tabLayout = findViewById(R.id.tabLayoutKazna);
        categoriesScroll = findViewById(R.id.scrollKaznaCategories);
        categoriesLayout = findViewById(R.id.layoutKaznaCategories);
        setActionsLayout = findViewById(R.id.layoutKaznaSetActions);
        refreshButton = findViewById(R.id.btnRefreshKazna);
        addSetButton = findViewById(R.id.btnKaznaAddSet);
        deleteSetButton = findViewById(R.id.btnKaznaDeleteSet);
        statusView = findViewById(R.id.tvKaznaStatus);
        emptyView = findViewById(R.id.tvKaznaEmpty);
        recyclerView = findViewById(R.id.recyclerKazna);

        itemAdapter = new KaznaItemAdapter(this);
        setAdapter = new KaznaSetAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(itemAdapter);

        initTabs();
        refreshButton.setOnClickListener(v -> refreshKazna(true));
        addSetButton.setOnClickListener(v -> showCreateSetDialog(null, ""));
        deleteSetButton.setOnClickListener(v -> showDeleteSetDialog());
        reloadItemDetailsCache();
        allItemsSnapshot = manager.getCachedAllSnapshot();

        KaznaSnapshot cached = manager.getCachedSnapshot();
        KaznaSnapshot initialSnapshot = chooseInitialSnapshot(cached, allItemsSnapshot);
        if (initialSnapshot != null) {
            applySnapshot(initialSnapshot, "Кеш: " + formatTime(initialSnapshot.generatedAtMs));
        } else {
            renderCurrentTab();
        }
        refreshKazna(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadItemDetailsCache();
        renderCurrentTab();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTakeClicked(KaznaItem item) {
        setBusy("Берём из казны: " + item.displayName);
        manager.takeItem(item, new ApiRepository.ApiCallback<KaznaSnapshot>() {
            @Override
            public void onSuccess(KaznaSnapshot result) {
                setIdle();
                applySnapshot(result, "Вещь взята. Обновлено: " + formatTime(result.generatedAtMs));
                Toast.makeText(KaznaActivity.this, "Вещь взята из казны", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String message) {
                setIdle();
                showError("Не удалось взять вещь", message);
            }
        });
    }

    @Override
    public void onDonateClicked(KaznaItem item) {
        setBusy("Жертвуем в казну: " + item.displayName);
        manager.donateItem(item, new ApiRepository.ApiCallback<KaznaSnapshot>() {
            @Override
            public void onSuccess(KaznaSnapshot result) {
                setIdle();
                applySnapshot(result, "Пожертвовано. Обновлено: " + formatTime(result.generatedAtMs));
                Toast.makeText(KaznaActivity.this, "Вещь пожертвована", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String message) {
                setIdle();
                showError("Не удалось пожертвовать вещь", message);
            }
        });
    }

    @Override
    public void onAddToSetRequested(KaznaItem item) {
        String resolvedUid = KaznaItemAdapter.resolveActionUid(item, itemDetailsByUid);
        if (TextUtils.isEmpty(resolvedUid)) {
            Toast.makeText(this, "UID не найден ни в строке, ни в кеше инвентаря", Toast.LENGTH_SHORT).show();
            return;
        }
        if (item == null || !item.hasUid()) {
            AppLog.i(TAG, "KAZNA_TRACE add-to-set uses cached uid=" + resolvedUid
                    + ", item=" + (item == null ? "" : item.displayName));
        }
        showAddToSetDialog(item, resolvedUid);
    }

    @Override
    public void onCollectClicked(KaznaSet set) {
        setBusy("Собираем комплект: " + set.name);
        manager.collectSet(set, new ApiRepository.ApiCallback<KaznaSnapshot>() {
            @Override
            public void onSuccess(KaznaSnapshot result) {
                setIdle();
                applySnapshot(result, "Комплект обработан: " + set.name);
                Toast.makeText(KaznaActivity.this, "Сбор комплекта завершён", Toast.LENGTH_SHORT).show();
                selectedCategory = null;
                refreshKazna(true);
            }

            @Override
            public void onFailure(String message) {
                setIdle();
                showError("Не удалось собрать комплект", message);
            }
        });
    }

    @Override
    public void onWearClicked(KaznaSet set) {
        setBusy("Надеваем комплект: " + set.name);
        manager.wearSet(set, new ApiRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                setIdle();
                Toast.makeText(KaznaActivity.this, result, Toast.LENGTH_SHORT).show();
                statusView.setText(result);
            }

            @Override
            public void onFailure(String message) {
                setIdle();
                showError("Не удалось надеть комплект", message);
            }
        });
    }

    @Override
    public void onDeleteClicked(KaznaSet set) {
        manager.deleteSet(set);
        renderCurrentTab();
        Toast.makeText(this, "Комплект удалён", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRemoveItemClicked(KaznaSet set, String uid) {
        if (manager.removeItemFromSet(set, uid)) {
            renderCurrentTab();
            Toast.makeText(this, "Предмет удалён из комплекта", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Не удалось удалить предмет", Toast.LENGTH_SHORT).show();
        }
    }

    private void initTabs() {
        String[] titles = {"Все", "Арты", "Рары", "Обычные", "Комплекты"};
        for (String title : titles) {
            tabLayout.addTab(tabLayout.newTab().setText(title));
        }
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedTab = tab.getPosition();
                renderCurrentTab();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                renderCurrentTab();
            }
        });
    }

    private void refreshKazna(boolean manual) {
        setBusy(manual ? "Обновление казны..." : "Загрузка казны...");
        manager.refreshKazna(selectedCategory, new ApiRepository.ApiCallback<KaznaSnapshot>() {
            @Override
            public void onSuccess(KaznaSnapshot result) {
                setIdle();
                AppLog.i(TAG, "KAZNA_TRACE refresh success: tab=" + currentTabTitle()
                        + ", selectedCategory=" + selectedCategoryLabel()
                        + ", source=" + result.sourceUrl
                        + ", currentWca=" + result.currentWca
                        + ", items=" + result.items.size());
                applySnapshot(result, "Обновлено: " + formatTime(result.generatedAtMs));
            }

            @Override
            public void onFailure(String message) {
                setIdle();
                KaznaSnapshot cached = manager.getCachedSnapshot();
                if (cached != null) {
                    applySnapshot(cached, "Ошибка обновления, показан кеш: " + safe(message));
                } else {
                    statusView.setText("Ошибка обновления: " + safe(message));
                    renderCurrentTab();
                }
            }
        });
    }

    /**
     * Центральная точка применения нового snapshot к UI.
     *
     * Зависимости:
     * - `KaznaManager.refreshKazna(...)`, `takeItem(...)`, `donateItem(...)` и
     *   `collectSet(...)` возвращают уже распарсенный `KaznaSnapshot`;
     * - `isAllSnapshot(...)` допускает в `allItemsSnapshot` только reset-ответ
     *   `wfo=1` без `wca`, чтобы не закешировать последнюю категорию как полный список;
     * - `renderCategories(...)` и `renderCurrentTab()` всегда вызываются вместе,
     *   чтобы выбранная категория и локальная вкладка не расходились визуально.
     */
    private void applySnapshot(KaznaSnapshot snapshot, String status) {
        currentSnapshot = snapshot;
        if (isAllSnapshot(snapshot)) {
            allItemsSnapshot = snapshot;
        }
        statusBaseText = safe(status);
        renderCategories(snapshot == null ? null : snapshot.categories);
        renderCurrentTab();
    }

    private void renderCategories(@Nullable List<KaznaCategory> categories) {
        categoriesLayout.removeAllViews();
        categoriesLayout.addView(createCategoryView(null));
        if (categories == null) {
            return;
        }
        for (KaznaCategory category : categories) {
            categoriesLayout.addView(createCategoryView(category));
        }
    }

    /**
     * Создаёт кнопку серверной категории. `category == null` - локальная кнопка
     * `Все`, которая сбрасывает `selectedCategory` и запускает refresh базового
     * URL `KaznaParser.BASE_KAZNA_URL` через `KaznaManager`.
     */
    private View createCategoryView(@Nullable KaznaCategory category) {
        boolean selected = (category == null && selectedCategory == null)
                || (category != null && selectedCategory != null && category.wca.equals(selectedCategory.wca));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(6), 0);
        root.setLayoutParams(lp);
        root.setBackgroundColor(selected
                ? ContextCompat.getColor(this, R.color.an_autoboi_group_selected_bg)
                : android.graphics.Color.TRANSPARENT);

        if (category != null && !TextUtils.isEmpty(category.iconUrl)) {
            ImageView icon = new ImageView(this);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(36), dp(42));
            icon.setLayoutParams(iconLp);
            icon.setAdjustViewBounds(true);
            Glide.with(this).load(category.iconUrl).into(icon);
            root.addView(icon);
        }

        TextView text = new TextView(this);
        text.setGravity(Gravity.CENTER);
        text.setText(category == null ? "Все" : category.title);
        text.setTextColor(ContextCompat.getColor(this, R.color.colorText));
        text.setTextSize(11f);
        text.setMaxLines(2);
        root.addView(text);

        root.setOnClickListener(v -> {
            selectedCategory = category;
            AppLog.i(TAG, "KAZNA_TRACE category click: tab=" + currentTabTitle()
                    + ", selectedCategory=" + selectedCategoryLabel()
                    + ", cachedAllItems=" + (allItemsSnapshot == null ? 0 : allItemsSnapshot.items.size()));
            if (category == null) {
                renderAllCategoryFromCache();
            } else {
                renderCategories(currentSnapshot == null ? null : currentSnapshot.categories);
            }
            refreshKazna(true);
        });
        return root;
    }

    /**
     * Выбирает snapshot для первого рендера экрана.
     *
     * Если профильный `kazna.txt` был перезаписан конкретной `wca`-категорией,
     * экран берёт профильный `kazna_all.txt`, чтобы вкладки `Все/Арты/Рары/Обычные`
     * стартовали с полного списка, а не с последней серверной категории.
     */
    private KaznaSnapshot chooseInitialSnapshot(@Nullable KaznaSnapshot cached, @Nullable KaznaSnapshot cachedAll) {
        if (isAllSnapshot(cached)) {
            return cached;
        }
        if (cachedAll != null) {
            return cachedAll;
        }
        return cached;
    }

    /**
     * Мгновенно возвращает UI к последнему валидному all-cache до сетевого ответа.
     *
     * Это не заменяет refresh. Метод нужен только для UX: после нажатия `Все` текущая
     * вкладка сразу показывает полный локальный набор своего типа, а затем
     * `refreshKazna(true)` обновляет данные с сервера и заново сохраняет кеши.
     */
    private void renderAllCategoryFromCache() {
        if (allItemsSnapshot != null) {
            currentSnapshot = allItemsSnapshot;
            statusBaseText = "Все категории из кеша: " + formatTime(allItemsSnapshot.generatedAtMs);
            renderCategories(allItemsSnapshot.categories);
            renderCurrentTab();
            return;
        }
        renderCategories(currentSnapshot == null ? null : currentSnapshot.categories);
        renderCurrentTab();
    }

    /**
     * Выбирает adapter и данные для текущей вкладки.
     *
     * Наименования соответствуют UI и TODO2:
     * - `TAB_ALL` - все строки текущего snapshot;
     * - `TAB_ARTS` - `KaznaItem.hasArtifactCoefficient()`;
     * - `TAB_RARES` - `KaznaItem.isRare()` без коэффициента и max durability >= 300;
     * - `TAB_ORDINARY` - `KaznaItem.isOrdinary()` без коэффициента и max durability < 300;
     * - `TAB_SETS` - локальные `KaznaSet` из `info/<profile nick>/kazna/kazna_sets.json`.
     *
     * Счётчик статуса намеренно считает только предметы казны: строки личного
     * инвентаря с action `Пожертвовать` остаются в списке, но не входят в число.
     */
    private void renderCurrentTab() {
        updateSetsChromeVisibility();
        // `uids.txt` может обновиться существующим inventory/go=inf контуром,
        // пока экран открыт; перечитываем его перед передачей context в adapter.
        reloadItemDetailsCache();
        if (selectedTab == TAB_SETS) {
            recyclerView.setAdapter(setAdapter);
            List<KaznaSet> sets = manager == null ? new ArrayList<>() : manager.loadSets();
            AppLog.d(TAG, "KAZNA_TRACE sets context: details=" + itemDetailsByUid.size()
                    + ", snapshotItems=" + (currentSnapshot == null ? 0 : currentSnapshot.items.size())
                    + ", source=" + (currentSnapshot == null ? "" : currentSnapshot.sourceUrl));
            setAdapter.submitContext(itemDetailsByUid, currentSnapshot);
            setAdapter.submitList(sets);
            updateEmptyState(sets.isEmpty(), "Комплектов нет. Долгий тап по вещи добавит её в комплект.");
            updateStatusCount(sets.size(), true);
            return;
        }

        recyclerView.setAdapter(itemAdapter);
        List<KaznaItem> filtered = filterItems(currentSnapshot == null ? null : currentSnapshot.items);
        itemAdapter.submitDetails(itemDetailsByUid);
        itemAdapter.submitList(filtered);
        updateEmptyState(filtered.isEmpty(), "В этой вкладке нет предметов.");
        updateStatusCount(countKaznaItems(filtered), false);
    }

    /** Вкладка `Комплекты` не использует серверные category-фильтры казны. */
    private void updateSetsChromeVisibility() {
        boolean setsTab = selectedTab == TAB_SETS;
        if (categoriesScroll != null) {
            categoriesScroll.setVisibility(setsTab ? View.GONE : View.VISIBLE);
        }
        if (setActionsLayout != null) {
            setActionsLayout.setVisibility(setsTab ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Дописывает в статус количество именно текущей вкладки.
     *
     * Раньше refresh-status использовал `KaznaSnapshot.items.size()`, из-за чего во
     * вкладках `Арты`, `Рары` и `Обычные` показывалось общее количество строк
     * snapshot. Теперь счётчик берётся после `filterItems(...)`, но не включает
     * строки личного инвентаря с `Пожертвовать`, либо после загрузки локальных `KaznaSet`.
     */
    private void updateStatusCount(int visibleCount, boolean setsTab) {
        if (isRefreshBusy()) {
            return;
        }
        StringBuilder status = new StringBuilder(statusBaseText);
        if (status.length() > 0) {
            status.append(" | ");
        }
        status.append("Вкладка: ").append(currentTabTitle()).append(" | ");
        status.append(setsTab ? "Комплектов: " : "Предметов в казне: ").append(visibleCount);
        statusView.setText(status.toString());
    }

    /** Возвращает количество строк, которые лежат в казне, а не в личном инвентаре. */
    private int countKaznaItems(@Nullable List<KaznaItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (KaznaItem item : items) {
            if (item != null && !item.hasDonateAction()) {
                count++;
            }
        }
        return count;
    }

    private boolean isRefreshBusy() {
        return refreshButton != null && !refreshButton.isEnabled();
    }

    private void reloadItemDetailsCache() {
        itemDetailsByUid = manager == null ? new LinkedHashMap<>() : manager.loadItemDetailsByUid();
    }

    /** См. `renderCurrentTab()` - здесь хранится только предметная часть фильтрации. */
    private List<KaznaItem> filterItems(@Nullable List<KaznaItem> source) {
        ArrayList<KaznaItem> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (KaznaItem item : source) {
            if (selectedTab == TAB_ALL
                    || (selectedTab == TAB_ARTS && item.hasArtifactCoefficient())
                    || (selectedTab == TAB_RARES && item.isRare())
                    || (selectedTab == TAB_ORDINARY && item.isOrdinary())) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Валидирует, что snapshot действительно является полным списком казны.
     *
     * Проверка `currentWca` недостаточна: сервер мог вернуть категорийный список на
     * URL без `wca`, если предыдущий фильтр остался в серверном состоянии. Поэтому
     * обязательно проверяем reset-параметр `wfo=1` через `KaznaParser.isAllKaznaResetUrl(...)`.
     */
    private boolean isAllSnapshot(@Nullable KaznaSnapshot snapshot) {
        return snapshot != null
                && TextUtils.isEmpty(snapshot.currentWca)
                && !snapshot.categories.isEmpty()
                && KaznaParser.isAllKaznaResetUrl(snapshot.sourceUrl);
    }

    private String selectedCategoryLabel() {
        if (selectedCategory == null) {
            return "all";
        }
        return "wca=" + selectedCategory.wca + " " + selectedCategory.title;
    }

    private String currentTabTitle() {
        TabLayout.Tab tab = tabLayout == null ? null : tabLayout.getTabAt(selectedTab);
        CharSequence text = tab == null ? null : tab.getText();
        return text == null ? "" : text.toString();
    }

    private void showAddToSetDialog(KaznaItem item, String resolvedUid) {
        List<KaznaSet> sets = manager.loadSets();
        ArrayList<String> choices = new ArrayList<>();
        choices.add("Новый комплект");
        for (KaznaSet set : sets) {
            choices.add(set.name);
        }
        new AlertDialog.Builder(this)
                .setTitle("Добавить в комплект")
                .setItems(choices.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        showCreateSetDialog(item, resolvedUid);
                    } else {
                        addItemToSet(choices.get(which), resolvedUid);
                    }
                })
                .show();
    }

    private void showCreateSetDialog(@Nullable KaznaItem item, String resolvedUid) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Название комплекта");
        int pad = dp(16);
        input.setPadding(pad, pad / 2, pad, pad / 2);
        new AlertDialog.Builder(this)
                .setTitle("Новый комплект")
                .setView(input)
                .setPositiveButton("Добавить", (dialog, which) -> {
                    if (item == null) {
                        createEmptySet(input.getText().toString());
                    } else {
                        addItemToSet(input.getText().toString(), resolvedUid);
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void createEmptySet(String setName) {
        if (TextUtils.isEmpty(setName)) {
            Toast.makeText(this, "Введите название комплекта", Toast.LENGTH_SHORT).show();
            return;
        }
        if (manager.addSet(setName)) {
            renderCurrentTab();
            Toast.makeText(this, "Комплект добавлен", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Не удалось добавить комплект", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeleteSetDialog() {
        List<KaznaSet> sets = manager.loadSets();
        if (sets.isEmpty()) {
            Toast.makeText(this, "Нет комплектов для удаления", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> choices = new ArrayList<>();
        for (KaznaSet set : sets) {
            choices.add(set.name);
        }
        new AlertDialog.Builder(this)
                .setTitle("Удалить комплект")
                .setItems(choices.toArray(new String[0]), (dialog, which) -> {
                    manager.deleteSet(sets.get(which));
                    renderCurrentTab();
                    Toast.makeText(this, "Комплект удалён", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void addItemToSet(String setName, String resolvedUid) {
        if (TextUtils.isEmpty(setName)) {
            Toast.makeText(this, "Введите название комплекта", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(resolvedUid)) {
            Toast.makeText(this, "UID предмета не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean saved = manager.addItemToSet(setName, resolvedUid);
        if (saved) {
            Toast.makeText(this, "Добавлено в комплект", Toast.LENGTH_SHORT).show();
            if (selectedTab == TAB_SETS) {
                renderCurrentTab();
            }
        } else {
            Toast.makeText(this, "Не удалось добавить вещь", Toast.LENGTH_SHORT).show();
        }
    }

    private void setBusy(String text) {
        refreshButton.setEnabled(false);
        statusView.setText(text);
        AppLog.d(TAG, "KAZNA_TRACE busy: " + text);
    }

    private void setIdle() {
        refreshButton.setEnabled(true);
    }

    private void updateEmptyState(boolean empty, String message) {
        emptyView.setText(message);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showError(String title, String message) {
        String text = title + ": " + safe(message);
        statusView.setText(text);
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
        AppLog.w(TAG, "KAZNA_TRACE error: " + text);
    }

    private String formatTime(long timeMs) {
        if (timeMs <= 0L) {
            return "-";
        }
        return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(new Date(timeMs));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

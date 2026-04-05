package ru.neverlands.abclient.ui;


import ru.neverlands.abclient.utils.AppLog;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.tabs.TabLayout;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.abclient.R;
import ru.neverlands.abclient.bridge.WebAppInterface;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.ExtMap;
import ru.neverlands.abclient.utils.FileLogger;

/**
 * Навигатор клиента.
 *
 * Назначение:
 * - отображает диалог "Навигатор" с вкладками;
 * - запускает/останавливает автомаршрут через AutoFunctionsManager;
 * - хранит редактируемые списки клеток в профиле;
 * - строит индекс карты (боты/травы/рыбалка) из map.xml для вкладок с автозаполнением.
 *
 * Ключевые зависимости:
 * - {@link AutoFunctionsManager}: запуск и остановка авто-переходов;
 * - {@link AppVars}: текущее состояние автомаршрута и профиль пользователя;
 * - {@link ExtMap}: справочник клеток и телепортов для валидации/подсказок;
 * - {@link TabLayout}: вкладки "Локации / Боты / Травы / Рыбалка".
 */
public class Navigator {
    private static final int TAB_LOCATIONS = 0;
    private static final int TAB_BOTS = 1;
    private static final int TAB_HERBS = 2;
    private static final int TAB_FISH = 3;

    private static final int NAV_CATEGORY_FAVORITES = 1;
    private static final int NAV_CATEGORY_CITY_VILLAGE = 2;
    private static final int NAV_CATEGORY_CITY_FORPOST = 3;
    private static final int NAV_CATEGORY_CITY_OKTAL = 4;
    private static final int NAV_CATEGORY_OBJECTS = 5;
    private static final int NAV_CATEGORY_TELEPORTS = 6;
    private static final int NAV_CATEGORY_CITIES_ALL = 7;
    private static final String NAV_MINIMAP_TRACE_PREFIX = "NAV_MINIMAP_TRACE";

    private static final String CITY_SUBCATEGORY_ENTRY_DELIMITER = ";";
    private static final String CITY_SUBCATEGORY_VALUE_DELIMITER = "|";
    private static final Pattern CELL_PATTERN = Pattern.compile("(\\d+-\\d+)");

    private final Context context;
    private final AutoFunctionsManager autoFunctionsManager;
    private final Runnable onStateChanged;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private NavigatorMapIndex mapIndexCache;
    private OnCellPicked navigatorMiniMapSelectionListener;
    private android.widget.AutoCompleteTextView navigatorDestInput;
    private WebView navigatorMiniMapView;
    private boolean navigatorMiniMapReady;
    private int navigatorMiniMapCenterX;
    private int navigatorMiniMapCenterY;

    /**
     * Универсальный callback выбора/удаления клетки.
     *
     * Зависимости:
     * - используется в UI-обработчиках добавления/удаления;
     * - позволяет переиспользовать один рендерер строк для разных категорий.
     */
    private interface OnCellPicked {
        void onCellPicked(String cellNum);
    }

    /**
     * Callback построения контента внутри раскрываемой категории.
     */
    private interface SectionContentBuilder {
        void build(LinearLayout container);
    }

    /**
     * Локальная модель подкатегории в секции "Города".
     *
     * Зависимости:
     * - сериализуется в строку профиля через encodeCitySubcategories(...);
     * - восстанавливается из профиля через parseCitySubcategories(...);
     * - используется при рендере и редактировании клеток подкатегории.
     */
    private static final class CitySubcategory {
        final String name;
        final String[] cells;

        CitySubcategory(String name, String[] cells) {
            this.name = name;
            this.cells = cells;
        }
    }

    /**
     * Кэш индексированных данных карты.
     *
     * Структуры:
     * - botGroups: бот -> диапазон уровней -> клетки;
     * - herbGroups: номер группы трав -> клетки;
     * - fishGroups: тип рыбалки/водоема -> клетки.
     *
     * Источник данных: runtime map.xml (метод buildNavigatorMapIndex()).
     */
    private static final class NavigatorMapIndex {
        final Map<String, Map<String, LinkedHashSet<String>>> botGroups = new LinkedHashMap<>();
        final Map<String, LinkedHashSet<String>> herbGroups = new LinkedHashMap<>();
        final Map<String, LinkedHashSet<String>> fishGroups = new LinkedHashMap<>();
    }

    /**
     * JS bridge для миникарты навигатора.
     *
     * Принцип:
     * - использует те же mapnav-контракты (`IsCellExists`, `GenMoveLink`, `CellAltText`, `CellDivText`, `MoveTo`);
     * - `MoveTo` в этом bridge не запускает AutoMoving, а возвращает выбранную клетку в Navigator;
     * - рендер текста клетки делегируется в общий контур `WebAppInterface.CellDivTextNavigatorMini(...)`.
     */
    private final class NavigatorMiniMapBridge {
        private final WebAppInterface sharedMapBridge;

        NavigatorMiniMapBridge() {
            this.sharedMapBridge = new WebAppInterface(context);
        }

        @JavascriptInterface
        public int GetHalfMapWidth() {
            int mapMiniWidth = (AppVars.Profile != null) ? AppVars.Profile.MapMiniWidth : 5;
            mapMiniWidth = Math.max(3, mapMiniWidth);
            if ((mapMiniWidth & 1) == 0) mapMiniWidth -= 1;
            return Math.max(1, (mapMiniWidth - 1) / 2);
        }

        @JavascriptInterface
        public int GetHalfMapHeight() {
            int mapMiniHeight = (AppVars.Profile != null) ? AppVars.Profile.MapMiniHeight : 3;
            mapMiniHeight = Math.max(3, mapMiniHeight);
            if ((mapMiniHeight & 1) == 0) mapMiniHeight -= 1;
            return Math.max(1, (mapMiniHeight - 1) / 2);
        }

        @JavascriptInterface
        public int GetMapScale() {
            int scale = (AppVars.Profile != null) ? AppVars.Profile.MapMiniScale : 100;
            if (scale < 50) scale = 50;
            if (scale > 150) scale = 150;
            return scale;
        }

        @JavascriptInterface
        public boolean IsCellExists(int x, int y) {
            ExtMap.init(context);
            String pos = ExtMap.makePosition(x, y);
            ru.neverlands.abclient.model.Position p = ExtMap.Location.get(pos);
            return p != null && p.RegNum != null && ExtMap.Cells.containsKey(p.RegNum);
        }

        @JavascriptInterface
        public String GenMoveLink(int x, int y) {
            ExtMap.init(context);
            String pos = ExtMap.makePosition(x, y);
            ru.neverlands.abclient.model.Position p = ExtMap.Location.get(pos);
            if (p == null || p.RegNum == null) {
                return "";
            }
            return p.RegNum;
        }

        @JavascriptInterface
        public String CellAltText(int x, int y, int scale) {
            return sharedMapBridge.CellAltText(x, y, scale);
        }

        @JavascriptInterface
        public String CellDivText(int x, int y, int scale, String link, boolean showmove, boolean isframe) {
            return sharedMapBridge.CellDivTextNavigatorMini(x, y, scale, showmove, isframe);
        }

        @JavascriptInterface
        public void MoveTo(String dest) {
            runOnUiThreadSafe(() -> {
                String resolved = resolveCellNumber(dest);
                if (resolved.isEmpty()) {
                    traceNavigatorMiniMap("invalid_cell", "bridge_move_to=" + String.valueOf(dest));
                    return;
                }
                if (navigatorMiniMapSelectionListener != null) {
                    navigatorMiniMapSelectionListener.onCellPicked(resolved);
                }
            });
        }
    }

    private void runOnUiThreadSafe(Runnable task) {
        if (task == null) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            uiHandler.post(task);
        }
    }

    private void traceNavigatorMiniMap(String action, String details) {
        String safeAction = action == null ? "unknown" : action.trim();
        String safeDetails = details == null ? "" : details.trim();
        String msg = NAV_MINIMAP_TRACE_PREFIX + " " + safeAction + (safeDetails.isEmpty() ? "" : " | " + safeDetails);
        Log.d("Navigator", msg);
        FileLogger.trace("Navigator", msg);
    }

    private int[] resolveCoordinatesByCell(String cellNum) {
        if (cellNum == null || cellNum.trim().isEmpty()) {
            return null;
        }
        ExtMap.init(context);
        String normalized = resolveCellNumber(cellNum);
        if (normalized.isEmpty()) {
            return null;
        }
        String pos = ExtMap.InvLocation.get(normalized);
        if (pos == null || pos.trim().isEmpty()) {
            return null;
        }
        ru.neverlands.abclient.model.Position point = ExtMap.Location.get(pos);
        if (point == null) {
            return null;
        }
        return new int[] {point.X, point.Y};
    }

    private int[] resolveFallbackCoordinates() {
        ExtMap.init(context);
        String mapLocation = (AppVars.Profile != null) ? AppVars.Profile.MapLocation : "";
        int[] byLocation = resolveCoordinatesByCell(mapLocation);
        if (byLocation != null) {
            return byLocation;
        }
        if (!ExtMap.Location.isEmpty()) {
            ru.neverlands.abclient.model.Position first = ExtMap.Location.values().iterator().next();
            if (first != null) {
                return new int[] {first.X, first.Y};
            }
        }
        return new int[] {0, 0};
    }

    private String buildMiniMapHtml() {
        return "<!doctype html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;padding:0;background:#0E1218;overflow:hidden;}</style>"
                + "</head><body>"
                + "<script>try{if(window.AndroidBridge){window.external=window.AndroidBridge;}}catch(e){}</script>"
                + "<script src='file:///android_asset/mapnav.js'></script>"
                + "</body></html>";
    }

    private int resolveMiniMapViewHeightPx() {
        int mapMiniHeight = (AppVars.Profile != null) ? AppVars.Profile.MapMiniHeight : 3;
        int mapMiniScale = (AppVars.Profile != null) ? AppVars.Profile.MapMiniScale : 100;
        if (mapMiniHeight < 3) mapMiniHeight = 3;
        if ((mapMiniHeight & 1) == 0) mapMiniHeight -= 1;
        if (mapMiniScale < 50) mapMiniScale = 50;
        if (mapMiniScale > 150) mapMiniScale = 150;
        int estimate = (mapMiniHeight * mapMiniScale) + (mapMiniHeight + 2);
        estimate = Math.round(estimate * 1.25f);
        int minHeight = dpToPx(130);
        int maxHeight = dpToPx(450);
        if (estimate < minHeight) estimate = minHeight;
        if (estimate > maxHeight) estimate = maxHeight;
        return estimate;
    }

    private void renderNavigatorMiniMapCenter(String source) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThreadSafe(() -> renderNavigatorMiniMapCenter(source));
            return;
        }
        if (!navigatorMiniMapReady || navigatorMiniMapView == null) {
            return;
        }
        String js = "try{showMap(" + navigatorMiniMapCenterX + "," + navigatorMiniMapCenterY + ");}catch(e){}";
        navigatorMiniMapView.evaluateJavascript(js, null);
        traceNavigatorMiniMap(
                source == null ? "center" : source,
                "xy=" + navigatorMiniMapCenterX + "_" + navigatorMiniMapCenterY
        );
    }

    private void applyNavigatorMiniMapSelection(String cellNum, String source) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThreadSafe(() -> applyNavigatorMiniMapSelection(cellNum, source));
            return;
        }
        String resolved = resolveCellNumber(cellNum);
        if (resolved.isEmpty()) {
            traceNavigatorMiniMap("invalid_cell", "source=" + source + ", value=" + String.valueOf(cellNum));
            return;
        }
        if (navigatorDestInput != null) {
            navigatorDestInput.setText(resolved);
            navigatorDestInput.setSelection(navigatorDestInput.getText().length());
        }
        int[] coords = resolveCoordinatesByCell(resolved);
        if (coords == null) {
            traceNavigatorMiniMap("invalid_cell", "source=" + source + ", cell=" + resolved);
            return;
        }
        navigatorMiniMapCenterX = coords[0];
        navigatorMiniMapCenterY = coords[1];
        renderNavigatorMiniMapCenter(source);
    }

    private void clearNavigatorMiniMapRuntimeState() {
        navigatorMiniMapSelectionListener = null;
        navigatorDestInput = null;
        navigatorMiniMapReady = false;
        navigatorMiniMapCenterX = 0;
        navigatorMiniMapCenterY = 0;
        if (navigatorMiniMapView != null) {
            try {
                navigatorMiniMapView.removeJavascriptInterface("AndroidBridge");
                navigatorMiniMapView.stopLoading();
                navigatorMiniMapView.loadUrl("about:blank");
                navigatorMiniMapView.destroy();
            } catch (Exception ignored) {
            }
        }
        navigatorMiniMapView = null;
    }

    /**
     * Инициализация навигатора.
     *
     * @param context Android context для UI, ресурсов и сохранения профиля
     * @param autoFunctionsManager менеджер авто-функций (старт/стоп движения)
     * @param onStateChanged callback синхронизации UI быстрых кнопок после смены состояния
     */
    public Navigator(Context context, AutoFunctionsManager autoFunctionsManager, Runnable onStateChanged) {
        this.context = context;
        this.autoFunctionsManager = autoFunctionsManager;
        this.onStateChanged = onStateChanged;
    }

    /**
     * Основная точка входа: показывает окно навигатора.
     *
     * Алгоритм:
     * 1) Если автомаршрут уже активен, показывает окно остановки.
     * 2) Иначе собирает диалог с полем назначения, чекбоксом телепортов и вкладками.
     * 3) На "Начать" валидирует клетку и запускает AutoMoving.
     *
     * Зависимости:
     * - {@link AppVars#AutoMoving}, {@link AppVars#AutoMovingDestinaton};
     * - {@link ExtMap#init(Context)} для подсказок и валидации клеток;
     * - {@link AppVars.Profile#NavigatorAllowTeleports} для чекбокса телепортов.
     */
    @SuppressLint("SetJavaScriptEnabled")
    public void showDialog() {
        if (AppVars.AutoMoving) {
            String dest = AppVars.AutoMovingDestinaton != null ? AppVars.AutoMovingDestinaton : "?";
            new AlertDialog.Builder(context)
                    .setTitle("Навигатор")
                    .setMessage("Навигатор активен. Пункт назначения: " + dest + "\nОстановить?")
                    .setPositiveButton("Остановить", (d, w) -> {
                        autoFunctionsManager.stopAutoMoving();
                        Toast.makeText(context, "Навигатор остановлен", Toast.LENGTH_SHORT).show();
                        if (onStateChanged != null) onStateChanged.run();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
            return;
        }

        ExtMap.init(context);
        clearNavigatorMiniMapRuntimeState();

        int pad = dpToPx(16);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, 0);

        navigatorDestInput = new android.widget.AutoCompleteTextView(context);
        navigatorDestInput.setHint("Номер клетки (напр. 8-259)");
        navigatorDestInput.setSingleLine();
        navigatorDestInput.setThreshold(1);
        navigatorDestInput.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, buildAutocompleteValues()));

        String currentLoc = (AppVars.Profile != null
                && AppVars.Profile.MapLocation != null
                && !AppVars.Profile.MapLocation.isEmpty())
                ? AppVars.Profile.MapLocation : "неизвестно";

        int[] initialCenter = resolveCoordinatesByCell(navigatorDestInput.getText() != null ? navigatorDestInput.getText().toString() : null);
        if (initialCenter == null) {
            initialCenter = resolveCoordinatesByCell(currentLoc);
        }
        if (initialCenter == null) {
            initialCenter = resolveFallbackCoordinates();
        }
        navigatorMiniMapCenterX = initialCenter[0];
        navigatorMiniMapCenterY = initialCenter[1];
        navigatorMiniMapReady = false;

        navigatorMiniMapView = new WebView(context);
        WebSettings miniSettings = navigatorMiniMapView.getSettings();
        miniSettings.setJavaScriptEnabled(true);
        miniSettings.setDomStorageEnabled(true);
        miniSettings.setLoadWithOverviewMode(true);
        miniSettings.setUseWideViewPort(true);
        miniSettings.setSupportZoom(false);
        miniSettings.setBuiltInZoomControls(false);
        miniSettings.setDisplayZoomControls(false);
        navigatorMiniMapView.setVerticalScrollBarEnabled(false);
        navigatorMiniMapView.setHorizontalScrollBarEnabled(false);
        navigatorMiniMapView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        navigatorMiniMapView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                navigatorMiniMapReady = true;
                renderNavigatorMiniMapCenter("open");
            }
        });
        navigatorMiniMapView.addJavascriptInterface(new NavigatorMiniMapBridge(), "AndroidBridge");
        navigatorMiniMapView.loadDataWithBaseURL(
                "file:///android_asset/",
                buildMiniMapHtml(),
                "text/html",
                "UTF-8",
                null
        );
        int miniMapHeightPx = resolveMiniMapViewHeightPx();
        traceNavigatorMiniMap("layout_size", "w=match_parent, h=" + miniMapHeightPx);
        LinearLayout.LayoutParams miniMapLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                miniMapHeightPx
        );
        root.addView(navigatorMiniMapView, miniMapLp);

        navigatorMiniMapSelectionListener = selectedCell -> applyNavigatorMiniMapSelection(selectedCell, "center_from_map_click");
        navigatorDestInput.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            String resolved = resolveCellNumber(item == null ? "" : String.valueOf(item));
            if (!resolved.isEmpty()) {
                applyNavigatorMiniMapSelection(resolved, "center_from_list");
            }
        });

        TextView locLabel = new TextView(context);
        locLabel.setText("Текущая позиция: " + currentLoc);
        locLabel.setTextColor(0xFF666666);
        root.addView(locLabel);

        root.addView(navigatorDestInput);

        CheckBox allowTeleCb = new CheckBox(context);
        allowTeleCb.setText("Разрешить телепорты");
        allowTeleCb.setChecked(AppVars.Profile == null || AppVars.Profile.NavigatorAllowTeleports);
        root.addView(allowTeleCb);

        TabLayout tabLayout = new TabLayout(context);
        tabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
        tabLayout.setTabTextColors(
                ContextCompat.getColor(context, R.color.ab_autoboi_text_secondary),
                ContextCompat.getColor(context, R.color.ab_autoboi_text_primary)
        );
        tabLayout.setSelectedTabIndicatorColor(ContextCompat.getColor(context, R.color.ab_autoboi_text_primary));
        tabLayout.addTab(tabLayout.newTab().setText("Локации"));
        tabLayout.addTab(tabLayout.newTab().setText("Боты"));
        tabLayout.addTab(tabLayout.newTab().setText("Травы"));
        tabLayout.addTab(tabLayout.newTab().setText("Рыбалка"));
        root.addView(tabLayout);

        ScrollView scroll = new ScrollView(context);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(280)));
        LinearLayout listLayout = new LinearLayout(context);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(0, dpToPx(4), 0, dpToPx(8));
        scroll.addView(listLayout);
        root.addView(scroll);

        final int[] selectedTab = new int[] { TAB_LOCATIONS };
        final Map<String, Boolean> expandedStates = new LinkedHashMap<>();
        Runnable rerender = () -> renderTabContent(listLayout, navigatorDestInput, selectedTab[0], expandedStates);
        rerender.run();

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedTab[0] = tab.getPosition();
                rerender.run();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                selectedTab[0] = tab.getPosition();
                rerender.run();
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Навигатор")
                .setView(root)
                .setPositiveButton("Начать", null)
                .setNegativeButton("Отмена", null)
                .create();

        allowTeleCb.setOnCheckedChangeListener((cb, checked) -> {
            if (AppVars.Profile != null) {
                AppVars.Profile.NavigatorAllowTeleports = checked;
                AppVars.Profile.save(context);
            }
            if (selectedTab[0] == TAB_LOCATIONS) rerender.run();
        });
        dialog.setOnShowListener(d -> {
            android.widget.Button startButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            android.widget.Button cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (startButton != null) {
                int primaryColor = ContextCompat.getColor(context, R.color.purple_500);
                int textColor = ContextCompat.getColor(context, R.color.white);
                startButton.setBackgroundColor(primaryColor);
                startButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primaryColor));
                startButton.setTextColor(textColor);
                startButton.setOnClickListener(v -> {
                    String cell = resolveCellNumber(navigatorDestInput.getText().toString());
                    if (cell.isEmpty()) {
                        Toast.makeText(context, "\u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u043f\u0443\u043d\u043a\u0442 \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u044f", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    autoFunctionsManager.startAutoMoving(cell);
                    Toast.makeText(context, "\u041d\u0430\u0432\u0438\u0433\u0430\u0442\u043e\u0440 \u0437\u0430\u043f\u0443\u0449\u0435\u043d \u2192 " + cell, Toast.LENGTH_SHORT).show();
                    if (onStateChanged != null) onStateChanged.run();
                    dialog.dismiss();
                });
            }
            if (cancelButton != null) {
                cancelButton.setTextColor(ContextCompat.getColor(context, R.color.white));
            }
        });

        dialog.setOnDismissListener(d -> clearNavigatorMiniMapRuntimeState());

        dialog.show();
    }

    /**
     * Роутер рендера вкладок.
     *
     * Зависимости:
     * - TAB_* константы;
     * - специализированные методы renderLocationTab/renderBotTab/renderHerbTab/renderFishTab.
     */
    private void renderTabContent(LinearLayout parent,
                                  EditText destInput,
                                  int tabIndex,
                                  Map<String, Boolean> expandedStates) {
        if (tabIndex == TAB_BOTS) {
            renderBotTab(parent, destInput, expandedStates);
            return;
        }
        if (tabIndex == TAB_HERBS) {
            renderHerbTab(parent, destInput, expandedStates);
            return;
        }
        if (tabIndex == TAB_FISH) {
            renderFishTab(parent, destInput, expandedStates);
            return;
        }
        renderLocationTab(parent, destInput, () -> renderTabContent(parent, destInput, TAB_LOCATIONS, expandedStates), expandedStates);
    }

    /**
     * Рендер вкладки "Локации" (редактируемая пользователем).
     *
     * Состав:
     * - Избранное;
     * - Города (подкатегории, редактируются пользователем);
     * - Объекты;
     * - Телепорты.
     *
     * Зависимости:
     * - данные профиля AppVars.Profile;
     * - addLeafCategory/addCitySubcategory/renderCells;
     * - повторный рендер через rerender после изменений.
     */
    private void renderLocationTab(LinearLayout parent,
                                   EditText destInput,
                                   Runnable rerender,
                                   Map<String, Boolean> expandedStates) {
        parent.removeAllViews();
        addCollapsibleSection(
                parent,
                "tab_loc:favorites",
                "Избранное",
                0,
                () -> showAddCitySubcategoryDialog(rerender),
                expandedStates,
                content -> {
                    for (CitySubcategory subcategory : getCitySubcategories()) {
                        addCitySubcategory(content, subcategory, dpToPx(10), destInput, rerender, expandedStates);
                    }
                });

        addLeafCategory(parent, "\u0413\u043e\u0440\u043e\u0434\u0430", NAV_CATEGORY_CITIES_ALL, navCityAllCells(), 0, destInput, rerender, expandedStates, "tab_loc:cities");

        addLeafCategory(parent, "Объекты", NAV_CATEGORY_OBJECTS, navObjectCells(), 0, destInput, rerender, expandedStates, "tab_loc:objects");
        addLeafCategory(parent, "Телепорты", NAV_CATEGORY_TELEPORTS, navTelepCells(), 0, destInput, rerender, expandedStates, "tab_loc:teleports");
    }

    /**
     * Рендер вкладки "Боты" (данные только из map.xml).
     *
     * Группировка:
     * - категория: имя бота;
     * - подкатегория: диапазон уровней min-max;
     * - элементы: клетки появления.
     *
     * Зависимости:
     * - getNavigatorMapIndex();
     * - compareLevelRanges(...) для сортировки диапазонов уровней.
     */
    private void renderBotTab(LinearLayout parent, EditText destInput, Map<String, Boolean> expandedStates) {
        parent.removeAllViews();
        NavigatorMapIndex index = getNavigatorMapIndex();
        if (index.botGroups.isEmpty()) {
            addEmptyHint(parent, "Нет данных ботов в map.xml");
            return;
        }

        ArrayList<String> botNames = new ArrayList<>(index.botGroups.keySet());
        Collections.sort(botNames, String.CASE_INSENSITIVE_ORDER);

        for (String botName : botNames) {
            addCollapsibleSection(
                    parent,
                    "tab_bots:" + botName.toLowerCase(),
                    botName,
                    0,
                    null,
                    expandedStates,
                    content -> {
                        Map<String, LinkedHashSet<String>> levels = index.botGroups.get(botName);
                        ArrayList<String> levelRanges = new ArrayList<>(levels.keySet());
                        // ✅ API 21 compatible sort (ArrayList#sort requires API 24)
                        Collections.sort(levelRanges, this::compareLevelRanges);

                        for (String range : levelRanges) {
                            addCollapsibleSubSection(
                                    content,
                                    "tab_bots:" + botName.toLowerCase() + ":lvl:" + range,
                                    "Уровень " + range,
                                    dpToPx(10),
                                    expandedStates,
                                    subContent -> {
                                        String[] cells = sortAndNormalizeCells(levels.get(range).toArray(new String[0]));
                                        renderCells(subContent, dpToPx(10), cells, destInput, null);
                                    });
                        }
                    });
        }
    }

    /**
     * Рендер вкладки "Травы" (данные из map.xml по herbGroup).
     *
     * Группировка:
     * - категория: номер herbGroup;
     * - элементы: клетки этой группы.
     *
     * Зависимости:
     * - getNavigatorMapIndex();
     * - sortAndNormalizeCells(...) для стабильного вывода.
     */
    private void renderHerbTab(LinearLayout parent, EditText destInput, Map<String, Boolean> expandedStates) {
        parent.removeAllViews();
        NavigatorMapIndex index = getNavigatorMapIndex();
        if (index.herbGroups.isEmpty()) {
            addEmptyHint(parent, "Нет данных трав в map.xml");
            return;
        }

        ArrayList<String> groups = new ArrayList<>(index.herbGroups.keySet());
        // ✅ API 21 compatible sort (ArrayList#sort requires API 24)
        Collections.sort(groups, (a, b) -> Integer.compare(parseIntSafe(a, Integer.MAX_VALUE), parseIntSafe(b, Integer.MAX_VALUE)));

        for (String group : groups) {
            addCollapsibleSection(
                    parent,
                    "tab_herbs:" + group,
                    "Травы №" + group,
                    0,
                    null,
                    expandedStates,
                    content -> {
                        String[] cells = sortAndNormalizeCells(index.herbGroups.get(group).toArray(new String[0]));
                        renderCells(content, 0, cells, destInput, null);
                    });
        }
    }

    /**
     * Рендер вкладки "Рыбалка" (данные из map.xml по hasWater=True).
     *
     * Группировка:
     * - категория: тип рыбалки/метка водной клетки (deriveFishCategory);
     * - элементы: клетки с водой.
     *
     * Зависимости:
     * - getNavigatorMapIndex();
     * - deriveFishCategory(...) для ключа группы.
     */
    private void renderFishTab(LinearLayout parent, EditText destInput, Map<String, Boolean> expandedStates) {
        parent.removeAllViews();
        NavigatorMapIndex index = getNavigatorMapIndex();
        if (index.fishGroups.isEmpty()) {
            addEmptyHint(parent, "Нет водных клеток в map.xml");
            return;
        }

        ArrayList<String> fishTypes = new ArrayList<>(index.fishGroups.keySet());
        Collections.sort(fishTypes, String.CASE_INSENSITIVE_ORDER);

        for (String fishType : fishTypes) {
            addCollapsibleSection(
                    parent,
                    "tab_fish:" + fishType.toLowerCase(),
                    fishType,
                    0,
                    null,
                    expandedStates,
                    content -> {
                        String[] cells = sortAndNormalizeCells(index.fishGroups.get(fishType).toArray(new String[0]));
                        renderCells(content, 0, cells, destInput, null);
                    });
        }
    }

    /**
     * Добавляет сворачиваемую категорию:
     * - заголовок с опциональной кнопкой "+" и стрелкой в конце;
     * - контейнер контента, который скрывается/показывается по клику.
     *
     * Важное поведение:
     * - по умолчанию категория свернута;
     * - состояние запоминается в expandedStates на время жизни диалога.
     */
    private void addCollapsibleSection(LinearLayout parent,
                                       String stateKey,
                                       String title,
                                       int leftPaddingPx,
                                       Runnable onAddClick,
                                       Map<String, Boolean> expandedStates,
                                       SectionContentBuilder contentBuilder) {
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setBackgroundColor(ContextCompat.getColor(context, R.color.ab_autoboi_group_selected_bg));
        headerRow.setPadding(leftPaddingPx + dpToPx(10), dpToPx(8), dpToPx(6), dpToPx(8));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        // Категории выделяем красным, чтобы визуально отличались от подкатегорий.
        titleView.setTextColor(0xFFC62828);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        headerRow.addView(titleView, titleLp);

        if (onAddClick != null) {
            ImageButton addBtn = buildIconButton(android.R.drawable.ic_input_add, 0xFF2E7D32);
            addBtn.setOnClickListener(v -> onAddClick.run());
            headerRow.addView(addBtn);
        }

        TextView arrow = new TextView(context);
        arrow.setTextSize(14f);
        arrow.setTextColor(ContextCompat.getColor(context, R.color.ab_autoboi_text_primary));
        arrow.setPadding(dpToPx(6), 0, dpToPx(4), 0);
        headerRow.addView(arrow);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dpToPx(6);
        parent.addView(headerRow, lp);

        LinearLayout contentContainer = new LinearLayout(context);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        parent.addView(contentContainer);
        contentBuilder.build(contentContainer);

        // ✅ API 21 compatible: getOrDefault requires API 24
        Boolean expandedValue = expandedStates.get(stateKey);
        boolean expanded = expandedValue != null ? expandedValue : false;
        applyCategoryExpanded(contentContainer, arrow, expanded);
        headerRow.setOnClickListener(v -> {
            boolean nextExpanded = contentContainer.getVisibility() != View.VISIBLE;
            expandedStates.put(stateKey, nextExpanded);
            applyCategoryExpanded(contentContainer, arrow, nextExpanded);
        });
    }

    /**
     * Применяет визуальное состояние раскрытия категории.
     */
    private void applyCategoryExpanded(LinearLayout contentContainer, TextView arrow, boolean expanded) {
        contentContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        arrow.setText(expanded ? "▲" : "▼");
    }

    /**
     * Добавляет сворачиваемую подкатегорию (например, диапазон уровня внутри категории "Боты").
     *
     * Отличие от addCollapsibleSection(...):
     * - без кнопки "+";
     * - с дополнительным отступом слева, чтобы визуально быть вложенным уровнем.
     */
    private void addCollapsibleSubSection(LinearLayout parent,
                                          String stateKey,
                                          String title,
                                          int leftPaddingPx,
                                          Map<String, Boolean> expandedStates,
                                          SectionContentBuilder contentBuilder) {
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setBackgroundColor(ContextCompat.getColor(context, R.color.ab_autoboi_group_selected_bg));
        headerRow.setPadding(leftPaddingPx + dpToPx(10), dpToPx(6), dpToPx(6), dpToPx(6));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setTextColor(ContextCompat.getColor(context, R.color.ab_autoboi_text_primary));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        headerRow.addView(titleView, titleLp);

        TextView arrow = new TextView(context);
        arrow.setTextSize(14f);
        arrow.setTextColor(ContextCompat.getColor(context, R.color.ab_autoboi_text_primary));
        arrow.setPadding(dpToPx(6), 0, dpToPx(4), 0);
        headerRow.addView(arrow);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dpToPx(4);
        parent.addView(headerRow, lp);

        LinearLayout contentContainer = new LinearLayout(context);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        parent.addView(contentContainer);
        contentBuilder.build(contentContainer);

        // ✅ API 21 compatible: getOrDefault requires API 24
        Boolean expandedValue = expandedStates.get(stateKey);
        boolean expanded = expandedValue != null ? expandedValue : false;
        applyCategoryExpanded(contentContainer, arrow, expanded);
        headerRow.setOnClickListener(v -> {
            boolean nextExpanded = contentContainer.getVisibility() != View.VISIBLE;
            expandedStates.put(stateKey, nextExpanded);
            applyCategoryExpanded(contentContainer, arrow, nextExpanded);
        });
    }

    /**
     * Добавляет подзаголовок внутри категории (например, диапазон уровней бота).
     */
    private void addSubHeader(LinearLayout parent, String title, int leftPaddingPx) {
        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setTextColor(0xFF555555);
        tv.setPadding(leftPaddingPx + dpToPx(14), dpToPx(6), dpToPx(10), dpToPx(2));
        parent.addView(tv);
    }

    /**
     * Добавляет редактируемую "листовую" категорию (без подкатегорий):
     * заголовок + кнопка добавления + список клеток с удалением.
     *
     * Зависимости:
     * - showAddCellDialog(...) -> addCellToCategory(...);
     * - renderCells(...) -> removeCellFromCategory(...);
     * - rerender для немедленного обновления UI после правок.
     */
    private void addLeafCategory(LinearLayout parent,
                                 String title,
                                 int categoryId,
                                 String[] cells,
                                 int leftPaddingPx,
                                 EditText destInput,
                                 Runnable rerender,
                                 Map<String, Boolean> expandedStates,
                                 String stateKey) {
        addCollapsibleSection(
                parent,
                stateKey,
                title,
                leftPaddingPx,
                () -> showAddCellDialog(title, cell -> addCellToCategory(categoryId, cell), rerender),
                expandedStates,
                content -> renderCells(content, leftPaddingPx, sortAndNormalizeCells(cells), destInput, cellNum -> {
                    removeCellFromCategory(categoryId, cellNum);
                    if (rerender != null) rerender.run();
                }));
    }
    /**
     * Добавляет редактируемую подкатегорию в блоке "Города":
     * - "+" для добавления клетки;
     * - "-" для удаления всей подкатегории;
     * - список клеток подкатегории с удалением отдельных элементов.
     *
     * Зависимости:
     * - addCellToCitySubcategory/removeCitySubcategory/removeCellFromCitySubcategory;
     * - профиль AppVars.Profile (сериализация/сохранение).
     */
    private void addCitySubcategory(LinearLayout parent,
                                    CitySubcategory subcategory,
                                    int leftPaddingPx,
                                    EditText destInput,
                                    Runnable rerender,
                                    Map<String, Boolean> expandedStates) {
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setBackgroundColor(ContextCompat.getColor(context, R.color.ab_autoboi_group_selected_bg));
        headerRow.setPadding(leftPaddingPx + dpToPx(10), dpToPx(8), dpToPx(6), dpToPx(8));
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headerLp.topMargin = dpToPx(6);

        TextView titleView = new TextView(context);
        titleView.setText(subcategory.name);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setTextColor(ContextCompat.getColor(context, R.color.ab_autoboi_text_primary));

        ImageButton addBtn = buildIconButton(android.R.drawable.ic_input_add, 0xFF2E7D32);
        addBtn.setOnClickListener(v -> showAddCellDialog(subcategory.name, cell -> addCellToCitySubcategory(subcategory.name, cell), rerender));

        ImageButton removeBtn = buildIconButton(android.R.drawable.ic_delete, 0xFFC62828);
        removeBtn.setOnClickListener(v -> {
            removeCitySubcategory(subcategory.name);
            if (rerender != null) rerender.run();
            Toast.makeText(context, "Удалена подкатегория: " + subcategory.name, Toast.LENGTH_SHORT).show();
        });

        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        headerRow.addView(titleView, titleLp);
        headerRow.addView(addBtn);
        headerRow.addView(removeBtn);
        TextView arrow = new TextView(context);
        arrow.setTextSize(14f);
        arrow.setTextColor(ContextCompat.getColor(context, R.color.ab_autoboi_text_primary));
        arrow.setPadding(dpToPx(6), 0, dpToPx(4), 0);
        headerRow.addView(arrow);
        parent.addView(headerRow, headerLp);

        LinearLayout contentContainer = new LinearLayout(context);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        parent.addView(contentContainer);
        renderCells(contentContainer, leftPaddingPx, sortAndNormalizeCells(subcategory.cells), destInput, cellNum -> {
            removeCellFromCitySubcategory(subcategory.name, cellNum);
            if (rerender != null) rerender.run();
        });

        String stateKey = "tab_loc:city_sub:" + subcategory.name.toLowerCase();
        // ✅ API 21 compatible getOrDefault (Map#getOrDefault requires API 24)
        boolean expanded = expandedStates.containsKey(stateKey) ? expandedStates.get(stateKey) : false;
        applyCategoryExpanded(contentContainer, arrow, expanded);
        headerRow.setOnClickListener(v -> {
            boolean nextExpanded = contentContainer.getVisibility() != View.VISIBLE;
            expandedStates.put(stateKey, nextExpanded);
            applyCategoryExpanded(contentContainer, arrow, nextExpanded);
        });
    }

    /**
     * Рендер строк клеток.
     *
     * Поведение:
     * - клик по строке подставляет клетку в поле назначения;
     * - при наличии onRemoveCell добавляет кнопку удаления "-" для строки.
     *
     * Зависимости:
     * - buildCellLabel(...) для отображения "cell - name";
     * - resolveCellNumber(...) и профильные операции удаления в callback.
     */
    private void renderCells(LinearLayout parent, int leftPaddingPx, String[] cells, EditText destInput, OnCellPicked onRemoveCell) {
        if (cells == null || cells.length == 0) {
            addEmptyHint(parent, "Пусто");
            return;
        }

        for (String cellNum : cells) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(leftPaddingPx + dpToPx(14), dpToPx(4), dpToPx(2), dpToPx(4));

            TextView item = new TextView(context);
            item.setText(buildCellLabel(cellNum));
            item.setTextColor(ContextCompat.getColor(context, R.color.white));
            item.setOnClickListener(v -> {
                if (navigatorMiniMapSelectionListener != null) {
                    applyNavigatorMiniMapSelection(cellNum, "center_from_list");
                } else {
                    destInput.setText(cellNum);
                }
            });
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(item, itemLp);

            if (onRemoveCell != null) {
                ImageButton removeBtn = buildIconButton(android.R.drawable.ic_delete, 0xFFC62828);
                removeBtn.setOnClickListener(v -> {
                    onRemoveCell.onCellPicked(cellNum);
                    Toast.makeText(context, "Удалено: " + cellNum, Toast.LENGTH_SHORT).show();
                });
                row.addView(removeBtn);
            }
            parent.addView(row);
        }
    }

    /**
     * Показывает служебную строку-подсказку ("Пусто"/"Нет данных...").
     */
    private void addEmptyHint(LinearLayout parent, String message) {
        TextView empty = new TextView(context);
        empty.setText(message);
        empty.setTextColor(0xFF777777);
        empty.setPadding(dpToPx(14), dpToPx(8), dpToPx(10), dpToPx(8));
        parent.addView(empty);
    }

    /**
     * Фабрика мини-кнопок действий (плюс/минус) в заголовках и строках.
     */
    private ImageButton buildIconButton(int drawableRes, int tintColor) {
        ImageButton btn = new ImageButton(context);
        btn.setImageResource(drawableRes);
        btn.setColorFilter(tintColor);
        btn.setBackgroundResource(android.R.color.transparent);
        btn.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        return btn;
    }

    /**
     * Диалог добавления клетки в выбранную категорию/подкатегорию.
     *
     * Валидация:
     * - извлекается формат region-number;
     * - проверяется существование клетки в ExtMap.
     *
     * Зависимости:
     * - buildAutocompleteValues(...), resolveCellNumber(...), ExtMap.Cells;
     * - onCellPicked передает выбранную клетку в слой сохранения.
     */
    private void showAddCellDialog(String categoryTitle, OnCellPicked onCellPicked, Runnable onChanged) {
        android.widget.AutoCompleteTextView input = new android.widget.AutoCompleteTextView(context);
        input.setHint("Клетка (напр. 8-259)");
        input.setSingleLine();
        input.setThreshold(1);
        input.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, buildAutocompleteValues()));

        new AlertDialog.Builder(context)
                .setTitle("Добавить в: " + categoryTitle)
                .setView(input)
                .setPositiveButton("Добавить", (d, w) -> {
                    String cell = resolveCellNumber(input.getText().toString());
                    if (cell.isEmpty()) {
                        Toast.makeText(context, "Неверный формат клетки", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (ExtMap.Cells == null || !ExtMap.Cells.containsKey(cell)) {
                        Toast.makeText(context, "Клетка не найдена в map.xml", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    onCellPicked.onCellPicked(cell);
                    if (onChanged != null) onChanged.run();
                    Toast.makeText(context, "Добавлено: " + cell, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    /**
     * Диалог создания новой подкатегории в секции "Города".
     *
     * Зависимости:
     * - sanitizeCitySubcategoryName(...), addCitySubcategory(...);
     * - saveCitySubcategories(...) через addCitySubcategory(...).
     */
    private void showAddCitySubcategoryDialog(Runnable onChanged) {
        EditText input = new EditText(context);
        input.setHint("Название подкатегории");
        input.setSingleLine(true);

        new AlertDialog.Builder(context)
                .setTitle("Новая категория: Избранное")
                .setView(input)
                .setPositiveButton("Добавить", (d, w) -> {
                    String name = sanitizeCitySubcategoryName(input.getText().toString());
                    if (name.isEmpty()) {
                        Toast.makeText(context, "Введите название", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!addCitySubcategory(name)) {
                        Toast.makeText(context, "Подкатегория уже существует", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (onChanged != null) onChanged.run();
                    Toast.makeText(context, "Добавлена подкатегория: " + name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    /**
     * Формирует список автоподсказок для ввода клетки:
     * - источник: ExtMap.Cells;
     * - формат: "region-number - имя клетки".
     */
    private String[] buildAutocompleteValues() {
        if (ExtMap.Cells == null || ExtMap.Cells.isEmpty()) return new String[0];
        ArrayList<String> values = new ArrayList<>();
        for (String cellNum : ExtMap.Cells.keySet()) values.add(buildCellLabel(cellNum));
        Collections.sort(values);
        return values.toArray(new String[0]);
    }

    /**
     * Возвращает display-метку клетки в формате "cell - name/tooltip".
     */
    private String buildCellLabel(String cellNum) {
        if (cellNum == null) return "";
        ru.neverlands.abclient.model.Cell c = ExtMap.Cells != null ? ExtMap.Cells.get(cellNum) : null;
        String suffix = "";
        if (c != null) {
            if (c.Name != null && !c.Name.trim().isEmpty()) suffix = c.Name.trim();
            else if (c.Tooltip != null && !c.Tooltip.trim().isEmpty()) suffix = c.Tooltip.trim();
        }
        return suffix.isEmpty() ? cellNum : (cellNum + " - " + suffix);
    }

    /**
     * Универсально извлекает cellNumber из произвольной строки.
     *
     * Пример:
     * - "8-259" -> "8-259"
     * - "8-259 - Форпост" -> "8-259"
     */
    private String resolveCellNumber(String raw) {
        if (raw == null) return "";
        Matcher m = CELL_PATTERN.matcher(raw.trim());
        return m.find() ? m.group(1) : "";
    }

    /**
     * Нормализует и сортирует массив клеток:
     * - удаляет мусор/дубликаты;
     * - оставляет только валидный формат region-number.
     */
    private String[] sortAndNormalizeCells(String[] source) {
        if (source == null || source.length == 0) return new String[0];
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String item : source) {
            String cell = resolveCellNumber(item);
            if (!cell.isEmpty()) normalized.add(cell);
        }
        String[] arr = normalized.toArray(new String[0]);
        Arrays.sort(arr);
        return arr;
    }

    /**
     * Добавляет клетку в массив с учетом нормализации и дедупликации.
     */
    private String[] appendCellToArray(String[] source, String cellNum) {
        String normalized = resolveCellNumber(cellNum);
        if (normalized.isEmpty()) return source == null ? new String[0] : source;
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (source != null) {
            for (String item : source) {
                String cell = resolveCellNumber(item);
                if (!cell.isEmpty()) set.add(cell);
            }
        }
        set.add(normalized);
        return sortAndNormalizeCells(set.toArray(new String[0]));
    }

    /**
     * Удаляет клетку из массива по нормализованному идентификатору.
     */
    private String[] removeCellFromArray(String[] source, String cellNum) {
        String normalized = resolveCellNumber(cellNum);
        ArrayList<String> result = new ArrayList<>();
        if (source != null) {
            for (String item : source) {
                String cell = resolveCellNumber(item);
                if (!cell.isEmpty() && !cell.equals(normalized)) result.add(cell);
            }
        }
        return result.toArray(new String[0]);
    }

    /**
     * Добавляет клетку в одну из базовых категорий навигатора профиля.
     *
     * Зависимости:
     * - getCategoryCells/applyCategoryCells;
     * - AppVars.Profile.save(context) для персистентности.
     */
    private void addCellToCategory(int categoryId, String cellNum) {
        if (AppVars.Profile == null) return;
        applyCategoryCells(categoryId, appendCellToArray(getCategoryCells(categoryId), cellNum));
        AppVars.Profile.save(context);
    }

    /**
     * Удаляет клетку из базовой категории навигатора профиля.
     */
    private void removeCellFromCategory(int categoryId, String cellNum) {
        if (AppVars.Profile == null) return;
        applyCategoryCells(categoryId, removeCellFromArray(getCategoryCells(categoryId), cellNum));
        AppVars.Profile.save(context);
    }

    /**
     * Читает клетки базовой категории из профиля.
     *
     * Важно:
     * - часть категорий имеет дефолтные значения, если в профиле пусто.
     */
    private String[] getCategoryCells(int categoryId) {
        if (AppVars.Profile == null) return new String[0];
        switch (categoryId) {
            case NAV_CATEGORY_FAVORITES: return navFavCells();
            case NAV_CATEGORY_CITY_VILLAGE: return navCityVillageCells();
            case NAV_CATEGORY_CITY_FORPOST: return navCityForpostCells();
            case NAV_CATEGORY_CITY_OKTAL: return navCityOktalCells();
            case NAV_CATEGORY_OBJECTS: return navObjectCells();
            case NAV_CATEGORY_TELEPORTS: return navTelepCells();
            case NAV_CATEGORY_CITIES_ALL: return navCityAllCells();
            default: return new String[0];
        }
    }

    /**
     * Применяет обновленный список клеток в выбранную категорию профиля.
     *
     * Зависимости:
     * - методы UserConfig setNav*Locations(...);
     * - sortAndNormalizeCells(...) для согласованного формата.
     */
    private void applyCategoryCells(int categoryId, String[] cells) {
        if (AppVars.Profile == null) return;
        switch (categoryId) {
            case NAV_CATEGORY_FAVORITES: AppVars.Profile.FavLocations = sortAndNormalizeCells(cells); break;
            case NAV_CATEGORY_CITY_VILLAGE: AppVars.Profile.setNavCityVillageLocations(cells); break;
            case NAV_CATEGORY_CITY_FORPOST: AppVars.Profile.setNavCityForpostLocations(cells); break;
            case NAV_CATEGORY_CITY_OKTAL: AppVars.Profile.setNavCityOktalLocations(cells); break;
            case NAV_CATEGORY_OBJECTS: AppVars.Profile.setNavObjectLocations(cells); break;
            case NAV_CATEGORY_TELEPORTS: AppVars.Profile.setNavTeleportLocations(cells); break;
            case NAV_CATEGORY_CITIES_ALL:
                String[] normalizedCities = sortAndNormalizeCells(cells);
                AppVars.Profile.NavCityVillageLocations = normalizedCities.clone();
                AppVars.Profile.NavCityForpostLocations = normalizedCities.clone();
                AppVars.Profile.NavCityOktalLocations = normalizedCities.clone();
                break;
            default: break;
        }
    }

    /**
     * Избранные клетки (пользовательская категория).
     */
    private String[] navFavCells() {
        if (AppVars.Profile == null || AppVars.Profile.FavLocations == null) return new String[0];
        return sortAndNormalizeCells(AppVars.Profile.FavLocations);
    }

    /**
     * Дефолт/значения профиля для подкатегории "Деревня".
     */
    private String[] navCityVillageCells() {
        if (AppVars.Profile == null || AppVars.Profile.NavCityVillageLocations == null) return new String[] {"8-197"};
        return sortAndNormalizeCells(AppVars.Profile.NavCityVillageLocations);
    }

    /**
     * Дефолт/значения профиля для подкатегории "Форпост".
     */
    private String[] navCityForpostCells() {
        if (AppVars.Profile == null || AppVars.Profile.NavCityForpostLocations == null) return new String[] {"8-259", "8-294"};
        return sortAndNormalizeCells(AppVars.Profile.NavCityForpostLocations);
    }

    /**
     * Дефолт/значения профиля для подкатегории "Октал".
     */
    private String[] navCityOktalCells() {
        if (AppVars.Profile == null || AppVars.Profile.NavCityOktalLocations == null) return new String[] {"12-428", "12-494", "12-521"};
        return sortAndNormalizeCells(AppVars.Profile.NavCityOktalLocations);
    }

    private String[] navCityAllCells() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String c : navCityVillageCells()) set.add(c);
        for (String c : navCityForpostCells()) set.add(c);
        for (String c : navCityOktalCells()) set.add(c);
        return sortAndNormalizeCells(set.toArray(new String[0]));
    }

    /**
     * Дефолт/значения профиля для категории "Объекты".
     */
    private String[] navObjectCells() {
        if (AppVars.Profile == null || AppVars.Profile.NavObjectLocations == null) return new String[] {"8-227", "2-482", "9-494", "26-430"};
        return sortAndNormalizeCells(AppVars.Profile.NavObjectLocations);
    }

    /**
     * Список телепортов:
     * - сначала профиль;
     * - если пусто, fallback на ExtMap.Teleports.
     */
    private String[] navTelepCells() {
        if (AppVars.Profile != null && AppVars.Profile.NavTeleportLocations != null && AppVars.Profile.NavTeleportLocations.length > 0) {
            return sortAndNormalizeCells(AppVars.Profile.NavTeleportLocations);
        }
        if (ExtMap.Teleports == null || ExtMap.Teleports.isEmpty()) return new String[0];
        String[] keys = ExtMap.Teleports.keySet().toArray(new String[0]);
        Arrays.sort(keys);
        return keys;
    }

    /**
     * Возвращает список подкатегорий секции "Города":
     * - из профиля, если есть;
     * - иначе дефолтные подкатегории.
     */
    private List<CitySubcategory> getCitySubcategories() {
        if (AppVars.Profile == null) return buildDefaultCitySubcategories();
        List<CitySubcategory> parsed = parseCitySubcategories(AppVars.Profile.NavCitySubcategories);
        return parsed.isEmpty() ? buildDefaultCitySubcategories() : parsed;
    }

    /**
     * Дефолтный набор подкатегорий для "Города".
     */
    private List<CitySubcategory> buildDefaultCitySubcategories() {
        ArrayList<CitySubcategory> defaults = new ArrayList<>();
        defaults.add(new CitySubcategory("Избранное", navFavCells()));
        return defaults;
    }

    /**
     * Парсит строку профиля NavCitySubcategories в список моделей.
     *
     * Формат:
     * - "Имя|8-259,8-294;Другая|12-428".
     */
    private List<CitySubcategory> parseCitySubcategories(String raw) {
        ArrayList<CitySubcategory> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return result;
        String[] entries = raw.split(Pattern.quote(CITY_SUBCATEGORY_ENTRY_DELIMITER));
        for (String entry : entries) {
            if (entry == null || entry.trim().isEmpty()) continue;
            String[] pair = entry.split(Pattern.quote(CITY_SUBCATEGORY_VALUE_DELIMITER), 2);
            String name = sanitizeCitySubcategoryName(pair[0]);
            if (name.isEmpty()) continue;
            String[] cells = new String[0];
            if (pair.length > 1 && pair[1] != null && !pair[1].trim().isEmpty()) cells = sortAndNormalizeCells(pair[1].split(","));
            result.add(new CitySubcategory(name, cells));
        }
        return result;
    }

    /**
     * Санитизирует имя подкатегории от служебных разделителей формата.
     */
    private String sanitizeCitySubcategoryName(String name) {
        if (name == null) return "";
        String sanitized = name.replace(CITY_SUBCATEGORY_ENTRY_DELIMITER, " ")
                .replace(CITY_SUBCATEGORY_VALUE_DELIMITER, " ")
                .replace(",", " ").trim();
        while (sanitized.contains("  ")) sanitized = sanitized.replace("  ", " ");
        return sanitized;
    }

    /**
     * Ищет индекс подкатегории по имени (без учета регистра).
     */
    private int findCitySubcategoryIndex(List<CitySubcategory> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            CitySubcategory s = list.get(i);
            if (s != null && s.name != null && s.name.equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    /**
     * Сериализует список подкатегорий в строковый формат профиля.
     */
    private String encodeCitySubcategories(List<CitySubcategory> subcategories) {
        if (subcategories == null || subcategories.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (CitySubcategory sub : subcategories) {
            if (sub == null) continue;
            String name = sanitizeCitySubcategoryName(sub.name);
            if (name.isEmpty()) continue;
            String[] cells = sortAndNormalizeCells(sub.cells);
            if (sb.length() > 0) sb.append(CITY_SUBCATEGORY_ENTRY_DELIMITER);
            sb.append(name).append(CITY_SUBCATEGORY_VALUE_DELIMITER);
            for (int i = 0; i < cells.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(cells[i]);
            }
        }
        return sb.toString();
    }

    /**
     * Добавляет новую подкатегорию "Города" в профиль.
     *
     * @return true если добавлено; false если имя пустое/дубликат/нет профиля
     */
    private boolean addCitySubcategory(String name) {
        if (AppVars.Profile == null) return false;
        String normalizedName = sanitizeCitySubcategoryName(name);
        if (normalizedName.isEmpty()) return false;
        List<CitySubcategory> subcategories = getCitySubcategories();
        if (findCitySubcategoryIndex(subcategories, normalizedName) >= 0) return false;
        subcategories.add(new CitySubcategory(normalizedName, new String[0]));
        saveCitySubcategories(subcategories);
        return true;
    }

    /**
     * Удаляет подкатегорию "Города" по имени.
     */
    private void removeCitySubcategory(String name) {
        if (AppVars.Profile == null) return;
        List<CitySubcategory> subcategories = getCitySubcategories();
        int index = findCitySubcategoryIndex(subcategories, name);
        if (index < 0) return;
        subcategories.remove(index);
        saveCitySubcategories(subcategories);
    }

    /**
     * Добавляет клетку в конкретную подкатегорию "Города".
     */
    private void addCellToCitySubcategory(String subcategoryName, String cellNum) {
        if (AppVars.Profile == null) return;
        List<CitySubcategory> subcategories = getCitySubcategories();
        int index = findCitySubcategoryIndex(subcategories, subcategoryName);
        if (index < 0) return;
        CitySubcategory existing = subcategories.get(index);
        subcategories.set(index, new CitySubcategory(existing.name, appendCellToArray(existing.cells, cellNum)));
        saveCitySubcategories(subcategories);
    }

    /**
     * Удаляет клетку из конкретной подкатегории "Города".
     */
    private void removeCellFromCitySubcategory(String subcategoryName, String cellNum) {
        if (AppVars.Profile == null) return;
        List<CitySubcategory> subcategories = getCitySubcategories();
        int index = findCitySubcategoryIndex(subcategories, subcategoryName);
        if (index < 0) return;
        CitySubcategory existing = subcategories.get(index);
        subcategories.set(index, new CitySubcategory(existing.name, removeCellFromArray(existing.cells, cellNum)));
        saveCitySubcategories(subcategories);
    }

    /**
     * Сохраняет текущее состояние подкатегорий "Города" в профиль.
     */
    private void saveCitySubcategories(List<CitySubcategory> subcategories) {
        if (AppVars.Profile == null) return;
        AppVars.Profile.NavCitySubcategories = encodeCitySubcategories(subcategories);
        AppVars.Profile.FavLocations = flattenCells(subcategories);
        AppVars.Profile.save(context);
    }

    private String[] flattenCells(List<CitySubcategory> subcategories) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (subcategories != null) {
            for (CitySubcategory sub : subcategories) {
                if (sub == null || sub.cells == null) continue;
                for (String cell : sub.cells) {
                    String normalized = resolveCellNumber(cell);
                    if (!normalized.isEmpty()) set.add(normalized);
                }
            }
        }
        return sortAndNormalizeCells(set.toArray(new String[0]));
    }

    /**
     * Ленивая инициализация кэша индекса map.xml.
     */
    private NavigatorMapIndex getNavigatorMapIndex() {
        if (mapIndexCache != null) return mapIndexCache;
        mapIndexCache = buildNavigatorMapIndex();
        return mapIndexCache;
    }

    /**
     * Строит индекс карты из runtime `map.xml` (шаблон из assets + дополняемые данные) для вкладок:
     * - Боты: по тегам bots (name/minLevel/maxLevel);
     * - Травы: по атрибуту herbGroup;
     * - Рыбалка: по hasWater=True.
     *
     * Зависимости:
     * - XmlPullParser;
     * - deriveFishCategory(...) для ключа вкладки "Рыбалка";
     * - resolveCellNumber(...) для нормализации ключей клеток.
     */
    private NavigatorMapIndex buildNavigatorMapIndex() {
        NavigatorMapIndex index = new NavigatorMapIndex();
        try (InputStream in = ExtMap.openRuntimeMapInputStream(context)) {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(in, "UTF-8");

            String currentCell = null;
            String currentName = "";
            String currentTooltip = "";
            boolean currentHasWater = false;

            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("cell".equalsIgnoreCase(tag)) {
                        currentCell = resolveCellNumber(safeTrim(parser.getAttributeValue(null, "cellNumber")));
                        currentName = safeTrim(parser.getAttributeValue(null, "name"));
                        currentTooltip = safeTrim(parser.getAttributeValue(null, "tooltip"));
                        currentHasWater = "true".equalsIgnoreCase(safeTrim(parser.getAttributeValue(null, "hasWater")));

                        String herbGroup = safeTrim(parser.getAttributeValue(null, "herbGroup"));
                        if (currentCell != null && !currentCell.isEmpty()) {
                            for (String token : herbGroup.split("[,;]")) {
                                String group = token.trim();
                                if (group.isEmpty() || "0".equals(group)) continue;
                                // ✅ API 21 compatible computeIfAbsent (Map#computeIfAbsent requires API 24)
                                if (!index.herbGroups.containsKey(group)) {
                                    index.herbGroups.put(group, new LinkedHashSet<>());
                                }
                                index.herbGroups.get(group).add(currentCell);
                            }
                            if (currentHasWater) {
                                String fishKey = deriveFishCategory(currentName, currentTooltip);
                                // ✅ API 21 compatible computeIfAbsent
                                if (!index.fishGroups.containsKey(fishKey)) {
                                    index.fishGroups.put(fishKey, new LinkedHashSet<>());
                                }
                                index.fishGroups.get(fishKey).add(currentCell);
                            }
                        }
                    } else if ("bots".equalsIgnoreCase(tag) && currentCell != null && !currentCell.isEmpty()) {
                        String botName = safeTrim(parser.getAttributeValue(null, "name"));
                        String min = safeTrim(parser.getAttributeValue(null, "minLevel"));
                        String max = safeTrim(parser.getAttributeValue(null, "maxLevel"));
                        if (!botName.isEmpty()) {
                            String range = (min.isEmpty() ? "?" : min) + "-" + (max.isEmpty() ? "?" : max);
                            // ✅ API 21 compatible computeIfAbsent chain
                            if (!index.botGroups.containsKey(botName)) {
                                index.botGroups.put(botName, new LinkedHashMap<>());
                            }
                            Map<String, LinkedHashSet<String>> botRanges = index.botGroups.get(botName);
                            if (!botRanges.containsKey(range)) {
                                botRanges.put(range, new LinkedHashSet<>());
                            }
                            botRanges.get(range).add(currentCell);
                        }
                    }
                } else if (event == XmlPullParser.END_TAG && "cell".equalsIgnoreCase(parser.getName())) {
                    currentCell = null;
                    currentName = "";
                    currentTooltip = "";
                    currentHasWater = false;
                }
                event = parser.next();
            }
        } catch (Exception e) {
            AppLog.e("Navigator", "buildNavigatorMapIndex error: " + e.getMessage(), e);
        }
        return index;
    }

    /**
     * Формирует человекочитаемый ключ категории рыбалки.
     */
    private String deriveFishCategory(String name, String tooltip) {
        if (name != null && !name.trim().isEmpty()) return "Рыба: " + name.trim();
        if (tooltip != null && !tooltip.trim().isEmpty()) return "Рыба: " + tooltip.trim();
        return "Рыба: Без названия";
    }

    /**
     * Компаратор диапазонов уровней в формате "min-max".
     */
    private int compareLevelRanges(String left, String right) {
        String[] l = left.split("-");
        String[] r = right.split("-");
        int lMin = parseIntSafe(l.length > 0 ? l[0].trim() : "", Integer.MAX_VALUE);
        int rMin = parseIntSafe(r.length > 0 ? r[0].trim() : "", Integer.MAX_VALUE);
        if (lMin != rMin) return Integer.compare(lMin, rMin);
        int lMax = parseIntSafe(l.length > 1 ? l[1].trim() : "", Integer.MAX_VALUE);
        int rMax = parseIntSafe(r.length > 1 ? r[1].trim() : "", Integer.MAX_VALUE);
        if (lMax != rMax) return Integer.compare(lMax, rMax);
        return left.compareToIgnoreCase(right);
    }

    /**
     * Безопасный парсинг int с fallback-значением.
     */
    private int parseIntSafe(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception e) { return fallback; }
    }

    /**
     * Null-safe trim.
     */
    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Перевод dp в px.
     */
    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}

package ru.neverlands.abclient.ui;

import android.content.Context;
import android.content.res.AssetManager;
import android.view.Gravity;
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
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.ExtMap;

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

    private static final String CITY_SUBCATEGORY_ENTRY_DELIMITER = ";";
    private static final String CITY_SUBCATEGORY_VALUE_DELIMITER = "|";
    private static final Pattern CELL_PATTERN = Pattern.compile("(\\d+-\\d+)");

    private final Context context;
    private final AutoFunctionsManager autoFunctionsManager;
    private final Runnable onStateChanged;

    private NavigatorMapIndex mapIndexCache;

    private interface OnCellPicked {
        void onCellPicked(String cellNum);
    }

    private static final class CitySubcategory {
        final String name;
        final String[] cells;

        CitySubcategory(String name, String[] cells) {
            this.name = name;
            this.cells = cells;
        }
    }

    private static final class NavigatorMapIndex {
        final Map<String, Map<String, LinkedHashSet<String>>> botGroups = new LinkedHashMap<>();
        final Map<String, LinkedHashSet<String>> herbGroups = new LinkedHashMap<>();
        final Map<String, LinkedHashSet<String>> fishGroups = new LinkedHashMap<>();
    }

    public Navigator(Context context, AutoFunctionsManager autoFunctionsManager, Runnable onStateChanged) {
        this.context = context;
        this.autoFunctionsManager = autoFunctionsManager;
        this.onStateChanged = onStateChanged;
    }

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

        int pad = dpToPx(16);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, 0);

        String currentLoc = (AppVars.Profile != null
                && AppVars.Profile.MapLocation != null
                && !AppVars.Profile.MapLocation.isEmpty())
                ? AppVars.Profile.MapLocation : "неизвестно";

        TextView locLabel = new TextView(context);
        locLabel.setText("Текущая позиция: " + currentLoc);
        locLabel.setTextColor(0xFF666666);
        root.addView(locLabel);

        android.widget.AutoCompleteTextView destInput = new android.widget.AutoCompleteTextView(context);
        destInput.setHint("Номер клетки (напр. 8-259)");
        destInput.setSingleLine();
        destInput.setThreshold(1);
        destInput.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, buildAutocompleteValues()));
        root.addView(destInput);

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
        Runnable rerender = () -> renderTabContent(listLayout, destInput, selectedTab[0]);
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

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String cell = resolveCellNumber(destInput.getText().toString());
            if (cell.isEmpty()) {
                Toast.makeText(context, "Введите пункт назначения", Toast.LENGTH_SHORT).show();
                return;
            }
            autoFunctionsManager.startAutoMoving(cell);
            Toast.makeText(context, "Навигатор запущен → " + cell, Toast.LENGTH_SHORT).show();
            if (onStateChanged != null) onStateChanged.run();
            dialog.dismiss();
        }));

        dialog.show();
    }

    private void renderTabContent(LinearLayout parent, EditText destInput, int tabIndex) {
        if (tabIndex == TAB_BOTS) {
            renderBotTab(parent, destInput);
            return;
        }
        if (tabIndex == TAB_HERBS) {
            renderHerbTab(parent, destInput);
            return;
        }
        if (tabIndex == TAB_FISH) {
            renderFishTab(parent, destInput);
            return;
        }
        renderLocationTab(parent, destInput, () -> renderTabContent(parent, destInput, TAB_LOCATIONS));
    }

    private void renderLocationTab(LinearLayout parent, EditText destInput, Runnable rerender) {
        parent.removeAllViews();
        addLeafCategory(parent, "Избранное", NAV_CATEGORY_FAVORITES, navFavCells(), 0, destInput, rerender);

        addSectionHeader(parent, "Города", 0, () -> showAddCitySubcategoryDialog(rerender));
        for (CitySubcategory citySubcategory : getCitySubcategories()) {
            addCitySubcategory(parent, citySubcategory, dpToPx(10), destInput, rerender);
        }

        addLeafCategory(parent, "Объекты", NAV_CATEGORY_OBJECTS, navObjectCells(), 0, destInput, rerender);
        addLeafCategory(parent, "Телепорты", NAV_CATEGORY_TELEPORTS, navTelepCells(), 0, destInput, rerender);
    }

    private void renderBotTab(LinearLayout parent, EditText destInput) {
        parent.removeAllViews();
        NavigatorMapIndex index = getNavigatorMapIndex();
        if (index.botGroups.isEmpty()) {
            addEmptyHint(parent, "Нет данных ботов в map.xml");
            return;
        }

        ArrayList<String> botNames = new ArrayList<>(index.botGroups.keySet());
        Collections.sort(botNames, String.CASE_INSENSITIVE_ORDER);

        for (String botName : botNames) {
            addSectionHeader(parent, botName, 0, null);
            Map<String, LinkedHashSet<String>> levels = index.botGroups.get(botName);
            ArrayList<String> levelRanges = new ArrayList<>(levels.keySet());
            levelRanges.sort(this::compareLevelRanges);

            for (String range : levelRanges) {
                addSubHeader(parent, "Уровень " + range, dpToPx(10));
                String[] cells = sortAndNormalizeCells(levels.get(range).toArray(new String[0]));
                renderCells(parent, dpToPx(10), cells, destInput, null);
            }
        }
    }

    private void renderHerbTab(LinearLayout parent, EditText destInput) {
        parent.removeAllViews();
        NavigatorMapIndex index = getNavigatorMapIndex();
        if (index.herbGroups.isEmpty()) {
            addEmptyHint(parent, "Нет данных трав в map.xml");
            return;
        }

        ArrayList<String> groups = new ArrayList<>(index.herbGroups.keySet());
        groups.sort((a, b) -> Integer.compare(parseIntSafe(a, Integer.MAX_VALUE), parseIntSafe(b, Integer.MAX_VALUE)));

        for (String group : groups) {
            addSectionHeader(parent, "Травы №" + group, 0, null);
            String[] cells = sortAndNormalizeCells(index.herbGroups.get(group).toArray(new String[0]));
            renderCells(parent, 0, cells, destInput, null);
        }
    }

    private void renderFishTab(LinearLayout parent, EditText destInput) {
        parent.removeAllViews();
        NavigatorMapIndex index = getNavigatorMapIndex();
        if (index.fishGroups.isEmpty()) {
            addEmptyHint(parent, "Нет водных клеток в map.xml");
            return;
        }

        ArrayList<String> fishTypes = new ArrayList<>(index.fishGroups.keySet());
        Collections.sort(fishTypes, String.CASE_INSENSITIVE_ORDER);

        for (String fishType : fishTypes) {
            addSectionHeader(parent, fishType, 0, null);
            String[] cells = sortAndNormalizeCells(index.fishGroups.get(fishType).toArray(new String[0]));
            renderCells(parent, 0, cells, destInput, null);
        }
    }

    private void addSectionHeader(LinearLayout parent, String title, int leftPaddingPx, Runnable onAddClick) {
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setBackgroundColor(ContextCompat.getColor(context, R.color.ab_autoboi_group_selected_bg));
        headerRow.setPadding(leftPaddingPx + dpToPx(10), dpToPx(8), dpToPx(6), dpToPx(8));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setTextColor(ContextCompat.getColor(context, R.color.ab_autoboi_text_primary));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        headerRow.addView(titleView, titleLp);

        if (onAddClick != null) {
            ImageButton addBtn = buildIconButton(android.R.drawable.ic_input_add, 0xFF2E7D32);
            addBtn.setOnClickListener(v -> onAddClick.run());
            headerRow.addView(addBtn);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dpToPx(6);
        parent.addView(headerRow, lp);
    }

    private void addSubHeader(LinearLayout parent, String title, int leftPaddingPx) {
        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setTextColor(0xFF555555);
        tv.setPadding(leftPaddingPx + dpToPx(14), dpToPx(6), dpToPx(10), dpToPx(2));
        parent.addView(tv);
    }

    private void addLeafCategory(LinearLayout parent, String title, int categoryId, String[] cells, int leftPaddingPx, EditText destInput, Runnable rerender) {
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setBackgroundColor(ContextCompat.getColor(context, R.color.ab_autoboi_group_selected_bg));
        headerRow.setPadding(leftPaddingPx + dpToPx(10), dpToPx(8), dpToPx(6), dpToPx(8));
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headerLp.topMargin = dpToPx(6);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setTextColor(ContextCompat.getColor(context, R.color.ab_autoboi_text_primary));

        ImageButton addBtn = buildIconButton(android.R.drawable.ic_input_add, 0xFF2E7D32);
        addBtn.setOnClickListener(v -> showAddCellDialog(title, cell -> addCellToCategory(categoryId, cell), rerender));

        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        headerRow.addView(titleView, titleLp);
        headerRow.addView(addBtn);
        parent.addView(headerRow, headerLp);

        renderCells(parent, leftPaddingPx, sortAndNormalizeCells(cells), destInput, cellNum -> {
            removeCellFromCategory(categoryId, cellNum);
            if (rerender != null) rerender.run();
        });
    }
    private void addCitySubcategory(LinearLayout parent, CitySubcategory subcategory, int leftPaddingPx, EditText destInput, Runnable rerender) {
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
        parent.addView(headerRow, headerLp);

        renderCells(parent, leftPaddingPx, sortAndNormalizeCells(subcategory.cells), destInput, cellNum -> {
            removeCellFromCitySubcategory(subcategory.name, cellNum);
            if (rerender != null) rerender.run();
        });
    }

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
            item.setTextColor(0xFF0000AA);
            item.setOnClickListener(v -> destInput.setText(cellNum));
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

    private void addEmptyHint(LinearLayout parent, String message) {
        TextView empty = new TextView(context);
        empty.setText(message);
        empty.setTextColor(0xFF777777);
        empty.setPadding(dpToPx(14), dpToPx(8), dpToPx(10), dpToPx(8));
        parent.addView(empty);
    }

    private ImageButton buildIconButton(int drawableRes, int tintColor) {
        ImageButton btn = new ImageButton(context);
        btn.setImageResource(drawableRes);
        btn.setColorFilter(tintColor);
        btn.setBackgroundResource(android.R.color.transparent);
        btn.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        return btn;
    }

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

    private void showAddCitySubcategoryDialog(Runnable onChanged) {
        EditText input = new EditText(context);
        input.setHint("Название подкатегории");
        input.setSingleLine(true);

        new AlertDialog.Builder(context)
                .setTitle("Новая подкатегория: Города")
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

    private String[] buildAutocompleteValues() {
        if (ExtMap.Cells == null || ExtMap.Cells.isEmpty()) return new String[0];
        ArrayList<String> values = new ArrayList<>();
        for (String cellNum : ExtMap.Cells.keySet()) values.add(buildCellLabel(cellNum));
        Collections.sort(values);
        return values.toArray(new String[0]);
    }

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

    private String resolveCellNumber(String raw) {
        if (raw == null) return "";
        Matcher m = CELL_PATTERN.matcher(raw.trim());
        return m.find() ? m.group(1) : "";
    }

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

    private void addCellToCategory(int categoryId, String cellNum) {
        if (AppVars.Profile == null) return;
        applyCategoryCells(categoryId, appendCellToArray(getCategoryCells(categoryId), cellNum));
        AppVars.Profile.save(context);
    }

    private void removeCellFromCategory(int categoryId, String cellNum) {
        if (AppVars.Profile == null) return;
        applyCategoryCells(categoryId, removeCellFromArray(getCategoryCells(categoryId), cellNum));
        AppVars.Profile.save(context);
    }

    private String[] getCategoryCells(int categoryId) {
        if (AppVars.Profile == null) return new String[0];
        switch (categoryId) {
            case NAV_CATEGORY_FAVORITES: return navFavCells();
            case NAV_CATEGORY_CITY_VILLAGE: return navCityVillageCells();
            case NAV_CATEGORY_CITY_FORPOST: return navCityForpostCells();
            case NAV_CATEGORY_CITY_OKTAL: return navCityOktalCells();
            case NAV_CATEGORY_OBJECTS: return navObjectCells();
            case NAV_CATEGORY_TELEPORTS: return navTelepCells();
            default: return new String[0];
        }
    }

    private void applyCategoryCells(int categoryId, String[] cells) {
        if (AppVars.Profile == null) return;
        switch (categoryId) {
            case NAV_CATEGORY_FAVORITES: AppVars.Profile.FavLocations = sortAndNormalizeCells(cells); break;
            case NAV_CATEGORY_CITY_VILLAGE: AppVars.Profile.setNavCityVillageLocations(cells); break;
            case NAV_CATEGORY_CITY_FORPOST: AppVars.Profile.setNavCityForpostLocations(cells); break;
            case NAV_CATEGORY_CITY_OKTAL: AppVars.Profile.setNavCityOktalLocations(cells); break;
            case NAV_CATEGORY_OBJECTS: AppVars.Profile.setNavObjectLocations(cells); break;
            case NAV_CATEGORY_TELEPORTS: AppVars.Profile.setNavTeleportLocations(cells); break;
            default: break;
        }
    }

    private String[] navFavCells() {
        if (AppVars.Profile == null || AppVars.Profile.FavLocations == null) return new String[0];
        return sortAndNormalizeCells(AppVars.Profile.FavLocations);
    }

    private String[] navCityVillageCells() {
        if (AppVars.Profile == null || AppVars.Profile.NavCityVillageLocations == null) return new String[] {"8-197"};
        return sortAndNormalizeCells(AppVars.Profile.NavCityVillageLocations);
    }

    private String[] navCityForpostCells() {
        if (AppVars.Profile == null || AppVars.Profile.NavCityForpostLocations == null) return new String[] {"8-259", "8-294"};
        return sortAndNormalizeCells(AppVars.Profile.NavCityForpostLocations);
    }

    private String[] navCityOktalCells() {
        if (AppVars.Profile == null || AppVars.Profile.NavCityOktalLocations == null) return new String[] {"12-428", "12-494", "12-521"};
        return sortAndNormalizeCells(AppVars.Profile.NavCityOktalLocations);
    }

    private String[] navObjectCells() {
        if (AppVars.Profile == null || AppVars.Profile.NavObjectLocations == null) return new String[] {"8-227", "2-482", "9-494", "26-430"};
        return sortAndNormalizeCells(AppVars.Profile.NavObjectLocations);
    }

    private String[] navTelepCells() {
        if (AppVars.Profile != null && AppVars.Profile.NavTeleportLocations != null && AppVars.Profile.NavTeleportLocations.length > 0) {
            return sortAndNormalizeCells(AppVars.Profile.NavTeleportLocations);
        }
        if (ExtMap.Teleports == null || ExtMap.Teleports.isEmpty()) return new String[0];
        String[] keys = ExtMap.Teleports.keySet().toArray(new String[0]);
        Arrays.sort(keys);
        return keys;
    }

    private List<CitySubcategory> getCitySubcategories() {
        if (AppVars.Profile == null) return buildDefaultCitySubcategories();
        List<CitySubcategory> parsed = parseCitySubcategories(AppVars.Profile.NavCitySubcategories);
        return parsed.isEmpty() ? buildDefaultCitySubcategories() : parsed;
    }

    private List<CitySubcategory> buildDefaultCitySubcategories() {
        ArrayList<CitySubcategory> defaults = new ArrayList<>();
        defaults.add(new CitySubcategory("Деревня", navCityVillageCells()));
        defaults.add(new CitySubcategory("Форпост", navCityForpostCells()));
        defaults.add(new CitySubcategory("Октал", navCityOktalCells()));
        return defaults;
    }

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

    private String sanitizeCitySubcategoryName(String name) {
        if (name == null) return "";
        String sanitized = name.replace(CITY_SUBCATEGORY_ENTRY_DELIMITER, " ")
                .replace(CITY_SUBCATEGORY_VALUE_DELIMITER, " ")
                .replace(",", " ").trim();
        while (sanitized.contains("  ")) sanitized = sanitized.replace("  ", " ");
        return sanitized;
    }

    private int findCitySubcategoryIndex(List<CitySubcategory> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            CitySubcategory s = list.get(i);
            if (s != null && s.name != null && s.name.equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

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

    private void removeCitySubcategory(String name) {
        if (AppVars.Profile == null) return;
        List<CitySubcategory> subcategories = getCitySubcategories();
        int index = findCitySubcategoryIndex(subcategories, name);
        if (index < 0) return;
        subcategories.remove(index);
        saveCitySubcategories(subcategories);
    }

    private void addCellToCitySubcategory(String subcategoryName, String cellNum) {
        if (AppVars.Profile == null) return;
        List<CitySubcategory> subcategories = getCitySubcategories();
        int index = findCitySubcategoryIndex(subcategories, subcategoryName);
        if (index < 0) return;
        CitySubcategory existing = subcategories.get(index);
        subcategories.set(index, new CitySubcategory(existing.name, appendCellToArray(existing.cells, cellNum)));
        saveCitySubcategories(subcategories);
    }

    private void removeCellFromCitySubcategory(String subcategoryName, String cellNum) {
        if (AppVars.Profile == null) return;
        List<CitySubcategory> subcategories = getCitySubcategories();
        int index = findCitySubcategoryIndex(subcategories, subcategoryName);
        if (index < 0) return;
        CitySubcategory existing = subcategories.get(index);
        subcategories.set(index, new CitySubcategory(existing.name, removeCellFromArray(existing.cells, cellNum)));
        saveCitySubcategories(subcategories);
    }

    private void saveCitySubcategories(List<CitySubcategory> subcategories) {
        if (AppVars.Profile == null) return;
        AppVars.Profile.NavCitySubcategories = encodeCitySubcategories(subcategories);
        AppVars.Profile.save(context);
    }

    private NavigatorMapIndex getNavigatorMapIndex() {
        if (mapIndexCache != null) return mapIndexCache;
        mapIndexCache = buildNavigatorMapIndex();
        return mapIndexCache;
    }

    private NavigatorMapIndex buildNavigatorMapIndex() {
        NavigatorMapIndex index = new NavigatorMapIndex();
        AssetManager assets = context.getAssets();
        try (InputStream in = assets.open("map.xml")) {
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
                                index.herbGroups.computeIfAbsent(group, k -> new LinkedHashSet<>()).add(currentCell);
                            }
                            if (currentHasWater) {
                                String fishKey = deriveFishCategory(currentName, currentTooltip);
                                index.fishGroups.computeIfAbsent(fishKey, k -> new LinkedHashSet<>()).add(currentCell);
                            }
                        }
                    } else if ("bots".equalsIgnoreCase(tag) && currentCell != null && !currentCell.isEmpty()) {
                        String botName = safeTrim(parser.getAttributeValue(null, "name"));
                        String min = safeTrim(parser.getAttributeValue(null, "minLevel"));
                        String max = safeTrim(parser.getAttributeValue(null, "maxLevel"));
                        if (!botName.isEmpty()) {
                            String range = (min.isEmpty() ? "?" : min) + "-" + (max.isEmpty() ? "?" : max);
                            index.botGroups
                                    .computeIfAbsent(botName, k -> new LinkedHashMap<>())
                                    .computeIfAbsent(range, k -> new LinkedHashSet<>())
                                    .add(currentCell);
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
            android.util.Log.e("Navigator", "buildNavigatorMapIndex error: " + e.getMessage(), e);
        }
        return index;
    }

    private String deriveFishCategory(String name, String tooltip) {
        if (name != null && !name.trim().isEmpty()) return "Рыба: " + name.trim();
        if (tooltip != null && !tooltip.trim().isEmpty()) return "Рыба: " + tooltip.trim();
        return "Рыба: Без названия";
    }

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

    private int parseIntSafe(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception e) { return fallback; }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
package ru.neverlands.anclient;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ru.neverlands.anclient.info.ProfessionRatingRepository;
import ru.neverlands.anclient.info.RecipeDatabase;
import ru.neverlands.anclient.utils.AppLog;

/**
 * Справочник рецептов с разделами по исходным HTML-таблицам.
 */
public class TablesActivity extends AppCompatActivity {
    public static final String EXTRA_IMAGE_FILE = "ru.neverlands.anclient.extra.RECIPE_IMAGE_FILE";
    public static final String EXTRA_SECTION_KEY = "ru.neverlands.anclient.extra.RECIPE_SECTION_KEY";
    public static final String EXTRA_ITEM_NAME = "ru.neverlands.anclient.extra.RECIPE_ITEM_NAME";

    private static final String TAG = "TablesActivity";
    private static final String PROF_RATINGS_SECTION_KEY = "profession_ratings";
    private static final int ACTION_COLOR = Color.rgb(74, 91, 202);
    private static final int ACTION_COLOR_DARK = Color.rgb(42, 57, 146);
    private static final int TITLE_COLOR = Color.rgb(30, 35, 45);
    private static final int MUTED_COLOR = Color.rgb(82, 92, 115);

    private LinearLayout rootLayout;
    private LinearLayout sectionBar;
    private LinearLayout contentLayout;
    private TextView statusView;
    private EditText searchInput;

    private RecipeDatabase database;
    private String selectedSectionKey;
    private String launchImageFile;
    private String launchItemName;
    private RecipeDatabase.RecipeItem selectedItem;
    private final List<RecipeDatabase.RecipeItem> detailBackStack = new ArrayList<>();
    private int selectedRatingCategoryId = 10;
    private ProfessionRatingRepository.RatingTable selectedRatingTable;
    private boolean ratingLoading;
    private String ratingError = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Таблицы");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        launchImageFile = RecipeDatabase.normalizeImageFile(getIntent().getStringExtra(EXTRA_IMAGE_FILE));
        launchItemName = getIntent().getStringExtra(EXTRA_ITEM_NAME);
        selectedSectionKey = getIntent().getStringExtra(EXTRA_SECTION_KEY);
        buildLayout();
        loadDatabase();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void buildLayout() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dp(14), dp(14), dp(14), dp(14));
        rootLayout.setBackgroundColor(Color.rgb(235, 241, 255));

        HorizontalScrollView sectionScroll = new HorizontalScrollView(this);
        sectionScroll.setHorizontalScrollBarEnabled(false);
        sectionBar = new LinearLayout(this);
        sectionBar.setOrientation(LinearLayout.HORIZONTAL);
        sectionScroll.addView(sectionBar);
        rootLayout.addView(sectionScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        statusView = text("Загрузка базы рецептов...", 14, Typeface.NORMAL, MUTED_COLOR);
        statusView.setPadding(dp(12), dp(10), dp(12), dp(10));
        statusView.setBackground(cardBackground(Color.WHITE, Color.rgb(216, 225, 245), dp(14)));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(10), 0, dp(10));
        rootLayout.addView(statusView, statusParams);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Поиск по названию, gif или ресурсам");
        searchInput.setTextColor(TITLE_COLOR);
        searchInput.setHintTextColor(Color.rgb(130, 139, 158));
        searchInput.setTextSize(14);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setPadding(dp(14), 0, dp(14), 0);
        searchInput.setBackground(cardBackground(Color.WHITE, Color.rgb(203, 214, 241), dp(18)));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                selectedItem = null;
                detailBackStack.clear();
                renderContent();
            }
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        searchParams.setMargins(0, 0, 0, dp(10));
        rootLayout.addView(searchInput, searchParams);

        ScrollView scrollView = new ScrollView(this);
        contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(contentLayout);
        rootLayout.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(rootLayout);
    }

    private void loadDatabase() {
        new Thread(() -> {
            try {
                RecipeDatabase loaded = RecipeDatabase.load(this);
                database = loaded;
                if (selectedSectionKey == null
                        || (!isProfessionRatingsSection() && loaded.findSection(selectedSectionKey) == null)) {
                    selectedSectionKey = loaded.getFirstSectionKey();
                }
                if (!launchImageFile.isEmpty()) {
                    List<RecipeDatabase.RecipeItem> matches = loaded.findByImageFile(launchImageFile);
                    if (!matches.isEmpty()) {
                        selectedSectionKey = matches.get(0).sectionKey;
                        selectedItem = chooseItemByName(matches, launchItemName);
                    }
                }
                runOnUiThread(() -> {
                    renderSections();
                    renderContent();
                });
            } catch (Exception error) {
                AppLog.w(TAG, "TABLES_LOAD_FAILED", error);
                runOnUiThread(() -> {
                    statusView.setText("База рецептов не загружена: " + error.getMessage());
                    renderContent();
                });
            }
        }).start();
    }

    private RecipeDatabase.RecipeItem chooseItemByName(List<RecipeDatabase.RecipeItem> matches, String requestedNameRaw) {
        if (matches.size() == 1) {
            return matches.get(0);
        }
        String requestedName = normalizeForCompare(requestedNameRaw);
        if (!requestedName.isEmpty()) {
            for (RecipeDatabase.RecipeItem item : matches) {
                if (normalizeForCompare(item.name).equals(requestedName)) {
                    return item;
                }
            }
            for (RecipeDatabase.RecipeItem item : matches) {
                String itemName = normalizeForCompare(item.name);
                if (itemName.contains(requestedName) || requestedName.contains(itemName)) {
                    return item;
                }
            }
            String requestedKey = normalizeNameKey(requestedNameRaw);
            if (!requestedKey.isEmpty()) {
                for (RecipeDatabase.RecipeItem item : matches) {
                    String itemKey = normalizeNameKey(item.name);
                    if (itemKey.equals(requestedKey) || itemKey.contains(requestedKey) || requestedKey.contains(itemKey)) {
                        return item;
                    }
                }
            }
        }
        return null;
    }

    private String normalizeForCompare(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ")
                .trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNameKey(String value) {
        String normalized = normalizeForCompare(value);
        int colonIndex = normalized.indexOf(':');
        if (colonIndex > 0) {
            normalized = normalized.substring(0, colonIndex);
        }
        return normalized.replaceAll("\\([^)]*\\)", " ")
                .replaceAll("[0-9.,:;\\[\\]{}<>/\\\\|+=_-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void renderSections() {
        sectionBar.removeAllViews();
        if (database == null) {
            return;
        }
        Button ratingsButton = tabButton("Рейтинги Проф (" + ProfessionRatingRepository.getCategories().size() + ")",
                isProfessionRatingsSection());
        ratingsButton.setOnClickListener(v -> {
            selectedSectionKey = PROF_RATINGS_SECTION_KEY;
            launchImageFile = "";
            selectedItem = null;
            detailBackStack.clear();
            if (searchInput != null && searchInput.length() > 0) {
                searchInput.setText("");
            }
            renderSections();
            renderContent();
        });
        LinearLayout.LayoutParams ratingsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(46));
        ratingsParams.setMargins(0, 0, dp(8), 0);
        sectionBar.addView(ratingsButton, ratingsParams);
        for (RecipeDatabase.RecipeSection section : database.getSections()) {
            Button button = tabButton(section.title + " (" + section.items.size() + ")",
                    section.key.equals(selectedSectionKey));
            button.setOnClickListener(v -> {
                selectedSectionKey = section.key;
                launchImageFile = "";
                selectedItem = null;
                detailBackStack.clear();
                if (searchInput != null && searchInput.length() > 0) {
                    searchInput.setText("");
                }
                renderSections();
                renderContent();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(46));
            params.setMargins(0, 0, dp(8), 0);
            sectionBar.addView(button, params);
        }
    }

    private Button tabButton(String title, boolean active) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        styleButton(button, active ? ButtonStyle.PRIMARY : ButtonStyle.SECONDARY);
        return button;
    }

    private void renderContent() {
        if (contentLayout == null) {
            return;
        }
        contentLayout.removeAllViews();
        if (isProfessionRatingsSection()) {
            renderProfessionRatingsContent();
            return;
        }
        if (searchInput != null) {
            searchInput.setHint("Поиск по названию, gif или ресурсам");
        }
        if (database == null) {
            contentLayout.addView(heroCard("Таблицы", "База рецептов загружается из assets/info/tables."));
            return;
        }
        if (selectedItem != null) {
            renderDetail(selectedItem);
            return;
        }
        List<RecipeDatabase.RecipeItem> baseItems;
        String title;
        if (!launchImageFile.isEmpty()) {
            baseItems = new ArrayList<>(database.findByImageFile(launchImageFile));
            title = "Совпадения по " + launchImageFile;
        } else {
            RecipeDatabase.RecipeSection section = database.findSection(selectedSectionKey);
            baseItems = section == null ? new ArrayList<>() : new ArrayList<>(section.items);
            title = section == null ? "Раздел" : section.title;
        }
        List<RecipeDatabase.RecipeItem> items = filterItems(baseItems);
        statusView.setText(title + ": " + items.size() + " из " + baseItems.size()
                + " | база: files/info/recipe_tables_db.tsv");
        contentLayout.addView(heroCard(title, "Откройте предмет, чтобы увидеть ресурсы, инструменты и все поля исходной строки."));
        if (items.isEmpty()) {
            contentLayout.addView(cardText("Ничего не найдено", "Измените раздел или строку поиска."));
            return;
        }
        for (RecipeDatabase.RecipeItem item : items) {
            contentLayout.addView(itemCard(item));
        }
    }

    private boolean isProfessionRatingsSection() {
        return PROF_RATINGS_SECTION_KEY.equals(selectedSectionKey);
    }

    private void renderProfessionRatingsContent() {
        if (searchInput != null) {
            searchInput.setHint("Поиск по нику, уровню, очкам или клану");
        }
        ProfessionRatingRepository.Category category = ProfessionRatingRepository.findCategory(selectedRatingCategoryId);
        if (category == null && !ProfessionRatingRepository.getCategories().isEmpty()) {
            category = ProfessionRatingRepository.getCategories().get(0);
            selectedRatingCategoryId = category.id;
        }

        statusView.setText("Рейтинги Проф: еженедельные Top-листы service.neverlands.ru");
        contentLayout.addView(heroCard("Рейтинги Проф",
                "Справочник weekly-рейтингов профессий и боевых номинаций. Данные загружаются из service.neverlands.ru/rate."));
        contentLayout.addView(ratingCategoryBar());

        if (category == null) {
            contentLayout.addView(cardText("Рейтинги недоступны", "Список категорий пуст."));
            return;
        }

        Button refreshButton = tabButton("Обновить: " + category.title, false);
        refreshButton.setOnClickListener(v -> {
            loadSelectedRating(true);
            renderContent();
        });
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        refreshParams.setMargins(0, 0, 0, dp(10));
        contentLayout.addView(refreshButton, refreshParams);

        ProfessionRatingRepository.RatingTable cached = ProfessionRatingRepository.getCachedRating(category.id);
        if (selectedRatingTable == null || selectedRatingTable.category.id != category.id) {
            selectedRatingTable = cached;
        }
        if (selectedRatingTable == null && !ratingLoading) {
            loadSelectedRating(false);
        }

        if (ratingLoading) {
            contentLayout.addView(cardText("Загрузка рейтинга", category.title + " загружается из weekly_" + category.id + ".txt."));
            return;
        }
        if (!ratingError.isEmpty() && selectedRatingTable == null) {
            contentLayout.addView(cardText("Рейтинг не загружен", ratingError));
            return;
        }
        if (selectedRatingTable == null) {
            contentLayout.addView(cardText("Рейтинг не загружен", "Нажмите обновить."));
            return;
        }

        List<ProfessionRatingRepository.RatingEntry> entries = filterRatingEntries(selectedRatingTable.entries);
        String loadedAt = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date(selectedRatingTable.loadedAtMs));
        statusView.setText(category.title + ": " + entries.size() + " из " + selectedRatingTable.entries.size()
                + " | источник: weekly_" + category.id + ".txt | " + loadedAt);
        contentLayout.addView(sectionTitle(category.title + " | " + selectedRatingTable.sourceUrl));
        if (entries.isEmpty()) {
            contentLayout.addView(cardText("Ничего не найдено", "Измените строку поиска или выберите другую категорию."));
            return;
        }
        for (ProfessionRatingRepository.RatingEntry entry : entries) {
            contentLayout.addView(ratingEntryRow(entry));
        }
    }

    private View ratingCategoryBar() {
        HorizontalScrollView scrollView = new HorizontalScrollView(this);
        scrollView.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scrollView.addView(row);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scrollParams.setMargins(0, 0, 0, dp(10));
        scrollView.setLayoutParams(scrollParams);

        for (ProfessionRatingRepository.Category category : ProfessionRatingRepository.getCategories()) {
            Button button = tabButton(category.title, category.id == selectedRatingCategoryId);
            button.setOnClickListener(v -> {
                selectedRatingCategoryId = category.id;
                selectedRatingTable = ProfessionRatingRepository.getCachedRating(category.id);
                ratingError = "";
                renderContent();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(44));
            params.setMargins(0, 0, dp(8), 0);
            row.addView(button, params);
        }
        return scrollView;
    }

    private void loadSelectedRating(boolean forceRefresh) {
        if (ratingLoading) {
            return;
        }
        ProfessionRatingRepository.Category category = ProfessionRatingRepository.findCategory(selectedRatingCategoryId);
        if (category == null) {
            ratingError = "Неизвестная категория рейтинга.";
            return;
        }
        ratingLoading = true;
        ratingError = "";
        new Thread(() -> {
            try {
                ProfessionRatingRepository.RatingTable table = ProfessionRatingRepository.loadRating(category.id, forceRefresh);
                runOnUiThread(() -> {
                    selectedRatingTable = table;
                    ratingLoading = false;
                    renderContent();
                });
            } catch (Exception error) {
                AppLog.w(TAG, "WEEKLY_RATING_UI_LOAD_FAILED: id=" + category.id, error);
                runOnUiThread(() -> {
                    ratingError = error.getMessage() == null ? "Ошибка загрузки" : error.getMessage();
                    ratingLoading = false;
                    renderContent();
                });
            }
        }, "prof-rating-ui-" + category.id).start();
    }

    private List<ProfessionRatingRepository.RatingEntry> filterRatingEntries(
            List<ProfessionRatingRepository.RatingEntry> entries) {
        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return entries;
        }
        List<ProfessionRatingRepository.RatingEntry> result = new ArrayList<>();
        for (ProfessionRatingRepository.RatingEntry entry : entries) {
            if (contains(entry.nick, query)
                    || contains(entry.clanIco, query)
                    || String.valueOf(entry.level).contains(query)
                    || String.valueOf(entry.rate).contains(query)
                    || String.valueOf(entry.rank).contains(query)) {
                result.add(entry);
            }
        }
        return result;
    }

    private View ratingEntryRow(ProfessionRatingRepository.RatingEntry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(cardBackground(Color.WHITE, Color.rgb(209, 219, 242), dp(18)));
        row.setElevation(dp(3));
        row.setClickable(true);
        row.setOnClickListener(v -> openPinfo(entry.nick));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(4), 0, dp(8));
        row.setLayoutParams(rowParams);

        TextView rankView = text(entry.rank + ".", 15, Typeface.BOLD, ACTION_COLOR_DARK);
        row.addView(rankView, new LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.WRAP_CONTENT));

        addRatingIcon(row, ProfessionRatingRepository.buildTotemIconUrl(entry.clanTotem));
        addRatingIcon(row, ProfessionRatingRepository.buildClanIconUrl(entry.clanIco));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.addView(text(entry.nick + " [" + entry.level + "]", 16, Typeface.BOLD, TITLE_COLOR));
        TextView meta = text("Очки рейтинга: " + entry.rate + " | clanIco: " + entry.clanIco,
                12, Typeface.NORMAL, MUTED_COLOR);
        meta.setPadding(0, dp(3), 0, 0);
        textColumn.addView(meta);
        row.addView(textColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageView infoIcon = imageView(dp(20), dp(20));
        infoIcon.setPadding(dp(2), dp(2), dp(2), dp(2));
        infoIcon.setOnClickListener(v -> openPinfo(entry.nick));
        Glide.with(this).load(ProfessionRatingRepository.getInfoIconUrl()).into(infoIcon);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        infoParams.setMargins(dp(8), 0, dp(8), 0);
        row.addView(infoIcon, infoParams);

        TextView rateView = text("(" + entry.rate + ")", 15, Typeface.BOLD, Color.rgb(35, 128, 73));
        row.addView(rateView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void addRatingIcon(LinearLayout row, String url) {
        ImageView icon = imageView(dp(18), dp(18));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(24), dp(24));
        params.setMargins(0, 0, dp(5), 0);
        row.addView(icon, params);
        if (url == null || url.isEmpty()) {
            icon.setVisibility(View.INVISIBLE);
        } else {
            Glide.with(this).load(url).into(icon);
        }
    }

    private void openPinfo(String nick) {
        Intent intent = new Intent(this, PinfoActivity.class);
        intent.putExtra("url", ProfessionRatingRepository.buildPinfoUrl(nick));
        startActivity(intent);
    }

    private List<RecipeDatabase.RecipeItem> filterItems(List<RecipeDatabase.RecipeItem> items) {
        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return items;
        }
        List<RecipeDatabase.RecipeItem> result = new ArrayList<>();
        for (RecipeDatabase.RecipeItem item : items) {
            if (matches(item, query)) {
                result.add(item);
            }
        }
        return result;
    }

    private boolean matches(RecipeDatabase.RecipeItem item, String query) {
        if (contains(item.name, query) || contains(item.itemImageFile, query) || contains(item.sectionTitle, query)) {
            return true;
        }
        for (RecipeDatabase.RecipeField field : item.fields) {
            if (contains(field.name, query) || contains(field.value, query)) {
                return true;
            }
        }
        for (RecipeDatabase.RecipeResource resource : item.resources) {
            if (contains(resource.imageFile, query) || contains(resource.label, query)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private View itemCard(RecipeDatabase.RecipeItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(cardBackground(Color.WHITE, Color.rgb(209, 219, 242), dp(20)));
        card.setElevation(dp(4));
        card.setClickable(true);
        card.setOnClickListener(v -> openDetailItem(item, false));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, dp(6), 0, dp(10));
        card.setLayoutParams(cardParams);

        ImageView image = imageView(dp(58), dp(58));
        LinearLayout imageBox = imageBox(dp(76), dp(76));
        imageBox.addView(image, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        imageParams.setMargins(0, 0, dp(14), 0);
        card.addView(imageBox, imageParams);
        Glide.with(this).load(item.itemImageUrl).into(image);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.addView(text(item.name, 16, Typeface.BOLD, TITLE_COLOR));
        TextView meta = text(item.sectionTitle + " | " + item.itemImageFile, 13, Typeface.NORMAL, MUTED_COLOR);
        meta.setPadding(0, dp(3), 0, 0);
        textColumn.addView(meta);
        TextView summary = text(summary(item), 13, Typeface.NORMAL, Color.rgb(72, 80, 94));
        summary.setPadding(0, dp(5), 0, 0);
        textColumn.addView(summary);
        card.addView(textColumn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }

    private String summary(RecipeDatabase.RecipeItem item) {
        StringBuilder builder = new StringBuilder();
        int added = 0;
        for (RecipeDatabase.RecipeField field : item.fields) {
            String key = field.name.toLowerCase(Locale.ROOT);
            if (key.contains("умение") || key.contains("уров") || key.contains("гос")
                    || key.contains("время") || key.contains("эффект") || key.contains("долговеч")) {
                if (builder.length() > 0) {
                    builder.append("; ");
                }
                builder.append(field.name).append(": ").append(field.value);
                added++;
                if (added >= 2) {
                    break;
                }
            }
        }
        if (builder.length() == 0) {
            builder.append("Ресурсов/инструментов: ").append(item.resources.size());
        }
        return builder.toString();
    }

    private void renderDetail(RecipeDatabase.RecipeItem item) {
        Button backButton = tabButton("Назад к списку", false);
        backButton.setOnClickListener(v -> {
            if (detailBackStack.isEmpty()) {
                selectedItem = null;
            } else {
                selectedItem = detailBackStack.remove(detailBackStack.size() - 1);
                selectedSectionKey = selectedItem.sectionKey;
            }
            renderSections();
            renderContent();
        });
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        backParams.setMargins(0, 0, 0, dp(10));
        contentLayout.addView(backButton, backParams);

        statusView.setText(item.name + " | " + item.sectionTitle + " | " + item.itemImageFile);
        contentLayout.addView(detailHeader(item));

        TextView resourcesTitle = sectionTitle("Ресурсы и инструменты");
        contentLayout.addView(resourcesTitle);
        if (item.resources.isEmpty()) {
            contentLayout.addView(cardText("Ресурсы", "В исходной строке ресурсы не распознаны как изображения."));
        } else {
            for (RecipeDatabase.RecipeResource resource : item.resources) {
                contentLayout.addView(resourceRow(resource));
            }
        }

        contentLayout.addView(sectionTitle("Все поля исходной таблицы"));
        for (RecipeDatabase.RecipeField field : item.fields) {
            contentLayout.addView(fieldRow(field));
        }
    }

    private View detailHeader(RecipeDatabase.RecipeItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(cardBackground(Color.rgb(32, 48, 96), Color.rgb(32, 48, 96), dp(22)));
        card.setElevation(dp(5));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(4), 0, dp(12));
        card.setLayoutParams(params);

        ImageView image = imageView(dp(72), dp(72));
        LinearLayout imageBox = imageBox(dp(90), dp(90));
        imageBox.setBackground(cardBackground(Color.rgb(240, 244, 255), Color.rgb(147, 163, 222), dp(18)));
        imageBox.addView(image, new LinearLayout.LayoutParams(dp(72), dp(72)));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(90), dp(90));
        imageParams.setMargins(0, 0, dp(14), 0);
        card.addView(imageBox, imageParams);
        Glide.with(this).load(item.itemImageUrl).into(image);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.addView(text(item.name, 20, Typeface.BOLD, Color.WHITE));
        TextView meta = text(item.sectionTitle + "\n" + item.itemImageFile
                        + "\nИсточник: " + item.sourceAsset,
                13, Typeface.NORMAL, Color.rgb(218, 226, 255));
        meta.setPadding(0, dp(5), 0, 0);
        textColumn.addView(meta);
        card.addView(textColumn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }

    private View resourceRow(RecipeDatabase.RecipeResource resource) {
        RecipeDatabase.RecipeItem linkedItem = findLinkedResourceItem(resource);
        boolean linked = linkedItem != null;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        row.setBackground(cardBackground(Color.WHITE,
                linked ? Color.rgb(118, 133, 220) : Color.rgb(224, 231, 247), dp(16)));
        if (linked) {
            row.setClickable(true);
            row.setOnClickListener(v -> openDetailItem(linkedItem, true));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(4), 0, dp(6));
        row.setLayoutParams(params);

        ImageView image = imageView(dp(36), dp(36));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        imageParams.setMargins(0, 0, dp(10), 0);
        row.addView(image, imageParams);
        Glide.with(this).load(resource.imageUrl).into(image);

        String label = resource.label == null || resource.label.trim().isEmpty()
                ? resource.imageFile : resource.label;
        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.addView(text(label, 14, Typeface.NORMAL, Color.rgb(55, 64, 82)));
        if (linked) {
            TextView link = text("Открыть: " + linkedItem.name, 12, Typeface.BOLD, ACTION_COLOR_DARK);
            link.setPadding(0, dp(3), 0, 0);
            textColumn.addView(link);
        }
        row.addView(textColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private RecipeDatabase.RecipeItem findLinkedResourceItem(RecipeDatabase.RecipeResource resource) {
        if (database == null || resource == null) {
            return null;
        }
        String resourceUrl = resource.imageUrl == null ? "" : resource.imageUrl.toLowerCase(Locale.ROOT);
        if (resourceUrl.contains("/tools/")) {
            return null;
        }
        List<RecipeDatabase.RecipeItem> matches = database.findByImageFile(resource.imageFile);
        if (matches.isEmpty()) {
            return null;
        }
        RecipeDatabase.RecipeItem item = chooseItemByName(matches, resource.label);
        return item == null ? matches.get(0) : item;
    }

    private void openDetailItem(RecipeDatabase.RecipeItem item, boolean keepCurrentInBackStack) {
        if (item == null) {
            return;
        }
        if (keepCurrentInBackStack && selectedItem != null && selectedItem != item) {
            detailBackStack.add(selectedItem);
        } else if (!keepCurrentInBackStack) {
            detailBackStack.clear();
        }
        selectedItem = item;
        selectedSectionKey = item.sectionKey;
        launchImageFile = "";
        launchItemName = null;
        renderSections();
        renderContent();
    }

    private View fieldRow(RecipeDatabase.RecipeField field) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(13), dp(10), dp(13), dp(10));
        row.setBackground(cardBackground(Color.rgb(248, 250, 255), Color.rgb(226, 233, 249), dp(14)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(3), 0, dp(6));
        row.setLayoutParams(params);
        row.addView(text(field.name, 12, Typeface.BOLD, ACTION_COLOR_DARK));
        TextView value = text(field.value, 14, Typeface.NORMAL, Color.rgb(55, 64, 82));
        value.setPadding(0, dp(3), 0, 0);
        row.addView(value);
        return row;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 18, Typeface.BOLD, TITLE_COLOR);
        view.setPadding(0, dp(12), 0, dp(6));
        return view;
    }

    private View heroCard(String title, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(cardBackground(Color.rgb(32, 48, 96), Color.rgb(32, 48, 96), dp(22)));
        card.setElevation(dp(4));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(12));
        card.setLayoutParams(params);
        card.addView(text(title, 19, Typeface.BOLD, Color.WHITE));
        TextView bodyView = text(body, 13, Typeface.NORMAL, Color.rgb(218, 226, 255));
        bodyView.setPadding(0, dp(5), 0, 0);
        card.addView(bodyView);
        return card;
    }

    private TextView cardText(String title, String body) {
        TextView view = text(title + "\n" + body, 15, Typeface.NORMAL, TITLE_COLOR);
        view.setPadding(dp(16), dp(14), dp(16), dp(14));
        view.setBackground(cardBackground(Color.WHITE, Color.rgb(216, 225, 245), dp(18)));
        view.setElevation(dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(10));
        view.setLayoutParams(params);
        return view;
    }

    private ImageView imageView(int width, int height) {
        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return image;
    }

    private LinearLayout imageBox(int width, int height) {
        LinearLayout imageBox = new LinearLayout(this);
        imageBox.setGravity(Gravity.CENTER);
        imageBox.setBackground(cardBackground(Color.rgb(240, 244, 255), Color.rgb(214, 224, 248), dp(16)));
        imageBox.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return imageBox;
    }

    private TextView text(String value, int sp, int style, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable cardBackground(int fillColor, int strokeColor, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void styleButton(Button button, ButtonStyle style) {
        int fillColor = style == ButtonStyle.PRIMARY ? ACTION_COLOR : Color.rgb(247, 249, 255);
        int strokeColor = style == ButtonStyle.PRIMARY ? ACTION_COLOR_DARK : Color.rgb(183, 195, 229);
        int textColor = style == ButtonStyle.PRIMARY ? Color.WHITE : ACTION_COLOR_DARK;
        button.setSelected(style == ButtonStyle.PRIMARY);
        button.setTextColor(textColor);
        button.setTextSize(13);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackgroundTintList(null);
        button.setBackground(cardBackground(fillColor, strokeColor, dp(22)));
        button.setBackgroundTintList(null);
        button.setElevation(style == ButtonStyle.PRIMARY ? dp(5) : dp(2));
        button.refreshDrawableState();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private enum ButtonStyle {
        PRIMARY,
        SECONDARY
    }
}

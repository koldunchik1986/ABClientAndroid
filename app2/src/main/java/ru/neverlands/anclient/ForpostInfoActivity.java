package ru.neverlands.anclient;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import ru.neverlands.anclient.info.RecipeDatabase;
import ru.neverlands.anclient.network.NetworkClient;
import ru.neverlands.anclient.proxy.CookiesManager;
import ru.neverlands.anclient.utils.AppLog;

/**
 * Порт WinForms-справочников APIForms/CityHall.cs и APIForms/ForpostBuildings.cs.
 */
public class ForpostInfoActivity extends AppCompatActivity {
    private static final String TAG = "ForpostInfoActivity";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final String IMAGE_WEAPON_BASE_URL = "http://image.neverlands.ru/weapon/";
    private static final int TITLE_COLOR_DEFAULT = Color.rgb(30, 35, 45);
    private static final int TITLE_COLOR_COMPLETE = Color.rgb(31, 142, 68);
    private static final int TITLE_COLOR_INCOMPLETE = Color.rgb(198, 40, 40);
    private static final int ACTION_COLOR = Color.rgb(74, 91, 202);
    private static final int ACTION_COLOR_DARK = Color.rgb(42, 57, 146);
    private static final String CITY_HALL_URL = "http://service.neverlands.ru/info/cityhall_%d.txt";
    private static final String FORPOST_CITY_API_URL = "http://neverlands.ru/modules/api/getcity.cgi?city1";

    private LinearLayout rootLayout;
    private LinearLayout tabBar;
    private LinearLayout contentLayout;
    private TextView statusView;
    private int selectedTab = 0;
    private int selectedCityHallId = 1;
    private CityHallData cityHall1;
    private CityHallData cityHall2;
    private ForpostData forpostData;
    private RecipeDatabase recipeDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Постройки");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        buildLayout();
        loadAllData();
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

        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        rootLayout.addView(tabBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        statusView = text("Загрузка справочников...", 14, Typeface.NORMAL, Color.rgb(80, 86, 96));
        statusView.setPadding(dp(12), dp(10), dp(12), dp(10));
        statusView.setBackground(cardBackground(Color.rgb(255, 255, 255), Color.rgb(216, 225, 245), dp(14)));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(10), 0, dp(10));
        statusView.setLayoutParams(statusParams);
        rootLayout.addView(statusView);

        ScrollView scrollView = new ScrollView(this);
        contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(contentLayout);
        rootLayout.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(rootLayout);
        renderTabs();
        renderSelectedTab();
    }

    private void renderTabs() {
        tabBar.removeAllViews();
        tabBar.addView(tabButton("Ратуша", 0));
        tabBar.addView(tabButton("Здания", 1));
    }

    private Button tabButton(String title, int tabIndex) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        styleButton(button, selectedTab == tabIndex ? ButtonStyle.PRIMARY : ButtonStyle.SECONDARY);
        button.setOnClickListener(v -> {
            selectedTab = tabIndex;
            renderTabs();
            renderSelectedTab();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void loadAllData() {
        statusView.setText("Загрузка справочников...");
        new Thread(() -> {
            CityHallData loadedCityHall1 = null;
            CityHallData loadedCityHall2 = null;
            ForpostData loadedForpost = null;
            RecipeDatabase loadedRecipeDatabase = null;
            String status;
            try {
                loadedRecipeDatabase = RecipeDatabase.load(this);
            } catch (Exception e) {
                AppLog.w(TAG, "RECIPE_DATABASE_LOAD_FAILED", e);
            }
            try {
                loadedCityHall1 = parseCityHall(fetchText(String.format(Locale.US, CITY_HALL_URL, 1)), 1);
                loadedCityHall2 = parseCityHall(fetchText(String.format(Locale.US, CITY_HALL_URL, 2)), 2);
            } catch (Exception e) {
                AppLog.w(TAG, "CITY_HALL_LOAD_FAILED", e);
            }
            try {
                loadedForpost = loadForpostData();
            } catch (Exception e) {
                AppLog.w(TAG, "FORPOST_BUILDINGS_LOAD_FAILED", e);
            }
            cityHall1 = loadedCityHall1;
            cityHall2 = loadedCityHall2;
            forpostData = loadedForpost;
            recipeDatabase = loadedRecipeDatabase;
            if ((cityHall1 != null || cityHall2 != null) && forpostData != null) {
                status = "Справочники загружены";
            } else if (cityHall1 != null || cityHall2 != null) {
                status = "Ратуша загружена, здания недоступны";
            } else if (forpostData != null) {
                status = "Здания загружены, справочник ратуши недоступен";
            } else {
                status = "Справочники недоступны. Проверьте сеть или service.neverlands.ru";
            }
            String finalStatus = status;
            runOnUiThread(() -> {
                statusView.setText(finalStatus);
                renderSelectedTab();
            });
        }).start();
    }

    private ForpostData loadForpostData() throws IOException {
        try {
            return parseForpost(fetchText(FORPOST_CITY_API_URL), FORPOST_CITY_API_URL);
        } catch (IOException error) {
            if (error.getMessage() == null || !error.getMessage().contains("HTTP 535")) {
                throw error;
            }
            AppLog.w(TAG, "GETCITY_HTTP_535: retry via WebView");
            return parseForpost(fetchTextViaWebView(FORPOST_CITY_API_URL), FORPOST_CITY_API_URL);
        }
    }

    private String fetchText(String url) throws IOException {
        OkHttpClient client = NetworkClient.getInstance();
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/plain,text/html,*/*")
                .get();
        if (url.contains("neverlands.ru")) {
            String cookie = buildBestEffortCookieHeader(url);
            if (cookie != null && !cookie.trim().isEmpty()) {
                builder.header("Cookie", cookie);
                AppLog.d(TAG, "GETCITY_COOKIE_APPLIED: bytes=" + cookie.length());
            } else {
                AppLog.w(TAG, "GETCITY_COOKIE_APPLIED: bytes=0");
            }
            builder.header("Referer", "http://neverlands.ru/main.php");
        }
        Request request = builder.build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " for " + url);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response for " + url);
            }
            String text = new String(body.bytes(), WINDOWS_1251);
            AppLog.d(TAG, "FETCH_TEXT_OK: url=" + url + ", chars=" + text.length()
                    + ", preview=" + preview(text));
            return text;
        }
    }

    private String buildBestEffortCookieHeader(String url) {
        LinkedHashMap<String, String> cookieByName = new LinkedHashMap<>();
        addCookiePairs(cookieByName, CookiesManager.obtain("neverlands.ru"));
        addCookiePairs(cookieByName, CookiesManager.obtain("www.neverlands.ru"));
        try {
            CookieManager webCookieManager = CookieManager.getInstance();
            addCookiePairs(cookieByName, webCookieManager.getCookie(url));
            addCookiePairs(cookieByName, webCookieManager.getCookie("http://neverlands.ru/"));
            addCookiePairs(cookieByName, webCookieManager.getCookie("http://www.neverlands.ru/"));
            addCookiePairs(cookieByName, webCookieManager.getCookie("http://neverlands.ru/main.php"));
        } catch (Throwable error) {
            AppLog.w(TAG, "GETCITY_COOKIE_WEBVIEW_READ_FAILED", error);
        }
        if (cookieByName.isEmpty()) {
            return "";
        }
        StringBuilder header = new StringBuilder();
        for (Map.Entry<String, String> pair : cookieByName.entrySet()) {
            if (header.length() > 0) {
                header.append("; ");
            }
            header.append(pair.getKey()).append("=").append(pair.getValue());
        }
        return header.toString();
    }

    private void addCookiePairs(Map<String, String> out, String cookieHeader) {
        if (cookieHeader == null || cookieHeader.trim().isEmpty()) {
            return;
        }
        for (String rawPair : cookieHeader.split(";")) {
            String pair = rawPair == null ? "" : rawPair.trim();
            int delimiter = pair.indexOf('=');
            if (delimiter <= 0) {
                continue;
            }
            String name = pair.substring(0, delimiter).trim();
            String value = pair.substring(delimiter + 1).trim();
            if (!name.isEmpty()) {
                out.put(name, value);
            }
        }
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    private String fetchTextViaWebView(String url) throws IOException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>("");
        AtomicReference<Throwable> errorRef = new AtomicReference<>(null);
        runOnUiThread(() -> {
            WebView webView = new WebView(this);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setUserAgentString(USER_AGENT);
            webView.setVisibility(View.GONE);
            rootLayout.addView(webView, new LinearLayout.LayoutParams(1, 1));
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String finishedUrl) {
                    view.evaluateJavascript("document.body ? document.body.innerText : document.documentElement.innerText", value -> {
                        try {
                            result.set(decodeJavascriptString(value));
                        } catch (Throwable error) {
                            errorRef.set(error);
                        } finally {
                            rootLayout.removeView(view);
                            view.destroy();
                            latch.countDown();
                        }
                    });
                }
            });
            webView.loadUrl(url);
        });
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IOException("WebView timeout for " + url);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("WebView interrupted for " + url, e);
        }
        Throwable error = errorRef.get();
        if (error != null) {
            throw new IOException("WebView decode failed for " + url, error);
        }
        String text = result.get();
        AppLog.d(TAG, "FETCH_TEXT_WEBVIEW_OK: url=" + url + ", chars=" + text.length()
                + ", preview=" + preview(text));
        return text;
    }

    private String decodeJavascriptString(String value) throws Exception {
        if (value == null || "null".equals(value)) {
            return "";
        }
        return new JSONArray("[" + value + "]").getString(0);
    }

    private CityHallData parseCityHall(String raw, int cityId) {
        CityHallData data = new CityHallData();
        data.cityId = cityId;
        data.title = cityId == 1 ? "Форпост" : "Октал";
        if (raw == null) {
            return data;
        }
        String[] lines = raw.replace("\r", "").split("\n");
        if (lines.length > 0) {
            String[] parts = lines[0].split("\\|");
            data.headerFields.addAll(Arrays.asList(parts));
        }
        if (lines.length > 1) {
            String buildingsLine = lines[1].replace("#", "").trim();
            for (String item : buildingsLine.split("@")) {
                CityBuilding building = parseCityBuilding(item);
                if (building != null) {
                    data.buildings.add(building);
                }
            }
        }
        if (lines.length > 2) {
            data.footer = lines[2].trim();
        }
        return data;
    }

    private CityBuilding parseCityBuilding(String item) {
        if (item == null || item.trim().isEmpty()) {
            return null;
        }
        String[] parts = item.split(",");
        CityBuilding building = new CityBuilding();
        building.raw = item.trim();
        building.name = part(parts, 2, part(parts, 0, "Строение"));
        building.level = part(parts, 4, "");
        building.progress = part(parts, 5, "");
        building.imageUrl = findWeaponImageUrl(parts);
        return building;
    }

    private ForpostData parseForpost(String raw, String sourceUrl) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        ForpostData data = new ForpostData();
        data.sourceUrl = sourceUrl;
        String normalized = raw.replace("\r", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)</div>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ");
        for (String line : normalized.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            ForpostBuilding building = parseForpostBuilding(trimmed);
            if (building != null) {
                data.buildings.add(building);
            }
        }
        return data;
    }

    private ForpostBuilding parseForpostBuilding(String raw) {
        String[] parts = raw.split(",", 3);
        if (parts.length < 2) {
            return null;
        }
        ForpostBuilding building = new ForpostBuilding();
        building.code = parts[0].trim();
        building.name = parts[1].trim().isEmpty() ? "Здание " + building.code : parts[1].trim();
        building.raw = raw;
        if (parts.length < 3 || parts[2].trim().isEmpty()) {
            building.broken = false;
            return building;
        }
        building.broken = true;
        for (String resourceRaw : parts[2].split("@")) {
            ForpostRepairResource resource = parseForpostRepairResource(resourceRaw);
            if (resource != null) {
                building.resources.add(resource);
            }
        }
        return building;
    }

    private ForpostRepairResource parseForpostRepairResource(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String[] parts = raw.trim().split("\\|");
        if (parts.length < 3) {
            return null;
        }
        ForpostRepairResource resource = new ForpostRepairResource();
        resource.resourceId = parts[0].trim();
        resource.required = parseInt(parts[1]);
        resource.inserted = parseInt(parts[2]);
        resource.remaining = Math.max(0, resource.required - resource.inserted);
        return resource;
    }

    private void renderSelectedTab() {
        if (contentLayout == null) {
            return;
        }
        contentLayout.removeAllViews();
        if (selectedTab == 0) {
            renderCityHall();
        } else {
            renderForpost();
        }
    }

    private void renderCityHall() {
        Button refreshButton = actionButton("Обновить");
        refreshButton.setOnClickListener(v -> loadAllData());
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        refreshParams.setMargins(0, 0, 0, dp(10));
        contentLayout.addView(refreshButton, refreshParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button city1Button = choiceButton("Форпост", selectedCityHallId == 1);
        Button city2Button = choiceButton("Октал", selectedCityHallId == 2);
        city1Button.setOnClickListener(v -> {
            selectedCityHallId = 1;
            renderSelectedTab();
        });
        city2Button.setOnClickListener(v -> {
            selectedCityHallId = 2;
            renderSelectedTab();
        });
        buttons.addView(city1Button, new LinearLayout.LayoutParams(0, dp(44), 1f));
        buttons.addView(city2Button, new LinearLayout.LayoutParams(0, dp(44), 1f));
        contentLayout.addView(buttons);
        showCityHall(selectedCityHallId == 1 ? cityHall1 : cityHall2);
    }

    private void showCityHall(CityHallData data) {
        if (data == null) {
            contentLayout.addView(cardText("Ратуша", "Данные cityhall_1.txt/cityhall_2.txt пока недоступны."));
            return;
        }
        StringBuilder header = new StringBuilder();
        for (int i = 0; i < data.headerFields.size(); i++) {
            String value = data.headerFields.get(i).trim();
            if (!value.isEmpty()) {
                header.append(cityHallHeaderLabel(i)).append(": ").append(value).append('\n');
            }
        }
        if (data.footer != null && !data.footer.isEmpty()) {
            header.append("\nДополнительно: ").append(data.footer);
        }
        contentLayout.addView(cardText(data.title, header.length() == 0 ? "Общие сведения отсутствуют" : header.toString().trim()));

        TextView section = text("Строения", 18, Typeface.BOLD, Color.rgb(35, 35, 35));
        section.setPadding(0, dp(12), 0, dp(6));
        contentLayout.addView(section);
        if (data.buildings.isEmpty()) {
            contentLayout.addView(cardText("Список пуст", "В ответе нет записей строений."));
            return;
        }
        for (CityBuilding building : sortedCityHallBuildings(data.buildings)) {
            String recipeImageFile = findRecipeImageFile(building);
            String body = "Состояние: " + emptyDash(building.level)
                    + "/" + emptyDash(building.progress)
                    + (recipeImageFile.isEmpty() ? "" : "\nРецепт: есть в разделе Таблицы")
                    + "\nИсходная запись: " + building.raw;
            contentLayout.addView(buildingCard(building.name, body, building.imageUrl,
                    buildingTitleColor(building), recipeImageFile));
        }
    }

    private String findRecipeImageFile(CityBuilding building) {
        if (building == null || recipeDatabase == null) {
            return "";
        }
        String imageFile = RecipeDatabase.normalizeImageFile(building.imageUrl);
        if (recipeDatabase.hasImageFile(imageFile)) {
            return imageFile;
        }
        return "";
    }

    private void renderForpost() {
        Button refreshButton = actionButton("Обновить");
        refreshButton.setOnClickListener(v -> loadAllData());
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        refreshParams.setMargins(0, 0, 0, dp(10));
        contentLayout.addView(refreshButton, refreshParams);

        if (forpostData == null || forpostData.buildings.isEmpty()) {
            contentLayout.addView(heroCard("Здания",
                    "API форпоста недоступен. Нажмите обновить; если браузер открывает API, "
                            + "приложение подтянет WebView-cookie при следующем запросе."));
            return;
        }
        contentLayout.addView(heroCard("Здания форпоста", "Источник: " + forpostData.sourceUrl));
        for (ForpostBuilding building : forpostData.buildings) {
            contentLayout.addView(forpostBuildingCard(building));
        }
    }

    private String buildForpostBuildingBody(ForpostBuilding building) {
        StringBuilder body = new StringBuilder();
        body.append("Код: ").append(emptyDash(building.code)).append('\n');
        if (!building.broken) {
            body.append("Статус: целое");
            return body.toString();
        }
        body.append("Статус: поломано");
        if (building.resources.isEmpty()) {
            body.append("\nРесурсы ремонта не указаны");
            return body.toString();
        }
        body.append("\nРесурсы для ремонта:");
        for (ForpostRepairResource resource : building.resources) {
            body.append("\n#").append(emptyDash(resource.resourceId))
                    .append(": нужно ").append(resource.required)
                    .append(", вложено ").append(resource.inserted)
                    .append(", осталось ").append(resource.remaining);
        }
        return body.toString();
    }

    private List<CityBuilding> sortedCityHallBuildings(List<CityBuilding> buildings) {
        List<CityBuilding> sorted = new ArrayList<>(buildings);
        Collections.sort(sorted, (left, right) -> Integer.compare(cityHallSortWeight(left), cityHallSortWeight(right)));
        return sorted;
    }

    private int cityHallSortWeight(CityBuilding building) {
        Float level = parseNullableFloat(building.level);
        Float progress = parseNullableFloat(building.progress);
        if (level == null || progress == null) {
            return 2;
        }
        int compare = Float.compare(level, progress);
        if (compare < 0) {
            return 0;
        }
        if (compare == 0) {
            return 1;
        }
        return 2;
    }

    private View forpostBuildingCard(ForpostBuilding building) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        int stroke = building.broken ? Color.rgb(255, 205, 210) : Color.rgb(200, 230, 201);
        card.setBackground(cardBackground(Color.WHITE, stroke, dp(20)));
        card.setElevation(dp(4));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(10));
        card.setLayoutParams(params);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = text(building.name, 17, Typeface.BOLD,
                building.broken ? TITLE_COLOR_INCOMPLETE : TITLE_COLOR_COMPLETE);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView badge = text(building.broken ? "Поломано" : "Целое", 13, Typeface.BOLD,
                building.broken ? Color.rgb(183, 28, 28) : Color.rgb(27, 94, 32));
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        badge.setBackground(cardBackground(
                building.broken ? Color.rgb(255, 235, 238) : Color.rgb(232, 245, 233),
                stroke,
                dp(14)));
        header.addView(badge);
        card.addView(header);

        TextView code = text("Код: " + emptyDash(building.code), 13, Typeface.NORMAL, Color.rgb(93, 102, 120));
        code.setPadding(0, dp(4), 0, building.broken ? dp(8) : 0);
        card.addView(code);

        if (building.broken) {
            if (building.resources.isEmpty()) {
                card.addView(text("Ресурсы ремонта не указаны", 14, Typeface.NORMAL, Color.rgb(93, 102, 120)));
            } else {
                for (ForpostRepairResource resource : building.resources) {
                    TextView row = text("Ресурс #" + emptyDash(resource.resourceId)
                                    + ": нужно " + resource.required
                                    + ", вложено " + resource.inserted
                                    + ", осталось " + resource.remaining,
                            14, Typeface.NORMAL, Color.rgb(72, 80, 94));
                    row.setPadding(dp(10), dp(7), dp(10), dp(7));
                    row.setBackground(cardBackground(Color.rgb(248, 250, 255), Color.rgb(228, 234, 247), dp(12)));
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    rowParams.setMargins(0, dp(4), 0, 0);
                    card.addView(row, rowParams);
                }
            }
        }
        return card;
    }

    private TextView cardText(String title, String body) {
        TextView view = text(title + "\n" + body, 15, Typeface.NORMAL, Color.rgb(30, 35, 45));
        view.setPadding(dp(16), dp(14), dp(16), dp(14));
        view.setBackground(cardBackground(Color.WHITE, Color.rgb(216, 225, 245), dp(18)));
        view.setElevation(dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(10));
        view.setLayoutParams(params);
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

    private View buildingCard(String title, String body, String imageUrl, int titleColor, String recipeImageFile) {
        boolean hasRecipe = recipeImageFile != null && !recipeImageFile.trim().isEmpty();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(cardBackground(Color.WHITE,
                hasRecipe ? Color.rgb(118, 133, 220) : Color.rgb(209, 219, 242), dp(20)));
        card.setElevation(dp(4));
        if (hasRecipe) {
            card.setClickable(true);
            card.setOnClickListener(v -> {
                Intent intent = new Intent(this, TablesActivity.class);
                intent.putExtra(TablesActivity.EXTRA_IMAGE_FILE, recipeImageFile);
                intent.putExtra(TablesActivity.EXTRA_ITEM_NAME, title);
                startActivity(intent);
            });
        }
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, dp(6), 0, dp(10));
        card.setLayoutParams(cardParams);

        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            LinearLayout imageBox = new LinearLayout(this);
            imageBox.setGravity(android.view.Gravity.CENTER);
            imageBox.setBackground(cardBackground(Color.rgb(240, 244, 255), Color.rgb(214, 224, 248), dp(16)));
            ImageView imageView = new ImageView(this);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageBox.addView(imageView, new LinearLayout.LayoutParams(dp(58), dp(58)));
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(76), dp(76));
            imageParams.setMargins(0, 0, dp(14), 0);
            card.addView(imageBox, imageParams);
            Glide.with(this).load(imageUrl).into(imageView);
        }

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 16, Typeface.BOLD, titleColor);
        TextView bodyView = text(body, 14, Typeface.NORMAL, Color.rgb(72, 80, 94));
        bodyView.setPadding(0, dp(4), 0, 0);
        textColumn.addView(titleView);
        textColumn.addView(bodyView);
        if (hasRecipe) {
            TextView linkView = text("Открыть рецепт в Таблицах", 13, Typeface.BOLD, ACTION_COLOR_DARK);
            linkView.setPadding(0, dp(7), 0, 0);
            textColumn.addView(linkView);
        }
        card.addView(textColumn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }

    private GradientDrawable cardBackground(int fillColor, int strokeColor, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private Button smallButton(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        styleButton(button, ButtonStyle.SECONDARY);
        button.setOnLongClickListener(v -> {
            Toast.makeText(this, title, Toast.LENGTH_SHORT).show();
            return true;
        });
        return button;
    }

    private Button actionButton(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        styleButton(button, ButtonStyle.PRIMARY);
        return button;
    }

    private Button choiceButton(String title, boolean active) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        styleButton(button, active ? ButtonStyle.PRIMARY : ButtonStyle.SECONDARY);
        return button;
    }

    private void styleButton(Button button, ButtonStyle style) {
        int fillColor = style == ButtonStyle.PRIMARY ? ACTION_COLOR : Color.rgb(247, 249, 255);
        int strokeColor = style == ButtonStyle.PRIMARY ? ACTION_COLOR_DARK : Color.rgb(183, 195, 229);
        int textColor = style == ButtonStyle.PRIMARY ? Color.WHITE : ACTION_COLOR_DARK;
        button.setSelected(style == ButtonStyle.PRIMARY);
        button.setTextColor(textColor);
        button.setTextSize(14);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(14), 0, dp(14), 0);
        // Отключаем theme tint, иначе системный Button может съесть нашу заливку.
        button.setBackgroundTintList(null);
        button.setBackground(cardBackground(fillColor, strokeColor, dp(22)));
        button.setBackgroundTintList(null);
        button.setElevation(style == ButtonStyle.PRIMARY ? dp(5) : dp(2));
        button.refreshDrawableState();
    }

    private enum ButtonStyle {
        PRIMARY,
        SECONDARY
    }

    private TextView text(String value, int sp, int style, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setTextColor(color);
        return view;
    }

    private String part(String[] parts, int index, String fallback) {
        if (parts == null || index < 0 || index >= parts.length || parts[index].trim().isEmpty()) {
            return fallback;
        }
        return parts[index].trim();
    }

    private String findNumeric(String[] parts, int startIndex) {
        if (parts == null || parts.length == 0) {
            return "0";
        }
        for (int i = Math.max(0, startIndex); i < parts.length; i++) {
            String value = parts[i].trim().replace(',', '.');
            if (value.matches("-?\\d+(\\.\\d+)?")) {
                return value;
            }
        }
        return "0";
    }

    private String findWeaponImageUrl(String[] parts) {
        if (parts == null) {
            return "";
        }
        for (String part : parts) {
            String value = part == null ? "" : part.trim();
            int gifIndex = value.toLowerCase(Locale.US).indexOf(".gif");
            if (gifIndex < 0) {
                continue;
            }
            int start = gifIndex;
            while (start > 0) {
                char ch = value.charAt(start - 1);
                if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '/') {
                    start--;
                    continue;
                }
                break;
            }
            String fileName = value.substring(start, gifIndex + 4);
            int slashIndex = fileName.lastIndexOf('/');
            if (slashIndex >= 0 && slashIndex + 1 < fileName.length()) {
                fileName = fileName.substring(slashIndex + 1);
            }
            if (!fileName.isEmpty()) {
                return IMAGE_WEAPON_BASE_URL + fileName;
            }
        }
        return "";
    }

    private float parseFloat(String value) {
        try {
            return Float.parseFloat(value == null ? "0" : value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value == null ? "0" : value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String joinNonEmpty(String[] parts, int fromInclusive, int toExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int i = Math.max(0, fromInclusive); parts != null && i < Math.min(parts.length, toExclusive); i++) {
            String value = parts[i].trim();
            if (!value.isEmpty()) {
                if (builder.length() > 0) {
                    builder.append("; ");
                }
                builder.append(value);
            }
        }
        return builder.toString();
    }

    private String emptyDash(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }

    private String cityHallHeaderLabel(int index) {
        switch (index) {
            case 0: return "Город";
            case 1: return "Начало";
            case 2: return "Конец";
            default: return "Поле " + index;
        }
    }

    private int buildingTitleColor(CityBuilding building) {
        Float level = parseNullableFloat(building.level);
        Float progress = parseNullableFloat(building.progress);
        if (level == null || progress == null) {
            return TITLE_COLOR_DEFAULT;
        }
        int compare = Float.compare(level, progress);
        if (compare == 0) {
            return TITLE_COLOR_COMPLETE;
        }
        if (compare < 0) {
            return TITLE_COLOR_INCOMPLETE;
        }
        return TITLE_COLOR_DEFAULT;
    }

    private Float parseNullableFloat(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Float.parseFloat(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class CityHallData {
        int cityId;
        String title;
        String footer;
        final List<String> headerFields = new ArrayList<>();
        final List<CityBuilding> buildings = new ArrayList<>();
    }

    private static class CityBuilding {
        String name;
        String level;
        String progress;
        String imageUrl;
        String raw;
    }

    private static class ForpostData {
        String sourceUrl;
        final List<ForpostBuilding> buildings = new ArrayList<>();
    }

    private static class ForpostBuilding {
        String code;
        String name;
        String raw;
        boolean broken;
        final List<ForpostRepairResource> resources = new ArrayList<>();
    }

    private static class ForpostRepairResource {
        String resourceId;
        int required;
        int inserted;
        int remaining;
    }
}

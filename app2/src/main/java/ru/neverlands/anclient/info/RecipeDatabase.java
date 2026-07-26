package ru.neverlands.anclient.info;

import android.content.Context;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.ParseUtils;

/**
 * Общая база рецептов из HTML-таблиц Викиневера.
 */
public final class RecipeDatabase {
    private static final String TAG = "RecipeDatabase";
    private static final String BASE_URI = "http://wiki.neverlands.ru/";
    private static final String IMAGE_BASE_URI = "http://image.neverlands.ru/";
    private static final String NORMALIZED_ASSET_PATH = "info/recipe_tables.txt";
    private static final String NORMALIZED_FORMAT_HEADER = "#ABCLIENT_RECIPE_TABLES_V1";
    private static final String DB_DIR_NAME = "info";
    private static final String DB_FILE_NAME = "recipe_tables_db.tsv";
    private static final String[] RECIPE_ITEM_HEADERS = new String[] {
            "картинка", "зелье", "аптечка", "свиток", "блюдо", "предмет", "название"
    };
    private static final String[] RECIPE_IMAGE_PATHS = new String[] {"/weapon/"};
    private static final String[] RESOURCE_ITEM_IMAGE_PATHS = new String[] {"/resources/"};

    private static final Source[] SOURCES = new Source[] {
            new Source("crafts_items", "Чертежи вещей", "info/tables/crafts_items.html"),
            new Source("crafts_weapons", "Чертежи оружия", "info/tables/crafts_weapons.html"),
            new Source("crafts_prof", "Чертежи проф", "info/tables/crafts_prof.html"),
            new Source("doctor_recipes", "Рецепты Доктора", "info/tables/doctor_recipes.html"),
            new Source("alchemist_recipes", "Рецепты Алхимика", "info/tables/alchemist_recipes.html"),
            new Source("calligrapher_recipes", "Рецепты Каллиграфа", "info/tables/calligrapher_recipes.html"),
            new Source("cook_recipes", "Рецепты Повара", "info/tables/cook_recipes.html"),
            new Source("alchemist_blanks", "Заготовки Алхимика", "info/tables/alchemist_blanks.html",
                    new String[] {"получаемый ингредиент", "получаемый материал", "продукт"}, RESOURCE_ITEM_IMAGE_PATHS),
            new Source("carpenter_table", "Плотник", "info/tables/carpenter_table.html",
                    new String[] {"готовая продукция", "получаемые ресурсы", "получаемый материал", "название"}, RESOURCE_ITEM_IMAGE_PATHS),
            new Source("calligrapher_materials", "Материалы Каллиграфа", "info/tables/calligrapher_materials.html",
                    new String[] {"получаемый материал"}, RESOURCE_ITEM_IMAGE_PATHS),
            new Source("cook_blanks", "Заготовки Повара", "info/tables/cook_blanks.html",
                    new String[] {"название"}, RESOURCE_ITEM_IMAGE_PATHS),
            new Source("metals_alloys", "Металлы и сплавы", "info/tables/metals_alloys.html",
                    new String[] {"сплавы", "металлы"}, RESOURCE_ITEM_IMAGE_PATHS)
    };

    private static RecipeDatabase cachedDatabase;

    private final List<RecipeSection> sections = new ArrayList<>();
    private final List<RecipeItem> allItems = new ArrayList<>();
    private final Map<String, List<RecipeItem>> itemsByImageFile = new LinkedHashMap<>();
    private File consolidatedFile;

    private RecipeDatabase() {
    }

    public static synchronized RecipeDatabase load(Context context) {
        if (cachedDatabase != null) {
            return cachedDatabase;
        }
        RecipeDatabase database = new RecipeDatabase();
        Context appContext = context.getApplicationContext();
        boolean loadedNormalized = false;
        try {
            loadedNormalized = database.parseNormalizedSource(appContext);
        } catch (Exception error) {
            AppLog.w(TAG, "RECIPE_NORMALIZED_PARSE_FAILED", error);
        }
        if (!loadedNormalized) {
            for (Source source : SOURCES) {
                RecipeSection section = new RecipeSection(source.key, source.title, source.assetPath);
                database.sections.add(section);
                try {
                    database.parseSource(appContext, source, section);
                    AppLog.i(TAG, "RECIPE_SECTION_PARSED: " + source.assetPath + ", items=" + section.items.size());
                } catch (Exception error) {
                    AppLog.w(TAG, "RECIPE_SECTION_PARSE_FAILED: " + source.assetPath, error);
                }
            }
        }
        database.rebuildIndex();
        database.writeConsolidatedFile(appContext);
        cachedDatabase = database;
        return cachedDatabase;
    }

    private boolean parseNormalizedSource(Context context) throws IOException {
        Map<String, RecipeSection> sectionsByKey = new LinkedHashMap<>();
        Map<String, RecipeItem> itemsById = new LinkedHashMap<>();
        try (InputStream inputStream = context.getAssets().open(NORMALIZED_ASSET_PATH);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();
            if (!NORMALIZED_FORMAT_HEADER.equals(firstLine)) {
                AppLog.w(TAG, "RECIPE_NORMALIZED_BAD_HEADER: " + firstLine);
                return false;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\t", -1);
                String recordType = parts.length == 0 ? "" : parts[0];
                if ("SECTION".equals(recordType)) {
                    if (parts.length < 4) {
                        continue;
                    }
                    RecipeSection section = getOrCreateSection(sectionsByKey, parts[1], parts[2], parts[3]);
                    if (!sections.contains(section)) {
                        sections.add(section);
                    }
                } else if ("ITEM".equals(recordType)) {
                    if (parts.length < 9) {
                        continue;
                    }
                    String itemId = parts[1];
                    RecipeSection section = getOrCreateSection(sectionsByKey, parts[2], parts[3], parts[4]);
                    if (!sections.contains(section)) {
                        sections.add(section);
                    }
                    int tableIndex = ParseUtils.parseIntSafe(parts[5]);
                    String imageUrl = parts[7];
                    String imageFile = parts[8].isEmpty() ? normalizeImageFile(imageUrl) : normalizeImageFile(parts[8]);
                    if (imageFile.isEmpty()) {
                        continue;
                    }
                    RecipeItem item = new RecipeItem(section.key, section.title, section.assetPath,
                            tableIndex, parts[6], imageUrl, imageFile);
                    section.items.add(item);
                    allItems.add(item);
                    itemsById.put(itemId, item);
                } else if ("FIELD".equals(recordType)) {
                    if (parts.length < 4) {
                        continue;
                    }
                    RecipeItem item = itemsById.get(parts[1]);
                    if (item != null && !parts[2].isEmpty() && !parts[3].isEmpty()) {
                        item.fields.add(new RecipeField(parts[2], parts[3]));
                    }
                } else if ("RESOURCE".equals(recordType)) {
                    if (parts.length < 5) {
                        continue;
                    }
                    RecipeItem item = itemsById.get(parts[1]);
                    if (item != null) {
                        String imageUrl = parts[2];
                        String imageFile = parts[3].isEmpty() ? normalizeImageFile(imageUrl) : normalizeImageFile(parts[3]);
                        if (!imageFile.isEmpty()) {
                            item.resources.add(new RecipeResource(imageUrl, imageFile, parts[4]));
                        }
                    }
                }
            }
        }
        AppLog.i(TAG, "RECIPE_NORMALIZED_PARSED: sections=" + sections.size() + ", items=" + allItems.size());
        return !sections.isEmpty();
    }

    private RecipeSection getOrCreateSection(Map<String, RecipeSection> sectionsByKey, String key,
                                             String title, String assetPath) {
        RecipeSection section = sectionsByKey.get(key);
        if (section == null) {
            section = new RecipeSection(key, title, assetPath);
            sectionsByKey.put(key, section);
        }
        return section;
    }


    public static String normalizeImageFile(String imageUrlOrFile) {
        if (imageUrlOrFile == null) {
            return "";
        }
        String value = imageUrlOrFile.trim();
        if (value.isEmpty()) {
            return "";
        }
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        int hashIndex = value.indexOf('#');
        if (hashIndex >= 0) {
            value = value.substring(0, hashIndex);
        }
        int gifIndex = value.toLowerCase(Locale.US).indexOf(".gif");
        if (gifIndex < 0) {
            return "";
        }
        if (gifIndex >= 0 && gifIndex + 4 < value.length()) {
            value = value.substring(0, gifIndex + 4);
        }
        int slashIndex = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        if (slashIndex >= 0 && slashIndex + 1 < value.length()) {
            value = value.substring(slashIndex + 1);
        }
        return value.toLowerCase(Locale.US);
    }

    public List<RecipeSection> getSections() {
        return Collections.unmodifiableList(sections);
    }

    public List<RecipeItem> getAllItems() {
        return Collections.unmodifiableList(allItems);
    }

    public File getConsolidatedFile() {
        return consolidatedFile;
    }

    public RecipeSection findSection(String sectionKey) {
        if (sectionKey == null) {
            return null;
        }
        for (RecipeSection section : sections) {
            if (section.key.equals(sectionKey)) {
                return section;
            }
        }
        return null;
    }

    public String getFirstSectionKey() {
        return sections.isEmpty() ? "" : sections.get(0).key;
    }

    public boolean hasImageFile(String imageFile) {
        return !findByImageFile(imageFile).isEmpty();
    }

    public List<RecipeItem> findByImageFile(String imageFile) {
        String normalized = normalizeImageFile(imageFile);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        List<RecipeItem> items = itemsByImageFile.get(normalized);
        if (items == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(items);
    }

    private void parseSource(Context context, Source source, RecipeSection section) throws IOException {
        try (InputStream inputStream = context.getAssets().open(source.assetPath)) {
            Document document = Jsoup.parse(inputStream, "UTF-8", BASE_URI);
            Elements tables = document.select("table.wikitable");
            int tableIndex = 0;
            for (Element table : tables) {
                tableIndex++;
                parseTable(source, section, table, tableIndex);
            }
        }
    }

    private void parseTable(Source source, RecipeSection section, Element table, int tableIndex) {
        List<String> headers = parseHeaders(table);
        Elements rows = table.select("tr");
        for (Element row : rows) {
            Elements cells = directChildren(row, "td");
            if (cells.isEmpty()) {
                continue;
            }
            if (hasRepeatedItemHeader(source, headers)) {
                parseGroupedItemRow(source, section, headers, cells, tableIndex);
            } else {
                RecipeItem item = parseStandardItem(source, section, headers, cells, tableIndex);
                if (item != null) {
                    section.items.add(item);
                    allItems.add(item);
                }
            }
        }
    }

    private List<String> parseHeaders(Element table) {
        List<String> headers = new ArrayList<>();
        for (Element row : table.select("tr")) {
            Elements ths = directChildren(row, "th");
            if (ths.isEmpty()) {
                continue;
            }
            for (Element th : ths) {
                headers.add(normalizeText(th.text()));
            }
            break;
        }
        return headers;
    }

    private Elements directChildren(Element parent, String tagName) {
        Elements result = new Elements();
        for (Element child : parent.children()) {
            if (tagName.equalsIgnoreCase(child.normalName())) {
                result.add(child);
            }
        }
        return result;
    }

    private boolean hasRepeatedItemHeader(Source source, List<String> headers) {
        int count = 0;
        for (String header : headers) {
            if (isItemHeader(source, header)) {
                count++;
            }
        }
        return count > 1;
    }

    private boolean isItemHeader(Source source, String header) {
        String key = normalizeKey(header);
        for (String headerNeedle : source.itemHeaderNeedles) {
            if (key.contains(headerNeedle)) {
                return true;
            }
        }
        return false;
    }

    private void parseGroupedItemRow(Source source, RecipeSection section, List<String> headers,
                                     Elements cells, int tableIndex) {
        for (int i = 0; i < headers.size() && i < cells.size(); i++) {
            if (!isItemHeader(source, headers.get(i))) {
                continue;
            }
            int recipeIndex = i + 1;
            if (recipeIndex >= cells.size()) {
                continue;
            }
            Element itemCell = cells.get(i);
            Element recipeCell = cells.get(recipeIndex);
            RecipeItem item = buildItem(source, section, tableIndex, itemCell, null,
                    headersForGroup(headers, i, recipeIndex), cellsForGroup(itemCell, recipeCell));
            if (item != null) {
                section.items.add(item);
                allItems.add(item);
            }
        }
    }

    private List<String> headersForGroup(List<String> headers, int itemIndex, int recipeIndex) {
        List<String> result = new ArrayList<>();
        result.add(headerOrDefault(headers, itemIndex, "Предмет"));
        result.add(headerOrDefault(headers, recipeIndex, "Составные части"));
        return result;
    }

    private Elements cellsForGroup(Element itemCell, Element recipeCell) {
        Elements result = new Elements();
        result.add(itemCell);
        result.add(recipeCell);
        return result;
    }

    private RecipeItem parseStandardItem(Source source, RecipeSection section, List<String> headers,
                                         Elements cells, int tableIndex) {
        int itemIndex = findItemCellIndex(source, headers, cells);
        if (itemIndex < 0 || itemIndex >= cells.size()) {
            return null;
        }
        int nameIndex = findNameCellIndex(headers, itemIndex, cells.size());
        Element nameCell = nameIndex >= 0 && nameIndex < cells.size() ? cells.get(nameIndex) : null;
        return buildItem(source, section, tableIndex, cells.get(itemIndex), nameCell, headers, cells);
    }

    private int findItemCellIndex(Source source, List<String> headers, Elements cells) {
        for (String headerNeedle : source.itemHeaderNeedles) {
            for (int i = 0; i < headers.size() && i < cells.size(); i++) {
                String key = normalizeKey(headers.get(i));
                if (key.contains(headerNeedle) && !firstImageUrl(cells.get(i), source.itemImagePathMarkers).isEmpty()) {
                    return i;
                }
            }
        }
        for (int i = 0; i < cells.size(); i++) {
            if (!firstImageUrl(cells.get(i), source.itemImagePathMarkers).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private int findNameCellIndex(List<String> headers, int itemIndex, int cellsCount) {
        for (int i = 0; i < headers.size() && i < cellsCount; i++) {
            String key = normalizeKey(headers.get(i));
            if (key.contains("название")) {
                return i;
            }
        }
        if (itemIndex + 1 < cellsCount && itemIndex < headers.size()
                && normalizeKey(headers.get(itemIndex)).contains("картинка")) {
            return itemIndex + 1;
        }
        return -1;
    }

    private RecipeItem buildItem(Source source, RecipeSection section, int tableIndex, Element itemCell,
                                 Element nameCell, List<String> headers, Elements cells) {
        String itemImageUrl = firstImageUrl(itemCell, source.itemImagePathMarkers);
        String itemImageFile = normalizeImageFile(itemImageUrl);
        if (itemImageFile.isEmpty()) {
            return null;
        }
        String name = extractItemName(itemCell, nameCell, itemImageFile);
        RecipeItem item = new RecipeItem(source.key, section.title, source.assetPath, tableIndex,
                name, itemImageUrl, itemImageFile);
        for (int i = 0; i < cells.size(); i++) {
            String header = headerOrDefault(headers, i, "Поле " + (i + 1));
            String value = normalizeText(cells.get(i).text());
            if (!header.isEmpty() && !value.isEmpty()) {
                item.fields.add(new RecipeField(header, value));
            }
            if (isRecipeLikeHeader(header)) {
                item.resources.addAll(parseResources(cells.get(i)));
            }
        }
        return item;
    }

    private String extractItemName(Element itemCell, Element nameCell, String itemImageFile) {
        String name = nameFromCell(nameCell);
        if (!name.isEmpty()) {
            return name;
        }
        name = nameFromCell(itemCell);
        if (!name.isEmpty()) {
            return name;
        }
        return itemImageFile;
    }

    private String nameFromCell(Element cell) {
        if (cell == null) {
            return "";
        }
        Element bold = cell.selectFirst("b");
        if (bold != null) {
            String boldText = normalizeText(bold.text());
            if (!boldText.isEmpty()) {
                return boldText;
            }
        }
        return normalizeText(cell.text());
    }

    private boolean isRecipeLikeHeader(String header) {
        String key = normalizeKey(header);
        return key.contains("ресурс")
                || key.contains("рецепт")
                || key.contains("состав")
                || key.contains("исход")
                || key.contains("инструмент")
                || key.contains("молот")
                || key.contains("дров")
                || key.contains("минерал");
    }

    private List<RecipeResource> parseResources(Element cell) {
        List<RecipeResource> resources = new ArrayList<>();
        for (Element image : cell.select("img")) {
            String imageUrl = absoluteImageUrl(image);
            String imageFile = normalizeImageFile(imageUrl);
            if (imageFile.isEmpty()) {
                continue;
            }
            String label = extractTextAfterImage(image);
            if (label.isEmpty()) {
                label = image.attr("alt");
            }
            resources.add(new RecipeResource(imageUrl, imageFile, normalizeText(label)));
        }
        return resources;
    }

    private String extractTextAfterImage(Element image) {
        StringBuilder builder = new StringBuilder();
        Node node = image.nextSibling();
        while (node != null) {
            if (node instanceof Element) {
                Element element = (Element) node;
                String tag = element.normalName();
                if ("img".equals(tag) || "br".equals(tag)) {
                    break;
                }
                builder.append(' ').append(element.text());
            } else if (node instanceof TextNode) {
                builder.append(' ').append(((TextNode) node).text());
            }
            node = node.nextSibling();
        }
        String text = normalizeText(builder.toString());
        while (text.startsWith("-") || text.startsWith(":")) {
            text = text.substring(1).trim();
        }
        return text;
    }

    private String firstImageUrl(Element cell, String pathMarker) {
        return firstImageUrl(cell, pathMarker == null ? null : new String[] {pathMarker});
    }

    private String firstImageUrl(Element cell, String[] pathMarkers) {
        if (cell == null) {
            return "";
        }
        for (Element image : cell.select("img")) {
            String url = absoluteImageUrl(image);
            if (matchesPathMarker(url, pathMarkers)) {
                return url;
            }
        }
        return "";
    }

    private boolean matchesPathMarker(String url, String[] pathMarkers) {
        if (url == null || normalizeImageFile(url).isEmpty()) {
            return false;
        }
        if (pathMarkers == null || pathMarkers.length == 0) {
            return true;
        }
        String lowerUrl = url.toLowerCase(Locale.US);
        for (String marker : pathMarkers) {
            if (marker != null && lowerUrl.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String absoluteImageUrl(Element image) {
        String src = image.hasAttr("abs:src") ? image.attr("abs:src") : image.attr("src");
        if (src == null) {
            return "";
        }
        src = src.trim();
        if (src.startsWith("//")) {
            return "http:" + src;
        }
        if (src.startsWith("http://") || src.startsWith("https://")) {
            return src;
        }
        if (src.startsWith("/")) {
            return IMAGE_BASE_URI + src.substring(1);
        }
        if (src.contains(".gif")) {
            return IMAGE_BASE_URI + src;
        }
        return src;
    }

    private String headerOrDefault(List<String> headers, int index, String fallback) {
        if (index >= 0 && index < headers.size()) {
            String header = normalizeText(headers.get(index));
            if (!header.isEmpty()) {
                return header;
            }
        }
        return fallback;
    }

    private String normalizeKey(String value) {
        return normalizeText(value).toLowerCase(Locale.US);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private void rebuildIndex() {
        itemsByImageFile.clear();
        for (RecipeItem item : allItems) {
            List<RecipeItem> items = itemsByImageFile.get(item.itemImageFile);
            if (items == null) {
                items = new ArrayList<>();
                itemsByImageFile.put(item.itemImageFile, items);
            }
            items.add(item);
        }
    }

    private void writeConsolidatedFile(Context context) {
        File dir = new File(context.getFilesDir(), DB_DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            AppLog.w(TAG, "RECIPE_DB_DIR_CREATE_FAILED: " + dir.getAbsolutePath());
            return;
        }
        consolidatedFile = new File(dir, DB_FILE_NAME);
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(consolidatedFile), StandardCharsets.UTF_8))) {
            writer.write("section_key\tsection_title\timage_file\tname\timage_url\tfields\tresources\n");
            for (RecipeItem item : allItems) {
                writer.write(tsv(item.sectionKey));
                writer.write('\t');
                writer.write(tsv(item.sectionTitle));
                writer.write('\t');
                writer.write(tsv(item.itemImageFile));
                writer.write('\t');
                writer.write(tsv(item.name));
                writer.write('\t');
                writer.write(tsv(item.itemImageUrl));
                writer.write('\t');
                writer.write(tsv(fieldsToText(item.fields)));
                writer.write('\t');
                writer.write(tsv(resourcesToText(item.resources)));
                writer.write('\n');
            }
            AppLog.i(TAG, "RECIPE_DB_WRITTEN: " + consolidatedFile.getAbsolutePath()
                    + ", items=" + allItems.size());
        } catch (Exception error) {
            AppLog.w(TAG, "RECIPE_DB_WRITE_FAILED", error);
        }
    }

    private String fieldsToText(List<RecipeField> fields) {
        StringBuilder builder = new StringBuilder();
        for (RecipeField field : fields) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(field.name).append('=').append(field.value);
        }
        return builder.toString();
    }

    private String resourcesToText(List<RecipeResource> resources) {
        StringBuilder builder = new StringBuilder();
        for (RecipeResource resource : resources) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(resource.imageFile).append('=').append(resource.label);
        }
        return builder.toString();
    }

    private String tsv(String value) {
        return value == null ? "" : value.replace('\t', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
    }

    private static final class Source {
        final String key;
        final String title;
        final String assetPath;
        final String[] itemHeaderNeedles;
        final String[] itemImagePathMarkers;

        Source(String key, String title, String assetPath) {
            this(key, title, assetPath, RECIPE_ITEM_HEADERS, RECIPE_IMAGE_PATHS);
        }

        Source(String key, String title, String assetPath, String[] itemHeaderNeedles, String[] itemImagePathMarkers) {
            this.key = key;
            this.title = title;
            this.assetPath = assetPath;
            this.itemHeaderNeedles = itemHeaderNeedles;
            this.itemImagePathMarkers = itemImagePathMarkers;
        }
    }

    public static final class RecipeSection {
        public final String key;
        public final String title;
        public final String assetPath;
        public final List<RecipeItem> items = new ArrayList<>();

        RecipeSection(String key, String title, String assetPath) {
            this.key = key;
            this.title = title;
            this.assetPath = assetPath;
        }
    }

    public static final class RecipeItem {
        public final String sectionKey;
        public final String sectionTitle;
        public final String sourceAsset;
        public final int tableIndex;
        public final String name;
        public final String itemImageUrl;
        public final String itemImageFile;
        public final List<RecipeField> fields = new ArrayList<>();
        public final List<RecipeResource> resources = new ArrayList<>();

        RecipeItem(String sectionKey, String sectionTitle, String sourceAsset, int tableIndex,
                   String name, String itemImageUrl, String itemImageFile) {
            this.sectionKey = sectionKey;
            this.sectionTitle = sectionTitle;
            this.sourceAsset = sourceAsset;
            this.tableIndex = tableIndex;
            this.name = name;
            this.itemImageUrl = itemImageUrl;
            this.itemImageFile = itemImageFile;
        }
    }

    public static final class RecipeField {
        public final String name;
        public final String value;

        RecipeField(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    public static final class RecipeResource {
        public final String imageUrl;
        public final String imageFile;
        public final String label;

        RecipeResource(String imageUrl, String imageFile, String label) {
            this.imageUrl = imageUrl;
            this.imageFile = imageFile;
            this.label = label;
        }
    }
}

package ru.neverlands.anclient.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.anclient.model.KaznaCategory;
import ru.neverlands.anclient.model.KaznaItem;
import ru.neverlands.anclient.model.KaznaSnapshot;

/**
 * HTML-парсер клановой казны.
 *
 * Связь с остальным модулем:
 * - `KaznaManager` передаёт сюда HTML, скачанный через `ApiRepository.downloadFile(...)`;
 * - результатом всегда является `KaznaSnapshot` с `KaznaCategory` и `KaznaItem`;
 * - `KaznaActivity` не читает HTML напрямую и работает только с моделями parser-слоя;
 * - `KaznaItemAdapter` использует `KaznaItem.artifactCoefficient`, `maxDurability`,
 *   `takeUrl`, `donateUrl`, `categoryWca` и `rowHtml` для отображения и действий.
 *
 * Decision points:
 * - Jsoup используется только как tolerant HTML-reader, потому что серверная
 *   страница windows-1251 содержит старую табличную вёрстку без валидного DOM;
 * - классификация `Арты/Рары/Обычные` не зависит от названия предмета, а берёт
 *   коэффициент и долговечность из тех же ячеек, которые видит пользователь;
 * - `uid` не синтезируется: если сервер не отдал action-link для занятого
 *   предмета, строка остаётся видимой, но действия по UID недоступны;
 * - базовый URL содержит `wfo=1`, как в ANClient, чтобы сервер явно сбрасывал
 *   предыдущий `wca`-фильтр перед отдачей полного списка казны.
 */
public final class KaznaParser {
    public static final String BASE_MAIN_URL = "http://neverlands.ru/main.php";
    public static final String BASE_KAZNA_URL = BASE_MAIN_URL + "?wfo=1&useaction=clan-action&addid=3";

    private static final Pattern WCA_PATTERN = Pattern.compile("(?:[?&])wca=([^&]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UID_PATTERN = Pattern.compile("(?:[?&])uid=([^&]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INDEX_PATTERN = Pattern.compile("^\\s*(\\d+)\\.\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern DURABILITY_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
    private static final Pattern ARTIFACT_PATTERN = Pattern.compile("(?<!\\d)([12]\\.\\d{2})(?!\\d)");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("location\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);

    private KaznaParser() {
    }

    /**
     * Единственная публичная точка разбора HTML казны.
     *
     * `sourceUrl` сохраняется в `KaznaSnapshot.sourceUrl` и каждой `KaznaItem.sourceUrl`,
     * чтобы downstream-код мог отличить полный reset-ответ `wfo=1` от категории `wca=...`.
     */
    public static KaznaSnapshot parse(String html, String sourceUrl) {
        String safeHtml = html == null ? "" : html;
        String normalizedSourceUrl = normalizeMainUrl(sourceUrl);
        Document document = Jsoup.parse(safeHtml, BASE_MAIN_URL);
        List<KaznaCategory> categories = parseCategories(document);
        String currentWca = extractWca(normalizedSourceUrl);
        String currentCategoryTitle = findCategoryTitle(categories, currentWca);
        List<KaznaItem> items = parseItems(document, normalizedSourceUrl, currentWca, currentCategoryTitle);
        return new KaznaSnapshot(
                System.currentTimeMillis(),
                normalizedSourceUrl,
                currentWca,
                currentCategoryTitle,
                categories,
                items);
    }

    private static List<KaznaCategory> parseCategories(Document document) {
        Map<String, KaznaCategory> byWca = new LinkedHashMap<>();
        Elements links = document.select("a[href*=useaction=clan-action][href*=addid=3]");
        for (Element link : links) {
            String href = normalizeMainUrl(link.attr("href"));
            String wca = extractWca(href);
            if (wca.isEmpty() || byWca.containsKey(wca)) {
                continue;
            }
            Element image = link.selectFirst("img");
            if (image == null) {
                continue;
            }
            String iconUrl = normalizeAssetUrl(image.attr("src"));
            String title = firstNonEmpty(image.attr("title"), image.attr("alt"), link.text(), "wca=" + wca);
            byWca.put(wca, new KaznaCategory(wca, title, iconUrl, href));
        }
        return new ArrayList<>(byWca.values());
    }

    private static List<KaznaItem> parseItems(
            Document document,
            String sourceUrl,
            String currentWca,
            String currentCategoryTitle) {
        List<KaznaItem> items = new ArrayList<>();
        int fallbackIndex = 1;
        for (Element row : document.select("tr")) {
            Elements cells = row.select("> td");
            if (cells.size() < 5) {
                continue;
            }
            String durabilityCellText = cleanText(cells.get(2).text());
            if (!containsIgnoreCase(durabilityCellText, "Долговечность") || !DURABILITY_PATTERN.matcher(durabilityCellText).find()) {
                continue;
            }

            ParsedName parsedName = parseName(cells.get(0));
            if (parsedName.displayName.isEmpty()) {
                continue;
            }

            Durability durability = parseDurability(durabilityCellText);
            // Вторая колонка казны - не владелец вещи, а текущая отметка
            // `В-инвентаре: <ник>`. Имя поля `owner` оставлено для совместимости
            // JSON-кеша и существующих adapter-ов.
            String owner = cleanText(cells.get(1).text());
            String status = cleanText(cells.get(3).text());
            boolean free = containsIgnoreCase(status, "свобод");

            // UID берём только из серверных action-link/rowHtml. Если вещь занята и
            // сервер не отдал кнопку, строка остаётся видимой, но action недоступен.
            ActionLinks actionLinks = parseActionLinks(cells.get(4));
            String uid = firstNonEmpty(extractUid(actionLinks.takeUrl), extractUid(actionLinks.donateUrl), extractUid(row.outerHtml()));

            int rowIndex = parsedName.index > 0 ? parsedName.index : fallbackIndex;
            fallbackIndex++;
            items.add(new KaznaItem(
                    uid,
                    rowIndex,
                    parsedName.displayName,
                    parsedName.baseName,
                    owner,
                    durability.text,
                    durability.current,
                    durability.max,
                    status,
                    free,
                    parsedName.artifactCoefficient,
                    actionLinks.takeUrl,
                    actionLinks.donateUrl,
                    sourceUrl,
                    currentWca,
                    currentCategoryTitle,
                    row.outerHtml()));
        }
        return items;
    }

    private static ParsedName parseName(Element nameCell) {
        String text = cleanText(nameCell.text());
        String artifact = "";
        Matcher artifactMatcher = ARTIFACT_PATTERN.matcher(text);
        if (artifactMatcher.find()) {
            artifact = artifactMatcher.group(1);
            text = cleanText(text.replace(artifact, ""));
        }

        int index = -1;
        Matcher indexMatcher = INDEX_PATTERN.matcher(text);
        if (indexMatcher.matches()) {
            index = parseInt(indexMatcher.group(1), -1);
            text = cleanText(indexMatcher.group(2));
        }

        Element firstBold = nameCell.selectFirst("b");
        String baseName = firstBold == null ? text : cleanText(firstBold.text().replace(artifact, ""));
        if (baseName.isEmpty()) {
            baseName = text;
        }
        return new ParsedName(index, text, baseName, artifact);
    }

    private static Durability parseDurability(String durabilityCellText) {
        Matcher matcher = DURABILITY_PATTERN.matcher(durabilityCellText);
        if (!matcher.find()) {
            return new Durability("", -1, -1);
        }
        int current = parseInt(matcher.group(1), -1);
        int max = parseInt(matcher.group(2), -1);
        return new Durability(current + "/" + max, current, max);
    }

    /**
     * Разбирает только реальные кнопки действий казны.
     *
     * Наименования из HTML:
     * - `Взять из казны` -> `KaznaItem.takeUrl`, серверный endpoint `get_id=29`;
     * - `Пожертвовать` -> `KaznaItem.donateUrl`, серверный endpoint `get_id=18`.
     */
    private static ActionLinks parseActionLinks(Element actionCell) {
        String takeUrl = "";
        String donateUrl = "";
        for (Element input : actionCell.select("input")) {
            String value = cleanText(input.attr("value"));
            String rawUrl = extractLocation(input.attr("onclick"));
            if (rawUrl.isEmpty()) {
                continue;
            }
            String url = normalizeMainUrl(rawUrl);
            if (containsIgnoreCase(value, "Взять из казны")) {
                takeUrl = url;
            } else if (containsIgnoreCase(value, "Пожертвовать")) {
                donateUrl = url;
            }
        }
        return new ActionLinks(takeUrl, donateUrl);
    }

    private static String extractLocation(String onclick) {
        if (onclick == null || onclick.trim().isEmpty()) {
            return "";
        }
        Matcher matcher = LOCATION_PATTERN.matcher(onclick);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * Нормализует относительные ссылки Neverlands к абсолютным URL.
     *
     * `null` или пустая строка намеренно возвращают `BASE_KAZNA_URL`, потому это
     * используется как безопасный fallback для кнопки `Все` и `refreshKazna(null)`.
     */
    public static String normalizeMainUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return BASE_KAZNA_URL;
        }
        String normalized = url.trim().replace("&amp;", "&");
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        if (normalized.startsWith("?")) {
            return BASE_MAIN_URL + normalized;
        }
        if (normalized.startsWith("/")) {
            return "http://neverlands.ru" + normalized;
        }
        if (normalized.startsWith("main.php")) {
            return "http://neverlands.ru/" + normalized;
        }
        return normalized;
    }

    private static String normalizeAssetUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "";
        }
        String normalized = url.trim().replace("&amp;", "&");
        if (normalized.startsWith("//")) {
            return "http:" + normalized;
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        if (normalized.startsWith("/")) {
            return "http://image.neverlands.ru" + normalized;
        }
        return normalized;
    }

    public static String extractWca(String url) {
        if (url == null) {
            return "";
        }
        Matcher matcher = WCA_PATTERN.matcher(url.replace("&amp;", "&"));
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * Проверяет именно reset URL полного списка казны.
     *
     * Метод общий для `KaznaManager.isAllScopeSnapshot(...)` и
     * `KaznaActivity.isAllSnapshot(...)`, чтобы критерий all-cache был одинаковым
     * в сетевом слое и UI. Наличие `wfo=1` взято из ПК-эталона `ClanKaznaUrl`.
     */
    public static boolean isAllKaznaResetUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        String normalized = normalizeMainUrl(url).toLowerCase(Locale.ROOT).replace("&amp;", "&");
        return normalized.contains("useaction=clan-action")
                && normalized.contains("addid=3")
                && normalized.contains("wfo=1")
                && extractWca(normalized).isEmpty();
    }

    public static String extractUid(String value) {
        if (value == null) {
            return "";
        }
        Matcher matcher = UID_PATTERN.matcher(value.replace("&amp;", "&"));
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String findCategoryTitle(List<KaznaCategory> categories, String wca) {
        if (wca == null || wca.isEmpty()) {
            return "";
        }
        for (KaznaCategory category : categories) {
            if (wca.equals(category.wca)) {
                return category.title;
            }
        }
        return "";
    }

    /** Делегат к канонической реализации {@link ru.neverlands.anclient.utils.HelperStrings#containsIgnoreCase(String, String)} (D5). */
    private static boolean containsIgnoreCase(String value, String token) {
        return ru.neverlands.anclient.utils.HelperStrings.containsIgnoreCase(value, token);
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String cleaned = cleanText(value);
            if (!cleaned.isEmpty()) {
                return cleaned;
            }
        }
        return "";
    }

    private static String cleanText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static final class ParsedName {
        final int index;
        final String displayName;
        final String baseName;
        final String artifactCoefficient;

        ParsedName(int index, String displayName, String baseName, String artifactCoefficient) {
            this.index = index;
            this.displayName = displayName;
            this.baseName = baseName;
            this.artifactCoefficient = artifactCoefficient;
        }
    }

    private static final class Durability {
        final String text;
        final int current;
        final int max;

        Durability(String text, int current, int max) {
            this.text = text;
            this.current = current;
            this.max = max;
        }
    }

    private static final class ActionLinks {
        final String takeUrl;
        final String donateUrl;

        ActionLinks(String takeUrl, String donateUrl) {
            this.takeUrl = takeUrl;
            this.donateUrl = donateUrl;
        }
    }
}

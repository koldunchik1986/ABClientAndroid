package ru.neverlands.anclient.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.anclient.model.KaznaItemDetails;
import ru.neverlands.anclient.utils.HelperStrings;

/**
 * Парсер UID-деталей из одного `htmlEntry` инвентаря.
 *
 * Связь с доработками казны:
 * - `InventoryParser` уже нарезает HTML инвентаря на `htmlEntry`, поэтому этот
 *   parser не создаёт отдельный сетевой обход и не читает глобальный VCode из `AppVars`;
 * - результат сохраняется через `KaznaItemDetailsCache` и потом используется
 *   `KaznaItemAdapter` только для изображения/свойств/подсказочного UID;
 * - `KaznaManager.takeItem(...)` и `donateItem(...)` не зависят от этих данных,
 *   чтобы не подменять серверные action URL inferred UID-ом.
 *
 * Decision points:
 * - входом является уже найденный штатным `InventoryParser.mainPhpInv(...)` блок одной вещи;
 * - UID берётся только из ссылки `get_id=57`, чтобы не спутать предмет с другими UID в JS;
 * - свойства берутся из первого подходящего `font.weaponch`, то есть из того же HTML,
 *   который сервер показывает пользователю в карточке вещи.
 */
public final class KaznaItemDetailsParser {
    private static final Pattern UID_LINK_PATTERN = Pattern.compile(
            "get_id\\s*=\\s*57[^'\"<>\\s]*[?&]uid=([^&'\"<>\\s]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT_TAG_PATTERN = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SRC_ATTR_PATTERN = Pattern.compile(
            "\\bsrc\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BR_PATTERN = Pattern.compile("(?i)<br\\s*/?>");

    private KaznaItemDetailsParser() {
    }

    public static KaznaItemDetails parseFromInventoryEntry(String htmlEntry) {
        if (htmlEntry == null || htmlEntry.isEmpty()) {
            return null;
        }
        String normalizedHtml = htmlEntry.replace("&amp;", "&");
        String uid = extractUid(normalizedHtml);
        if (uid.isEmpty()) {
            return null;
        }

        String name = extractName(normalizedHtml);
        String imageUrl = extractImageUrl(normalizedHtml, uid);
        String propertiesText = extractPropertiesText(normalizedHtml, name);
        KaznaItemDetails details = new KaznaItemDetails(
                uid,
                name,
                imageUrl,
                propertiesText,
                System.currentTimeMillis());
        return details.hasKnownDetails() ? details : null;
    }

    private static String extractUid(String htmlEntry) {
        Matcher matcher = UID_LINK_PATTERN.matcher(htmlEntry);
        return matcher.find() ? cleanText(matcher.group(1)) : "";
    }

    private static String extractName(String htmlEntry) {
        String name = HelperStrings.subString(htmlEntry, "<font class=nickname><b> ", "</b>");
        if (name == null || name.isEmpty()) {
            name = HelperStrings.subString(htmlEntry, "<font class=nickname><b>", "</b>");
        }
        if (name == null || name.isEmpty()) {
            try {
                Element bold = Jsoup.parseBodyFragment(htmlEntry).selectFirst("font.nickname b");
                name = bold == null ? "" : bold.text();
            } catch (Exception ignored) {
                name = "";
            }
        }
        return cleanText(stripTags(name));
    }

    private static String extractImageUrl(String htmlEntry, String uid) {
        Matcher matcher = INPUT_TAG_PATTERN.matcher(htmlEntry);
        while (matcher.find()) {
            String tag = matcher.group();
            String lower = tag.toLowerCase(Locale.ROOT);
            if (!lower.contains("get_id=57") || !lower.contains("uid=" + uid.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!lower.contains("type=image") && !lower.contains("type=\"image\"") && !lower.contains("type='image'")) {
                continue;
            }
            String src = extractSrc(tag);
            if (!src.isEmpty()) {
                return normalizeImageUrl(src);
            }
        }
        return "";
    }

    private static String extractSrc(String tag) {
        Matcher matcher = SRC_ATTR_PATTERN.matcher(tag);
        if (!matcher.find()) {
            return "";
        }
        for (int index = 1; index <= 3; index++) {
            String value = matcher.group(index);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String extractPropertiesText(String htmlEntry, String itemName) {
        try {
            Document document = Jsoup.parseBodyFragment(htmlEntry, KaznaParser.BASE_MAIN_URL);
            Elements blocks = document.select("font.weaponch");
            String firstNonEmpty = "";
            for (Element block : blocks) {
                String text = normalizePropertiesText(block.outerHtml(), itemName);
                if (text.isEmpty()) {
                    continue;
                }
                if (firstNonEmpty.isEmpty()) {
                    firstNonEmpty = text;
                }
                if (isLikelyPropertiesBlock(text)) {
                    return text;
                }
            }
            return firstNonEmpty;
        } catch (Exception ignored) {
            return extractPropertiesTextFallback(htmlEntry, itemName);
        }
    }

    private static String extractPropertiesTextFallback(String htmlEntry, String itemName) {
        int start = indexOfIgnoreCase(htmlEntry, "<font class=weaponch>");
        if (start == -1) {
            return "";
        }
        int end = indexOfIgnoreCase(htmlEntry, "</td><td", start);
        if (end == -1) {
            end = htmlEntry.length();
        }
        return normalizePropertiesText(htmlEntry.substring(start, end), itemName);
    }

    private static boolean isLikelyPropertiesBlock(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("цена:")
                || lower.contains("долговечность:")
                || lower.contains("материал:")
                || lower.contains("владелец:")
                || lower.contains("коэффициент:")
                || lower.contains("класс брони")
                || lower.contains("можно надевать");
    }

    private static String normalizePropertiesText(String html, String itemName) {
        String withBreaks = BR_PATTERN.matcher(html == null ? "" : html).replaceAll("\n");
        String text = Jsoup.parseBodyFragment(withBreaks).body().wholeText();
        String[] lines = text.replace('\u00A0', ' ').split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        String cleanName = cleanText(itemName);
        for (String line : lines) {
            String cleaned = cleanText(line);
            if (cleaned.isEmpty()) {
                continue;
            }
            if (!cleanName.isEmpty() && cleaned.equalsIgnoreCase(cleanName)) {
                continue;
            }
            if (cleaned.equalsIgnoreCase("свойства")) {
                continue;
            }
            if (cleaned.equalsIgnoreCase("требования")) {
                break;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(cleaned);
        }
        return sb.toString();
    }

    private static String normalizeImageUrl(String src) {
        String normalized = cleanText(src).replace("&amp;", "&");
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        if (normalized.startsWith("//")) {
            return "http:" + normalized;
        }
        if (normalized.startsWith("/")) {
            return "http://image.neverlands.ru" + normalized;
        }
        return "http://image.neverlands.ru/" + normalized;
    }

    private static String stripTags(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Jsoup.parseBodyFragment(value).text();
    }

    private static String cleanText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static int indexOfIgnoreCase(String source, String needle) {
        return indexOfIgnoreCase(source, needle, 0);
    }

    private static int indexOfIgnoreCase(String source, String needle, int fromIndex) {
        if (source == null || needle == null) {
            return -1;
        }
        return source.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT), Math.max(0, fromIndex));
    }
}

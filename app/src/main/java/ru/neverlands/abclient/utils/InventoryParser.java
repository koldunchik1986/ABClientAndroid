package ru.neverlands.abclient.utils;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import ru.neverlands.abclient.utils.FileLogger;
import ru.neverlands.abclient.utils.HelperStrings;

/**
 * Парсит инвентарь из HTML таблицы.
 * Эквивалент C# функции GetInvList из ABClient/PostFilter/MainPhpWear.cs
 *
 * Структура инвентаря в HTML:
 * <table border=0 cellpadding=0 cellspacing=0 width=100%>
 *   <tr><td bgcolor=#F5F5F5> [... item block ...] </td></tr>
 *   <tr><td bgcolor=#F5F5F5> [... item block ...] </td></tr>
 * </table>
 *
 * Каждый item содержит кнопку Надеть:
 * <input type=button ... onclick="location='main.php?get_id=57&wid=27975541&vcode=...'" value="Надеть">
 */
public class InventoryParser {
    private static final String TAG = "InventoryParser";

    /**
     * Результат парсинга одного предмета инвентаря
     */
    public static class InventoryItem {
        public String name;           // "Телескопическая Облегченная Удочка"
        public String wearUrl;        // "main.php?get_id=57&wid=27975541&vcode=..."
        public String durability;     // "8/600"
        public int position;          // позиция в инвентаре (для сортировки)

        public InventoryItem(String name, String wearUrl, String durability, int position) {
            this.name = name;
            this.wearUrl = wearUrl;
            this.durability = durability;
            this.position = position;
        }

        @Override
        public String toString() {
            return String.format("'%s' (dur=%s) wearUrl=%s", name, durability, wearUrl);
        }
    }

    /**
     * Парсит инвентарь из HTML и возвращает список предметов
     *
     * @param html HTML страницы инвентаря
     * @return List<InventoryItem> отсортированный по позициям
     */
    public static List<InventoryItem> parseInventory(String html) {
        List<InventoryItem> items = new ArrayList<>();

        if (html == null || html.isEmpty()) {
            Log.d(TAG, "InventoryParser: empty html");
            return items;
        }

        try {
            // Находим начало таблицы инвентаря
            // По паттерну C# кода ищем строку "</b></font></td></tr>"
            // которая обычно завершает заголовок таблицы
            final String patternStartInv = "</b></font></td></tr>";
            int pos = html.indexOf(patternStartInv);
            if (pos == -1) {
                Log.d(TAG, "InventoryParser: inventory table start pattern not found");
                return items;
            }

            pos += patternStartInv.length();
            int itemCount = 0;

            while (pos < html.length()) {
                // Ищем начало строки с предметом
                final String patternStartTr = "<tr><td bgcolor=#F5F5F5>";
                if (pos + patternStartTr.length() > html.length() ||
                    !html.substring(pos, Math.min(pos + patternStartTr.length(), html.length()))
                         .startsWith(patternStartTr)) {
                    break;
                }

                // Ищем конец предмета
                final String patternEndTr = "<td bgcolor=#FCFAF3><img src=http://image.neverlands.ru/1x1.gif width=5 height=1></td></tr></table></td></tr></table></td></tr>";
                int posEnd = html.indexOf(patternEndTr, pos);

                if (posEnd == -1) {
                    // Альтернативный конец
                    final String patternEndTrShort = "<img src=http://image.neverlands.ru/1x1.gif width=1 height=5></td></tr></table></td></tr>";
                    posEnd = html.indexOf(patternEndTrShort, pos);
                    if (posEnd == -1) {
                        break;  // Не найдено окончание
                    }
                    posEnd += patternEndTrShort.length();
                } else {
                    posEnd += patternEndTr.length();
                }

                // Парсим блок предмета
                String htmlEntry = html.substring(pos, posEnd);
                InventoryItem item = parseInventoryItem(htmlEntry, itemCount);
                if (item != null) {
                    items.add(item);
                    itemCount++;
                }

                pos = posEnd;
            }

            String msg = String.format("✅ InventoryParser: parsed %d items", items.size());
            Log.d(TAG, msg);
            FileLogger.trace(TAG, msg);

            return items;

        } catch (Exception e) {
            Log.w(TAG, "InventoryParser parse error", e);
            FileLogger.trace(TAG, "⚠️ InventoryParser error: " + e.getMessage());
            return items;
        }
    }

    /**
     * Парсит один предмет из HTML блока
     */
    private static InventoryItem parseInventoryItem(String htmlEntry, int position) {
        try {
            // Ищем кнопку Надеть с ссылкой wear
            // Паттерн: onclick="location='main.php?get_id=57&wid=...&vcode=...'" value="Надеть"
            String wearUrl = HelperStrings.subString(
                htmlEntry,
                "<input type=button class=invbut onclick=\"location='",
                "'\" value=\"Надеть\">"
            );

            if (wearUrl == null || wearUrl.isEmpty()) {
                // Если нет кнопки Надеть - это не одежда, скипаем
                return null;
            }

            // Ищем название предмета
            // Обычно перед кнопкой передачи есть строка вида:
            // "Телескопическая Облегченная Удочка" или в title атрибуте
            // Используем простой паттерн - ищем текст перед onclick=
            String nameMarker = "invbut onclick=\"location='";
            int nameStart = htmlEntry.lastIndexOf(">", htmlEntry.indexOf(nameMarker) - 1);
            String itemName = "";

            if (nameStart > 0) {
                // Ищем текст перед кнопкой
                String beforeButton = htmlEntry.substring(0, htmlEntry.indexOf(nameMarker));
                // Извлекаем последний текст перед "<button" или "<input"
                int lastGt = beforeButton.lastIndexOf(">");
                if (lastGt > 0) {
                    String textBefore = beforeButton.substring(lastGt + 1).trim();
                    // Убираем <b>, <font> и другие теги
                    itemName = textBefore
                        .replaceAll("<[^>]*>", "")
                        .trim();
                }
            }

            // Если не получилось извлечь имя стандартным способом, ищем в title
            if (itemName.isEmpty() || itemName.length() > 200) {
                String title = HelperStrings.subString(htmlEntry, "title='", "'");
                if (title != null && !title.isEmpty()) {
                    itemName = title;
                }
            }

            // Ищем durability в формате "текущее/максимум"
            // Обычно рядом с item name
            String durability = "";
            int durIndex = htmlEntry.indexOf(".");
            if (durIndex > 0) {
                // Простой поиск - последняя строка с цифрами/слешем
                String[] lines = htmlEntry.split("<br>");
                for (String line : lines) {
                    if (line.contains("/") && Character.isDigit(line.charAt(0))) {
                        durability = line.replaceAll("<[^>]*>", "").trim();
                        break;
                    }
                }
            }

            if (itemName.isEmpty()) {
                Log.w(TAG, "InventoryParser: could not extract item name from entry");
                return null;
            }

            InventoryItem item = new InventoryItem(itemName, wearUrl, durability, position);
            Log.d(TAG, "Parsed item: " + item);
            return item;

        } catch (Exception e) {
            Log.w(TAG, "InventoryParser: error parsing item", e);
            return null;
        }
    }

    /**
     * Ищет в инвентаре первый предмет по названию
     * @param items список предметов инвентаря
     * @param namePattern частичное совпадение имени (case-insensitive)
     * @return InventoryItem или null
     */
    public static InventoryItem findItemByName(List<InventoryItem> items, String namePattern) {
        if (items == null || namePattern == null) {
            return null;
        }

        String pattern = namePattern.toLowerCase();
        for (InventoryItem item : items) {
            if (item.name.toLowerCase().contains(pattern)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Ищет в инвентаре первый предмет удочку (удочка или спиннинг)
     */
    public static InventoryItem findAnyRod(List<InventoryItem> items) {
        if (items == null) {
            return null;
        }

        for (InventoryItem item : items) {
            String lower = item.name.toLowerCase();
            if (lower.contains("удочка") || lower.contains("спиннинг")) {
                return item;
            }
        }
        return null;
    }

    /**
     * Ищет in инвентаре конкретную удочку по названию
     */
    public static InventoryItem findSpecificRod(List<InventoryItem> items, String rodName) {
        if (rodName == null || rodName.equals("Любая удочка")) {
            return findAnyRod(items);
        }
        return findItemByName(items, rodName);
    }
}

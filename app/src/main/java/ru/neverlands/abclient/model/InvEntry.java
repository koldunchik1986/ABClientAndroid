package ru.neverlands.abclient.model;

import org.jsoup.nodes.Element;

import java.util.Locale;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.HelperStrings;

/**
 * Модель одной записи инвентаря (порт C# {@code ABClient/InvEntry.cs}).
 *
 * Зависимости:
 * - {@link HelperStrings} для шаблонного извлечения подстрок из HTML;
 * - {@link AppVars#ServerDateTime} для вычисления просроченности предмета;
 * - {@link AppVars#Profile} для флагов массовых кнопок (sell/drop).
 *
 * Назначение:
 * - хранить ключевые поля предмета для группировки/сортировки;
 * - уметь модифицировать HTML записи под пачку и массовые действия.
 */
public class InvEntry implements Cloneable, Comparable<InvEntry> {
    public String html;
    public String Dolg;
    public String Name;
    public int Count;
    public String Image;
    public String Id;
    public String Signature;
    public String WearLink;
    public String PssThing;
    public int PssPrice;
    public String PssLink;
    public String DropThing;
    public String DropPrice;
    public String DropLink;
    public boolean isArt;
    public boolean isUniq;

    private String rawHtml;
    private String properties = "";
    private int level;
    private int countButton;
    private int dolgOne;
    private int dolgTwo;
    private boolean expirible;
    private boolean expired;

    /**
     * Создаёт запись предмета из HTML-узла строки инвентаря.
     *
     * Зависимости:
     * - входной {@link Element} должен представлять одну запись `<tr>...</tr>` инвентаря;
     * - фактический разбор выполняется методом {@link #parseEntryHtml(String)}.
     */
    public InvEntry(Element row) {
        this.html = row == null ? "" : row.outerHtml();
        this.rawHtml = this.html;
        this.Count = 1;
        parseEntryHtml(this.html);
    }

    /**
     * Порт основного парсера C#-версии.
     *
     * Зависимости:
     * - HTML-шаблон сервера Neverlands (кнопки "Надеть", "Продать", delete-иконка);
     * - метки "Срок годности", "Долговечность", "Уровень", "Цена".
     *
     * Назначение:
     * - заполнить поля, необходимые для:
     *   1) dedup одинаковых предметов,
     *   2) корректной вставки bulk-кнопок,
     *   3) обработки просроченных предметов.
     */
    private void parseEntryHtml(String sourceHtml) {
        if (sourceHtml == null) {
            sourceHtml = "";
        }

        Name = safe(HelperStrings.subString(sourceHtml, "<font class=nickname><b> ", "</b>"));
        if (Name.isEmpty()) {
            Name = safe(HelperStrings.subString(sourceHtml, "<font class=nickname><b>", "</b>")).trim();
        }

        WearLink = safe(HelperStrings.subString(
                sourceHtml,
                "<input type=button class=invbut onclick=\"location='",
                "'\" value=\"Надеть\">"));
        if (WearLink.isEmpty()) {
            int wearButtonPos = sourceHtml.indexOf("value=\"Надеть\"");
            if (wearButtonPos != -1) {
                int locationPos = sourceHtml.lastIndexOf("location='", wearButtonPos);
                if (locationPos != -1) {
                    int start = locationPos + "location='".length();
                    int end = sourceHtml.indexOf('\'', start);
                    if (end > start) {
                        WearLink = sourceHtml.substring(start, end);
                    }
                }
            }
        }

        parseSellFields(sourceHtml);
        parseDropFields(sourceHtml);
        parseDurability(sourceHtml);
        parseExpiration(sourceHtml);

        Image = safe(HelperStrings.subString(sourceHtml, " src=http://", " "));
        if (!Image.isEmpty()) {
            Image = "http://" + Image;
        }

        String levelString = safe(HelperStrings.subString(sourceHtml, "<br>Уровень: <b>", "</b>"));
        if (!levelString.isEmpty()) {
            try {
                level = Integer.parseInt(levelString.trim());
            } catch (NumberFormatException ignore) {
                level = 0;
            }
        }

        countButton = countOccurrencesIgnoreCase(sourceHtml, "<input type=button");
        properties = extractComparableProperties(sourceHtml);

        isArt = sourceHtml.toLowerCase(Locale.ROOT).contains("artefact");
        isUniq = sourceHtml.toLowerCase(Locale.ROOT).contains("uniq");
    }

    /**
     * Парсит поля продажи (Pss*) из HTML записи.
     *
     * Порт участка C# `InvEntry(..)` с поиском блока `Продать за`.
     */
    private void parseSellFields(String sourceHtml) {
        int posSell = indexOfIgnoreCase(sourceHtml, "Продать за");
        if (posSell == -1) {
            return;
        }
        int sellStart = lastIndexOfIgnoreCase(sourceHtml, "<input", posSell);
        if (sellStart == -1) {
            return;
        }
        int sellEnd = sourceHtml.indexOf('>', posSell);
        if (sellEnd == -1) {
            return;
        }
        String sellHtml = sourceHtml.substring(sellStart, sellEnd + 1);

        String thing = HelperStrings.subString(sellHtml, "продать < ", " >");
        if (thing == null) {
            thing = HelperStrings.subString(sellHtml, "Продать < ", " >");
        }
        String priceString = HelperStrings.subString(sellHtml, "> за ", " NV");
        String link = HelperStrings.subString(sellHtml, "location='", "' ");
        if (link == null) {
            link = HelperStrings.subString(sellHtml, "location='", "'");
        }
        if (thing == null || priceString == null || link == null) {
            return;
        }
        try {
            int price = Integer.parseInt(priceString.trim());
            PssThing = thing;
            PssPrice = price;
            PssLink = link;
        } catch (NumberFormatException ignore) {
            // Невалидная цена — считаем, что массовая продажа для записи недоступна.
        }
    }

    /**
     * Парсит поля выброса (Drop*) из HTML записи.
     *
     * Порт участка C# `InvEntry(..)` с шаблоном `if(top.DeleteTrue('...'))`.
     */
    private void parseDropFields(String sourceHtml) {
        String thing = HelperStrings.subString(sourceHtml, "if(top.DeleteTrue('", "'))");
        if (thing == null || thing.isEmpty()) {
            return;
        }
        String pattern = "if(top.DeleteTrue('" + thing + "')) { location='";
        String link = HelperStrings.subString(sourceHtml, pattern, "'");
        if (link == null || link.isEmpty()) {
            return;
        }
        String price = HelperStrings.subString(sourceHtml, "Цена: <b>", " NV</b>");
        DropThing = thing;
        DropLink = link;
        DropPrice = safe(price);
    }

    /**
     * Извлекает долговечность и её числовые части.
     *
     * Зависимости:
     * - формат "Долговечность: <b>x/y</b>".
     */
    private void parseDurability(String sourceHtml) {
        Dolg = safe(HelperStrings.subString(sourceHtml, "Долговечность: <b>", "</b>"));
        if (Dolg.isEmpty()) {
            return;
        }
        String[] parts = Dolg.split("/");
        if (parts.length != 2) {
            return;
        }
        try {
            dolgOne = Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException ignore) {
            dolgOne = 0;
        }
        try {
            dolgTwo = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException ignore) {
            dolgTwo = 0;
        }
    }

    /**
     * Определяет просроченность предмета по серверному времени.
     *
     * Порт C#-логики:
     * - поле считается просроченным, если {@code ServerDateTime > exptime + 1 day}.
     */
    private void parseExpiration(String sourceHtml) {
        String exp = HelperStrings.subString(sourceHtml, "<font color=#cc0000>Срок годности: ", "</font>");
        if (exp == null || exp.isEmpty()) {
            expirible = false;
            expired = false;
            return;
        }
        expirible = true;
        try {
            String[] parts = exp.split("[. :]");
            if (parts.length >= 5) {
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int year = Integer.parseInt(parts[2]);
                int hour = Integer.parseInt(parts[3]);
                int minute = Integer.parseInt(parts[4]);
                java.util.Calendar calendar = java.util.Calendar.getInstance();
                calendar.set(java.util.Calendar.YEAR, year);
                calendar.set(java.util.Calendar.MONTH, month - 1);
                calendar.set(java.util.Calendar.DAY_OF_MONTH, day);
                calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
                calendar.set(java.util.Calendar.MINUTE, minute);
                calendar.set(java.util.Calendar.SECOND, 0);
                calendar.set(java.util.Calendar.MILLISECOND, 0);
                long expireMs = calendar.getTimeInMillis() + 24L * 60L * 60L * 1000L;
                java.util.Date serverDateTime = AppVars.ServerDateTime;
                if (serverDateTime != null && serverDateTime.getTime() > expireMs) {
                    expired = true;
                }
            }
        } catch (Exception ignore) {
            // Если формат даты неожиданно поменялся — просто не маркируем просрочку.
        }
    }

    /**
     * Формирует строку свойств для сравнения предметов.
     *
     * Назначение:
     * - сохранить C#-идею "одинаковые по свойствам предметы попадают в одну пачку",
     *   даже если у них одинаковое имя.
     */
    private String extractComparableProperties(String sourceHtml) {
        String lower = sourceHtml.toLowerCase(Locale.ROOT);
        int weaponChPos = lower.indexOf("<font class=weaponch>");
        if (weaponChPos == -1) {
            return "";
        }
        String tail = sourceHtml.substring(weaponChPos);
        String[] lines = tail.split("<br>");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String low = trimmed.toLowerCase(Locale.ROOT);
            if (low.contains("цена: <b>") || low.contains("материал: <b>")) {
                continue;
            }
            boolean packByDolg = AppVars.Profile != null && AppVars.Profile.DoInvPackDolg;
            if (packByDolg && low.contains("долговечность: <b>")) {
                // C# parity (`DoInvPackDolg=true`): убираем долговечность из ключа сравнения,
                // чтобы одинаковые предметы с разным x/y могли объединяться в одну пачку.
                continue;
            }
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(trimmed);
        }
        return sb.toString();
    }

    /**
     * Инкрементирует размер пачки.
     *
     * Зависимости:
     * - вызывается из алгоритма dedup в `MainPhp.mainPhpInv`.
     */
    public void inc() {
        Count++;
    }

    /**
     * Возвращает признак просроченного предмета.
     */
    public boolean isExpired() {
        return expirible && expired;
    }

    /**
     * Добавляет кнопку "Продать пачку" в HTML записи (если возможна массовая продажа).
     *
     * Порт C# `AddBulkSell()`:
     * - вставка кнопки рядом с обычной кнопкой "Продать за ...";
     * - вызов `window.external.StartBulkSell(...)`.
     */
    public void addBulkSell() {
        if (Count <= 1) {
            return;
        }
        if (PssThing == null || PssThing.isEmpty() || PssLink == null || PssLink.isEmpty() || PssPrice <= 0) {
            return;
        }
        int posSell = indexOfIgnoreCase(rawHtml, "Продать за");
        if (posSell == -1) {
            return;
        }
        int sellStart = lastIndexOfIgnoreCase(rawHtml, "<input", posSell);
        if (sellStart == -1) {
            return;
        }
        int sellEnd = rawHtml.indexOf('>', posSell);
        if (sellEnd == -1) {
            return;
        }
        String pssButton = String.format(Locale.ROOT,
                " <input type=button class=invbut onclick=\"javascript: if(confirm('Вы точно хотите продать все предметы < %s > по %d NV?')) { window.external.StartBulkSell('%s', '%d', '%s'); }\" value=\"Продать пачку за %d NV\">",
                PssThing,
                PssPrice,
                PssThing,
                PssPrice,
                PssLink,
                PssPrice * Count);
        rawHtml = rawHtml.substring(0, sellEnd + 1) + pssButton + rawHtml.substring(sellEnd + 1);
    }

    /**
     * Добавляет кнопку "Выбросить всю пачку" в HTML записи.
     *
     * Порт C# `AddBulkDelete()`:
     * - кнопка вставляется перед стандартной delete-иконкой;
     * - вызов `window.external.StartBulkDrop(...)`.
     */
    public void addBulkDelete() {
        if (Count <= 1) {
            return;
        }
        if (DropThing == null || DropThing.isEmpty() || DropPrice == null || DropPrice.isEmpty()) {
            return;
        }
        final String deletePattern = "<input type=image src=http://image.neverlands.ru/del.gif width=14 height=14 border=0 onclick=\"javascript: if(top.DeleteTrue('";
        int posDelete = indexOfIgnoreCase(rawHtml, deletePattern);
        if (posDelete == -1) {
            return;
        }
        String dropButton = " <input type=image src=http://image.neverlands.ru/del.gif width=14 height=14 border=0 title=\"Выбросить всю пачку\" onclick=\"javascript: if(top.DeleteTrue('Пачку')) { window.external.StartBulkDrop('"
                + DropThing + "', '" + DropPrice + "'); }\" value=\"X\">&nbsp;";
        rawHtml = rawHtml.substring(0, posDelete) + dropButton + rawHtml.substring(posDelete);
    }

    /**
     * Возвращает HTML записи после модификаций (count/prosrochka/bulk).
     *
     * Порт C# `Build()`:
     * - красит просроченные предметы;
     * - вставляет маркер "ПРОСРОЧЕНО!";
     * - добавляет суффикс `(<N> шт.)` к имени пачки.
     */
    public String build() {
        String work = rawHtml;
        if (isExpired()) {
            work = work.replace("bgcolor=#F5F5F5", "bgcolor=#F5E5E5");
            work = work.replace("bgcolor=#FFFFFF", "bgcolor=#F5E5E5");
            work = work.replace("bgcolor=#FCFAF3", "bgcolor=#F5E5E5");
            work = work.replace("bgcolor=#D8CDAF", "bgcolor=#F5D5D5");
            int posName = indexOfIgnoreCase(work, "<font class=nickname><b> ");
            if (posName != -1) {
                work = work.substring(0, posName)
                        + "<font class=nickname><font color=#cc0000><b>ПРОСРОЧЕНО!</b></font></font> "
                        + work.substring(posName);
            }
        }
        if (Count <= 1) {
            return work;
        }
        int posName = indexOfIgnoreCase(work, "<font class=nickname><b> ");
        if (posName != -1) {
            int posEnd = indexOfIgnoreCase(work, "</b>", posName);
            if (posEnd != -1) {
                String countText = " (" + Count + " шт.)";
                return work.substring(0, posEnd) + countText + work.substring(posEnd);
            }
        }
        return work;
    }

    /**
     * Сравнение "одинаковости" предмета для упаковки в пачку.
     *
     * Порт C# `CompareTo`:
     * - имя, картинка, признаки срока, уровень, число action-кнопок, свойства.
     */
    @Override
    public int compareTo(InvEntry other) {
        if (other == null) {
            return 1;
        }
        int result = safe(Name).compareTo(safe(other.Name));
        if (result != 0) {
            return result;
        }
        result = safe(Image).compareTo(safe(other.Image));
        if (result != 0) {
            return result;
        }
        if (expirible != other.expirible) {
            return Boolean.compare(expirible, other.expirible);
        }
        if (expirible && expired != other.expired) {
            return Boolean.compare(expired, other.expired);
        }
        result = Integer.compare(level, other.level);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(countButton, other.countButton);
        if (result != 0) {
            return result;
        }
        boolean packByDolg = AppVars.Profile != null && AppVars.Profile.DoInvPackDolg;
        if (!packByDolg) {
            result = safe(Dolg).compareTo(safe(other.Dolg));
            if (result != 0) {
                return result;
            }
        }
        return safe(properties).compareTo(safe(other.properties));
    }

    /**
     * Сравнение долговечности для выбора "лучшего представителя" пачки.
     *
     * Порт C# `CompareDolg`:
     * - сначала полнота (`x==y`), затем `x`, затем `y`.
     */
    public int compareDolg(InvEntry other) {
        if (other == null) {
            return 0;
        }
        boolean isFull = dolgOne == dolgTwo;
        boolean isFullOther = other.dolgOne == other.dolgTwo;
        int result = Boolean.compare(isFull, isFullOther);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(dolgOne, other.dolgOne);
        if (result != 0) {
            return result;
        }
        return Integer.compare(dolgTwo, other.dolgTwo);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int countOccurrencesIgnoreCase(String source, String pattern) {
        if (source == null || source.isEmpty() || pattern == null || pattern.isEmpty()) {
            return 0;
        }
        String lowerSource = source.toLowerCase(Locale.ROOT);
        String lowerPattern = pattern.toLowerCase(Locale.ROOT);
        int count = 0;
        int from = 0;
        while (true) {
            int pos = lowerSource.indexOf(lowerPattern, from);
            if (pos == -1) {
                return count;
            }
            count++;
            from = pos + lowerPattern.length();
        }
    }

    private static int indexOfIgnoreCase(String source, String needle) {
        if (source == null || needle == null) {
            return -1;
        }
        return source.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private static int indexOfIgnoreCase(String source, String needle, int fromIndex) {
        if (source == null || needle == null) {
            return -1;
        }
        return source.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT), Math.max(fromIndex, 0));
    }

    private static int lastIndexOfIgnoreCase(String source, String needle, int fromIndex) {
        if (source == null || needle == null) {
            return -1;
        }
        int safeFrom = Math.min(Math.max(fromIndex, 0), source.length());
        return source.toLowerCase(Locale.ROOT).lastIndexOf(needle.toLowerCase(Locale.ROOT), safeFrom);
    }
}

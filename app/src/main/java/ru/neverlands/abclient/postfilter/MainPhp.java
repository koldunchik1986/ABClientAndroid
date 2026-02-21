package ru.neverlands.abclient.postfilter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import ru.neverlands.abclient.model.InvComparer;
import ru.neverlands.abclient.model.InvEntry;
import ru.neverlands.abclient.manager.FastActionManager;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.HelperStrings;
import ru.neverlands.abclient.utils.Russian;

public class MainPhp {
    private static final String TAG = "MainPhp";

    public static byte[] process(String address, byte[] array) {
        android.util.Log.d(TAG, "process() called for " + address + ", bytes=" + (array != null ? array.length : 0));
        // Сохраняем исходный ответ, если он нужен где-то еще
        AppVars.lastMainPhpResponse = array;
        AppVars.IdleTimer = System.currentTimeMillis();
        AppVars.LastMainPhp = System.currentTimeMillis();
        AppVars.ContentMainPhp = null;

        String html = Russian.getString(array);
        android.util.Log.d(TAG, "HTML length after getString: " + html.length());
        android.util.Log.d(TAG, "HTML first 200: " + html.substring(0, Math.min(200, html.length())));
        html = Filter.removeDoctype(html);

        // Извлечение vcode - полезная логика из новой версии
        String vcode = HelperStrings.subString(html, "'main.php?get_id=56&act=10&go=inf&vcode=", "'");
        if (vcode != null) {
            AppVars.VCode = vcode;
        }

        // Логирование в файлы убрано, чтобы устранить зависания

        // Обработка быстрых действий (портировано из MainPhp.cs строки 1429-1619)
        // В C# FastAction обрабатывается ВНУТРИ MainPhp, а не в отдельном менеджере.
        // Алгоритм: MainPhpFindInv → BuildRedirect на инвентарь → MainPhpIsInv → MainPhpFast → BuildRedirect на категорию
        if (AppVars.FastNeed) {
            byte[] fastResult = processMainPhpFast(address, html);
            if (fastResult != null) {
                return fastResult;
            }
        }

        // Placeholder for fight logic
        if (html.contains("magic_slots();")) {
            html = mainPhpFight(html);
        }

        // Обработка инвентаря, основанная на стабильной версии из app_work
        if (html.contains("/invent/0.gif")) {
            html = mainPhpInv(html);
        }

        if (html.contains("var map = [[")) {
            html = MapAjax.process(html);
        }

        // ... other placeholders ...

        AppVars.ContentMainPhp = html;
        byte[] result = Russian.getBytes(html);
        android.util.Log.d(TAG, "process() returning " + result.length + " bytes for " + address);
        android.util.Log.d(TAG, "Result first 200: " + html.substring(0, Math.min(200, html.length())));
        return result;
    }

    /**
     * Обработка FastAction внутри MainPhp (аналог C# MainPhp.cs строки 1429-1619).
     *
     * Алгоритм C#:
     * 1. Определяем нужную категорию (wca=28 для свитков, wca=27 для зелий)
     * 2. Если мы НЕ на инвентаре — MainPhpFindInv → BuildRedirect на инвентарь с фильтром
     * 3. Если мы НА инвентаре — MainPhpFast → ищем предмет → авто-submit
     * 4. Если предмет не найден и мы не на нужной вкладке — BuildRedirect на вкладку
     * 5. Если мы на нужной вкладке и предмет не найден — отмена
     *
     * @return byte[] с результатом (HTML redirect или форма), или null если FastAction не обработан
     */
    private static byte[] processMainPhpFast(String address, String html) {
        if (!AppVars.FastNeed || AppVars.FastId == null) return null;

        String fastId = AppVars.FastId;
        android.util.Log.d(TAG, "processMainPhpFast: FastId=" + fastId + ", address=" + address);

        // Определяем нужный фильтр категории
        String filter = getInventoryFilter(fastId);
        if (filter == null) {
            android.util.Log.w(TAG, "processMainPhpFast: неизвестный FastId=" + fastId);
            return null;
        }

        android.util.Log.d(TAG, "processMainPhpFast: filter=" + filter
                + ", isInv=" + mainPhpIsInv(html)
                + ", w28_form=" + html.contains("w28_form(")
                + ", magicreform=" + html.contains("magicreform("));

        // 1. Если мы НЕ на инвентаре — ищем ссылку на инвентарь с фильтром
        String invRedirect = mainPhpFindInv(html, filter);
        if (invRedirect != null) {
            android.util.Log.d(TAG, "processMainPhpFast: redirect на инвентарь: " + invRedirect);
            return Russian.getBytes(invRedirect);
        }

        // 2. Если мы НА инвентаре — проверяем категорию и ищем предмет
        if (mainPhpIsInv(html)) {
            String filterClean = filter.startsWith("&") ? filter.substring(1) : filter;

            // 2a. Сначала проверяем, на правильной ли мы вкладке категории.
            // Если address не содержит нужный фильтр (wca=28/wca=27),
            // перенаправляем на нужную категорию ПЕРЕД поиском предмета.
            // Это критично при 500+ предметах в инвентаре — поиск по всему
            // HTML (695KB) вместо отфильтрованной страницы (28KB) слишком медленный.
            if (!address.contains(filterClean)) {
                android.util.Log.d(TAG, "processMainPhpFast: на инвентаре, но не на нужной категории ("
                        + filterClean + "), переключаем");
                return Filter.buildRedirect("Переключение на нужную категорию",
                        "main.php?" + filterClean);
            }

            // 2b. Мы на правильной вкладке — ищем предмет
            String fastHtml = FastActionManager.processMainPhp(html);
            if (fastHtml != null) {
                // Предмет найден! processMainPhp уже обработал FastCount
                android.util.Log.d(TAG, "processMainPhpFast: УСПЕХ, предмет найден");
                return Russian.getBytes(fastHtml);
            }

            // 3. Мы на правильной вкладке, предмет не найден — отмена
            android.util.Log.w(TAG, "processMainPhpFast: предмет не найден на правильной вкладке ("
                    + filterClean + "), отмена");
            FastActionManager.fastCancel();
            return null;
        }

        // Мы не на инвентаре и MainPhpFindInv не нашла ссылку — вероятно, нужен обычный reload
        android.util.Log.d(TAG, "processMainPhpFast: не на инвентаре, MainPhpFindInv не нашла ссылку");
        return null;
    }

    /**
     * Определяет фильтр инвентаря по FastId.
     * Аналог switch в C# MainPhp.cs строки 1436-1534
     *
     * @return строка фильтра (например "&im=0&wca=28") или null для неизвестного FastId
     */
    private static String getInventoryFilter(String fastId) {
        if (fastId == null) return null;

        switch (fastId) {
            // Свитки и нападалки → wca=28
            case "i_svi_001.gif":
            case "i_svi_002.gif":
            case "i_w28_26.gif":
            case "i_w28_26X.gif":
            case "i_svi_205.gif":
            case "i_w28_24.gif":
            case "i_w28_25.gif":
            case "i_w28_22.gif":
            case "i_w28_23.gif":
            case "i_w28_28.gif":
            case "i_svi_213.gif":
            case "i_w28_27.gif":
            case "i_w28_86.gif":
                return "&im=0&wca=28";

            // Зелья → wca=27
            case "Яд":
            case "Зелье Сильной Спины":
            case "Зелье Невидимости":
            case "Зелье Блаженства":
            case "Зелье Метаболизма":
            case "Зелье Просветления":
            case "Зелье Сокрушительных Ударов":
            case "Зелье Стойкости":
            case "Зелье Недосягаемости":
            case "Зелье Точного Попадания":
            case "Зелье Ловких Ударов":
            case "Зелье Мужества":
            case "Зелье Жизни":
            case "Зелье Лечения":
            case "Зелье Восстановления Маны":
            case "Зелье Энергии":
            case "Зелье Удачи":
            case "Зелье Силы":
            case "Зелье Ловкости":
            case "Зелье Гения":
            case "Зелье Боевой Славы":
            case "Зелье Секрет Волшебника":
            case "Зелье Медитации":
            case "Зелье Иммунитета":
            case "Зелье Лечения Отравлений":
            case "Зелье Огненного Ореола":
            case "Зелье Колкости":
            case "Зелье Загрубелой Кожи":
            case "Зелье Панциря":
            case "Зелье Человек-гора":
            case "Зелье Скорости":
            case "Жажда Жизни":
            case "Ментальная Жажда":
            case "Зелье подвижности":
            case "Ярость Берсерка":
            case "Зелье Хрупкости":
            case "Зелье Мифриловый Стержень":
            case "Зелье Соколиный взор":
            case "Секретное Зелье":
                return "&im=0&wca=27";

            // Эликсиры → im=6
            case "Эликсир Блаженства":
            case "Эликсир Мгновенного Исцеления":
            case "Эликсир Восстановления":
                return "&im=6";

            // Телепорт остров
            case "Телепорт (Остров Туротор)":
                return "&im=0&wca=28";

            default:
                return null;
        }
    }

    /**
     * Проверяет, что мы на странице инвентаря (аналог MainPhpIsInv в MainPhpDrink.cs:221-224).
     * Инвентарь содержит ссылку <a href="?im=0"><img...
     */
    private static boolean mainPhpIsInv(String html) {
        return html.contains("<a href=\"?im=0\"><img") || html.contains("<a href=?im=0><img");
    }

    /**
     * Ищет ссылку на инвентарь в текущем HTML и генерирует redirect.
     * Портировано из MainPhpDrink.cs — MainPhpFindInv (строки 86-219).
     *
     * В C# есть несколько стратегий поиска vcode:
     * 1. view_arena() + var vcode = [...] — арена
     * 2. view_moor()/view_taverna()/etc + var vcode = [[1,"..."]] — здания
     * 3. Кнопка "Инвентарь" с onclick location='...'
     * 4. JSON массив ["inv","Инвентарь","vcode"...]
     *
     * @param html   HTML страницы
     * @param filter Фильтр категории (например "&im=0&wca=28")
     * @return HTML redirect строка или null
     */
    private static String mainPhpFindInv(String html, String filter) {
        // Если мы уже на инвентаре — не нужен redirect
        if (mainPhpIsInv(html)) {
            return null;
        }

        // Стратегия 1: view_arena() — арена
        if (html.contains("view_arena()")) {
            String result = mainPhpFindInvArena(html, filter);
            if (result != null) return result;
        }

        // Стратегия 2: view_moor/taverna/magic_sch/library/teleport — здания
        if (html.contains("view_moor()") || html.contains("view_taverna()")
                || html.contains("view_magic_sch()") || html.contains("view_library()")
                || html.contains("view_teleport()")) {
            String result = mainPhpFindInvBuilding(html, filter);
            if (result != null) return result;
        }

        // Стратегия 3: Кнопка "Инвентарь" с onclick
        if (html.contains("Инвентарь") || html.contains("\u0418\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044C")) {
            String result = mainPhpFindInvOld(html, filter);
            if (result != null) return result;
        }

        // Стратегия 4: JSON ["inv","Инвентарь","vcode"...]
        String patternEnter = "[\"inv\",\"Инвентарь\",\"";
        int pos = html.indexOf(patternEnter);
        if (pos == -1) {
            // Пробуем варианты с экранированными кавычками
            patternEnter = "[\"inv\",\"\u0418\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044C\",\"";
            pos = html.indexOf(patternEnter);
        }
        if (pos != -1) {
            pos += patternEnter.length();
            int posEnd = html.indexOf('"', pos);
            if (posEnd != -1) {
                String vcodeInv = html.substring(pos, posEnd);
                String link = "main.php?get_id=56&act=10&go=inv&vcode=" + vcodeInv + filter;
                return buildRedirectHtml("Переключение на инвентарь", link);
            }
        }

        // Стратегия 5: Кнопка "Вернуться" → main.php (для случая когда мы внутри инвентаря, но на другой странице)
        if (html.contains("value=\"Вернуться\">") || html.contains("value=\"\u0412\u0435\u0440\u043D\u0443\u0442\u044C\u0441\u044F\">")) {
            if (html.contains("onclick=\"location='../main.php'\"") || html.contains("onclick=\"location='main.php'\"")) {
                return buildRedirectHtml("Переключение на инвентарь", "main.php");
            }
        }

        return null;
    }

    /**
     * Стратегия поиска инвентаря на арене (view_arena).
     * Аналог MainPhpDrink.cs строки 99-130
     */
    private static String mainPhpFindInvArena(String html, String filter) {
        String patternArena = "var vcode = [";
        int pos = html.indexOf(patternArena);
        if (pos == -1) return null;

        pos += patternArena.length();
        int posEnd = html.indexOf(']', pos);
        if (posEnd == -1) return null;

        String vcodeargs = html.substring(pos, posEnd);
        String[] pvcode = vcodeargs.split(",");
        if (pvcode.length < 2) return null;

        String avcode = pvcode[1].replace("\"", "").trim();
        if (avcode.isEmpty()) return null;

        String link = "main.php?get_id=56&act=10&go=inv&vcode=" + avcode + filter;
        return buildRedirectHtml("Переключение на инвентарь", link);
    }

    /**
     * Стратегия поиска инвентаря в зданиях (view_moor, view_taverna и т.д.).
     * Аналог MainPhpDrink.cs строки 142-180
     */
    private static String mainPhpFindInvBuilding(String html, String filter) {
        String patternArena = "var vcode = [";
        int pos = html.indexOf(patternArena);
        if (pos == -1) return null;

        pos += patternArena.length();
        // Ищем второй vcode в формате [1,"hash"]
        String pattern2 = ",[1,\"";
        pos = html.indexOf(pattern2, pos);
        if (pos == -1) return null;

        pos += pattern2.length();
        int posEnd = html.indexOf("]", pos);
        if (posEnd == -1) return null;

        // vcode заканчивается перед последней кавычкой и скобкой
        String avcode = html.substring(pos, posEnd - 1);
        if (avcode.isEmpty()) return null;

        String link = "main.php?get_id=56&act=10&go=inv&vcode=" + avcode + filter;
        return buildRedirectHtml("Переключение на инвентарь", link);
    }

    /**
     * Ищет кнопку "Инвентарь" с onclick (аналог MainPhpFindInvOld в MainPhpDrink.cs:33-84).
     */
    private static String mainPhpFindInvOld(String html, String filter) {
        // Вариант 1: value="Инвентарь">
        String s1 = "value=\"Инвентарь\">";
        int p1 = html.indexOf(s1);
        if (p1 == -1) {
            s1 = "value=\"\u0418\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044C\">";
            p1 = html.indexOf(s1);
        }

        if (p1 != -1) {
            String onclick = "onclick=\"location='";
            int p2 = html.lastIndexOf(onclick, p1);
            if (p2 != -1) {
                p2 += onclick.length();
                int p3 = html.indexOf("'", p2);
                if (p3 != -1) {
                    String link = html.substring(p2, p3) + filter;
                    return buildRedirectHtml("Переключение на инвентарь", link);
                }
            }
        }

        // Вариант 2: class=lbut value="Инвентарь"
        String s1x = "class=lbut value=\"Инвентарь\"";
        int p1x = html.indexOf(s1x);
        if (p1x == -1) {
            s1x = "class=lbut value=\"\u0418\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044C\"";
            p1x = html.indexOf(s1x);
        }

        if (p1x != -1) {
            String onclick = "onclick=\"location='";
            int p2 = html.indexOf(onclick, p1x);
            if (p2 != -1) {
                p2 += onclick.length();
                int p3 = html.indexOf("'", p2);
                if (p3 != -1) {
                    String link = html.substring(p2, p3) + filter;
                    return buildRedirectHtml("Переключение на инвентарь", link);
                }
            }
        }

        return null;
    }

    /**
     * Генерирует HTML-страницу с JavaScript redirect (String-версия buildRedirect).
     * Аналог BuildRedirect в Filter.cs:280-291
     */
    private static String buildRedirectHtml(String description, String link) {
        return ru.neverlands.abclient.utils.HtmlUtils.GENERATED_PAGE_MARKER +
                "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">" +
                "<title>ABClient</title></head><body>" +
                description +
                "<script language=\"JavaScript\">window.location = \"" + link + "\";</script></body></html>";
    }

    private static String mainPhpFight(String html) {
        // TODO: Port MainPhpFight.cs
        return html;
    }

    // Этот метод основан на стабильной и производительной реализации из app_work
    private static String mainPhpInv(String html) {
        try {
            Document doc = Jsoup.parse(html);
            
            // Ищем контейнер с инвентарем. Используем более гибкий селектор.
            Elements inventoryContainers = doc.select("td[background*='i_bg_2.gif']");
            if (inventoryContainers.isEmpty()) {
                return html; // Не нашли инвентарь, ничего не делаем
            }
            
            Element inventoryContainer = inventoryContainers.first();
            if (inventoryContainer == null) {
                return html;
            }

            // Ищем все таблицы внутри контейнера, которые могут быть предметами
            Elements tables = inventoryContainer.select("table");
            List<InvEntry> invList = new ArrayList<>();

            for (Element table : tables) {
                // Предмет в инвентаре обычно имеет картинку из /weapon/ или /invent/
                String tableHtml = table.html();
                if (tableHtml.contains("/weapon/") || tableHtml.contains("/invent/")) {
                    // В оригинальном коде брался parent().parent().parent(), 
                    // что соответствует строке таблицы инвентаря.
                    Element row = table;
                    while (row != null && !row.tagName().equals("tr")) {
                        row = row.parent();
                    }
                    if (row != null) {
                        invList.add(new InvEntry(row));
                    }
                }
            }

            if (invList.isEmpty()) {
                return html;
            }

            // Логика группировки (упаковки) предметов
            if (AppVars.Profile != null && AppVars.Profile.DoInvPack) {
                for (int i = 0; i < invList.size() - 1; i++) {
                    for (int j = i + 1; j < invList.size(); j++) {
                        if (invList.get(i).compareTo(invList.get(j)) == 0) {
                            if (invList.get(i).compareDolg(invList.get(j)) > 0) {
                                invList.set(i, invList.get(j));
                            }
                            invList.get(i).inc();
                            invList.remove(j);
                            j--;
                        }
                    }
                }
            }

            // Добавляем кастомные кнопки
            for (InvEntry entry : invList) {
                entry.addBulkSell();
                entry.addBulkDelete();
            }

            // Логика сортировки
            if (AppVars.Profile != null && AppVars.Profile.DoInvSort) {
                Collections.sort(invList, new InvComparer());
            }

            // Сохраняем в AppVars для доступа из других компонентов
            AppVars.InvList = new ArrayList<>(invList);

            // Пересобираем HTML инвентаря
            StringBuilder newHtml = new StringBuilder();
            newHtml.append("<tr><td align=center bgcolor=#f5f5f5>");
            for (InvEntry entry : invList) {
                newHtml.append(entry.build());
            }
            newHtml.append("</td></tr>");

            // Заменяем содержимое контейнера инвентаря
            inventoryContainer.parent().parent().html(newHtml.toString());
            
            return doc.outerHtml();
        } catch (Exception e) {
            // В случае любой ошибки парсинга, возвращаем исходный HTML, чтобы не уронить приложение
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            String exceptionAsString = sw.toString();
            ru.neverlands.abclient.utils.DebugLogger.log("Error during mainPhpInv processing: \n" + exceptionAsString);
            return html;
        }
    }
}

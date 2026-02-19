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

        // Обработка быстрых действий (портировано из MainPhpFast.cs)
        if (AppVars.FastNeed) {
            String fastResult = FastActionManager.processMainPhp(html);
            if (fastResult != null) {
                return Russian.getBytes(fastResult);
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

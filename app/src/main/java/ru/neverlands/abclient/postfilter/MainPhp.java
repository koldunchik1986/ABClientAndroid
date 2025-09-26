package ru.neverlands.abclient.postfilter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

public class MainPhp {

    /**
     * Основной метод обработки main.php.
     * На данный момент является "заглушкой" и выполняет только базовые функции.
     * Вся сложная логика автоматизации будет добавлена позже согласно плану в TODO/todo_MainPhp.cs.md.
     */
    public static byte[] process(String address, byte[] array) {
        filterGetLocation(address);

        AppVars.IdleTimer = System.currentTimeMillis();
        AppVars.LastMainPhp = System.currentTimeMillis();

        String html = Russian.getString(array);
        html = Filter.removeDoctype(html);

        AppVars.ContentMainPhp = html;

        // TODO: В будущем здесь будет вызываться конвейер обработчиков:
        // parsePlayerState(html);
        // handleSystemMessages(html);
        // String redirectHtml = handleAutoActions(html, address);
        // if (redirectHtml != null) return Russian.Codepage.getBytes(redirectHtml);

        return Russian.getBytes(html);
    }

    /**
     * Извлекает координаты (gx, gy) из URL и сохраняет их.
     */
    private static void filterGetLocation(String url) {
        if (url == null) return;

        try {
            Pattern patternX = Pattern.compile("&gx=(\\d+)");
            Matcher matcherX = patternX.matcher(url);
            if (!matcherX.find()) return;

            Pattern patternY = Pattern.compile("&gy=(\\d+)");
            Matcher matcherY = patternY.matcher(url);
            if (!matcherY.find()) return;

            int gx = Integer.parseInt(matcherX.group(1));
            int gy = Integer.parseInt(matcherY.group(1));

            // TODO: Реализовать Map.convertToRegNum(gx, gy) и сохранить в AppVars.LocationReal
            // AppVars.LocationReal = Map.convertToRegNum(gx, gy);

        } catch (Exception e) {
            // Ошибка парсинга, ничего не делаем
        }
    }
}

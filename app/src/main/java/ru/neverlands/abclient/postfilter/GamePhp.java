package ru.neverlands.abclient.postfilter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

/**
 * Класс для обработки содержимого файла game.php.
 * Аналог GamePhp.cs в оригинальном приложении.
 */
public class GamePhp {

    /**
     * Генерирует базовый HTML-заголовок.
     * Аналог HelperErrors.Head() в оригинальном приложении.
     * @return Строка с HTML-заголовком.
     */
    private static String generateHtmlHead() {
        return "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\"></head><body>";
    }

    /**
     * Обрабатывает HTML-содержимое game.php.
     * Вставляет логику для автоматического ввода Flash-пароля, если это необходимо.
     * @param array Массив байт, содержащий HTML-содержимое game.php.
     * @return Обработанный массив байт.
     */
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);

        // Удаляем DOCTYPE из HTML
        html = Filter.removeDoctype(html);

        if (AppVars.WaitFlash) {
            if (AppVars.Profile != null && !AppVars.Profile.UserPasswordFlash.isEmpty()) {
                // flashvars="plid=827098"
                final String flashid = "flashvars=\"plid=";
                int pos = html.toLowerCase().indexOf(flashid.toLowerCase()); // Ищем без учета регистра
                if (pos > -1) {
                    pos += flashid.length();
                    int pose = html.indexOf('"', pos);
                    if (pose > -1) {
                        String pid = html.substring(pos, pose);
                        StringBuilder sb = new StringBuilder(
                                generateHtmlHead() +
                                "Ввод флеш-пароля..." +
                                "<form action=\"./game.php\" method=POST name=ff>" +
                                "<input name=flcheck type=hidden value=\"" +
                                "");
                        sb.append(AppVars.Profile.UserPasswordFlash);
                        sb.append("\"> <input name=nid type=hidden value=\"" +
                                "");
                        sb.append(pid);
                        sb.append(
                                "\"></form>" +
                                "<script language=\"JavaScript\">" +
                                "document.ff.submit();" +
                                "</script></body></html>");
                        AppVars.ContentMainPhp = sb.toString();
                        return Russian.getBytes(AppVars.ContentMainPhp);
                    }

                    AppVars.ContentMainPhp = html;
                    return Russian.getBytes(AppVars.ContentMainPhp);
                }
            }
        }

        AppVars.WaitFlash = false;

        parseFrames(html);

        String result = generateHtmlHead() + "<body></body></html>";
        return Russian.getBytes(result);
    }

    /**
     * Парсит HTML-код для извлечения URL-адресов из тегов <frame>.
     * @param html HTML-код страницы.
     */
    private static void parseFrames(String html) {
        Pattern p = Pattern.compile("<frame src=\"([^\"]+)\" name=\"([^\"]+)\".*?>", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);

        while (m.find()) {
            String url = m.group(1);
            String name = m.group(2);

            switch (name) {
                case "main_top":
                    AppVars.url_main_top = url;
                    break;
                case "chmain":
                    AppVars.url_chmain = url;
                    break;
                case "ch_list":
                    AppVars.url_ch_list = url;
                    break;
                case "ch_buttons":
                    AppVars.url_ch_buttons = url;
                    break;
                case "ch_refr":
                    AppVars.url_ch_refr = url;
                    break;
            }
        }
    }
}

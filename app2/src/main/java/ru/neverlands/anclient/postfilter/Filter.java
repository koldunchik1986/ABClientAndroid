package ru.neverlands.anclient.postfilter;

import android.content.Context;

import java.util.Date;
import java.util.regex.Pattern;

import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.Russian;

// Маршрутизатор пост‑фильтров: выбирает обработчик по URL (JS/HTML/CGI).
public class Filter {
    private static final Pattern DOCTYPE_PATTERN = Pattern.compile("(?is)<!DOCTYPE[^>]*>");

    // Пред‑обработка (пока не используется, оставлена под будущие хуки).
    public static byte[] preProcess(String address, byte[] array) {
        return array;
    }

    // Главная точка входа фильтрации ответа.
    public static byte[] process(Context context, String address, byte[] array) {
        if (address == null || address.isEmpty() || array == null) {
            return array;
        }
        address = normalizeNeverlandsHost(address);

        // JS‑фильтры (подмены/инъекции скриптов).
        if (address.contains(".js")) {
            if (address.contains("liveinternet.ru") || address.contains("top.mail.ru") || address.contains("hotlog.ru")) {
                return CounterJs.process();
            }
            if (address.endsWith("/js/hp.js")) {
                return HpJs.process(array);
            }
            if (address.contains("/js/map.js")) {
                return MapJs.process(context, array);
            }
            if (address.contains("/js/mine")) {
                return MineJs.process(context, array);
            }
            if (address.endsWith("/js/game.js")) {
                return GameJs.process(array);
            }
            if (address.contains("/js/fight_v")) {
                return FightJs.process(array);
            }
            if (address.endsWith("/js/hpmp.js")) {
                return HpmpJs.process();
            }
            if (address.endsWith("/ch/ch_msg_v01.js")) {
                return ChMsgJs.process(array);
            }
            if (address.endsWith("/ch_list.js")) {
                return ChListJs.process(array);
            }
            if (address.endsWith("/castle.js")) {
                return CastleJs.process(array);
            }
            if (address.endsWith("/castle_v05.js")) {
                return CastleJs.process(array);
            }
            if (address.endsWith("/counter.js")) {
                return CounterJs.process();
            }
        }

        // Основные HTML/CGI обработчики.
        if (address.startsWith("http://neverlands.ru/index.cgi") || address.equals("http://neverlands.ru/")) {
            return IndexCgi.process(array);
        }

        if (address.startsWith("http://neverlands.ru/pbots.cgi")) {
            return removeDoctype(array);
        }

        if (address.startsWith("http://forum.neverlands.ru/")) {
            return removeDoctype(array);
        }

        if (address.startsWith("http://neverlands.ru/game.php")) {
            return GamePhp.process(array);
        }

        // Главная страница: обновляем таймер соединения, обрабатываем фреймы/бой.
        if (address.startsWith("http://neverlands.ru/main.php")) {
            AppVars.NextCheckNoConnection = new Date(System.currentTimeMillis() + 5 * 60 * 1000);
            return MainPhp.process(address, array);
        }

        // Чат: окно сообщений.
        if (address.startsWith("http://neverlands.ru/ch/msg.php")) {
            return MsgPhp.process(array);
        }

        // Чат: окно кнопок/ввода (инъекция submit и парсинг времени сервера).
        if (address.contains("/ch/but.php")) {
            return ButPhp.process(address, array);
        }

        if (address.contains("map_ajax.php")) {
            String html = Russian.getString(array);
            html = MapAjax.process(html);
            return Russian.getBytes(html);
        }

        if (address.contains("teleport_ajax.php")) {
            String html = Russian.getString(array);
            String telepResult = TeleportAjax.process(html);
            if (telepResult != null) {
                return Russian.getBytes(telepResult);
            }
            return Russian.getBytes(html);
        }

        if (address.contains("map_act_ajax.php")) {
            return MapActAjaxPhp.process(array);
        }

        if (address.startsWith("http://neverlands.ru/gameplay/ajax/fish_ajax.php")) {
            return FishAjaxPhp.process(address, array);
        }

        if (address.startsWith("http://neverlands.ru/gameplay/ajax/mine_ajax.php")) {
            return MineAjaxPhp.process(address, array);
        }

        if (address.startsWith("http://neverlands.ru/gameplay/ajax/alchemy_ajax.php")) {
            return AlchemyAjaxPhp.process(address, array);
        }

        // Список игроков в чате (RoomManager).
        if (address.contains("/ch.php?lo=1")) {
            String html = Russian.getString(array);
            AppVars.url_ch_list = address;
            html = ru.neverlands.anclient.manager.RoomManager.process(context, html);
            return Russian.getBytes(html);
        }

        return array;
    }

    public static String buildRedirectString(String description, String link) {
        return ru.neverlands.anclient.utils.HtmlUtils.GENERATED_PAGE_MARKER +
               "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\"><title>ANClient</title></head><body>" +
               description +
               "<script language=\"JavaScript\">if(typeof AndroidBridge !== 'undefined' && AndroidBridge.redirectToUrl){ AndroidBridge.redirectToUrl(\"" + link + "\"); } else { window.location = \"" + link + "\"; }</script></body></html>";
    }

    public static byte[] buildRedirect(String description, String link) {
        // Используем AndroidBridge.redirectToUrl вместо window.location для перехвата в Android
        String html = ru.neverlands.anclient.utils.HtmlUtils.GENERATED_PAGE_MARKER +
                      "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\"><title>ANClient</title></head><body>" +
                      description +
                      "<script language=\"JavaScript\">if(typeof AndroidBridge !== 'undefined' && AndroidBridge.redirectToUrl){ AndroidBridge.redirectToUrl(\"" + link + "\"); } else { window.location = \"" + link + "\"; }</script></body></html>";
        return Russian.getBytes(html);
    }

    public static byte[] buildPostForm(String description, String action, String... params) {
        StringBuilder sb = new StringBuilder();
        sb.append(ru.neverlands.anclient.utils.HtmlUtils.GENERATED_PAGE_MARKER);
        sb.append("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\"><title>ANClient</title></head><body>");
        sb.append(description);
        sb.append("<form action=\"").append(action).append("\" method=POST name=ff>");
        
        for (int i = 0; i < params.length; i += 2) {
            if (i + 1 < params.length) {
                sb.append("<input type=hidden name=\"").append(params[i]).append("\" value=\"").append(params[i + 1]).append("\">");
            }
        }
        
        sb.append("<script language=\"JavaScript\">document.ff.submit();</script></form></body></html>");
        return Russian.getBytes(sb.toString());
    }

    public static byte[] buildGetForm(String description, String action, String... params) {
        StringBuilder sb = new StringBuilder();
        sb.append(ru.neverlands.anclient.utils.HtmlUtils.GENERATED_PAGE_MARKER);
        sb.append("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\"><title>ANClient</title></head><body>");
        sb.append(description);
        
        // Формируем URL с GET параметрами
        StringBuilder url = new StringBuilder();
        url.append(action).append("?");
        for (int i = 0; i < params.length; i += 2) {
            if (i > 0) url.append("&");
            url.append(params[i]).append("=").append(params[i + 1]);
        }
        
        // Используем GET редирект с задержкой для имитации реального игрока
        // Задержка 500-1500ms + случайная составляющая
        sb.append("<script language=\"JavaScript\">");
        sb.append("var delay = 500 + Math.floor(Math.random() * 1000);");
        sb.append("setTimeout(function(){ window.location = \"").append(url.toString()).append("\"; }, delay);");
        sb.append("</script></body></html>");
        return Russian.getBytes(sb.toString());
    }

    public static String removeDoctype(String html) {
        return DOCTYPE_PATTERN.matcher(html).replaceAll("");
    }

    public static byte[] removeDoctype(byte[] array) {
        String html = Russian.getString(array);
        html = removeDoctype(html);
        return Russian.getBytes(html);
    }

    private static String normalizeNeverlandsHost(String address) {
        if (address == null || address.isEmpty()) {
            return address;
        }
        final String wwwPrefix = "http://www.neverlands.ru/";
        if (address.startsWith(wwwPrefix)) {
            return "http://neverlands.ru/" + address.substring(wwwPrefix.length());
        }
        if ("http://www.neverlands.ru".equals(address)) {
            return "http://neverlands.ru";
        }
        return address;
    }
}

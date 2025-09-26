package ru.neverlands.abclient.postfilter;

import java.util.Date;
import java.util.regex.Pattern;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

public class Filter {
    private static final Pattern DOCTYPE_PATTERN = Pattern.compile("(?is)<!DOCTYPE[^>]*>");

    public static byte[] preProcess(String address, byte[] array) {
        return array;
    }

    public static byte[] process(String address, byte[] array) {
        if (address == null || address.isEmpty() || array == null) {
            return array;
        }

        if (address.contains(".js")) {
            if (address.contains("liveinternet.ru") || address.contains("top.mail.ru") || address.contains("hotlog.ru")) {
                return CounterJs.process();
            }
            if (address.contains("/js/hp.js")) {
                return HpJs.process(array);
            }
            if (address.contains("/js/map.js")) {
                return MapJs.process(array);
            }
            if (address.contains("/arena")) {
                return ArenaJs.process();
            }
            if (address.endsWith("/js/game.js")) {
                return GameJs.process(array);
            }
            if (address.contains("pinfo_v01.js")) {
                return PinfoJs.process(array);
            }
            if (address.contains("/js/fight_v")) {
                // return FightJs.process(array);
                return array; // Заглушка
            }
            if (address.contains("/js/building")) {
                return BuildingJs.process(array);
            }
            if (address.endsWith("/js/hpmp.js")) {
                return HpmpJs.process();
            }
            if (address.endsWith("/ch/ch_msg_v01.js")) {
                return ChMsgJs.process(array);
            }
            if (address.endsWith("/js/pv.js")) {
                return PvJs.process(array);
            }
            if (address.endsWith("/ch/ch_list.js")) {
                return ChListJs.process();
            }
            if (address.endsWith("/js/svitok.js")) {
                return SvitokJs.process(array);
            }
            if (address.endsWith("/js/slots.js")) {
                return SlotsJs.process(array);
            }
            if (address.contains("/js/logs")) {
                return LogsJs.process(array);
            }
            if (address.contains("/js/shop")) {
                return ShopJs.process(array);
            }
            if (address.contains("/js/forum/forum_topic.js")) {
                return ForumTopicJs.process(array);
            }
            if (address.endsWith("/js/top.js")) {
                return TopJs.process(array);
            }
        }

        if (address.startsWith("http://www.neverlands.ru/game.php")) {
            return GamePhp.process(array);
        }

        if (address.startsWith("http://www.neverlands.ru/main.php")) {
            AppVars.NextCheckNoConnection = new Date(System.currentTimeMillis() + 5 * 60 * 1000);
            return MainPhp.process(address, array);
        }

        if (address.startsWith("http://www.neverlands.ru/ch/msg.php")) {
            return MsgPhp.process(array);
        }

        if (address.startsWith("http://www.neverlands.ru/ch/but.php")) {
            return ButPhp.process(array);
        }

        if (address.contains("/ch.php?0")) {
            return ChZero.process(array);
        }
        
        if (address.startsWith("http://www.neverlands.ru/gameplay/ajax/map_act_ajax.php")) {
            return MapActAjaxPhp.process(array);
        }

        // ... и другие обработчики ...

        return array;
    }

    public static byte[] buildRedirect(String description, String link) {
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\"><title>ABClient</title></head><body>" +
                      description +
                      "<script language=\"JavaScript\">window.location = \"" + link + "\";</script></body></html>";
        return Russian.getBytes(html);
    }

    public static String removeDoctype(String html) {
        return DOCTYPE_PATTERN.matcher(html).replaceAll("");
    }

    public static byte[] removeDoctype(byte[] array) {
        String html = Russian.getString(array);
        html = removeDoctype(html);
        return Russian.getBytes(html);
    }
}

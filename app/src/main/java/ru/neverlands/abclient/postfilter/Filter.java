package ru.neverlands.abclient.postfilter;

import android.content.Context;

import java.util.Date;
import java.util.regex.Pattern;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

public class Filter {
    private static final Pattern DOCTYPE_PATTERN = Pattern.compile("(?is)<!DOCTYPE[^>]*>");

    public static byte[] preProcess(String address, byte[] array) {
        return array;
    }

    public static byte[] process(Context context, String address, byte[] array) {
        if (address == null || address.isEmpty() || array == null) {
            return array;
        }

        if (address.contains(".js")) {
            if (address.contains("liveinternet.ru") || address.contains("top.mail.ru") || address.contains("hotlog.ru")) {
                return CounterJs.process();
            }
            if (address.endsWith("/js/hp.js")) {
                return HpJs.process(array);
            }
            if (address.endsWith("/js/map.js")) {
                return MapJs.process(array);
            }
            if (address.endsWith("/arena.js")) {
                return ArenaJs.process();
            }
            if (address.endsWith("/js/game.js")) {
                return GameJs.process(array);
            }
            if (address.contains("pinfo_v01.js")) {
                return PinfoJs.process(array);
            }
            if (address.contains("/js/fight_v")) {
                return FightJs.process(array);
            }
            if (address.contains("/js/building.js")) {
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
            if (address.endsWith("/forum_topic.js")) {
                return ForumTopicJs.process(array);
            }
            if (address.endsWith("/logs.js")) {
                return LogsJs.process(array);
            }
            if (address.endsWith("/nl_pinfo.js")) {
                return NlPinfoJs.process(array);
            }
            if (address.endsWith("/outpost.js")) {
                return OutpostJs.process(array);
            }
            if (address.endsWith("/pinfonew.js")) {
                return PinfonewJs.process(array);
            }
            if (address.endsWith("/shop.js")) {
                return ShopJs.process(array);
            }
            if (address.endsWith("/slots.js")) {
                return SlotsJs.process(array);
            }
            if (address.endsWith("/svitok.js")) {
                return SvitokJs.process(array);
            }
            if (address.endsWith("/tarena.js")) {
                return TarenaJs.process(array);
            }
            if (address.endsWith("/top.js")) {
                return TopJs.process(array);
            }
            if (address.endsWith("/tower.js")) {
                return TowerJs.process(array);
            }
        }

        if (address.startsWith("http://www.neverlands.ru/index.cgi") || address.equals("http://www.neverlands.ru/")) {
            return IndexCgi.process(array);
        }

        if (address.startsWith("http://www.neverlands.ru/pinfo.cgi")) {
            return Pinfo.process(array);
        }

        if (address.startsWith("http://www.neverlands.ru/pbots.cgi")) {
            return removeDoctype(array);
        }

        if (address.startsWith("http://forum.neverlands.ru/")) {
            return removeDoctype(array);
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
            return ButPhp.process(address, array);
        }

        if (address.startsWith("http://www.neverlands.ru/gameplay/trade.php")) {
            return TradePhp.process(array);
        }

        if (address.contains("map_ajax.php")) {
            String html = Russian.getString(array);
            html = MapAjax.process(html);
            return Russian.getBytes(html);
        }

        if (address.contains("map_act_ajax.php")) {
            return MapActAjaxPhp.process(array);
        }

        if (address.startsWith("http://www.neverlands.ru/gameplay/ajax/fish_ajax.php")) {
            return FishAjaxPhp.process(array);
        }

        if (address.startsWith("http://www.neverlands.ru/gameplay/ajax/shop_ajax.php")) {
            return ShopAjaxPhp.process(array);
        }

        if (address.startsWith("http://www.neverlands.ru/gameplay/ajax/roulette_ajax.php")) {
            return RouletteAjaxPhp.process(array);
        }

        if (address.startsWith("http://www.neverlands.ru/ch.php")) {
            String html = Russian.getString(array);
            html = ru.neverlands.abclient.manager.RoomManager.process(context, html);
            return Russian.getBytes(html);
        }

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

package ru.neverlands.abclient.manager;

import java.util.ArrayList;
import java.util.List;

import ru.neverlands.abclient.utils.AppVars;

public class TorgList {
    private static TorgPair[] table;

    public static boolean trigger = false;
    public static boolean triggerBuy = false;
    public static String messageThanks = "";
    public static String messageNoMoney = "";
    public static String uidThing = "";

    public static boolean parse(String torgString) {
        if (torgString == null || torgString.isEmpty()) {
            return false;
        }

        List<TorgPair> newTorgList = new ArrayList<>();

        String work = torgString.replace(System.lineSeparator(), "").replace(" ", "").replace("(0)", "(*0)").replace("(-", "(*");
        String[] sp = work.split("[\\-()*,]");

        int i = 0;
        while (i < sp.length) {
            if (sp[i].isEmpty()) {
                i++;
                continue;
            }

            try {
                int lowValue = Integer.parseInt(sp[i]);
                if (++i >= sp.length) return false;

                int highValue = Integer.parseInt(sp[i]);
                if (lowValue > highValue) return false;

                if (++i >= sp.length) return false;
                if (!sp[i].isEmpty()) return false;

                if (++i >= sp.length) return false;
                int price = Integer.parseInt(sp[i]);
                if (price < 0) return false;

                TorgPair torgPair = new TorgPair(lowValue, highValue, -price);
                newTorgList.add(torgPair);

                i++;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        if (newTorgList.isEmpty()) {
            return false;
        }

        table = newTorgList.toArray(new TorgPair[0]);
        return true;
    }

    public static int calculate(int price) {
        if (table == null) return 0;

        for (TorgPair pair : table) {
            if (price >= pair.priceLow && price <= pair.priceHi) {
                return price + pair.bonus;
            }
        }

        int bonus = 0;
        int diffmin = Integer.MAX_VALUE;

        for (TorgPair pair : table) {
            if (price < pair.priceLow) {
                continue;
            }

            int diff = price - pair.priceLow;
            if (diff >= diffmin) {
                continue;
            }

            diffmin = diff;
            bonus = price + pair.bonus;
        }

        return bonus;
    }

    public static String doFilter(String message, String thing, String thingLevel, int price, int tableprice, int thingRealDolg, int thingFullDolg, int price90) {
        if (message == null) return "";
        message = message.replace("{таблица}", AppVars.Profile.getTorgTabl());
        message = message.replace("{вещь}", thing);
        message = message.replace("{вещьур}", thingLevel);
        message = message.replace("{вещьдолг}", thingRealDolg + "/" + thingFullDolg);
        message = message.replace("{цена}", String.valueOf(price));
        message = message.replace("{минцена}", String.valueOf(tableprice));
        message = message.replace("{цена90}", String.valueOf(price90));
        return message;
    }
}

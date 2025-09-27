package ru.neverlands.abclient.manager;

import java.util.ArrayList;
import java.util.List;
import ru.neverlands.abclient.utils.AppVars;

public class TorgList {
    public static final TorgList INSTANCE = new TorgList();

    private TorgPair[] table = new TorgPair[0];

    public boolean trigger = false;
    public boolean triggerBuy = false;
    public String messageThanks = "";
    public String messageNoMoney = "";
    public String uidThing = "";

    private TorgList() { }

    public boolean parse(String torgString) {
        if (torgString == null || torgString.isEmpty()) {
            return false;
        }

        List<TorgPair> newTorgList = new ArrayList<>();
        try {
            String work = torgString.replace("\n", "").replace("\r", "").replace(" ", "").replace("(-", "(*");
            String[] parts = work.split(",");
            for (String part : parts) {
                String[] p = part.split("[-(*)]");
                List<String> filteredParts = new ArrayList<>();
                for(String s : p) {
                    if(!s.isEmpty()) {
                        filteredParts.add(s);
                    }
                }

                if (filteredParts.size() < 3) continue;

                int lowValue = Integer.parseInt(filteredParts.get(0));
                int highValue = Integer.parseInt(filteredParts.get(1));
                int price = Integer.parseInt(filteredParts.get(2));

                if (lowValue > highValue) return false;
                if (price < 0) return false;

                TorgPair torgPair = new TorgPair(lowValue, highValue, -price);
                newTorgList.add(torgPair);
            }
        } catch (Exception e) {
            return false;
        }

        if (newTorgList.isEmpty()) {
            return false;
        }

        table = newTorgList.toArray(new TorgPair[0]);
        return true;
    }

    public int calculate(int price) {
        if (table.length == 0) {
            return 0;
        }

        for (TorgPair torgPair : table) {
            if (price >= torgPair.priceLow && price <= torgPair.priceHi) {
                return price + torgPair.bonus;
            }
        }

        int bonus = 0;
        int diffmin = Integer.MAX_VALUE;

        for (TorgPair torgPair : table) {
            if (price < torgPair.priceLow) {
                continue;
            }

            int diff = price - torgPair.priceLow;
            if (diff >= diffmin) {
                continue;
            }

            diffmin = diff;
            bonus = price + torgPair.bonus;
        }

        return bonus;
    }

    public String doFilter(String message, String thing, String thingLevel, int price, int tableprice, int thingRealDolg, int thingFullDolg, int price90) {
        if (AppVars.Profile == null) return message;
        return message
                .replace("{таблица}", AppVars.Profile.getTorgTabl())
                .replace("{вещь}", thing)
                .replace("{вещьур}", thingLevel)
                .replace("{вещьдолг}", thingRealDolg + "/" + thingFullDolg)
                .replace("{цена}", String.valueOf(price))
                .replace("{минцена}", String.valueOf(tableprice))
                .replace("{цена90}", String.valueOf(price90));
    }
}
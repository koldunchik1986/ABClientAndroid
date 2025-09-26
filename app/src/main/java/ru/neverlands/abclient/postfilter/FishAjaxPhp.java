package ru.neverlands.abclient.postfilter;

import java.util.HashMap;
import java.util.Map;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;
import ru.neverlands.abclient.utils.HelperStrings;

import ru.neverlands.abclient.utils.StringUtils;

public class FishAjaxPhp {

    private static class FishData {
        double price;
        double mass;

        FishData(double price, double mass) {
            this.price = price;
            this.mass = mass;
        }
    }

    private static final Map<String, FishData> fishStats = new HashMap<>();
    private static final Map<String, FishData> baitStats = new HashMap<>();

    static {
        fishStats.put("Карась", new FishData(4.32, 2));
        fishStats.put("Плотва", new FishData(3.62, 2));
        // ... (остальные рыбы)
    }

    public static byte[] process(byte[] array) {
        AppVars.PriSelected = false;
        String html = Russian.getString(array);

        if (html.contains("У Вас нет рыболовных снастей.") ||
            html.contains("У Вас нет приманки, чтобы ловить рыбу.") ||
            html.contains("Приманок нет в наличии.") ||
            html.contains("У Вас не хватает умения, чтобы ловить тут рыбу.")) {
            // TODO: Send broadcast to stop fishing
            return array;
        }

        int posOpenBracket = html.indexOf('"');
        if (posOpenBracket == -1) return array;
        posOpenBracket++;
        int posCloseBracket = html.indexOf('"', posOpenBracket);
        if (posCloseBracket == -1) return array;

        if (html.contains("лёв:")) {
            String newString = fishReport(html);
            if (newString != null && !newString.isEmpty()) {
                html = html.substring(0, posOpenBracket) + newString + html.substring(posCloseBracket);
                array = Russian.getBytes(html);
            }
        }

        return array;
    }

    private static String fishReport(String html) {
        boolean fishUmUp = html.contains("повысилось на 1!");
        AppVars.AutoFishCheckUm = fishUmUp || AppVars.Profile.getFishUm() == 0;

        int p1 = html.indexOf('«');
        if (p1 == -1) return "";
        int p2 = html.indexOf('»', p1);
        if (p2 == -1) return "";

        String nameFish = html.substring(p1 + 1, p2);
        String strNumFish = StringUtils.subString(html, "Улов: ", " шт.");
        int numFish = (strNumFish != null) ? Integer.parseInt(strNumFish) : 0;

        String strCatchFish = StringUtils.subString(html, "Клёв: ", " шт.");
        int catchFish = (strCatchFish != null) ? Integer.parseInt(strCatchFish) : 0;

        if (html.contains("Нет клёва.")) {
            catchFish = 0;
        } else if (html.contains("Не удалось вытащить рыбу.")) {
            return "";
        }

        StringBuilder sbr = new StringBuilder();
        sbr.append("<b>").append(nameFish).append("</b> [<b>").append(numFish).append('/').append(catchFish).append("</b>]. ");

        if (fishUmUp) {
            AppVars.Profile.setFishUm(AppVars.Profile.getFishUm() + 1);
        }

        if (AppVars.Profile.getFishUm() > 0) {
            sbr.append("Умелка:&nbsp;<b>").append(AppVars.Profile.getFishUm()).append("</b>");
            if (fishUmUp) {
                sbr.append("&nbsp;(<font color=#008800><b>+1<img src=http://image.neverlands.ru/gameplay/up.gif></b></font>)");
            }
        }

        FishData fishData = fishStats.get(nameFish);
        double fishp = (fishData != null) ? fishData.price : 0;

        String namepri = AppVars.NamePri;
        FishData baitData = baitStats.get(namepri);
        double prim = (baitData != null) ? baitData.price : 0;

        double bal = (fishp * numFish) - (prim * catchFish);
        AppVars.AutoFishNV += bal;

        // TODO: Send broadcast to update fish NV: (int)bal

        sbr.append("<br>");
        sbr.append(AppVars.AutoFishNV < 0 ? "Потери&nbsp;за&nbsp;рыбалку" : "Доход&nbsp;за&nbsp;рыбалку");
        sbr.append(":&nbsp;");
        String sbal = String.format("%.2f", AppVars.AutoFishNV);
        if (AppVars.AutoFishNV < 0) {
            sbr.append("<font color=#CC0000><b>").append(sbal).append("<img src=http://image.neverlands.ru/gameplay/down.gif></b></font>&nbsp;NV");
        } else {
            sbr.append("<font color=#008800><b>+").append(sbal).append("<img src=http://image.neverlands.ru/gameplay/up.gif></b></font>&nbsp;NV");
        }

        AppVars.AutoFishCheckUd = true;
        AppVars.AutoFishWearUd = false;
        return sbr.toString();
    }
}
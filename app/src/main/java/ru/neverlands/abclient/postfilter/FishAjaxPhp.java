package ru.neverlands.abclient.postfilter;

import java.util.List;
import java.util.Locale;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.Intent;

import ru.neverlands.abclient.ABClientApplication;
import ru.neverlands.abclient.model.FishData;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Chat;
import ru.neverlands.abclient.utils.HelperStrings;
import ru.neverlands.abclient.utils.Russian;

public class FishAjaxPhp {

    public static byte[] process(byte[] array) {
        AppVars.PriSelected = false;
        String html = Russian.getString(array);

        if (html.contains("У Вас нет рыболовных снастей.") ||
                html.contains("У Вас нет приманки, чтобы ловить рыбу.") ||
                html.contains("Приманок нет в наличии.") ||
                html.contains("У Вас не хватает умения, чтобы ловить тут рыбу.")) {

            Intent intent = new Intent(AppVars.ACTION_STOP_AUTOFISH);
            LocalBroadcastManager.getInstance(ABClientApplication.getAppContext()).sendBroadcast(intent);
            return array;
        }

        int posOpenBracket = html.indexOf('"');
        if (posOpenBracket == -1) {
            return array;
        }

        posOpenBracket++;
        int posCloseBracket = html.indexOf('"', posOpenBracket);
        if (posCloseBracket == -1) {
            return array;
        }

        if (html.contains("лёв:") || html.contains("Нет клёва") || html.contains("Не удалось вытащить рыбу")) {
            String newString = fishReport(html);
            if (newString != null && !newString.isEmpty()) {
                html = html.substring(0, posOpenBracket) + newString + html.substring(posCloseBracket);
            }
            array = Russian.getBytes(html);
        }

        return array;
    }

    private static String fishReport(String html) {
        int numFish = 0;
        int catchFish = 0;
        boolean fishUmUp = html.contains("повысилось на 1!");

        AppVars.AutoFishCheckUm = fishUmUp || AppVars.Profile.getFishUm() == 0;

        String nameFish = HelperStrings.subString(html, "«", "»");
        if (nameFish == null) {
            nameFish = ""; // Handle cases like "Нет клёва"
        }

        String strNumFish = HelperStrings.subString(html, "Улов: ", " шт.");
        if (strNumFish != null) {
            try {
                numFish = Integer.parseInt(strNumFish);
            } catch (NumberFormatException e) { /* ignore */ }
        }

        String strCatchFish = HelperStrings.subString(html, "Клёв: ", " шт.");
        if (strCatchFish != null) {
            try {
                catchFish = Integer.parseInt(strCatchFish);
            } catch (NumberFormatException e) { /* ignore */ }
        }

        if (html.contains("Нет клёва.")) {
            catchFish = 0;
        } else if (html.contains("Не удалось вытащить рыбу.")) {
            return ""; // C# version returns empty string here
        }

        StringBuilder sbr = new StringBuilder();
        StringBuilder s2 = new StringBuilder(); // For tray notification

        sbr.append("<b>").append(nameFish).append("</b> [<b>").append(numFish)
           .append('/').append(catchFish).append("</b>]. ");

        if (fishUmUp) {
            AppVars.Profile.setFishUm(AppVars.Profile.getFishUm() + 1);
        }

        if (AppVars.Profile.getFishUm() > 0) {
            sbr.append("Умелка").append(":&nbsp;<b>").append(AppVars.Profile.getFishUm()).append("</b>");
            s2.append("Умелка: ").append(AppVars.Profile.getFishUm());
            if (fishUmUp) {
                sbr.append("&nbsp;(<font color=#008800><b>+1<img src=http://image.neverlands.ru/gameplay/up.gif width=10 height=14></b></font>)");
                s2.append(" (+1)");
            }
            s2.append("\n");
        }

        double fishPrice = 0;
        double fishMass = 0;
        if (FishData.FISH_PRICES.containsKey(nameFish)) {
            FishData.FishInfo info = FishData.FISH_PRICES.get(nameFish);
            fishPrice = info.price;
            fishMass = info.mass;
        }

        double baitPrice = 0;
        double baitMass = 0;
        String baitName = AppVars.NamePri;
        if (FishData.BAIT_PRICES.containsKey(baitName)) {
            FishData.FishInfo info = FishData.BAIT_PRICES.get(baitName);
            baitPrice = info.price;
            baitMass = info.mass;
        }

        double balance = (fishPrice * numFish) - (baitPrice * catchFish);

        if (AppVars.AutoFishHand1 != null && !AppVars.AutoFishHand1.isEmpty()) {
            sbr.append("<br>Долговечность").append(":&nbsp;<b>").append(AppVars.AutoFishHand1D).append("</b>");
            s2.append(AppVars.AutoFishHand1).append(" (до заброса): ").append(AppVars.AutoFishHand1D).append("\n");
            balance -= 2.5;
        }

        if (AppVars.AutoFishMassa != null && !AppVars.AutoFishMassa.isEmpty() && (numFish > 0 || catchFish > 0)) {
            sbr.append("<br>Масса").append(":&nbsp;<b>");
            s2.append("Масса: ");

            double massChange = (fishMass * numFish) - (baitMass * catchFish);
            String[] massParts = AppVars.AutoFishMassa.split("/");
            try {
                double currentMass = Double.parseDouble(massParts[0]) + massChange;
                AppVars.AutoFishMassa = String.format(Locale.US, "%.2f", currentMass) + "/" + massParts[1];
                sbr.append(AppVars.AutoFishMassa);
                s2.append(AppVars.AutoFishMassa);

                if (massChange != 0) {
                    String sign = massChange > 0 ? "+" : "";
                    String color = massChange > 0 ? "#008800" : "#CC0000";
                    String arrow = massChange > 0 ? "up.gif" : "down.gif";
                    sbr.append("</b>&nbsp;(<font color=").append(color).append("><b>").append(sign)
                       .append(String.format(Locale.US, "%.2f", massChange))
                       .append("<img src=http://image.neverlands.ru/gameplay/").append(arrow).append(" width=10 height=14></b></font>)");
                    s2.append(" (").append(sign).append(String.format(Locale.US, "%.2f", massChange)).append(")");
                }
            } catch (Exception e) { /* ignore */ }
            s2.append("\n");
        }

        if (baitName != null && !baitName.isEmpty()) {
            sbr.append("<br><b>").append(baitName).append("</b>&nbsp;(остаток):&nbsp;<b>");
            s2.append(baitName).append(" (остаток): ");
            int remainingBait = AppVars.ValPri - catchFish;
            sbr.append(remainingBait);
            s2.append(remainingBait);
            if (catchFish > 0) {
                sbr.append("</b>&nbsp;(<font color=#CC0000><b>-").append(catchFish)
                   .append("<img src=http://image.neverlands.ru/gameplay/down.gif width=10 height=14></b></font>)");
                s2.append(" (-").append(catchFish).append(")");
            }
            s2.append("\n");
        }

        s2.append(balance < 0 ? "Потери: " : "Доход: ");
        s2.append(String.format(Locale.US, "%.2f", balance)).append(" NV\n");

        AppVars.AutoFishNV += balance;

        // This should be done via ViewModel and LiveData
        // if (AppVars.MainForm != null) { AppVars.MainForm.updateFishNV(balance); }

        sbr.append("<br>");
        sbr.append(AppVars.AutoFishNV < 0 ? "Потери&nbsp;за&nbsp;рыбалку" : "Доход&nbsp;за&nbsp;рыбалку");
        sbr.append(":&nbsp;");
        s2.append(AppVars.AutoFishNV < 0 ? "Потери за рыбалку: " : "Доход за рыбалку: ");

        String totalBalanceStr = String.format(Locale.US, "%.2f", AppVars.AutoFishNV);
        if (AppVars.AutoFishNV < 0) {
            sbr.append("<font color=#CC0000><b>").append(totalBalanceStr)
               .append("<img src=http://image.neverlands.ru/gameplay/down.gif width=10 height=14></b></font>&nbsp;NV");
            s2.append(totalBalanceStr).append(" NV");
        } else {
            sbr.append("<font color=#008800><b>+").append(totalBalanceStr)
               .append("<img src=http://image.neverlands.ru/gameplay/up.gif width=10 height=14></b></font>&nbsp;NV");
            s2.append("+").append(totalBalanceStr).append(" NV");
        }

        if (AppVars.Profile.ShowTrayBaloons) {
            // This should be a native notification
            // NotificationManager.show(s2.toString());
            System.out.println("Tray Notification: " + s2.toString());
        }

        if (!nameFish.isEmpty() && AppVars.Profile.FishChatReport) {
            // This should be a call to a ChatViewModel or ChatManager
            Chat.addMessageToChat("Рыбалка: " + s2.toString().replace('\n', ' '));
        }

        AppVars.AutoFishCheckUd = true;
        AppVars.AutoFishWearUd = false;
        return sbr.toString();
    }
}

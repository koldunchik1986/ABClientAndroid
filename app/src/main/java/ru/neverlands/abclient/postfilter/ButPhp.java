package ru.neverlands.abclient.postfilter;

import java.util.Date;
import java.util.Calendar;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.HelperStrings;
import ru.neverlands.abclient.utils.Russian;

public class ButPhp {
    public static byte[] process(String address, byte[] array) {
        String html = Russian.getString(array);

        // Sync server time
        try {
            String hourStr = HelperStrings.subString(html, "hour=", "&");
            String minStr = HelperStrings.subString(html, "min=", "&");
            String secStr = HelperStrings.subString(html, "sec=", "\"");

            if (hourStr != null && minStr != null && secStr != null) {
                int hour = Integer.parseInt(hourStr);
                int min = Integer.parseInt(minStr);
                int sec = Integer.parseInt(secStr);

                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, min);
                cal.set(Calendar.SECOND, sec);
                Date serverTime = cal.getTime();
                
                long diff = new Date().getTime() - serverTime.getTime();
                AppVars.Profile.ServDiff = diff;
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }

        // Name the send button
        html = html.replace("/b1.gif", "/b1.gif name=butinp");

        // Hook smile buttons
        html = html.replace("smile_open('')", "javascript:AndroidBridge.showSmiles(1)");
        html = html.replace("smile_open('2')", "javascript:AndroidBridge.showSmiles(2)");

        return Russian.getBytes(html);
    }
}
package ru.neverlands.abclient.postfilter;

import java.util.Calendar;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

public class ButPhp {

    private static String subString(String text, String start, String end) {
        if (text == null) return null;
        int p1 = text.indexOf(start);
        if (p1 == -1) {
            return null;
        }
        p1 += start.length();
        int p2 = text.indexOf(end, p1);
        if (p2 == -1) {
            return null;
        }
        return text.substring(p1, p2);
    }

    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);

        // Time parsing logic
        String sHour = subString(html, "hour=", "&");
        String sMin = subString(html, "min=", "&");
        String sSec = subString(html, "sec=", "\"");

        if (sHour != null && sMin != null && sSec != null) {
            try {
                int hour = Integer.parseInt(sHour);
                int min = Integer.parseInt(sMin);
                int sec = Integer.parseInt(sSec);

                Calendar now = Calendar.getInstance();
                Calendar serverTime = (Calendar) now.clone();
                serverTime.set(Calendar.HOUR_OF_DAY, hour);
                serverTime.set(Calendar.MINUTE, min);
                serverTime.set(Calendar.SECOND, sec);

                long diff = now.getTimeInMillis() - serverTime.getTimeInMillis();

                // In C#: if (AppVars.Profile.ServDiff > new TimeSpan(1,0,0,0)) ... which is 1 day.
                if (diff > (24 * 60 * 60 * 1000L)) {
                    diff = 0;
                }
                
                if (AppVars.Profile != null) {
                    AppVars.Profile.ServDiff = diff;
                }

            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
        }

        html = html.replace("/b1.gif", "/b1.gif name=butinp");
        html = html.replace("smile_open('')", "window.external.ShowSmiles(1)");
        html = html.replace("smile_open('2')", "window.external.ShowSmiles(2)");

        return Russian.getBytes(html);
    }
}

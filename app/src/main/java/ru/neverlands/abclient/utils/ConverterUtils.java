package ru.neverlands.abclient.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class ConverterUtils {
    private ConverterUtils() {}

    public static String timeSpanToString(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        if (hours > 0) {
            return String.format(Locale.US, "(%d:%02d:%02d)", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format(Locale.US, "(%d:%02d)", minutes, seconds);
        } else {
            return String.format(Locale.US, "(0:%02d)", seconds);
        }
    }

    public static String minsToStr(int mins) {
        return "(" + (mins / 60) + ":" + String.format(Locale.US, "%02d", (mins % 60)) + ":00)";
    }

    public static String nickEncode(String nick) {
        if (nick == null) return null;
        try {
            String s1 = nick.replace('+', '|');
            String s2 = URLEncoder.encode(s1, "windows-1251");
            String s3 = s2.replace("+", "%20");
            return s3.replace("%7C", "%2B"); // %7C это |
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return nick; // fallback
        }
    }

    public static String nickDecode(String nick) {
        if (nick == null) return null;
        try {
            String s = nick.replace('+', ' ');
            String decoded = URLDecoder.decode(s, "windows-1251");
            return decoded.replace("|", " ")
                          .replace("%20", " ")
                          .replace("%2B", "+")
                          .replace("%23", "#")
                          .replace("%3D", "=");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return nick; // fallback
        }
    }

    public static String addressEncode(String address) {
        // TODO: Перенести логику с проверкой адресов pinfo.cgi и т.д., если она понадобится
        final String pinfo = "http://neverlands.ru/pinfo.cgi?";
        if (address.toLowerCase().startsWith(pinfo)) {
            return pinfo + nickEncode(address.substring(pinfo.length()));
        }
        return address;
    }
}

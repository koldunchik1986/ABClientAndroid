package ru.neverlands.abclient.postfilter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import ru.neverlands.abclient.model.CityGateType;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.MapPath;

public class MainPhpCityNavigation {
    private static final String TAG = "MainPhpCityNavigation";

    private static final String[] CITY_GATES = {"8-259", "8-294", "8-197", "12-428", "12-494", "12-521"};

    private static final Map<String, String> CITY_LINKS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("city1_fon_1:8-259", "\u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430");
        m.put("city1_fon_1:8-294", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0436\u0438\u043b\u043e\u0439 \u043a\u0432\u0430\u0440\u0442\u0430\u043b");
        m.put("city1_fon_2:8-259", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043d\u0430 \u0433\u043e\u0440\u043e\u0434\u0441\u043a\u0443\u044e \u043f\u043b\u043e\u0449\u0430\u0434\u044c");
        m.put("city1_fon_2:8-294", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u043a\u0432\u0430\u0440\u0442\u0430\u043b \u0437\u0430\u043a\u043e\u043d\u0430");
        m.put("city1_fon_3:8-259", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0436\u0438\u043b\u043e\u0439 \u043a\u0432\u0430\u0440\u0442\u0430\u043b");
        m.put("city1_fon_3:8-294", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0436\u0438\u043b\u043e\u0439 \u043a\u0432\u0430\u0440\u0442\u0430\u043b");
        m.put("city1_fon_4:8-259", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043d\u0430 \u0433\u043e\u0440\u043e\u0434\u0441\u043a\u0443\u044e \u043f\u043b\u043e\u0449\u0430\u0434\u044c");
        m.put("city1_fon_4:8-294", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043d\u0430 \u0433\u043e\u0440\u043e\u0434\u0441\u043a\u0443\u044e \u043f\u043b\u043e\u0449\u0430\u0434\u044c");
        m.put("city1_fon_5_new:8-259", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0436\u0438\u043b\u043e\u0439 \u043a\u0432\u0430\u0440\u0442\u0430\u043b");
        m.put("city1_fon_5_new:8-294", "\u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430");
        m.put("country1_fon:8-197", "\u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0434\u0435\u0440\u0435\u0432\u043d\u0438");
        m.put("city2_1:12-428", "\u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430, \u0417\u0430\u043f\u0430\u0434\u043d\u044b\u0435 \u0412\u043e\u0440\u043e\u0442\u0430");
        m.put("city2_1:12-494", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0422\u043e\u0440\u0433\u043e\u0432\u044b\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b");
        m.put("city2_1:12-521", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0422\u043e\u0440\u0433\u043e\u0432\u044b\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b");
        m.put("city2_2_new:12-428", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043d\u0430 \u0426\u0435\u043d\u0442\u0440\u0430\u043b\u044c\u043d\u0443\u044e \u041f\u043b\u043e\u0449\u0430\u0434\u044c");
        m.put("city2_2_new:12-494", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u041f\u0440\u043e\u043c\u044b\u0448\u043b\u0435\u043d\u043d\u044b\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b");
        m.put("city2_2_new:12-521", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u041f\u0440\u043e\u043c\u044b\u0448\u043b\u0435\u043d\u043d\u044b\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b");
        m.put("city2_3:12-428", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043d\u0430 \u0426\u0435\u043d\u0442\u0440\u0430\u043b\u044c\u043d\u0443\u044e \u041f\u043b\u043e\u0449\u0430\u0434\u044c");
        m.put("city2_3:12-494", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u041f\u0440\u043e\u043c\u044b\u0448\u043b\u0435\u043d\u043d\u044b\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b");
        m.put("city2_3:12-521", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u041f\u0440\u043e\u043c\u044b\u0448\u043b\u0435\u043d\u043d\u044b\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b");
        m.put("city2_4:12-428", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0422\u043e\u0440\u0433\u043e\u0432\u044b\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b");
        m.put("city2_4:12-494", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043a \u041a\u043e\u043d\u044e\u0448\u043d\u044f\u043c");
        m.put("city2_4:12-521", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043a \u041a\u043e\u043d\u044e\u0448\u043d\u044f\u043c");
        m.put("city2_5:12-428", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u041f\u0440\u043e\u043c\u044b\u0448\u043b\u0435\u043d\u043d\u044b\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b");
        m.put("city2_5:12-494", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043d\u0430 \u041f\u043b\u043e\u0449\u0430\u0434\u044c \u0413\u0438\u043b\u044c\u0434\u0438\u0439");
        m.put("city2_5:12-521", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043d\u0430 \u041f\u043b\u043e\u0449\u0430\u0434\u044c \u0413\u0438\u043b\u044c\u0434\u0438\u0439");
        m.put("city2_6:12-428", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0436\u0438\u043b\u043e\u0439 \u043a\u0432\u0430\u0440\u0442\u0430\u043b");
        m.put("city2_6:12-494", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043a \u041a\u043e\u043d\u044e\u0448\u043d\u044f\u043c");
        m.put("city2_6:12-521", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043a \u041a\u043e\u043d\u044e\u0448\u043d\u044f\u043c");
        m.put("city2_7_exit:12-428", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u041f\u0440\u043e\u043c\u044b\u0448\u043b\u0435\u043d\u043d\u044b\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b");
        m.put("city2_7_exit:12-494", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043d\u0430 \u041f\u043b\u043e\u0449\u0430\u0434\u044c \u0413\u0438\u043b\u044c\u0434\u0438\u0439");
        m.put("city2_7_exit:12-521", "\u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430, \u042e\u0436\u043d\u044b\u0435 \u0412\u043e\u0440\u043e\u0442\u0430");
        m.put("city2_8:12-428", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0414\u0435\u043b\u043e\u0432\u043e\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b");
        m.put("city2_8:12-494", "\u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430, \u0412\u043e\u0441\u0442\u043e\u0447\u043d\u044b\u0435 \u0412\u043e\u0440\u043e\u0442\u0430");
        m.put("city2_8:12-521", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043a \u041a\u043e\u043d\u044e\u0448\u043d\u044f\u043c");
        m.put("city2_8_elko:12-428", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0414\u0435\u043b\u043e\u0432\u043e\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b");
        m.put("city2_8_elko:12-494", "\u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430, \u0412\u043e\u0441\u0442\u043e\u0447\u043d\u044b\u0435 \u0412\u043e\u0440\u043e\u0442\u0430");
        m.put("city2_8_elko:12-521", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043a \u041a\u043e\u043d\u044e\u0448\u043d\u044f\u043c");
        m.put("city2_9:12-428", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u041a\u0432\u0430\u0440\u0442\u0430\u043b \u0417\u043d\u0430\u043d\u0438\u0439");
        m.put("city2_9:12-494", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u041a\u0432\u0430\u0440\u0442\u0430\u043b \u0417\u043d\u0430\u043d\u0438\u0439");
        m.put("city2_9:12-521", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u041a\u0432\u0430\u0440\u0442\u0430\u043b \u0417\u043d\u0430\u043d\u0438\u0439");
        CITY_LINKS = Collections.unmodifiableMap(m);
    }

    public static String process(String html) {
        if (!AppVars.AutoMoving) return null;

        String result = mainPhpCityNavigation(html);
        if (result != null && !result.isEmpty()) return result;

        result = mainPhpStartFromCityNavigation(html);
        if (result != null && !result.isEmpty()) return result;

        return null;
    }

    private static String mainPhpCityNavigation(String html) {
        String lhtml = html.toLowerCase(Locale.ROOT);
        if (!lhtml.contains("<area shape=") && !lhtml.contains("url(http://image.neverlands.ru/cities/forpost/")) {
            return null;
        }

        CityGateType gate = AppVars.AutoMovingCityGate;
        String[] area = new String[0];
        switch (gate) {
            case ForpostRightToLeftGate:
                area = new String[]{"\u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043d\u0430 \u0433\u043e\u0440\u043e\u0434\u0441\u043a\u0443\u044e \u043f\u043b\u043e\u0449\u0430\u0434\u044c", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0436\u0438\u043b\u043e\u0439 \u043a\u0432\u0430\u0440\u0442\u0430\u043b"};
                break;
            case ForpostLeftToRightGate:
                area = new String[]{"\u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u043a\u0432\u0430\u0440\u0442\u0430\u043b \u0437\u0430\u043a\u043e\u043d\u0430", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0436\u0438\u043b\u043e\u0439 \u043a\u0432\u0430\u0440\u0442\u0430\u043b"};
                break;
            case OktalLeftToRightGate:
                area = new String[]{"\u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430, \u0412\u043e\u0441\u0442\u043e\u0447\u043d\u044b\u0435 \u0412\u043e\u0440\u043e\u0442\u0430", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043d\u0430 \u041f\u043b\u043e\u0449\u0430\u0434\u044c \u0413\u0438\u043b\u044c\u0434\u0438\u0439", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0414\u0435\u043b\u043e\u0432\u043e\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u041f\u0440\u043e\u043c\u044b\u0448\u043b\u0435\u043d\u043d\u044b\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0422\u043e\u0440\u0433\u043e\u0432\u044b\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b"};
                break;
            case OktalRightToLeftGate:
                area = new String[]{"\u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430, \u0417\u0430\u043f\u0430\u0434\u043d\u044b\u0435 \u0412\u043e\u0440\u043e\u0442\u0430", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043d\u0430 \u0426\u0435\u043d\u0442\u0440\u0430\u043b\u044c\u043d\u0443\u044e \u041f\u043b\u043e\u0449\u0430\u0434\u044c", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0422\u043e\u0440\u0433\u043e\u0432\u044b\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u041f\u0440\u043e\u043c\u044b\u0448\u043b\u0435\u043d\u043d\u044b\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0414\u0435\u043b\u043e\u0432\u043e\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b"};
                break;
            case OktalLeftToBottomGate:
                area = new String[]{"\u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430, \u042e\u0436\u043d\u044b\u0435 \u0412\u043e\u0440\u043e\u0442\u0430", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043a \u041a\u043e\u043d\u044e\u0448\u043d\u044f\u043c", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u041f\u0440\u043e\u043c\u044b\u0448\u043b\u0435\u043d\u043d\u044b\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0422\u043e\u0440\u0433\u043e\u0432\u044b\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b"};
                break;
            case OktalRightToBottomGate:
                area = new String[]{"\u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430, \u042e\u0436\u043d\u044b\u0435 \u0412\u043e\u0440\u043e\u0442\u0430", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043a \u041a\u043e\u043d\u044e\u0448\u043d\u044f\u043c"};
                break;
            case OktalBottomToLeftGate:
                area = new String[]{"\u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430, \u0417\u0430\u043f\u0430\u0434\u043d\u044b\u0435 \u0412\u043e\u0440\u043e\u0442\u0430", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043d\u0430 \u0426\u0435\u043d\u0442\u0440\u0430\u043b\u044c\u043d\u0443\u044e \u041f\u043b\u043e\u0449\u0430\u0434\u044c", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0422\u043e\u0440\u0433\u043e\u0432\u044b\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u041f\u0440\u043e\u043c\u044b\u0448\u043b\u0435\u043d\u043d\u044b\u0439 \u041a\u0432\u0430\u0440\u0442\u0430\u043b"};
                break;
            case OktalBottomToRightGate:
                area = new String[]{"\u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430, \u0412\u043e\u0441\u0442\u043e\u0447\u043d\u044b\u0435 \u0412\u043e\u0440\u043e\u0442\u0430", "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u043d\u0430 \u041f\u043b\u043e\u0449\u0430\u0434\u044c \u0413\u0438\u043b\u044c\u0434\u0438\u0439"};
                break;
            default:
                return null;
        }

        if (area.length == 0) return null;

        if (gate == CityGateType.ForpostLeftToRightGate) {
            if (lhtml.contains("\u0432\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430") && lhtml.contains("\u043f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0434\u0435\u043b\u043e\u0432\u043e\u0439 \u043a\u0432\u0430\u0440\u0442\u0430\u043b")) {
                String result = mainPhpCityArea(html, "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0436\u0438\u043b\u043e\u0439 \u043a\u0432\u0430\u0440\u0442\u0430\u043b");
                if (result != null && !result.isEmpty()) return result;
            }
            if (lhtml.contains("\u0432\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430") && !lhtml.contains("\u043f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0434\u0435\u043b\u043e\u0432\u043e\u0439 \u043a\u0432\u0430\u0440\u0442\u0430\u043b")) {
                String result = mainPhpCityArea(html, "\u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430");
                if (result != null && !result.isEmpty()) return result;
            }
        }
        if (gate == CityGateType.ForpostRightToLeftGate) {
            if (lhtml.contains("\u0432\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430") && !lhtml.contains("\u043f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0434\u0435\u043b\u043e\u0432\u043e\u0439 \u043a\u0432\u0430\u0440\u0442\u0430\u043b")) {
                String result = mainPhpCityArea(html, "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0436\u0438\u043b\u043e\u0439 \u043a\u0432\u0430\u0440\u0442\u0430\u043b");
                if (result != null && !result.isEmpty()) return result;
            }
            if (lhtml.contains("\u0432\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430") && lhtml.contains("\u043f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0434\u0435\u043b\u043e\u0432\u043e\u0439 \u043a\u0432\u0430\u0440\u0442\u0430\u043b")) {
                String result = mainPhpCityArea(html, "\u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0433\u043e\u0440\u043e\u0434\u0430");
                if (result != null && !result.isEmpty()) return result;
            }
        }

        for (String areaName : area) {
            String result = mainPhpCityArea(html, areaName);
            if (result != null && !result.isEmpty()) return result;
        }
        return null;
    }

    static String mainPhpCityArea(String html, String area) {
        String s1 = "tooltip(this,'" + area + "')";
        int p1 = html.toLowerCase(Locale.ROOT).indexOf(s1.toLowerCase(Locale.ROOT));
        if (p1 == -1) return null;

        String staticOnClick = "href=\"";
        int p2 = html.toLowerCase(Locale.ROOT).lastIndexOf(staticOnClick.toLowerCase(Locale.ROOT), p1);
        if (p2 == -1) return null;

        int p3 = html.indexOf('"', p2 + staticOnClick.length());
        if (p3 == -1) return null;

        String link = html.substring(p2 + staticOnClick.length(), p3);
        return Filter.buildRedirectString("\u041d\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044f \u043f\u043e \u0433\u043e\u0440\u043e\u0434\u0443", link);
    }

    private static String mainPhpStartFromCityNavigation(String html) {
        if (!AppVars.AutoMoving) return null;
        if (AppVars.AutoMovingDestinaton == null) return null;
        String mapLocation = AppVars.Profile != null ? AppVars.Profile.MapLocation : null;
        if (mapLocation == null) return null;

        if (AppVars.AutoMovingMapPath == null) {
            java.util.List<String> dest = Collections.singletonList(AppVars.AutoMovingDestinaton);
            AppVars.AutoMovingMapPath = new MapPath(mapLocation, dest);
        } else if (!AppVars.AutoMovingMapPath.canUseExistingPath(mapLocation, AppVars.AutoMovingDestinaton)) {
            java.util.List<String> dest = Collections.singletonList(AppVars.AutoMovingDestinaton);
            AppVars.AutoMovingMapPath = new MapPath(mapLocation, dest);
        }

        if (!AppVars.AutoMovingMapPath.pathExists || AppVars.AutoMovingMapPath.path == null || AppVars.AutoMovingMapPath.path.length < 2) return null;

        String gateLocation = AppVars.AutoMovingMapPath.path[1];
        if (!isCityGate(gateLocation)) {
            gateLocation = AppVars.AutoMovingMapPath.path[0];
            if (!isCityGate(gateLocation)) return null;
        }

        String pat2 = ".jpg width=760 height=255 border=0 USEMAP=\"#links\">";
        int pos2 = html.toLowerCase(Locale.ROOT).indexOf(pat2.toLowerCase(Locale.ROOT));
        if (pos2 == -1) return null;

        int pos1 = html.lastIndexOf("/", pos2);
        if (pos1 == -1) return null;

        String cityPos = html.substring(pos1 + 1, pos2);
        if (cityPos.isEmpty()) return null;

        String key = cityPos + ":" + gateLocation;
        String textLink = CITY_LINKS.get(key);
        if (textLink == null) return null;

        return mainPhpCityArea(html, textLink);
    }

    private static boolean isCityGate(String cell) {
        for (String g : CITY_GATES) {
            if (g.equals(cell)) return true;
        }
        return false;
    }
}

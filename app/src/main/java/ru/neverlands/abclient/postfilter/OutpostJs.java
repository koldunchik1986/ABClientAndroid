package ru.neverlands.abclient.postfilter;

import java.io.IOException;
import java.io.InputStream;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.DataManager;
import ru.neverlands.abclient.utils.Russian;

public class OutpostJs {
    public static byte[] process(byte[] array) {
        try (InputStream is = AppVars.getAssetManager().open("js/json2.js")) {
            String json2 = Russian.getString(DataManager.readAllBytes(is));
            String html = Russian.getString(array);
            return Russian.getBytes(json2 + " " + html);
        } catch (IOException e) {
            e.printStackTrace();
            return array;
        }
    }
}

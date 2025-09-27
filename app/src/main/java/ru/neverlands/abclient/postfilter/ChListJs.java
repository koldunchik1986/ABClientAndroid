package ru.neverlands.abclient.postfilter;

import java.io.IOException;
import java.io.InputStream;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.DataManager;
import ru.neverlands.abclient.utils.Russian;

public class ChListJs {
    public static byte[] process() {
        try (InputStream is = AppVars.getAssetManager().open("ch_list.js")) {
            byte[] fileBytes = DataManager.readAllBytes(is);
            String html = Russian.getString(fileBytes);
            html = html.replace("alt=", "title=");
            return Russian.getBytes(html);
        } catch (IOException e) {
            e.printStackTrace();
            // Return an empty script to avoid breaking the page
            return new byte[0];
        }
    }
}

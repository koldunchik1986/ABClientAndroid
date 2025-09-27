package ru.neverlands.abclient.postfilter;

import java.io.IOException;
import java.io.InputStream;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.DataManager;
import ru.neverlands.abclient.utils.Russian;

public class ArenaJs {
    public static byte[] process() {
        try (InputStream is = AppVars.getAssetManager().open("arena_v04.js")) {
            String html = Russian.getString(DataManager.readAllBytes(is));
            if (AppVars.Profile != null && AppVars.Profile.ChatKeepMoving) {
                html = html.replace("top.clr_chat();", "");
            }
            return Russian.getBytes(html);
        } catch (IOException e) {
            e.printStackTrace();
            return new byte[0];
        }
    }
}
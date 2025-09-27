package ru.neverlands.abclient.postfilter;

import java.io.IOException;
import java.io.InputStream;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.DataManager;

public class MapJs {
    public static byte[] process(byte[] array) {
        // This filter completely replaces the server's map.js with a local version
        // from the assets, which contains custom logic.
        try (InputStream is = AppVars.getAssetManager().open("js/map.js")) {
            return DataManager.readAllBytes(is);
        } catch (IOException e) {
            // If the local file can't be read, return the original server file
            // to prevent crashing the game map.
            e.printStackTrace();
            return array;
        }
    }
}

package ru.neverlands.abclient.postfilter;

import android.content.res.AssetManager;
import java.io.InputStream;
import ru.neverlands.abclient.utils.AppVars;

public class MapJs {
    /**
     * Заменяет серверный map.js на кастомный из assets.
     */
    public static byte[] process(byte[] array) {
        try {
            AssetManager assetManager = AppVars.getAssetManager();
            InputStream inputStream = assetManager.open("js/map.js");
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);
            inputStream.close();
            return buffer;
        } catch (Exception e) {
            e.printStackTrace();
            // В случае ошибки возвращаем оригинальный массив, чтобы не сломать игру
            return array; 
        }
    }
}
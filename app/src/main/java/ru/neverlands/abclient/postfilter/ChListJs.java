package ru.neverlands.abclient.postfilter;

import android.content.res.AssetManager;
import java.io.InputStream;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

public class ChListJs {
    public static byte[] process() {
        try {
            AssetManager assetManager = AppVars.getAssetManager();
            InputStream inputStream = assetManager.open("js/ch_list.js");
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);
            inputStream.close();

            String js = Russian.getString(buffer);

            js = js.replace("alt=", "title=");
            js = js.replace("window.external.GetClassIdOfContact", "AndroidBridge.GetClassIdOfContact");
            // ... и другие замены ...

            return Russian.getBytes(js);

        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }
}
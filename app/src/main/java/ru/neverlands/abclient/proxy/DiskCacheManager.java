package ru.neverlands.abclient.proxy;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DiskCacheManager {
    private static final String TAG = "DiskCacheManager";
    private static File cacheDir;

    public static void init(Context context) {
        cacheDir = context.getExternalFilesDir("abcache");
        if (cacheDir != null && !cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }

    public static byte[] get(String url) {
        if (cacheDir == null) {
            return null;
        }

        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        String path = uri.getPath();

        if (host == null || path == null) {
            return null;
        }

        File file = new File(cacheDir, host + path);

        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] data = new byte[(int) file.length()];
                fis.read(data);
                return data;
            } catch (IOException e) {
                Log.e(TAG, "Error reading from cache", e);
            }
        }

        return null;
    }

    public static void put(String url, byte[] data) {
        if (cacheDir == null) {
            return;
        }

        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        String path = uri.getPath();

        if (host == null || path == null) {
            return;
        }

        File file = new File(cacheDir, host + path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        } catch (IOException e) {
            Log.e(TAG, "Error writing to cache", e);
        }
    }
}

package ru.neverlands.abclient.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Менеджер данных приложения.
 * Аналог DataManager.cs в оригинальном приложении.
 */
public class DataManager {
    private static final String TAG = "DataManager";
    private static Context appContext;
    
    /**
     * Инициализация менеджера данных
     * @param context контекст приложения
     */
    public static void init(Context context) {
        appContext = context.getApplicationContext();
        
        // Создание необходимых директорий
        createRequiredDirectories();
        
        // Копирование ресурсов из assets, если необходимо
        copyAssetsIfNeeded();
    }
    
    /**
     * Создание необходимых директорий
     */
    private static void createRequiredDirectories() {
        // Директория для кэша
        File cacheDir = new File(appContext.getFilesDir(), "abcache");
        if (!cacheDir.exists()) {
            if (!cacheDir.mkdirs()) {
                Log.e(TAG, "Failed to create cache directory");
            }
        }
        
        // Директория для логов
        File logsDir = new File(appContext.getFilesDir(), "logs");
        if (!logsDir.exists()) {
            if (!logsDir.mkdirs()) {
                Log.e(TAG, "Failed to create logs directory");
            }
        }
    }
    
    /**
     * Копирование ресурсов из assets, если необходимо
     */
    private static void copyAssetsIfNeeded() {
        try {
            // Копирование XML-файлов
            copyAssetIfNotExists("abcells.xml");
            copyAssetIfNotExists("abfavorites.xml");
            copyAssetIfNotExists("abteleports.xml");
            copyAssetIfNotExists("abthings.xml");
            copyAssetIfNotExists("map.xml");
            
            // Копирование JS-файлов
            copyAssetIfNotExists("arena_v04.js");
            copyAssetIfNotExists("ch_list.js");
            copyAssetIfNotExists("map.js");
        } catch (IOException e) {
            Log.e(TAG, "Error copying assets", e);
        }
    }
    
    /**
     * Копирование файла из assets, если он не существует
     * @param fileName имя файла
     * @throws IOException при ошибке ввода/вывода
     */
    private static void copyAssetIfNotExists(String fileName) throws IOException {
        File outFile = new File(appContext.getFilesDir(), fileName);
        if (!outFile.exists()) {
            try (InputStream in = appContext.getAssets().open(fileName);
                 OutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        }
    }
    
    /**
     * Чтение файла в строку
     * @param fileName имя файла
     * @return содержимое файла или null при ошибке
     */
    public static String readFileToString(String fileName) {
        File file = new File(appContext.getFilesDir(), fileName);
        if (!file.exists()) {
            return null;
        }
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.e(TAG, "Error reading file: " + fileName, e);
            return null;
        }
    }
    
    /**
     * Запись строки в файл
     * @param fileName имя файла
     * @param content содержимое
     * @return true при успешной записи, false при ошибке
     */
    public static boolean writeStringToFile(String fileName, String content) {
        File file = new File(appContext.getFilesDir(), fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error writing file: " + fileName, e);
            return false;
        }
    }
    
    /**
     * Чтение файла в байтовый массив
     * @param fileName имя файла
     * @return содержимое файла или null при ошибке
     */
    public static byte[] readFileToBytes(String fileName) {
        File file = new File(appContext.getFilesDir(), fileName);
        if (!file.exists()) {
            return null;
        }
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            return data;
        } catch (IOException e) {
            Log.e(TAG, "Error reading file: " + fileName, e);
            return null;
        }
    }
    
    /**
     * Запись байтового массива в файл
     * @param fileName имя файла
     * @param content содержимое
     * @return true при успешной записи, false при ошибке
     */
    public static boolean writeBytesToFile(String fileName, byte[] content) {
        File file = new File(appContext.getFilesDir(), fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error writing file: " + fileName, e);
            return false;
        }
    }
    
    /**
     * Проверка существования файла
     * @param fileName имя файла
     * @return true если файл существует, false в противном случае
     */
    public static boolean fileExists(String fileName) {
        File file = new File(appContext.getFilesDir(), fileName);
        return file.exists();
    }
    
    /**
     * Удаление файла
     * @param fileName имя файла
     * @return true при успешном удалении, false при ошибке
     */
    public static boolean deleteFile(String fileName) {
        File file = new File(appContext.getFilesDir(), fileName);
        return !file.exists() || file.delete();
    }
}
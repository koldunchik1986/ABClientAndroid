package ru.neverlands.abclient.utils;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ru.neverlands.abclient.ABClientApplication;

/**
 * Класс для работы с логами.
 * Аналог Log.cs в оригинальном приложении.
 */
public class AppLogger {
    private static final String TAG = "ABClient";
    private static final SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    
    /**
     * Запись в лог
     * @param message сообщение
     */
    public static void write(String message) {
        android.util.Log.d(TAG, message);
        writeToFile("log.txt", message);
    }
    
    /**
     * Запись в лог с тегом
     * @param tag тег
     * @param message сообщение
     */
    public static void write(String tag, String message) {
        android.util.Log.d(TAG + ":" + tag, message);
        writeToFile("log_" + tag + ".txt", message);
    }
    
    /**
     * Запись ошибки в лог
     * @param message сообщение
     * @param e исключение
     */
    public static void error(String message, Throwable e) {
        android.util.Log.e(TAG, message, e);
        writeToFile("error.txt", message + "\n" + android.util.Log.getStackTraceString(e));
    }
    
    /**
     * Запись HTTP-запроса в лог
     * @param url URL запроса
     * @param request тело запроса
     */
    public static void writeHttpRequest(String url, String request) {
        if (ru.neverlands.abclient.utils.AppVars.Profile != null && ru.neverlands.abclient.utils.AppVars.Profile.DoHttpLog) {
            android.util.Log.d(TAG + ":HTTP", "REQUEST: " + url + "\n" + request);
            writeToFile("http.txt", "REQUEST: " + url + "\n" + request);
        }
    }
    
    /**
     * Запись HTTP-ответа в лог
     * @param url URL запроса
     * @param response тело ответа
     */
    public static void writeHttpResponse(String url, String response) {
        if (ru.neverlands.abclient.utils.AppVars.Profile != null && ru.neverlands.abclient.utils.AppVars.Profile.DoHttpLog) {
            android.util.Log.d(TAG + ":HTTP", "RESPONSE: " + url + "\n" + response);
            writeToFile("http.txt", "RESPONSE: " + url + "\n" + response);
        }
    }
    
    /**
     * Запись текстового лога
     * @param message сообщение
     */
    public static void writeTexLog(String message) {
        if (ru.neverlands.abclient.utils.AppVars.Profile != null && ru.neverlands.abclient.utils.AppVars.Profile.DoTexLog) {
            android.util.Log.d(TAG + ":TEX", message);
            writeToFile("tex.txt", message);
        }
    }
    
    /**
     * Запись в файл
     * @param fileName имя файла
     * @param message сообщение
     */
    private static void writeToFile(String fileName, String message) {
        try {
            File logsDir = new File(ABClientApplication.getAppContext().getFilesDir(), "logs");
            if (!logsDir.exists()) {
                if (!logsDir.mkdirs()) {
                    android.util.Log.e(TAG, "Failed to create logs directory");
                    return;
                }
            }
            
            File logFile = new File(logsDir, fileName);
            boolean append = logFile.exists();
            
            try (FileOutputStream fos = new FileOutputStream(logFile, append)) {
                String timestamp = LOG_DATE_FORMAT.format(new Date());
                String logMessage = timestamp + " - " + message + "\n";
                fos.write(logMessage.getBytes());
            }
        } catch (IOException e) {
            android.util.Log.e(TAG, "Error writing to log file", e);
        }
    }
    
    /**
     * Очистка логов
     */
    public static void clearLogs() {
        File logsDir = new File(ABClientApplication.getAppContext().getFilesDir(), "logs");
        if (logsDir.exists()) {
            File[] files = logsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.delete()) {
                        android.util.Log.e(TAG, "Failed to delete log file: " + file.getName());
                    }
                }
            }
        }
    }
}

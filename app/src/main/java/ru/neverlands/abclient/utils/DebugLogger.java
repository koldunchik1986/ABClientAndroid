package ru.neverlands.abclient.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ru.neverlands.abclient.ABClientApplication;

public class DebugLogger {
    private static final String TAG = "DebugLogger";
    private static FileOutputStream fos;
    private static boolean isInitialized = false;

    private static void initialize() {
        if (isInitialized) return;

        try {
            Context context = ABClientApplication.getAppContext();
            File logsDir = context.getExternalFilesDir("Logs");
            if (!logsDir.exists()) {
                if (!logsDir.mkdirs()) {
                    Log.e(TAG, "Failed to create Logs directory");
                    return;
                }
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File logFile = new File(logsDir, "Log_" + timestamp + ".txt");

            fos = new FileOutputStream(logFile, true);
            isInitialized = true;
            log("Logger initialized.");

        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize logger", e);
        }
    }

    public static void log(String message) {
        initialize();
        if (fos == null) {
            Log.e(TAG, "Logger not initialized, cannot write log.");
            return;
        }

        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            String logMessage = timestamp + ": " + message + "\n";
            fos.write(logMessage.getBytes());
            fos.flush(); // Принудительно записываем на диск, чтобы гарантировать немедленное сохранение
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to log file", e);
        }
    }

    public static void close() {
        if (fos != null) {
            try {
                log("Logger closing.");
                fos.close();
                fos = null;
                isInitialized = false;
            } catch (IOException e) {
                Log.e(TAG, "Failed to close log file", e);
            }
        }
    }
}

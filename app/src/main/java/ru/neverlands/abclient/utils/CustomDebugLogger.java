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

public class CustomDebugLogger {
    private static final String TAG = "CustomDebugLogger";
    private static FileOutputStream fos;
    private static boolean isInitialized = false;

    public static void initialize(String fileName) {
        if (isInitialized) return;

        try {
            Context context = ABClientApplication.getAppContext();
            File logsDir = context.getExternalFilesDir("Logs");
            if (logsDir == null) {
                Log.e(TAG, "External logs directory is null.");
                return;
            }
            if (!logsDir.exists()) {
                if (!logsDir.mkdirs()) {
                    Log.e(TAG, "Failed to create Logs directory");
                    return;
                }
            }

            File logFile = new File(logsDir, fileName);
            fos = new FileOutputStream(logFile, true);
            isInitialized = true;
            log("Logger initialized for " + fileName);

        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize logger", e);
        }
    }

    public static void log(String message) {
        if (!isInitialized) {
            // Не инициализируем автоматически, чтобы избежать создания логов по умолчанию
            Log.e(TAG, "Logger not initialized, cannot write log.");
            return;
        }
        if (fos == null) {
            return;
        }

        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            String logMessage = timestamp + ": " + message + "\n";
            fos.write(logMessage.getBytes());
            fos.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to log file", e);
        }
    }

    public static void close() {
        if (fos != null) {
            try {
                log("Logger closing.");
                fos.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close log file", e);
            } finally {
                fos = null;
                isInitialized = false;
            }
        }
    }
}

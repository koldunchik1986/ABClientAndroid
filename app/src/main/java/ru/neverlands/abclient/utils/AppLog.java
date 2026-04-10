package ru.neverlands.abclient.utils;

import java.util.Locale;

/**
 * Единая точка логирования для Android-кода:
 * - пишет в системный logcat (читается LogcatFileRecorder),
 * - дублирует события в FileLogger (файлы в files/Logs/...).
 *
 * Используется как безопасная замена прямых вызовов android.util.Log
 * в прикладных модулях, чтобы не дублировать ручной код двойного логирования.
 */
public final class AppLog {
    private static final String DEFAULT_CHAIN = "app_log";

    private AppLog() {
    }

    public static int d(String tag, String message) {
        int result = android.util.Log.d(tag, message);
        FileLogger.trace(resolveChain(tag), normalize(message));
        return result;
    }

    public static int d(String chain, String tag, String message) {
        int result = android.util.Log.d(tag, message);
        FileLogger.trace(resolveChain(chain), normalize(message));
        return result;
    }

    public static int i(String tag, String message) {
        int result = android.util.Log.i(tag, message);
        FileLogger.trace(resolveChain(tag), normalize(message));
        return result;
    }

    public static int i(String chain, String tag, String message) {
        int result = android.util.Log.i(tag, message);
        FileLogger.trace(resolveChain(chain), normalize(message));
        return result;
    }

    public static int w(String tag, String message) {
        int result = android.util.Log.w(tag, message);
        FileLogger.warn(resolveChain(tag), normalize(message));
        return result;
    }

    public static int w(String chain, String tag, String message) {
        int result = android.util.Log.w(tag, message);
        FileLogger.warn(resolveChain(chain), normalize(message));
        return result;
    }

    public static int w(String tag, String message, Throwable error) {
        int result = android.util.Log.w(tag, message, error);
        FileLogger.warn(resolveChain(tag), normalize(message));
        if (error != null) {
            FileLogger.error(resolveChain(tag), "warning throwable", error);
        }
        return result;
    }

    public static int w(String chain, String tag, String message, Throwable error) {
        int result = android.util.Log.w(tag, message, error);
        FileLogger.warn(resolveChain(chain), normalize(message));
        if (error != null) {
            FileLogger.error(resolveChain(chain), "warning throwable", error);
        }
        return result;
    }

    public static int e(String tag, String message) {
        int result = android.util.Log.e(tag, message);
        FileLogger.error(resolveChain(tag), normalize(message), null);
        return result;
    }

    public static int e(String chain, String tag, String message) {
        int result = android.util.Log.e(tag, message);
        FileLogger.error(resolveChain(chain), normalize(message), null);
        return result;
    }

    public static int e(String tag, String message, Throwable error) {
        int result = android.util.Log.e(tag, message, error);
        FileLogger.error(resolveChain(tag), normalize(message), error);
        return result;
    }

    public static int e(String chain, String tag, String message, Throwable error) {
        int result = android.util.Log.e(tag, message, error);
        FileLogger.error(resolveChain(chain), normalize(message), error);
        return result;
    }

    public static String getStackTraceString(Throwable error) {
        return android.util.Log.getStackTraceString(error);
    }

    private static String resolveChain(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return DEFAULT_CHAIN;
        }
        return tag.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalize(String message) {
        return message == null ? "null" : message;
    }
}

package ru.neverlands.abclient.utils;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FileLogger {
    private static final String TAG = "FileLogger";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final SimpleDateFormat DAY_FORMAT = new SimpleDateFormat("yyyyMMdd", Locale.US);
    private static final SimpleDateFormat TS_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat PROXY_SEGMENT_FORMAT = new SimpleDateFormat("yyyyMMdd_HH", Locale.US);
    private static final Object FILE_LOCK = new Object();
    private static final long MAX_FILE_BYTES = 8L * 1024L * 1024L;
    private static final int MAX_ROTATIONS = 2;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ab-file-logger");
        thread.setDaemon(true);
        return thread;
    });

    private FileLogger() {
    }

    public static void trace(String chain, String message) {
        write("TRACE", chain, message, null);
    }

    public static void warn(String chain, String message) {
        write("WARN", chain, message, null);
    }

    public static void error(String chain, String message, Throwable error) {
        write("ERROR", chain, message, error);
    }

    /**
     * Логирует proxy-pool трафик в фиксированный файл `files/Logs/pool/*_proxy.txt`.
     * Используется для детального post/get трассинга локального proxy-контура.
     */
    public static void proxyPool(String message) {
        writeToProxySegment("TRACE", message, null);
    }

    public static void proxyPoolError(String message, Throwable error) {
        writeToProxySegment("ERROR", message, error);
    }

    private static void write(String level, String chain, String message, Throwable error) {
        if (chain == null || chain.trim().isEmpty() || message == null || message.trim().isEmpty()) {
            return;
        }
        final String safeLevel = level == null ? "TRACE" : level.trim().toUpperCase(Locale.ROOT);
        final String safeChain = sanitizeChain(chain);
        final String safeMessage = sanitizeMessage(message);
        IO.execute(() -> appendLine(safeLevel, safeChain, safeMessage, error));
    }

    private static void writeToProxySegment(String level, String message, Throwable error) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        final String safeLevel = level == null ? "TRACE" : level.trim().toUpperCase(Locale.ROOT);
        final String safeMessage = sanitizeMessage(message);
        IO.execute(() -> appendLineToProxySegment(safeLevel, safeMessage, error));
    }

    private static void appendLine(String level, String chain, String message, Throwable error) {
        try {
            File logsRoot = resolveLogsRoot();
            if (logsRoot == null) {
                return;
            }
            File criticalDir = new File(logsRoot, "Critical");
            if (!criticalDir.exists() && !criticalDir.mkdirs()) {
                Log.e(TAG, "Failed to create critical log directory: " + criticalDir.getAbsolutePath());
                return;
            }

            String day = DAY_FORMAT.format(new Date());
            File targetFile = new File(criticalDir, day + "_" + chain + ".log");
            synchronized (FILE_LOCK) {
                rotateIfNeeded(targetFile);
                try (FileOutputStream stream = new FileOutputStream(targetFile, true);
                     OutputStreamWriter writer = new OutputStreamWriter(stream, UTF8)) {
                    writer.write(TS_FORMAT.format(new Date()));
                    writer.write(" [");
                    writer.write(level);
                    writer.write("] ");
                    writer.write(message);
                    writer.write('\n');
                    if (error != null) {
                        writer.write(Log.getStackTraceString(error));
                        writer.write('\n');
                    }
                    writer.flush();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Critical file log write failed", e);
        }
    }

    private static void appendLineToProxySegment(String level, String message, Throwable error) {
        try {
            File logsRoot = resolveLogsRoot();
            if (logsRoot == null) {
                return;
            }
            File poolDir = new File(logsRoot, "pool");
            if (!poolDir.exists() && !poolDir.mkdirs()) {
                Log.e(TAG, "Failed to create proxy pool log directory: " + poolDir.getAbsolutePath());
                return;
            }
            String segmentName = buildProxySegmentName(System.currentTimeMillis());
            File targetFile = new File(poolDir, segmentName + "_proxy.txt");
            synchronized (FILE_LOCK) {
                rotateIfNeeded(targetFile);
                try (FileOutputStream stream = new FileOutputStream(targetFile, true);
                     OutputStreamWriter writer = new OutputStreamWriter(stream, UTF8)) {
                    writer.write(TS_FORMAT.format(new Date()));
                    writer.write(" [");
                    writer.write(level);
                    writer.write("] ");
                    writer.write(message);
                    writer.write('\n');
                    if (error != null) {
                        writer.write(Log.getStackTraceString(error));
                        writer.write('\n');
                    }
                    writer.flush();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Proxy segment log write failed", e);
        }
    }

    private static void rotateIfNeeded(File targetFile) {
        if (!targetFile.exists() || targetFile.length() < MAX_FILE_BYTES) {
            return;
        }
        for (int i = MAX_ROTATIONS; i >= 1; i--) {
            File src = i == 1
                    ? targetFile
                    : new File(targetFile.getParentFile(), targetFile.getName() + "." + (i - 1));
            File dst = new File(targetFile.getParentFile(), targetFile.getName() + "." + i);
            if (!src.exists()) {
                continue;
            }
            if (dst.exists() && !dst.delete()) {
                Log.w(TAG, "Failed to delete old rotated file: " + dst.getAbsolutePath());
            }
            if (!src.renameTo(dst)) {
                Log.w(TAG, "Failed to rotate file: " + src.getAbsolutePath());
            }
        }
    }

    private static File resolveLogsRoot() {
        File logs = AppVars.getLogsDir();
        if (logs != null) {
            return logs;
        }
        if (AppVars.getContext() == null) {
            return null;
        }
        File fallback = new File(AppVars.getContext().getFilesDir(), "Logs");
        if (!fallback.exists() && !fallback.mkdirs()) {
            return null;
        }
        return fallback;
    }

    private static String sanitizeChain(String chain) {
        String value = chain.trim().toLowerCase(Locale.ROOT);
        value = value.replaceAll("[^a-z0-9_\\-]", "_");
        if (value.isEmpty()) {
            return "critical";
        }
        return value;
    }

    private static String sanitizeMessage(String message) {
        String value = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (value.length() > 3000) {
            return value.substring(0, 3000) + "...";
        }
        return value;
    }

    private static String buildProxySegmentName(long nowMs) {
        Date date = new Date(nowMs);
        String hourPrefix;
        synchronized (PROXY_SEGMENT_FORMAT) {
            hourPrefix = PROXY_SEGMENT_FORMAT.format(date);
        }
        int minute = Integer.parseInt(new SimpleDateFormat("mm", Locale.US).format(date));
        int bucket = (minute / 10) * 10;
        return hourPrefix + "_" + String.format(Locale.US, "%02d", bucket);
    }
}

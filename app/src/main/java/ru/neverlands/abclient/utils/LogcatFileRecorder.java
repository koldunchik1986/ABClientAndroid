package ru.neverlands.abclient.utils;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Фоновый записьщик logcat текущего процесса в файлы по 10-минутным сегментам.
 *
 * Назначение:
 * - сохранять критичные цепочки даже при переполнении системного буфера logcat;
 * - давать стабильный оффлайн-лог в `files/Logs/Logcat`.
 *
 * Ограничения Android:
 * - запись выполняется через `logcat --pid=<наш pid>`;
 * - если устройство/прошивка блокирует доступ, модуль пишет ошибку в `FileLogger` и
 *   отключается без падения приложения.
 */
public final class LogcatFileRecorder {
    private static final String TAG = "LogcatFileRecorder";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final SimpleDateFormat SEGMENT_FORMAT = new SimpleDateFormat("yyyyMMdd_HH", Locale.US);
    private static final Object LOCK = new Object();

    private static volatile boolean enabled;
    private static volatile Thread worker;
    private static volatile Process logcatProcess;

    private LogcatFileRecorder() {
    }

    public static void setEnabled(Context context, boolean shouldEnable) {
        if (context == null) {
            return;
        }
        if (shouldEnable) {
            start(context.getApplicationContext());
        } else {
            stop("disabled_by_setting");
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void start(Context appContext) {
        synchronized (LOCK) {
            if (enabled) {
                return;
            }
            enabled = true;
            Thread thread = new Thread(() -> runLoop(appContext), "ab-logcat-recorder");
            thread.setDaemon(true);
            worker = thread;
            thread.start();
            FileLogger.trace("logcat_recorder", "start requested");
        }
    }

    private static void stop(String reason) {
        synchronized (LOCK) {
            if (!enabled) {
                return;
            }
            enabled = false;
            if (logcatProcess != null) {
                try {
                    logcatProcess.destroy();
                } catch (Exception ignored) {
                }
                logcatProcess = null;
            }
            if (worker != null) {
                try {
                    worker.interrupt();
                } catch (Exception ignored) {
                }
                worker = null;
            }
            FileLogger.warn("logcat_recorder", "stopped: reason=" + reason);
        }
    }

    private static void runLoop(Context appContext) {
        BufferedWriter writer = null;
        String currentSegment = "";
        long lastFlushAt = 0L;
        try {
            while (enabled) {
                try {
                    Process process = buildLogcatProcess();
                    synchronized (LOCK) {
                        logcatProcess = process;
                    }
                    FileLogger.trace("logcat_recorder", "process started pid=" + android.os.Process.myPid());
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream(), UTF8))) {
                        String line;
                        while (enabled && (line = reader.readLine()) != null) {
                            String segment = buildSegmentName(System.currentTimeMillis());
                            if (!segment.equals(currentSegment) || writer == null) {
                                closeQuietly(writer);
                                File target = resolveSegmentFile(appContext, segment);
                                writer = new BufferedWriter(
                                        new OutputStreamWriter(new FileOutputStream(target, true), UTF8));
                                currentSegment = segment;
                            }
                            writer.write(line);
                            writer.newLine();
                            long now = System.currentTimeMillis();
                            if ((now - lastFlushAt) >= 1000L) {
                                writer.flush();
                                lastFlushAt = now;
                            }
                        }
                    } finally {
                        try {
                            int exit = process.waitFor();
                            FileLogger.warn("logcat_recorder", "process exited code=" + exit);
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception e) {
                    FileLogger.error("logcat_recorder", "runLoop iteration failed", e);
                    Log.e(TAG, "runLoop iteration failed", e);
                } finally {
                    synchronized (LOCK) {
                        logcatProcess = null;
                    }
                }

                if (!enabled) {
                    break;
                }
                try {
                    Thread.sleep(1500L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        } finally {
            closeQuietly(writer);
        }
    }

    private static Process buildLogcatProcess() throws Exception {
        String pidArg = "--pid=" + android.os.Process.myPid();
        ProcessBuilder builder = new ProcessBuilder(
                "logcat",
                "-v", "threadtime",
                pidArg,
                "*:V"
        );
        builder.redirectErrorStream(true);
        return builder.start();
    }

    private static String buildSegmentName(long nowMs) {
        Date date = new Date(nowMs);
        int minute = Integer.parseInt(new SimpleDateFormat("mm", Locale.US).format(date));
        int bucket = (minute / 10) * 10;
        return SEGMENT_FORMAT.format(date) + "_" + String.format(Locale.US, "%02d", bucket);
    }

    private static File resolveSegmentFile(Context appContext, String segment) throws Exception {
        File root = AppVars.getLogsDir();
        if (root == null) {
            root = new File(appContext.getFilesDir(), "Logs");
        }
        if (!root.exists() && !root.mkdirs()) {
            throw new IllegalStateException("Cannot create logs root: " + root.getAbsolutePath());
        }
        File dir = new File(root, "Logcat");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create logcat dir: " + dir.getAbsolutePath());
        }
        return new File(dir, segment + "_logcat.txt");
    }

    private static void closeQuietly(BufferedWriter writer) {
        if (writer == null) {
            return;
        }
        try {
            writer.flush();
        } catch (Exception ignored) {
        }
        try {
            writer.close();
        } catch (Exception ignored) {
        }
    }
}

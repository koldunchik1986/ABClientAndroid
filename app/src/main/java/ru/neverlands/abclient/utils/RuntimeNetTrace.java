package ru.neverlands.abclient.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Runtime-трассировка последнего сетевого события для on-screen debug строки.
 *
 * Назначение:
 * - хранит компактный срез "команда + значения" по последнему сетевому шагу;
 * - используется как общий канал между сетевыми слоями и UI status bar.
 *
 * Зависимости:
 * - источники: `WebViewRequestInterceptor`, `LocalHttpProxyServer`, `NetworkClient` и др.;
 * - потребитель: `MainActivity.updateClock()` (обновляет `network_debug_text_view`).
 */
public final class RuntimeNetTrace {
    private static final int MAX_TEXT_LEN = 220;
    private static volatile long lastAtMs = 0L;
    private static volatile String lastCommand = "NET";
    private static volatile String lastValues = "idle";

    private RuntimeNetTrace() {
    }

    /**
     * Публикует последнее runtime-событие сетевого слоя.
     *
     * @param command короткая команда/тип события (пример: `PROXY_SESSION`, `NAV`, `PROXY_FAIL`).
     * @param values ключевые значения события (url/mode/status/latency).
     */
    public static void push(String command, String values) {
        lastAtMs = System.currentTimeMillis();
        lastCommand = safe(command);
        lastValues = safe(values);
    }

    /**
     * Возвращает готовую строку для status bar (всегда ограниченную по длине).
     */
    public static String snapshotForUi() {
        long at = lastAtMs;
        String ts = at > 0L
                ? new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(at))
                : "--:--:--";
        String line = ts + " | " + safe(lastCommand) + " | " + safe(lastValues);
        if (line.length() > MAX_TEXT_LEN) {
            return line.substring(0, MAX_TEXT_LEN - 3) + "...";
        }
        return line;
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}


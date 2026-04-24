package ru.neverlands.anclient.utils;

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
    public static final int COLOR_NEUTRAL = 0;
    public static final int COLOR_OK = 1;
    public static final int COLOR_WARN = 2;
    public static final int COLOR_FAIL = 3;

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
        lastCommand = safe(command).toUpperCase(Locale.ROOT);
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

    /**
     * Возвращает цветовое состояние runtime-строки для status bar.
     *
     * Логика:
     * - FAIL: есть признаки ошибки/блокировки;
     * - WARN: direct route/timeout;
     * - OK: proxy/upstream пакет;
     * - NEUTRAL: прочие события.
     */
    public static int colorStateForUi() {
        String cmd = safe(lastCommand).toLowerCase(Locale.ROOT);
        String values = safe(lastValues).toLowerCase(Locale.ROOT);
        String combined = cmd + " " + values;

        if (combined.contains("fail")
                || combined.contains("blocked")
                || combined.contains("error")
                || combined.contains("407")
                || combined.contains("502")) {
            return COLOR_FAIL;
        }
        if (combined.contains("direct")
                || combined.contains("timeout")) {
            return COLOR_WARN;
        }
        if (combined.contains("proxy")
                || combined.contains("upstream")
                || combined.contains("mode=up")) {
            return COLOR_OK;
        }
        return COLOR_NEUTRAL;
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}

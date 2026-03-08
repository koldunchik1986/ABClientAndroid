package ru.neverlands.abclient.proxy;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Дедупликация шумных proxy-ошибок в logcat.
 *
 * Назначение:
 * - ограничивает повтор одного и того же `PROXY_FAIL` события по временному окну;
 * - сохраняет счетчик подавленных повторов и выводит его при следующем разрешенном логе.
 *
 * Зависимости:
 * - используется из `ProxyRuntimeManager` и `LocalHttpProxyServer` для стабилизации отладочных логов;
 * - не меняет runtime-поведение прокси, влияет только на частоту логирования.
 */
final class ProxyLogDeduper {
    private static final Map<String, DedupState> STATES = new HashMap<>();

    private ProxyLogDeduper() {
    }

    /**
     * Логирует warning-событие с подавлением повторов.
     *
     * @param tag logcat tag.
     * @param key стабильный ключ события (например, `accept_io`, `session_fail:host`).
     * @param message текст сообщения.
     * @param throwable ошибка/исключение (может быть null).
     * @param windowMs окно подавления в миллисекундах.
     */
    static void warn(String tag, String key, String message, Throwable throwable, long windowMs) {
        long now = System.currentTimeMillis();
        String finalMessage = message;
        synchronized (STATES) {
            DedupState state = STATES.get(key);
            if (state == null) {
                state = new DedupState();
                STATES.put(key, state);
            }
            if (now - state.lastLoggedAtMs < windowMs) {
                state.suppressedCount++;
                return;
            }
            if (state.suppressedCount > 0) {
                finalMessage = message + " (suppressed=" + state.suppressedCount + ")";
                state.suppressedCount = 0;
            }
            state.lastLoggedAtMs = now;
        }

        if (throwable != null) {
            Log.w(tag, finalMessage, throwable);
        } else {
            Log.w(tag, finalMessage);
        }
    }

    private static final class DedupState {
        long lastLoggedAtMs = 0L;
        int suppressedCount = 0;
    }
}


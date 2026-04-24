package ru.neverlands.anclient.postfilter;

import java.util.Locale;

import ru.neverlands.anclient.manager.CharacterVitalsManager;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.FileLogger;

/**
 * Handler server timer/wtime логики main.php.
 *
 * Источник выноса: MainPhp.mainPhpWtime(...), extractWtimeTimeoutSeconds(...), syncNeverTimerFromWtime(...).
 * Зависимости: AppVars.NeverTimer для suppress non-combat actions, AppVars.AutoMoving* для HTML-статуса,
 * CharacterVitalsManager.snapshot().tied для отображения усталости, AppLog/FileLogger для SERVER_TIMER_TRACE.
 */
final class MainPhpTimerHandler {

    private static final String TAG = "MainPhp";
    private static final long WTIME_SYNC_LOG_GUARD_MS = 1500L;

    private static volatile long lastWtimeSyncLogAtMs = 0L;

    private MainPhpTimerHandler() {
    }

    /**
     * Вставляет пользовательский статус ожидания/автоперехода рядом с серверным wtime.
     *
     * Важные переменные:
     * - html: текущий main.php HTML.
     * - AppVars.AutoMoving/AutoMovingJumps/AutoMovingDestinaton: состояние авто-движения.
     * - curTire: текущая усталость из CharacterVitalsManager.snapshot().tied.
     */
    static String mainPhpWtime(String html) {
        html = html.replace("id=wtime></div>", "id=wtime><i>\u0412\u044b\u043f\u043e\u043b\u043d\u044f\u0435\u0442\u0441\u044f \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435...</i></div>");
        String staticScriptEnd = "</SCRIPT>";
        int poswt = html.toLowerCase(Locale.ROOT).lastIndexOf(staticScriptEnd.toLowerCase());
        if (poswt != -1) {
            poswt += staticScriptEnd.length();
        }
        if (poswt != -1 && AppVars.AutoMoving && AppVars.AutoMovingJumps > 0) {
            int curTire = CharacterVitalsManager.snapshot().tied;
            String statusHtml = "<font class=nickname><div align=center style=\"color: #660066;\"><i>"
                    + "\u041f\u0443\u043d\u043a\u0442 \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u044f: <b>" + AppVars.AutoMovingDestinaton + "</b><br>"
                    + "\u0415\u0449\u0435 \u043f\u0435\u0440\u0435\u0445\u043e\u0434\u043e\u0432: <b>" + AppVars.AutoMovingJumps + "</b><br>"
                    + "\u0422\u0435\u043a\u0443\u0449\u0430\u044f \u0423\u0441\u0442\u0430\u043b\u043e\u0441\u0442\u044c: <b>" + curTire + "</b>"
                    + (AppVars.DoSearchBox ? "<br>\u0418\u0449\u0435\u043c \u043a\u043b\u0430\u0434..." : "")
                    + "</i></div></font>";
            html = html.substring(0, poswt) + statusHtml + html.substring(poswt);
        }
        return html;
    }

    /**
     * Извлекает server cooldown в секундах из разных вариантов HTML/JS таймера.
     * Поддерживаемые источники: `id=tdsec`, `time_left_sec`, `secgo`.
     */
    static int extractWtimeTimeoutSeconds(String html) {
        if (html == null || html.isEmpty()) {
            return 0;
        }
        String lower = html.toLowerCase(Locale.ROOT);

        int tdSecIdx = lower.indexOf("id=tdsec");
        if (tdSecIdx < 0) tdSecIdx = lower.indexOf("id=\"tdsec\"");
        if (tdSecIdx < 0) tdSecIdx = lower.indexOf("id='tdsec'");
        if (tdSecIdx >= 0) {
            int gt = lower.indexOf('>', tdSecIdx);
            if (gt >= 0) {
                int sec = parseUnsignedIntFrom(lower, gt + 1);
                if (sec > 0 && sec < 86400) {
                    return sec;
                }
            }
        }

        int leftIdx = lower.indexOf("time_left_sec");
        if (leftIdx >= 0) {
            int eq = lower.indexOf('=', leftIdx);
            if (eq >= 0) {
                int value = parseUnsignedIntFrom(lower, eq + 1);
                if (value > 0) {
                    if (value > 1000) {
                        return (int) Math.ceil(value / 1000.0d);
                    }
                    return value;
                }
            }
        }

        int secGoIdx = lower.indexOf("secgo");
        if (secGoIdx >= 0) {
            int eq = lower.indexOf('=', secGoIdx);
            if (eq >= 0) {
                int sec = parseUnsignedIntFrom(lower, eq + 1);
                if (sec > 0 && sec < 86400) {
                    return sec;
                }
            }
        }

        return 0;
    }

    /**
     * Синхронизирует AppVars.NeverTimer по server timer из main.php.
     *
     * Переменные для отладки:
     * - timeoutSec: результат extractWtimeTimeoutSeconds(html).
     * - now/dueAt: текущее время и новый deadline.
     * - prev/prevDelta/newDelta/updated: диагностика, почему NeverTimer был/не был обновлён.
     * - address: URL текущего кадра, пишется в SERVER_TIMER_TRACE.
     */
    static void syncNeverTimerFromWtime(String html, String address) {
        int timeoutSec = extractWtimeTimeoutSeconds(html);
        if (timeoutSec <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long dueAt = now + timeoutSec * 1000L;
        long prev = AppVars.NeverTimer;
        long prevDelta = prev - now;
        long newDelta = timeoutSec * 1000L;
        boolean updated = prev <= now || Math.abs(prevDelta - newDelta) > 1500L;
        if (updated) {
            AppVars.NeverTimer = dueAt;
        }

        if (updated || (now - lastWtimeSyncLogAtMs) >= WTIME_SYNC_LOG_GUARD_MS) {
            lastWtimeSyncLogAtMs = now;
            AppLog.d(TAG, "SERVER_TIMER_TRACE wtime sync: timeoutSec=" + timeoutSec
                    + ", updated=" + updated + ", address=" + address
                    + ", dueInMs=" + Math.max(0L, AppVars.NeverTimer - now));
            FileLogger.trace(TAG, "SERVER_TIMER_TRACE wtime sync: timeoutSec=" + timeoutSec
                    + ", updated=" + updated + ", address=" + address
                    + ", dueInMs=" + Math.max(0L, AppVars.NeverTimer - now));
        }
    }

    /**
     * Парсит первое unsigned int значение из text начиная с fromIndex.
     * Возвращает -1, если до цифр встретился HTML/JS-разделитель (`\n`, `\r`, `;`, `<`).
     */
    static int parseUnsignedIntFrom(String text, int fromIndex) {
        if (text == null || fromIndex < 0 || fromIndex >= text.length()) {
            return -1;
        }
        int i = fromIndex;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                break;
            }
            if (c == '\n' || c == '\r' || c == ';' || c == '<') {
                return -1;
            }
            i++;
        }
        if (i >= text.length()) {
            return -1;
        }
        int start = i;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            i++;
        }
        if (i <= start) {
            return -1;
        }
        try {
            return Integer.parseInt(text.substring(start, i));
        } catch (Exception ignored) {
            return -1;
        }
    }
}

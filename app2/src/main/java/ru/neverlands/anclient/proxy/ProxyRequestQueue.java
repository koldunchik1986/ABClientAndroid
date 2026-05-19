package ru.neverlands.anclient.proxy;

import java.util.Locale;

import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.SessionManager;

/**
 * Session-wide очередь динамических игровых запросов, портированная по логике
 * `ANClient/ANProxy/ProxyRequestQueue.cs`.
 *
 * Назначение:
 * - не менять бизнес-логику автофункций и HTML/JS flow;
 * - перед реальной отправкой динамического запроса к Neverlands через local/upstream proxy
 *   резервировать общий slot, чтобы несколько worker-потоков не стартовали в одну секунду;
 * - не задерживать static/cache/counter/safe lookup запросы.
 *
 * Зависимости:
 * - `LocalHttpProxyServer` вызывает `waitTurn(...)` перед remote socket connect;
 * - `SessionManager` хранит общий queue-slot текущей игровой сессии;
 * - `AppLog` пишет logcat + файловый critical log chain `proxy`.
 */
final class ProxyRequestQueue {
    private static final String TAG = "ProxyRequestQueue";
    private static final String LOG_CHAIN = "proxy";
    private static final int MAX_SAFE_URL_LENGTH = 220;

    private ProxyRequestQueue() {
    }

    /**
     * Главная точка входа перед отправкой запроса через proxy runtime.
     *
     * Зависимости:
     * - вызывается из proxy worker-потока, поэтому допустим короткий `Thread.sleep(...)`;
     * - `SessionManager.reserveProxyGameRequestSlot()` гарантирует общий порядок между worker-потоками;
     * - `ShouldQueue(...)` повторяет критерии C# и не задерживает static/cache lookup.
     *
     * @param url request-target или host+path, используемый для классификации и безопасного лога.
     * @param host origin host запроса.
     * @param isGameHost true, если host относится к Neverlands.
     * @param isCache true, если вызывающий контур уже знает, что запрос обслуживается cache/static.
     */
    static void waitTurn(String url, String host, boolean isGameHost, boolean isCache) {
        QueueDecision decision = shouldQueue(url, host, isGameHost, isCache);
        if (!decision.shouldQueue) {
            logSkipThrottled(url, isGameHost, isCache, decision.reason);
            return;
        }

        int waitMs = SessionManager.getInstance().reserveProxyGameRequestSlot();
        if (waitMs <= 0) {
            return;
        }

        AppLog.d(LOG_CHAIN, TAG, "PROXY_QUEUE: queued game action"
                + ", reason=" + decision.reason
                + ", waitMs=" + waitMs
                + ", url=" + safeUrl(url));
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            AppLog.w(LOG_CHAIN, TAG, "PROXY_QUEUE: interrupted while waiting, url=" + safeUrl(url), e);
        }
    }

    /**
     * Решает, должен ли запрос занимать queue-slot.
     *
     * Зависимости:
     * - `pathAndQuery(...)` нормализует absolute-form и origin-form request-target;
     * - `isStaticPath(...)`, `isSafeLookup(...)`, `isReadOnlyChatFrame(...)` задают исключения;
     * - критерии сохраняют поведение C# `ProxyRequestQueue.ShouldQueue(...)`.
     */
    private static QueueDecision shouldQueue(String url, String host, boolean isGameHost, boolean isCache) {
        String reason = "not_game_host";
        if (!isGameHost || isCache || isEmpty(url) || isEmpty(host)) {
            if (isGameHost && isCache) {
                reason = "cache_or_static";
            }
            if (isGameHost && isEmpty(url)) {
                reason = "empty_url";
            }
            return QueueDecision.skip(reason);
        }

        String lowerUrl = url.toLowerCase(Locale.ROOT);
        if (lowerUrl.contains("top.list.ru") || lowerUrl.contains("counter.yadro.ru")) {
            return QueueDecision.skip("counter");
        }

        String pathAndQuery = pathAndQuery(url, host);
        String path = pathOnly(pathAndQuery);
        if (isStaticPath(path)) {
            return QueueDecision.skip("static_path");
        }
        if (isSafeLookup(path)) {
            return QueueDecision.skip("safe_lookup");
        }
        if (isReadOnlyChatFrame(path, pathAndQuery)) {
            return QueueDecision.skip("read_only_chat_frame");
        }
        if (equalsIgnoreCase(path, "/main.php")) {
            return QueueDecision.queue("main_php");
        }
        if (startsWithIgnoreCase(path, "/gameplay/ajax/")) {
            return QueueDecision.queue("gameplay_ajax");
        }
        if (equalsIgnoreCase(path, "/ch.php")) {
            return QueueDecision.queue("room_or_chat_dynamic");
        }
        if (endsWithIgnoreCase(path, ".php")
                || endsWithIgnoreCase(path, ".cgi")
                || endsWithIgnoreCase(path, ".fcg")) {
            return QueueDecision.queue("dynamic_game_endpoint");
        }
        return QueueDecision.queue("dynamic_game_page");
    }

    /**
     * Нормализует request-target в `path?query`.
     *
     * Зависимости:
     * - local proxy получает как absolute-form (`http://host/path`) от HTTP proxy clients,
     *   так и origin-form (`/path`) в retry/tunnel ветках;
     * - host нужен только для случая `host/path`, как в C# реализации.
     */
    private static String pathAndQuery(String url, String host) {
        String value = url == null ? "" : url;
        int schemeIndex = value.indexOf("://");
        if (schemeIndex >= 0) {
            int pathIndex = value.indexOf('/', schemeIndex + 3);
            return pathIndex >= 0 ? value.substring(pathIndex) : "/";
        }
        if (!isEmpty(host) && startsWithIgnoreCase(value, host)) {
            value = value.substring(host.length());
        }
        if (value.isEmpty()) {
            return "/";
        }
        return value.charAt(0) == '/' ? value : "/" + value;
    }

    /**
     * Возвращает только path без query-string.
     * Зависимость: используется всеми path-based фильтрами очереди.
     */
    private static String pathOnly(String pathAndQuery) {
        if (isEmpty(pathAndQuery)) {
            return "/";
        }
        int queryIndex = pathAndQuery.indexOf('?');
        return queryIndex >= 0 ? pathAndQuery.substring(0, queryIndex) : pathAndQuery;
    }

    /**
     * Проверяет static/cache расширения, которые не должны занимать динамический game slot.
     * Зависимость: список совпадает с C# `IsStaticPath(...)`, включая `.txt`.
     */
    private static boolean isStaticPath(String path) {
        return endsWithIgnoreCase(path, ".gif")
                || endsWithIgnoreCase(path, ".jpg")
                || endsWithIgnoreCase(path, ".jpeg")
                || endsWithIgnoreCase(path, ".png")
                || endsWithIgnoreCase(path, ".swf")
                || endsWithIgnoreCase(path, ".ico")
                || endsWithIgnoreCase(path, ".css")
                || endsWithIgnoreCase(path, ".js")
                || endsWithIgnoreCase(path, ".txt");
    }

    /**
     * Исключает lookup endpoints, которые C# клиент считал безопасными для параллельного чтения.
     * Зависимость: важно не тормозить pinfo/getcity/info lookup поверх основного gameplay flow.
     */
    private static boolean isSafeLookup(String path) {
        return equalsIgnoreCase(path, "/modules/api/getid.cgi")
                || equalsIgnoreCase(path, "/modules/api/info.cgi")
                || equalsIgnoreCase(path, "/modules/api/getcity.cgi")
                || equalsIgnoreCase(path, "/pinfo.cgi")
                || equalsIgnoreCase(path, "/pbots.cgi")
                || equalsIgnoreCase(path, "/logs.fcg");
    }

    /**
     * Исключает read-only HTML frame чата.
     * Зависимость: polling/action endpoint `ch.php` остаётся динамическим, но `/ch/*.html` и `/ch.php?0` нет.
     */
    private static boolean isReadOnlyChatFrame(String path, String pathAndQuery) {
        if (startsWithIgnoreCase(path, "/ch/") && endsWithIgnoreCase(path, ".html")) {
            return true;
        }
        return equalsIgnoreCase(path, "/ch.php")
                && containsIgnoreCase(pathAndQuery, "/ch.php?0");
    }

    /**
     * Логирует skip не чаще throttle-окна, чтобы static/safe lookup не забивали файлы логов.
     * Зависимость: throttle-state хранится в `SessionManager` вместе с queue reservation state.
     */
    private static void logSkipThrottled(String url, boolean isGameHost, boolean isCache, String reason) {
        if (!isGameHost || isCache) {
            return;
        }
        if (!SessionManager.getInstance().shouldLogProxyRequestQueueSkip()) {
            return;
        }
        AppLog.d(LOG_CHAIN, TAG, "PROXY_QUEUE: skipped game request queue"
                + ", reason=" + reason
                + ", url=" + safeUrl(url));
    }

    /**
     * Готовит URL к логированию: `vcode` маскируется, строка режется до безопасной длины.
     * Зависимость: используется только диагностикой, на реальный request-target не влияет.
     */
    private static String safeUrl(String url) {
        String value = maskQueryParam(url, "vcode=");
        if (value == null) {
            return "";
        }
        if (value.length() <= MAX_SAFE_URL_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_SAFE_URL_LENGTH);
    }

    /**
     * Маскирует конкретный query parameter без URL-decoding, сохраняя wire-level строку.
     * Зависимость: повторяет C# `MaskQueryParam(...)` для `vcode=`.
     */
    private static String maskQueryParam(String value, String key) {
        if (isEmpty(value) || isEmpty(key)) {
            return value;
        }
        String result = value;
        String lowerResult = result.toLowerCase(Locale.ROOT);
        String lowerKey = key.toLowerCase(Locale.ROOT);
        int startIndex = lowerResult.indexOf(lowerKey);
        while (startIndex >= 0) {
            int valueStartIndex = startIndex + key.length();
            int valueEndIndex = result.indexOf('&', valueStartIndex);
            if (valueEndIndex < 0) {
                valueEndIndex = result.length();
            }
            result = result.substring(0, valueStartIndex) + "<redacted>" + result.substring(valueEndIndex);
            lowerResult = result.toLowerCase(Locale.ROOT);
            startIndex = lowerResult.indexOf(lowerKey, valueStartIndex + "<redacted>".length());
        }
        return result;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /** Case-insensitive equals без null-risk для компактных filter-проверок. */
    private static boolean equalsIgnoreCase(String value, String expected) {
        return value != null && expected != null && value.equalsIgnoreCase(expected);
    }

    /** Case-insensitive startsWith без regex, чтобы не добавлять overhead в hot proxy path. */
    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value != null && prefix != null && value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    /** Case-insensitive endsWith для path extension фильтров. */
    private static boolean endsWithIgnoreCase(String value, String suffix) {
        return value != null && suffix != null && value.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT));
    }

    /** Case-insensitive contains для counter/read-only chat exceptions. */
    private static boolean containsIgnoreCase(String value, String part) {
        return value != null && part != null && value.toLowerCase(Locale.ROOT).contains(part.toLowerCase(Locale.ROOT));
    }

    /** Decision DTO без внешних зависимостей, чтобы не создавать параллельную state-модель. */
    private static final class QueueDecision {
        final boolean shouldQueue;
        final String reason;

        private QueueDecision(boolean shouldQueue, String reason) {
            this.shouldQueue = shouldQueue;
            this.reason = reason == null ? "unknown" : reason;
        }

        /** Создаёт решение `queue` с диагностической причиной из C#-совместимого набора. */
        static QueueDecision queue(String reason) {
            return new QueueDecision(true, reason);
        }

        /** Создаёт решение `skip` с диагностической причиной из C#-совместимого набора. */
        static QueueDecision skip(String reason) {
            return new QueueDecision(false, reason);
        }
    }
}

package ru.neverlands.anclient.proxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.postfilter.MainPhp;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.FileLogger;
import ru.neverlands.anclient.utils.GameServerUrls;
import ru.neverlands.anclient.utils.RuntimeNetTrace;

/**
 * Локальный HTTP proxy-сервер (loopback), который повторяет базовый runtime-подход ПК версии:
 * весь трафик приложения идет через localhost, а дальше отправляется либо напрямую (DIRECT),
 * либо через внешний upstream proxy (UPSTREAM).
 *
 * Зависимости:
 * - {@link ProxyRuntimeManager.ProxyUpstreamSettings}: настройки внешнего прокси.
 * - {@link ProxyRuntimeManager}: orchestration start/stop + жизненный цикл.
 * - Java sockets ({@link ServerSocket}, {@link Socket}) для прозрачной проксирующей передачи.
 *
 * Ограничения текущей версии:
 * - поддерживается HTTP (GET/POST и другие обычные методы);
 * - HTTPS CONNECT в этой версии не реализован (возвращается 501), т.к. игровой трафик neverlands
 *   в рамках текущего клиента работает по http://.
 */
final class LocalHttpProxyServer {
    private static final String TAG = "LocalHttpProxyServer";
    private static final String LOG_CHAIN = "proxy";
    private static final int MAX_BIND_ATTEMPTS = 64;
    private static final int HEADER_SIZE_LIMIT_BYTES = 64 * 1024;
    private static final int RESPONSE_HEADER_SIZE_LIMIT_BYTES = 64 * 1024;
    private static final int SOCKET_TIMEOUT_MS = 20_000;
    private static final int LOG_DEDUP_WINDOW_MS = 5_000;
    private static final int SERVER_NOTICE_CAPTURE_MAX_BYTES = 96 * 1024;
    private static final int PROXY_WORKER_CORE_THREADS = 8;
    private static final int PROXY_WORKER_MAX_THREADS = 12;
    private static final int PROXY_WORKER_QUEUE_CAPACITY = 24;

    private final int startPort;
    private final ProxyRuntimeManager.ProxyUpstreamSettings upstreamSettings;

    private volatile boolean running = false;
    private volatile int boundPort = -1;
    private ServerSocket serverSocket;
    private ExecutorService acceptExecutor;
    private ExecutorService workerExecutor;
    private final Set<Socket> activeClientSockets = Collections.newSetFromMap(new ConcurrentHashMap<Socket, Boolean>());

    LocalHttpProxyServer(int startPort, ProxyRuntimeManager.ProxyUpstreamSettings upstreamSettings) {
        this.startPort = startPort;
        this.upstreamSettings = upstreamSettings;
    }

    /**
     * Стартует loopback-listener с авто-подбором свободного порта.
     *
     * Зависимости:
     * - использует локальный адрес 127.0.0.1 (не публикуется наружу);
     * - возвращаемый порт далее используется {@link ProxyRuntimeManager} для WebView/OkHttp bind.
     *
     * @return фактически занятый локальный порт.
     * @throws IOException если не удалось поднять listener.
     */
    synchronized int start() throws IOException {
        if (running) {
            return boundPort;
        }

        this.serverSocket = bindLoopbackSocket();
        this.boundPort = serverSocket.getLocalPort();
        this.running = true;

        this.acceptExecutor = Executors.newSingleThreadExecutor();
        this.workerExecutor = new ThreadPoolExecutor(
                PROXY_WORKER_CORE_THREADS,
                PROXY_WORKER_MAX_THREADS,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(PROXY_WORKER_QUEUE_CAPACITY),
                new ThreadPoolExecutor.AbortPolicy()
        );

        acceptExecutor.execute(this::acceptLoop);
        AppLog.i(LOG_CHAIN, TAG, "PROXY_BOOT: listener started at 127.0.0.1:" + boundPort);
        AppLog.i(LOG_CHAIN, TAG, "PROXY_UPSTREAM: enabled=" + upstreamSettings.enabled
                + ", host=" + upstreamSettings.host
                + ", port=" + upstreamSettings.port);
        AppLog.i(LOG_CHAIN, TAG, "PROXY_AUTH: upstream basic auth enabled="
                + (upstreamSettings.basicAuthHeader != null && !upstreamSettings.basicAuthHeader.isEmpty()));
        return boundPort;
    }

    /**
     * Останавливает listener и завершает все worker-потоки.
     *
     * Зависимости:
     * - вызывается только через {@link ProxyRuntimeManager#stop(boolean)}.
     */
    synchronized void stop() {
        running = false;
        closeQuietly(serverSocket);
        serverSocket = null;
        if (acceptExecutor != null) {
            acceptExecutor.shutdownNow();
            acceptExecutor = null;
        }
        if (workerExecutor != null) {
            workerExecutor.shutdownNow();
            workerExecutor = null;
        }
        for (Socket clientSocket : activeClientSockets) {
            closeQuietly(clientSocket);
        }
        activeClientSockets.clear();
        boundPort = -1;
        AppLog.i(LOG_CHAIN, TAG, "PROXY_BOOT: listener stopped");
    }

    int getBoundPort() {
        return boundPort;
    }

    private ServerSocket bindLoopbackSocket() throws IOException {
        IOException last = null;
        for (int i = 0; i < MAX_BIND_ATTEMPTS; i++) {
            int port = startPort + i;
            try {
                ServerSocket socket = new ServerSocket();
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
                socket.setSoTimeout(0);
                return socket;
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("Unable to bind local proxy socket");
    }

    private void acceptLoop() {
        while (running && serverSocket != null) {
            try {
                Socket client = serverSocket.accept();
                client.setSoTimeout(SOCKET_TIMEOUT_MS);
                client.setTcpNoDelay(true);
                AppLog.d(LOG_CHAIN, TAG, "PROXY_SESSION: accepted client=" + client.getRemoteSocketAddress());
                if (workerExecutor != null) {
                    activeClientSockets.add(client);
                    try {
                        workerExecutor.execute(() -> handleClient(client));
                    } catch (RejectedExecutionException e) {
                        activeClientSockets.remove(client);
                        closeQuietly(client);
                        AppLog.w(LOG_CHAIN, TAG, "PROXY_SESSION: worker queue full, client rejected", e);
                    }
                } else {
                    closeQuietly(client);
                }
            } catch (SocketException e) {
                if (running) {
                    ProxyLogDeduper.warn(
                            TAG,
                            "accept_socket_exception",
                            "PROXY_FAIL: accept socket exception",
                            e,
                            LOG_DEDUP_WINDOW_MS
                    );
                }
                break;
            } catch (IOException e) {
                if (running) {
                    ProxyLogDeduper.warn(
                            TAG,
                            "accept_io_exception",
                            "PROXY_FAIL: accept I/O exception",
                            e,
                            LOG_DEDUP_WINDOW_MS
                    );
                }
            } catch (Throwable t) {
                ProxyLogDeduper.warn(
                        TAG,
                        "accept_unexpected_failure",
                        "PROXY_FAIL: unexpected accept failure",
                        t,
                        LOG_DEDUP_WINDOW_MS
                );
            }
        }
    }

    private void handleClient(Socket client) {
        long startedAtMs = System.currentTimeMillis();
        String sessionTarget = "unknown";
        try (Socket clientSocket = client) {
            InputStream clientIn = clientSocket.getInputStream();
            OutputStream clientOut = clientSocket.getOutputStream();

            HttpRequest request = readRequest(clientIn);
            if (request == null) {
                writeSimpleError(clientOut, 400, "Bad Request", "Invalid proxy request");
                return;
            }
            logPoolProxyRequest(request);
            AppLog.d(LOG_CHAIN, TAG, "PROXY_SESSION: method=" + request.method
                    + ", uri=" + request.uriToken
                    + ", bodyBytes=" + (request.body == null ? 0 : request.body.length));
            RuntimeNetTrace.push("PROXY_REQ",
                    "method=" + request.method + " uri=" + trimForTrace(request.uriToken));

            if ("CONNECT".equalsIgnoreCase(request.method)) {
                writeSimpleError(clientOut, 501, "Not Implemented", "CONNECT is not supported");
                return;
            }

            ResolvedRoute route = resolveRoute(request);
            AppLog.d(LOG_CHAIN, TAG, "PROXY_SESSION: route origin=" + route.originHost + ":" + route.originPort
                    + ", connect=" + route.connectHost + ":" + route.connectPort
                    + ", target=" + route.requestTarget);
            RuntimeNetTrace.push("PROXY_ROUTE",
                    "origin=" + route.originHost + ":" + route.originPort
                            + " connect=" + route.connectHost + ":" + route.connectPort);
            sessionTarget = route.originHost + ":" + route.originPort;
            long copiedBytes = forwardRequest(route, request, clientOut);
            long elapsed = Math.max(0L, System.currentTimeMillis() - startedAtMs);
            AppLog.d(LOG_CHAIN, TAG, "PROXY_SESSION: target=" + sessionTarget
                    + " mode=" + (upstreamSettings.enabled ? "UPSTREAM" : "DIRECT")
                    + " bytesOut=" + copiedBytes
                    + " latencyMs=" + elapsed);
            RuntimeNetTrace.push("PROXY_DONE",
                    "mode=" + (upstreamSettings.enabled ? "UP" : "DIR")
                            + " bytes=" + copiedBytes + " ms=" + elapsed + " target=" + sessionTarget);
        } catch (Exception e) {
            // Клиент ушёл до конца записи ответа — это не сбой прокси и не повод для красной тревоги.
            // Отправлять 502 тоже некому: сокет уже закрыт.
            if (isClientAbort(e)) {
                AppLog.d(LOG_CHAIN, TAG, "PROXY_CLIENT_ABORT: client closed connection target=" + sessionTarget
                        + ", cause=" + e.getClass().getSimpleName());
                return;
            }

            if (isPoolProxyLoggingEnabled()) {
                FileLogger.proxyPoolError("SESSION_FAIL target=" + sessionTarget, e);
            }
            ProxyLogDeduper.warn(
                    TAG,
                    "session_failed:" + sessionTarget,
                    "PROXY_FAIL: session failed target=" + sessionTarget,
                    e,
                    LOG_DEDUP_WINDOW_MS
            );
            RuntimeNetTrace.push("PROXY_FAIL", e.getClass().getSimpleName() + " target=" + sessionTarget);
            try {
                OutputStream fallbackOut = client.getOutputStream();
                writeSimpleError(fallbackOut, 502, "Bad Gateway", "Proxy forwarding error");
            } catch (Throwable t) {
                // Клиент уже отвалился — 502 доставить некому, но факт важен для диагностики прокси-цепочки.
                AppLog.d(LOG_CHAIN, TAG, "PROXY_SESSION: failed to deliver 502 fallback: " + t.getClass().getSimpleName());
            }
        } finally {
            activeClientSockets.remove(client);
        }
    }

    private static String trimForTrace(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= 80) {
            return value;
        }
        return value.substring(0, 77) + "...";
    }

    private long forwardRequest(ResolvedRoute route, HttpRequest request, OutputStream clientOut) throws IOException {
        RetryConnection retryConnection = new RetryConnection();
        try {
            waitProxyQueueTurn(route, route.requestTarget, "primary");
            try (Socket remote = new Socket()) {
                remote.setTcpNoDelay(true);
                remote.setSoTimeout(SOCKET_TIMEOUT_MS);
                remote.connect(new InetSocketAddress(route.connectHost, route.connectPort), SOCKET_TIMEOUT_MS);

                OutputStream remoteOut = remote.getOutputStream();
                InputStream remoteIn = remote.getInputStream();

                writeRequest(remoteOut, route, request, route.requestTarget, true);
                ResponseHead responseHead = readResponseHead(remoteIn);
                logProxyResponse(route, request, route.requestTarget, 1, responseHead);

                if (shouldRetryAuthPostWithOriginForm(route, request, responseHead)) {
                    String absoluteWithPortTarget = buildAbsoluteTargetWithExplicitPort(route);
                    int retryAttempt = 2;
                    if (!absoluteWithPortTarget.equals(route.requestTarget)) {
                        AppLog.w(LOG_CHAIN, TAG, "PROXY_UPSTREAM_RETRY: 405 on absolute-form POST /game.php, retry absolute-form with explicit port");
                        ResponseHead retryHead = forwardSingleRetry(route, request, absoluteWithPortTarget, retryAttempt++, retryConnection);
                        if (isAcceptableRetryResponse(retryHead)) {
                            clientOut.write(retryHead.rawBytes);
                            CopyResult retryCopy = copyStreamWithCapture(
                                    retryConnection.inputStream,
                                    clientOut,
                                    SERVER_NOTICE_CAPTURE_MAX_BYTES
                            );
                            handleServerNoticeFromCapturedPayload(route, request, absoluteWithPortTarget, retryHead, retryCopy.capturedBytes);
                            return retryHead.rawBytes.length + retryCopy.totalBytes;
                        }
                        retryConnection.close();
                    }

                    String originFormTarget = buildOriginFormTarget(route);
                    if (!originFormTarget.equals(route.requestTarget) && !originFormTarget.equals(absoluteWithPortTarget)) {
                        AppLog.w(LOG_CHAIN, TAG, "PROXY_UPSTREAM_RETRY: 405 persists, retry origin-form POST target=" + originFormTarget);
                        ResponseHead retryHead = forwardSingleRetry(route, request, originFormTarget, retryAttempt++, retryConnection);
                        if (isAcceptableRetryResponse(retryHead)) {
                            clientOut.write(retryHead.rawBytes);
                            CopyResult retryCopy = copyStreamWithCapture(
                                    retryConnection.inputStream,
                                    clientOut,
                                    SERVER_NOTICE_CAPTURE_MAX_BYTES
                            );
                            handleServerNoticeFromCapturedPayload(route, request, originFormTarget, retryHead, retryCopy.capturedBytes);
                            return retryHead.rawBytes.length + retryCopy.totalBytes;
                        }
                        retryConnection.close();
                    }

                    AppLog.w(LOG_CHAIN, TAG, "PROXY_UPSTREAM_RETRY: 405 persists, retry via CONNECT tunnel");
                    return forwardViaUpstreamConnectTunnel(route, request, clientOut);
                }

                clientOut.write(responseHead.rawBytes);
                CopyResult copyResult = copyStreamWithCapture(remoteIn, clientOut, SERVER_NOTICE_CAPTURE_MAX_BYTES);
                handleServerNoticeFromCapturedPayload(route, request, route.requestTarget, responseHead, copyResult.capturedBytes);
                return responseHead.rawBytes.length + copyResult.totalBytes;
            }
        } finally {
            retryConnection.close();
        }
    }

    /**
     * Проверяет, можно ли принять retry-ответ как финальный.
     *
     * Зависимости:
     * - используется только в auth-fallback ветке `POST /game.php`;
     * - к финальному ответу допускаются только коды < 400 (успех/редирект).
     *
     * Почему:
     * - коды `400/403/5xx` означают, что retry-путь не сработал и нужно пробовать следующий fallback
     *   (например CONNECT-туннель), а не отдавать ошибку клиенту раньше времени.
     */
    private boolean isAcceptableRetryResponse(ResponseHead head) {
        return head != null && head.statusCode > 0 && head.statusCode < 400;
    }

    private final class RetryConnection {
        private Socket socket;
        private InputStream inputStream;

        private void replace(Socket newSocket, InputStream newInputStream) {
            close();
            socket = newSocket;
            inputStream = newInputStream;
        }

        private void close() {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                    // Ожидаемая ветка cleanup: поток уже закрыт/оборван, ресурс всё равно освобождается ниже.
                }
                inputStream = null;
            }
            if (socket != null) {
                closeQuietly(socket);
                socket = null;
            }
        }
    }

    /**
     * Выполняет один дополнительный retry в upstream-режиме с альтернативным request-target.
     * Возвращает разобранный head ответа, а body остаётся в request-local retry connection
     * для последующего проброса в клиент.
     */
    private ResponseHead forwardSingleRetry(ResolvedRoute route,
                                             HttpRequest request,
                                             String retryTarget,
                                             int attempt,
                                             RetryConnection retryConnection) throws IOException {
        retryConnection.close();
        waitProxyQueueTurn(route, retryTarget, "retry_" + attempt);
        Socket retrySocket = new Socket();
        try {
            retrySocket.setTcpNoDelay(true);
            retrySocket.setSoTimeout(SOCKET_TIMEOUT_MS);
            retrySocket.connect(new InetSocketAddress(route.connectHost, route.connectPort), SOCKET_TIMEOUT_MS);
            OutputStream retryOut = retrySocket.getOutputStream();
            InputStream retryIn = retrySocket.getInputStream();
            retryConnection.replace(retrySocket, retryIn);
            writeRequest(retryOut, route, request, retryTarget, true);
            ResponseHead retryHead = readResponseHead(retryIn);
            logProxyResponse(route, request, retryTarget, attempt, retryHead);
            return retryHead;
        } catch (IOException | RuntimeException e) {
            closeQuietly(retrySocket);
            throw e;
        }
    }

    private String buildAbsoluteTargetWithExplicitPort(ResolvedRoute route) {
        String path = route.originPath == null || route.originPath.isEmpty() ? "/" : route.originPath;
        return "http://" + route.originHost + ":" + route.originPort + path;
    }

    /**
     * Возвращает origin-form target (`/path?query`) для upstream-retry.
     *
     * Зависимости:
     * - используется только в fallback-ветке `POST /game.php` после 405;
     * - позволяет пройти через прокси/шлюзы, которые принимают POST только в origin-form.
     */
    private String buildOriginFormTarget(ResolvedRoute route) {
        if (route.originPath == null || route.originPath.isEmpty()) {
            return "/";
        }
        return route.originPath.startsWith("/") ? route.originPath : "/" + route.originPath;
    }

    /**
     * Формирует и отправляет HTTP-запрос на удалённый endpoint (direct/upstream) с заданным request-target.
     *
     * Зависимости:
     * - {@link #shouldSkipRequestHeader(String)} для фильтра hop-by-hop заголовков;
     * - поля {@code upstreamSettings.*} для проброса Proxy-Authorization в upstream-режиме;
     * - {@link ResolvedRoute#originHost}/{@link ResolvedRoute#originPort} для fallback Host.
     */
    private void writeRequest(OutputStream remoteOut,
                              ResolvedRoute route,
                              HttpRequest request,
                              String requestTarget,
                              boolean includeProxyAuthorizationHeader) throws IOException {
        // Анти-детект: внутренние маркеры клиента не должны попадать на сервер.
        String sanitizedTarget = stripClientMarkersFromTarget(requestTarget);

        StringBuilder outHead = new StringBuilder();
        outHead.append(request.method)
                .append(' ')
                .append(sanitizedTarget)
                .append(' ')
                .append(request.httpVersion)
                .append("\r\n");

        boolean hasHostHeader = false;
        for (Map.Entry<String, String> header : request.headers.entrySet()) {
            String key = header.getKey();
            if (key == null || key.isEmpty()) {
                continue;
            }
            String lower = key.toLowerCase(Locale.ROOT);
            if ("host".equals(lower)) {
                hasHostHeader = true;
            }
            if (shouldSkipRequestHeader(lower)) {
                continue;
            }
            // Анти-детект: идентифицирующие заголовки не пробрасываем как есть —
            // ниже они переписываются согласованным браузерным набором.
            if (isClientIdentityHeader(lower)) {
                continue;
            }
            outHead.append(key).append(": ").append(header.getValue()).append("\r\n");
        }

        appendBrowserIdentityHeaders(outHead);

        if (!hasHostHeader) {
            outHead.append("Host: ").append(route.originHost);
            if (route.originPort != 80) {
                outHead.append(':').append(route.originPort);
            }
            outHead.append("\r\n");
        }
        outHead.append("Connection: close\r\n");

        if (includeProxyAuthorizationHeader
                && upstreamSettings.enabled
                && upstreamSettings.basicAuthHeader != null
                && !upstreamSettings.basicAuthHeader.isEmpty()) {
            outHead.append("Proxy-Authorization: ").append(upstreamSettings.basicAuthHeader).append("\r\n");
        }

        outHead.append("\r\n");
        remoteOut.write(outHead.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (request.body != null && request.body.length > 0) {
            remoteOut.write(request.body);
        }
        remoteOut.flush();
    }

    /**
     * Fallback для upstream-auth POST `/game.php`:
     * открывает CONNECT-туннель к `neverlands.ru:80` через upstream proxy и повторяет POST в origin-form.
     *
     * Зависимости:
     * - upstream credentials ({@code Proxy-Authorization}) для CONNECT;
     * - {@link #writeRequest(OutputStream, ResolvedRoute, HttpRequest, String, boolean)}:
     *   отправка POST внутри туннеля без proxy-заголовков;
     * - {@link #readResponseHead(InputStream)} для диагностики CONNECT и итогового POST ответа.
     */
    private long forwardViaUpstreamConnectTunnel(ResolvedRoute route,
                                                  HttpRequest request,
                                                  OutputStream clientOut) throws IOException {
        waitProxyQueueTurn(route, route.originPath, "connect_tunnel");
        try (Socket tunnelSocket = new Socket()) {
            tunnelSocket.setTcpNoDelay(true);
            tunnelSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
            tunnelSocket.connect(new InetSocketAddress(route.connectHost, route.connectPort), SOCKET_TIMEOUT_MS);

            OutputStream tunnelOut = tunnelSocket.getOutputStream();
            InputStream tunnelIn = tunnelSocket.getInputStream();

            StringBuilder connectHead = new StringBuilder();
            connectHead.append("CONNECT ")
                    .append(route.originHost)
                    .append(":")
                    .append(route.originPort)
                    .append(" HTTP/1.1\r\n");
            connectHead.append("Host: ")
                    .append(route.originHost)
                    .append(":")
                    .append(route.originPort)
                    .append("\r\n");
            connectHead.append("Connection: keep-alive\r\n");
            if (upstreamSettings.basicAuthHeader != null && !upstreamSettings.basicAuthHeader.isEmpty()) {
                connectHead.append("Proxy-Authorization: ").append(upstreamSettings.basicAuthHeader).append("\r\n");
            }
            connectHead.append("\r\n");
            tunnelOut.write(connectHead.toString().getBytes(StandardCharsets.ISO_8859_1));
            tunnelOut.flush();

            ResponseHead connectResponse = readResponseHead(tunnelIn);
            AppLog.d(LOG_CHAIN, TAG, "PROXY_TUNNEL: CONNECT " + route.originHost + ":" + route.originPort
                    + " status=" + connectResponse.statusCode
                    + ", server=" + connectResponse.serverHeader
                    + ", statusLine=" + connectResponse.statusLine);

            if (connectResponse.statusCode != 200) {
                clientOut.write(connectResponse.rawBytes);
                CopyResult connectCopy = copyStreamWithCapture(tunnelIn, clientOut, SERVER_NOTICE_CAPTURE_MAX_BYTES);
                handleServerNoticeFromCapturedPayload(route, request, route.requestTarget, connectResponse, connectCopy.capturedBytes);
                return connectResponse.rawBytes.length + connectCopy.totalBytes;
            }

            writeRequest(tunnelOut, route, request, route.originPath, false);
            ResponseHead tunneledResponse = readResponseHead(tunnelIn);
            logProxyResponse(route, request, route.originPath, 2, tunneledResponse);
            clientOut.write(tunneledResponse.rawBytes);
            CopyResult tunneledCopy = copyStreamWithCapture(tunnelIn, clientOut, SERVER_NOTICE_CAPTURE_MAX_BYTES);
            handleServerNoticeFromCapturedPayload(route, request, route.originPath, tunneledResponse, tunneledCopy.capturedBytes);
            return tunneledResponse.rawBytes.length + tunneledCopy.totalBytes;
        }
    }

    /**
     * Делегирует динамический Neverlands request в session-wide queue перед реальным remote connect.
     *
     * Зависимости:
     * - `ProxyRequestQueue` повторяет фильтры C# `ANProxy.ProxyRequestQueue`;
     * - `SessionManager` внутри queue хранит общий timestamp-slot для всех proxy worker-потоков;
     * - вызывается только из мест, которые действительно открывают новый remote socket.
     *
     * Почему это здесь:
     * - `LocalHttpProxyServer` является единой точкой для WebView, OkHttp и `HttpURLConnection`,
     *   когда они идут через local/upstream proxy runtime;
     * - автофункции и parser-ветки не получают новый сетевой контур и не меняют порядок действий.
     */
    private void waitProxyQueueTurn(ResolvedRoute route, String requestTarget, String source) {
        if (route == null) {
            return;
        }
        String host = route.originHost == null ? "" : route.originHost;
        String queueUrl = buildQueueUrl(route, requestTarget);
        ProxyRequestQueue.waitTurn(queueUrl, host, isNeverlandsGameHost(host), false);
    }

    /**
     * Формирует host+path для queue-классификации и safe diagnostics.
     * Зависимость: `ResolvedRoute.originPath` содержит path+query независимо от direct/upstream mode.
     */
    private String buildQueueUrl(ResolvedRoute route, String requestTarget) {
        String path = requestTarget == null || requestTarget.isEmpty() ? route.originPath : requestTarget;
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return (route.originHost == null ? "" : route.originHost) + (path.startsWith("/") ? path : "/" + path);
    }

    /**
     * Определяет игровые host Neverlands для proxy queue.
     * Зависимость: повторяет C# `Session.ExecuteBasicRequestManipulations()` host-check.
     */
    private boolean isNeverlandsGameHost(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        return GameServerUrls.isNeverlandsGameHost(host);
    }

    /**
     * Считывает только head ответа (status-line + headers) и возвращает его в сыром виде для последующего
     * проброса клиенту без модификации. Body остаётся в исходном InputStream и дочитывается отдельно.
     *
     * Зависимости:
     * - лимит {@link #RESPONSE_HEADER_SIZE_LIMIT_BYTES} (защита от аномально больших заголовков);
     * - ISO-8859-1 для корректной передачи wire-level байтов HTTP-head.
     */
    private ResponseHead readResponseHead(InputStream remoteIn) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int state = 0;
        while (true) {
            int b = remoteIn.read();
            if (b == -1) {
                break;
            }
            head.write(b);
            if (head.size() > RESPONSE_HEADER_SIZE_LIMIT_BYTES) {
                throw new IOException("Response headers too large");
            }
            if (state == 0 && b == '\r') state = 1;
            else if (state == 1 && b == '\n') state = 2;
            else if (state == 2 && b == '\r') state = 3;
            else if (state == 3 && b == '\n') break;
            else state = 0;
        }

        byte[] raw = head.toByteArray();
        String text = new String(raw, StandardCharsets.ISO_8859_1);
        String[] lines = text.split("\r\n");
        String statusLine = lines.length > 0 ? lines[0] : "";
        int statusCode = parseStatusCode(statusLine);
        String serverHeader = "";
        String contentTypeHeader = "";
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isEmpty()) {
                continue;
            }
            int idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            if ("server".equalsIgnoreCase(key)) {
                serverHeader = value;
            } else if ("content-type".equalsIgnoreCase(key)) {
                contentTypeHeader = value;
            }
        }
        return new ResponseHead(raw, statusLine, statusCode, serverHeader, contentTypeHeader);
    }

    /**
     * Локальная диагностика ответа прокси-контура. Нужна для постмортема ошибок auth/game flow (405/407/5xx).
     */
    private void logProxyResponse(ResolvedRoute route,
                                  HttpRequest request,
                                  String requestTarget,
                                  int attempt,
                                  ResponseHead head) {
        String mode = upstreamSettings.enabled ? "UPSTREAM" : "DIRECT";
        AppLog.d(LOG_CHAIN, TAG, "PROXY_RESP: mode=" + mode
                + ", attempt=" + attempt
                + ", method=" + request.method
                + ", target=" + requestTarget
                + ", status=" + head.statusCode
                + ", server=" + head.serverHeader
                + ", statusLine=" + head.statusLine);
        RuntimeNetTrace.push("PROXY_RESP",
                "mode=" + mode + " code=" + head.statusCode + " target=" + trimForTrace(requestTarget));
        if (isPoolProxyLoggingEnabled()) {
            FileLogger.proxyPool("RESP"
                    + " mode=" + mode
                    + " attempt=" + attempt
                    + " method=" + safeValue(request.method)
                    + " target=" + safeValue(requestTarget)
                    + " status=" + head.statusCode
                    + " server=" + safeValue(head.serverHeader)
                    + " statusLine=" + safeValue(head.statusLine));
        }
    }

    /**
     * Fallback-ветка совместимости для upstream proxy:
     * некоторые upstream принимают GET в absolute-form, но отклоняют POST /game.php в absolute-form кодом 405.
     * В этом случае повторяем один раз origin-form ("/game.php"), сохраняя остальные заголовки/тело запроса.
     *
     * Зависимости:
     * - upstream-режим ({@link #upstreamSettings}),
     * - auth endpoint neverlands ({@link ResolvedRoute#originPath} = "/game.php"),
     * - статус первого ответа (405).
     */
    private boolean shouldRetryAuthPostWithOriginForm(ResolvedRoute route, HttpRequest request, ResponseHead head) {
        if (!upstreamSettings.enabled || head == null || head.statusCode != 405) {
            return false;
        }
        if (!"POST".equalsIgnoreCase(request.method)) {
            return false;
        }
        if (!isNeverlandsAuthHost(route.originHost)) {
            return false;
        }
        if (route.originPath == null || !route.originPath.startsWith("/game.php")) {
            return false;
        }
        return route.requestTarget != null && route.requestTarget.startsWith("http://");
    }

    /**
     * Проверяет, что хост относится к auth-зоне Neverlands (`neverlands.ru` или `www.neverlands.ru`).
     *
     * Зависимости:
     * - используется только fallback-веткой 405 для `POST /game.php`;
     * - нужен для 1:1 совместимости с C#-логикой, где auth часто идёт через `www`.
     */
    private boolean isNeverlandsAuthHost(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        return GameServerUrls.isNeverlandsGameHost(host);
    }

    private int parseStatusCode(String statusLine) {
        if (statusLine == null || statusLine.isEmpty()) {
            return -1;
        }
        String[] parts = statusLine.split(" ");
        if (parts.length < 2) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void logPoolProxyRequest(HttpRequest request) {
        if (!isPoolProxyLoggingEnabled() || request == null) {
            return;
        }
        int bodyLength = request.body == null ? 0 : request.body.length;
        String host = "";
        if (request.headers != null) {
            host = safeValue(request.headers.get("Host"));
            if (host.isEmpty()) {
                host = safeValue(request.headers.get("host"));
            }
        }
        FileLogger.proxyPool("REQ"
                + " method=" + safeValue(request.method)
                + " uri=" + safeValue(request.uriToken)
                + " host=" + host
                + " bodyBytes=" + bodyLength);
    }

    private boolean isPoolProxyLoggingEnabled() {
        return AppVars.Profile != null && AppVars.Profile.RecordProxyPoolLog;
    }

    private static String safeValue(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() > 400) {
            return normalized.substring(0, 400) + "...";
        }
        return normalized;
    }

    private ResolvedRoute resolveRoute(HttpRequest request) {
        String uriToken = request.uriToken;
        String originHost;
        int originPort;
        String originPath;

        if (uriToken.startsWith("http://") || uriToken.startsWith("https://")) {
            URI uri = URI.create(uriToken);
            originHost = uri.getHost();
            if (originHost == null || originHost.isEmpty()) {
                throw new IllegalArgumentException("Missing host in absolute URI: " + uriToken);
            }
            originPort = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
            String rawPath = uri.getRawPath();
            String rawQuery = uri.getRawQuery();
            originPath = (rawPath == null || rawPath.isEmpty()) ? "/" : rawPath;
            if (rawQuery != null && !rawQuery.isEmpty()) {
                originPath = originPath + "?" + rawQuery;
            }
        } else {
            String hostHeader = request.headers.get("Host");
            if (hostHeader == null) {
                hostHeader = request.headers.get("host");
            }
            if (hostHeader == null || hostHeader.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing Host header for origin-form request");
            }
            String normalized = hostHeader.trim();
            int idx = normalized.lastIndexOf(':');
            if (idx > 0 && idx < normalized.length() - 1 && normalized.indexOf(']') == -1) {
                originHost = normalized.substring(0, idx);
                originPort = parsePortSafe(normalized.substring(idx + 1), 80);
            } else {
                originHost = normalized;
                originPort = 80;
            }
            originPath = (uriToken == null || uriToken.isEmpty()) ? "/" : uriToken;
        }

        // ВЫБОР СЕРВЕРА (DE/KZ): подключаемся к IP выбранного сервера, но host НЕ подменяем.
        //
        // Раньше здесь выполнялось `originHost = selectedHost`, из-за чего IP сервера попадал
        // в заголовок Host, в URL и в ключи cookie. Сессия оказывалась размазана между
        // neverlands.ru / www.neverlands.ru / 136.243.18.79, и сервер обрывал её страницей
        // «Сеанс работы прерван» (причина «доступ к ресурсам сайта с другого хоста»).
        //
        // Теперь: Host и cookie остаются на публичном домене (единая сессия), а на сервер
        // DE/KZ уходит только TCP-соединение. Нужный игровой мир выбирается полем формы
        // логина server=de / server=KZ (GameServerUrls.loginFormServerCode).
        boolean routeToSelectedGameServer = AppVars.Profile != null
                && GameServerUrls.isNeverlandsGameHost(originHost)
                && !GameServerUrls.isSelectedServerHost(originHost);
        String selectedConnectHost = null;
        if (routeToSelectedGameServer) {
            selectedConnectHost = GameServerUrls.currentConnectHost();
            AppLog.d(LOG_CHAIN, TAG, "SERVER_ROUTE: connect " + originHost + ":" + originPort
                    + " -> " + selectedConnectHost + ":80 (Host остаётся " + originHost + ")");
        }

        String connectHost;
        int connectPort;
        String requestTarget;

        if (upstreamSettings.enabled) {
            connectHost = upstreamSettings.host;
            connectPort = upstreamSettings.port;
            // Через upstream-proxy выбрать конкретный сервер нельзя: DNS резолвит он сам,
            // а подмена хоста в absolute-form вернула бы прежнюю проблему с сессией.
            // Поэтому в upstream-режиме работаем с публичным доменом.
            if (uriToken.startsWith("http://") || uriToken.startsWith("https://")) {
                requestTarget = uriToken;
            } else {
                requestTarget = "http://" + originHost
                        + ((originPort == 80) ? "" : ":" + originPort)
                        + originPath;
            }
        } else {
            connectHost = (selectedConnectHost != null) ? selectedConnectHost : originHost;
            connectPort = (selectedConnectHost != null) ? 80 : originPort;
            requestTarget = originPath;
        }

        return new ResolvedRoute(originHost, originPort, connectHost, connectPort, requestTarget, originPath);
    }

    private HttpRequest readRequest(InputStream in) throws IOException {
        byte[] headerBlock = readHeaderBlock(in);
        if (headerBlock == null || headerBlock.length == 0) {
            return null;
        }
        String headersText = new String(headerBlock, StandardCharsets.ISO_8859_1);
        String[] lines = headersText.split("\r\n");
        if (lines.length == 0) {
            return null;
        }

        String requestLine = lines[0].trim();
        String[] reqParts = requestLine.split(" ");
        if (reqParts.length < 3) {
            return null;
        }

        HttpRequest request = new HttpRequest();
        request.method = reqParts[0].trim();
        request.uriToken = reqParts[1].trim();
        request.httpVersion = reqParts[2].trim();
        request.headers = new LinkedHashMap<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isEmpty()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            request.headers.put(key, value);
        }

        int contentLength = parseContentLength(request.headers);
        if (contentLength > 0) {
            request.body = readBody(in, contentLength);
        } else {
            request.body = new byte[0];
        }
        return request;
    }

    private byte[] readHeaderBlock(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int state = 0;
        while (true) {
            int b = in.read();
            if (b == -1) {
                return baos.size() == 0 ? null : baos.toByteArray();
            }
            baos.write(b);
            if (baos.size() > HEADER_SIZE_LIMIT_BYTES) {
                throw new IOException("Request headers too large");
            }

            if (state == 0 && b == '\r') state = 1;
            else if (state == 1 && b == '\n') state = 2;
            else if (state == 2 && b == '\r') state = 3;
            else if (state == 3 && b == '\n') break;
            else state = 0;
        }
        return baos.toByteArray();
    }

    private byte[] readBody(InputStream in, int contentLength) throws IOException {
        byte[] body = new byte[contentLength];
        int readTotal = 0;
        while (readTotal < contentLength) {
            int read = in.read(body, readTotal, contentLength - readTotal);
            if (read < 0) {
                break;
            }
            readTotal += read;
        }
        if (readTotal == contentLength) {
            return body;
        }
        byte[] actual = new byte[readTotal];
        System.arraycopy(body, 0, actual, 0, readTotal);
        return actual;
    }

    private int parseContentLength(Map<String, String> headers) {
        String value = headers.get("Content-Length");
        if (value == null) {
            value = headers.get("content-length");
        }
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * Удаляет из запроса внутренние маркеры клиента ({@code ab_*} / {@code an_*}).
     *
     * <p><b>Зачем (AGENTS п.4, анти-детект).</b> Клиент дописывает к игровым URL собственные
     * служебные параметры, чтобы потом узнавать свои же запросы в пост-фильтрах:
     * {@code ab_reload_probe}, {@code ab_bg_probe}, {@code ab_nav_bootstrap}, {@code ab_timer},
     * {@code an_auto_cut_tick}, {@code an_auto_cut_cleanup_verify}, {@code an_kazna_action},
     * {@code an_auto_mine_pickaxe}, {@code an_search_box_bootstrap} и другие (всего ~25).</p>
     *
     * <p>Ни один браузер такие параметры не отправляет — в серверных логах это однозначная
     * подпись неофициального клиента, которую тривиально найти обычным grep. В логах
     * {@code logs/Critical/20260726_14_*} наружу реально уходил
     * {@code main.php?get_id=56&act=10&go=inf&ab_reload_probe=1&ts=...}.</p>
     *
     * <p><b>Почему это безопасно.</b> Маркеры нужны только внутренней логике приложения, и она
     * читает их из своего собственного URL (например {@code Filter.process(context, urlString, ...)})
     * <i>до</i> отправки. Здесь мы вырезаем их лишь из строки запроса, уходящей на сервер, —
     * поведение постфильтров не меняется. Игровые параметры ({@code get_id}, {@code act},
     * {@code go}, {@code im}, {@code wca}, {@code vcode}, {@code code}, {@code fexp} и т.д.)
     * префиксов {@code ab_}/{@code an_} не имеют и не затрагиваются.</p>
     *
     * <p>Реализовано в единой точке — через прокси проходит весь игровой трафик, поэтому
     * не требуется править ~25 мест формирования URL (и рисковать регрессиями в авто-функциях).</p>
     *
     * @param requestTarget строка запроса (origin-form или absolute-form)
     * @return та же строка без служебных параметров клиента
     */
    private static String stripClientMarkersFromTarget(String requestTarget) {
        if (requestTarget == null || requestTarget.isEmpty()) {
            return requestTarget;
        }
        int queryStart = requestTarget.indexOf('?');
        if (queryStart < 0 || queryStart == requestTarget.length() - 1) {
            return requestTarget;
        }

        String path = requestTarget.substring(0, queryStart);
        String query = requestTarget.substring(queryStart + 1);

        // Фрагмент в запросе не передаётся, но подстрахуемся.
        String fragment = "";
        int hashPos = query.indexOf('#');
        if (hashPos >= 0) {
            fragment = query.substring(hashPos);
            query = query.substring(0, hashPos);
        }

        StringBuilder kept = new StringBuilder();
        StringBuilder removed = new StringBuilder();
        for (String part : query.split("&")) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            String name = (eq > 0 ? part.substring(0, eq) : part).toLowerCase(Locale.ROOT);
            if (name.startsWith("ab_") || name.startsWith("an_")) {
                if (removed.length() > 0) {
                    removed.append(',');
                }
                removed.append(name);
                continue;
            }
            if (kept.length() > 0) {
                kept.append('&');
            }
            kept.append(part);
        }

        if (removed.length() == 0) {
            return requestTarget;
        }

        String sanitized = kept.length() > 0 ? path + "?" + kept + fragment : path + fragment;
        AppLog.d(LOG_CHAIN, TAG, "PROXY_STEALTH: stripped client markers=[" + removed + "], target=" + sanitized);
        return sanitized;
    }

    /**
     * Заголовки, выдающие Android-WebView вместо обычного браузера.
     *
     * <p>Зачем (AGENTS п.4, анти-детект): администрация проекта блокирует неофициальные клиенты,
     * поэтому сервер не должен видеть ни одного признака приложения. Здесь перечислены
     * заголовки, которые Android добавляет самостоятельно:</p>
     * <ul>
     *   <li>{@code X-Requested-With} — WebView может подставить <b>имя пакета приложения</b>
     *       ({@code ru.neverlands.anclient}); это прямое разоблачение;</li>
     *   <li>{@code Sec-CH-UA*} — client hints реального движка. Они бы сообщили
     *       «Android WebView, Chrome 151», противореча нашему desktop User-Agent, —
     *       рассинхрон сам по себе является признаком подделки;</li>
     *   <li>{@code X-Android-*} — служебные заголовки Android-стека.</li>
     * </ul>
     *
     * <p>Все они вырезаются и заменяются согласованным набором в
     * {@link #appendBrowserIdentityHeaders(StringBuilder)}.</p>
     */
    private static boolean isClientIdentityHeader(String lowerKey) {
        return "user-agent".equals(lowerKey)
                || "x-requested-with".equals(lowerKey)
                || lowerKey.startsWith("sec-ch-ua")
                || lowerKey.startsWith("x-android");
    }

    /**
     * Подставляет единый браузерный {@code User-Agent} вместо родного UA Android-WebView.
     *
     * <p><b>Почему здесь НЕТ client hints ({@code Sec-CH-UA*}).</b> Это проверено по эталонной
     * записи реального трафика {@code Login.har}: Chrome 140 при работе по обычному
     * {@code http://} <b>не отправляет</b> ни одного заголовка {@code Sec-CH-UA*} —
     * client hints передаются только в secure context (HTTPS). Полный набор заголовков
     * настоящего браузера в HAR: {@code accept}, {@code accept-encoding}, {@code accept-language},
     * {@code cache-control}, {@code connection}, {@code content-length}, {@code content-type},
     * {@code dnt}, {@code host}, {@code origin}, {@code referer},
     * {@code upgrade-insecure-requests}, {@code user-agent}.</p>
     *
     * <p>Поэтому добавление {@code Sec-CH-UA} <i>усилило бы</i> детект, а не ослабило:
     * игра работает по cleartext HTTP, и такие заголовки там выглядят чужеродно.
     * Сами заголовки при этом продолжают вырезаться в {@link #isClientIdentityHeader(String)} —
     * на случай, если их пришлёт WebView.</p>
     *
     * <p>Это единая точка нормализации: через локальный прокси проходит весь игровой трафик —
     * и запросы WebView, и нативные запросы клиента.</p>
     */
    private static void appendBrowserIdentityHeaders(StringBuilder outHead) {
        outHead.append("User-Agent: ").append(AppVars.BROWSER_USER_AGENT).append("\r\n");
    }

    private boolean shouldSkipRequestHeader(String lowerKey) {
        return "connection".equals(lowerKey)
                || "proxy-connection".equals(lowerKey)
                || "keep-alive".equals(lowerKey)
                || "proxy-authenticate".equals(lowerKey)
                || "proxy-authorization".equals(lowerKey)
                || "te".equals(lowerKey)
                || "trailers".equals(lowerKey)
                || "transfer-encoding".equals(lowerKey)
                || "upgrade".equals(lowerKey);
    }

    /**
     * Копирует ответ upstream в сокет клиента.
     *
     * Важно про источники ошибок:
     * - `source.read(...)` — это upstream, его сбой = настоящая проблема прокси-цепочки;
     * - `sink.write(...)` — это сокет клиента (все вызовы передают `clientOut`),
     *   его сбой означает, что клиент ушёл (отменённая навигация, закрытый подфрейм).
     * Второй случай оборачивается в {@link ClientAbortException}, чтобы вызывающий код
     * не поднимал ложную тревогу `PROXY_FAIL` на успешно полученном ответе.
     */
    private CopyResult copyStreamWithCapture(InputStream source,
                                             OutputStream sink,
                                             int captureLimitBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0L;
        ByteArrayOutputStream capture = (captureLimitBytes > 0) ? new ByteArrayOutputStream() : null;
        int read;
        while ((read = source.read(buffer)) != -1) {
            try {
                sink.write(buffer, 0, read);
            } catch (IOException e) {
                throw new ClientAbortException("client aborted while receiving response body", e);
            }
            total += read;
            if (capture != null && capture.size() < captureLimitBytes) {
                int remain = captureLimitBytes - capture.size();
                int toWrite = Math.min(remain, read);
                if (toWrite > 0) {
                    capture.write(buffer, 0, toWrite);
                }
            }
        }
        try {
            sink.flush();
        } catch (IOException e) {
            throw new ClientAbortException("client aborted while flushing response body", e);
        }
        return new CopyResult(total, capture == null ? new byte[0] : capture.toByteArray());
    }

    /**
     * Клиент закрыл соединение до того, как прокси дописал ответ.
     *
     * Это НЕ сбой прокси: upstream-ответ уже получен (как правило со `status=200`),
     * а сокет закрыл сам WebView — обычное поведение браузера при отменённой навигации
     * или при загрузке фреймсета, когда часть подзапросов становится ненужной.
     */
    private static final class ClientAbortException extends IOException {
        ClientAbortException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Отличает уход клиента от настоящего сбоя прокси-цепочки.
     *
     * Зачем нужно:
     * - раньше любое исключение сессии поднимало `PROXY_FAIL` и красный индикатор в UI,
     *   хотя в логах рядом стоял успешный `PROXY_RESP ... status=200`;
     * - по замерам такие «сбои» давали ложную тревогу на старте приложения,
     *   когда фреймсет отменяет часть собственных подзапросов.
     *
     * Проверяются оба признака: типизированный {@link ClientAbortException} из основного
     * пути копирования и текст сообщения — на случай записи клиенту вне этого хелпера.
     */
    private static boolean isClientAbort(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof ClientAbortException) {
                return true;
            }
            String message = current.getMessage();
            if (message == null) {
                continue;
            }
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("broken pipe")
                    || lower.contains("connection reset")
                    || lower.contains("connection abort")
                    || lower.contains("socket closed")
                    || lower.contains("stream closed")) {
                return true;
            }
        }
        return false;
    }

    private void handleServerNoticeFromCapturedPayload(ResolvedRoute route,
                                                       HttpRequest request,
                                                       String requestTarget,
                                                       ResponseHead responseHead,
                                                       byte[] capturedBodyBytes) {
        try {
            if (!shouldInspectServerNotice(route, request, responseHead, capturedBodyBytes)) {
                return;
            }
            String noticeText = extractServerNoticeTextFromHtml(capturedBodyBytes);
            if (noticeText.isEmpty()) {
                return;
            }
            MainPhp.postServerNotificationToChat(
                    noticeText,
                    "proxy_post_response",
                    requestTarget
            );
            String msg = "PROXY_NOTICE: method=" + request.method
                    + ", target=" + requestTarget
                    + ", text=" + safeValue(noticeText);
            AppLog.d("proxy_notice", TAG, msg);
        } catch (Exception e) {
            FileLogger.error("proxy_notice", "handleServerNoticeFromCapturedPayload failed", e);
        }
    }

    private boolean shouldInspectServerNotice(ResolvedRoute route,
                                              HttpRequest request,
                                              ResponseHead responseHead,
                                              byte[] capturedBodyBytes) {
        if (route == null || request == null || responseHead == null || capturedBodyBytes == null) {
            return false;
        }
        if (!"POST".equalsIgnoreCase(request.method)) {
            return false;
        }
        if (responseHead.statusCode <= 0 || responseHead.statusCode >= 400) {
            return false;
        }
        if (capturedBodyBytes.length == 0) {
            return false;
        }
        String host = route.originHost == null ? "" : route.originHost.toLowerCase(Locale.ROOT);
        if (!GameServerUrls.isNeverlandsGameHost(host)) {
            return false;
        }
        String contentType = responseHead.contentTypeHeader == null
                ? ""
                : responseHead.contentTypeHeader.toLowerCase(Locale.ROOT);
        return contentType.isEmpty()
                || contentType.contains("text/html")
                || contentType.contains("application/xhtml");
    }

    private String extractServerNoticeTextFromHtml(byte[] bodyBytes) {
        if (bodyBytes == null || bodyBytes.length == 0) {
            return "";
        }
        String html = decodeHtmlBody(bodyBytes);
        if (html.isEmpty()) {
            return "";
        }

        // Reuse centralized main.php notice parser to keep behavior consistent across paths.
        String centralNotice = MainPhp.extractServerNoticeForUi(html);
        if (centralNotice != null && !centralNotice.trim().isEmpty()) {
            return centralNotice.trim();
        }

        Matcher boldRed = Pattern.compile(
                "(?is)<font\\s+class\\s*=\\s*['\\\"]?nickname['\\\"]?[^>]*>\\s*<font[^>]*color\\s*=\\s*['\\\"]?#?cc0000['\\\"]?[^>]*>\\s*<b>(.*?)<br\\s*/?>\\s*<br\\s*/?>\\s*</b>\\s*</font>\\s*</font>")
                .matcher(html);
        if (boldRed.find()) {
            return normalizeNoticeText(boldRed.group(1));
        }

        Matcher alert = Pattern.compile("(?is)alert\\s*\\(\\s*['\\\"](.*?)['\\\"]\\s*\\)")
                .matcher(html);
        if (alert.find()) {
            return normalizeNoticeText(alert.group(1));
        }

        String normalizedHtml = normalizeNoticeText(html);
        String lower = normalizedHtml.toLowerCase(Locale.ROOT);
        if (lower.contains("\u043f\u043e\u0437\u0434\u0440\u0430\u0432\u043b\u044f")
                && (lower.contains("\u0443\u0441\u043f\u0435\u0448")
                || lower.contains("\u0432\u0441\u0451 \u0443\u0441\u043f\u0435\u0448"))) {
            return "\u041f\u043e\u0437\u0434\u0440\u0430\u0432\u043b\u044f\u0435\u043c, \u0432\u0441\u0451 \u0443\u0441\u043f\u0435\u0448\u043d\u043e.";
        }
        if (lower.contains("поздравля") && lower.contains("успеш")) {
            return "Поздравляем, всё успешно.";
        }
        return "";
    }

    private String decodeHtmlBody(byte[] bodyBytes) {
        byte[] payload = maybeInflateGzipBody(bodyBytes);
        try {
            return new String(payload, Charset.forName("windows-1251"));
        } catch (Exception ignored) {
            // Ожидаемая ветка: charset недоступен/битые байты — ниже пробуем UTF-8.
        }
        try {
            return new String(payload, StandardCharsets.UTF_8);
        } catch (Exception e) {
            AppLog.d(LOG_CHAIN, TAG, "decodeHtmlBody: both windows-1251 and UTF-8 decode failed: " + e.getClass().getSimpleName());
        }
        return "";
    }

    private byte[] maybeInflateGzipBody(byte[] rawBody) {
        if (rawBody == null || rawBody.length < 2) {
            return rawBody == null ? new byte[0] : rawBody;
        }
        boolean gzipMagic = (rawBody[0] == (byte) 0x1F) && (rawBody[1] == (byte) 0x8B);
        if (!gzipMagic) {
            return rawBody;
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(rawBody);
             GZIPInputStream gzipInputStream = new GZIPInputStream(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = gzipInputStream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (Exception ignored) {
            return rawBody;
        }
    }

    private String normalizeNoticeText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text
                .replace('\u00A0', ' ')
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() > 700) {
            normalized = normalized.substring(0, 700) + "...";
        }
        return normalized;
    }

    private int parsePortSafe(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void writeSimpleError(OutputStream out, int code, String reason, String bodyText) {
        try {
            byte[] body = bodyText.getBytes(StandardCharsets.UTF_8);
            String head = "HTTP/1.1 " + code + " " + reason + "\r\n"
                    + "Content-Type: text/plain; charset=utf-8\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.ISO_8859_1));
            out.write(body);
            out.flush();
        } catch (IOException ignored) {
            // Ожидаемая ветка: сокет клиента уже закрыт, error-ответ доставить некуда.
        }
    }

    private void closeQuietly(ServerSocket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // Ожидаемая ветка cleanup: серверный сокет уже закрыт при остановке прокси.
        }
    }

    private void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // Ожидаемая ветка cleanup: клиентский сокет уже закрыт/оборван.
        }
    }

    private static final class HttpRequest {
        String method;
        String uriToken;
        String httpVersion;
        Map<String, String> headers;
        byte[] body;
    }

    private static final class ResolvedRoute {
        final String originHost;
        final int originPort;
        final String connectHost;
        final int connectPort;
        final String requestTarget;
        final String originPath;

        ResolvedRoute(String originHost,
                      int originPort,
                      String connectHost,
                      int connectPort,
                      String requestTarget,
                      String originPath) {
            this.originHost = originHost;
            this.originPort = originPort;
            this.connectHost = connectHost;
            this.connectPort = connectPort;
            this.requestTarget = requestTarget;
            this.originPath = originPath;
        }
    }

    private static final class CopyResult {
        final long totalBytes;
        final byte[] capturedBytes;

        CopyResult(long totalBytes, byte[] capturedBytes) {
            this.totalBytes = totalBytes;
            this.capturedBytes = capturedBytes == null ? new byte[0] : capturedBytes;
        }
    }

    private static final class ResponseHead {
        final byte[] rawBytes;
        final String statusLine;
        final int statusCode;
        final String serverHeader;
        final String contentTypeHeader;

        ResponseHead(byte[] rawBytes,
                     String statusLine,
                     int statusCode,
                     String serverHeader,
                     String contentTypeHeader) {
            this.rawBytes = rawBytes;
            this.statusLine = statusLine == null ? "" : statusLine;
            this.statusCode = statusCode;
            this.serverHeader = serverHeader == null ? "" : serverHeader;
            this.contentTypeHeader = contentTypeHeader == null ? "" : contentTypeHeader;
        }
    }
}

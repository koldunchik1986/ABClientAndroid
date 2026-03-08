package ru.neverlands.abclient.proxy;

import android.util.Log;

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
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final int MAX_BIND_ATTEMPTS = 64;
    private static final int HEADER_SIZE_LIMIT_BYTES = 64 * 1024;
    private static final int SOCKET_TIMEOUT_MS = 20_000;
    private static final int LOG_DEDUP_WINDOW_MS = 5_000;

    private final int startPort;
    private final ProxyRuntimeManager.ProxyUpstreamSettings upstreamSettings;

    private volatile boolean running = false;
    private volatile int boundPort = -1;
    private ServerSocket serverSocket;
    private ExecutorService acceptExecutor;
    private ExecutorService workerExecutor;

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
        this.workerExecutor = Executors.newCachedThreadPool();

        acceptExecutor.execute(this::acceptLoop);
        Log.i(TAG, "PROXY_BOOT: listener started at 127.0.0.1:" + boundPort);
        Log.i(TAG, "PROXY_UPSTREAM: enabled=" + upstreamSettings.enabled
                + ", host=" + upstreamSettings.host
                + ", port=" + upstreamSettings.port);
        Log.i(TAG, "PROXY_AUTH: upstream basic auth enabled="
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
        boundPort = -1;
        Log.i(TAG, "PROXY_BOOT: listener stopped");
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
                Log.d(TAG, "PROXY_SESSION: accepted client=" + client.getRemoteSocketAddress());
                if (workerExecutor != null) {
                    workerExecutor.execute(() -> handleClient(client));
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
            Log.d(TAG, "PROXY_SESSION: method=" + request.method
                    + ", uri=" + request.uriToken
                    + ", bodyBytes=" + (request.body == null ? 0 : request.body.length));

            if ("CONNECT".equalsIgnoreCase(request.method)) {
                writeSimpleError(clientOut, 501, "Not Implemented", "CONNECT is not supported");
                return;
            }

            ResolvedRoute route = resolveRoute(request);
            Log.d(TAG, "PROXY_SESSION: route origin=" + route.originHost + ":" + route.originPort
                    + ", connect=" + route.connectHost + ":" + route.connectPort
                    + ", target=" + route.requestTarget);
            sessionTarget = route.originHost + ":" + route.originPort;
            long copiedBytes = forwardRequest(route, request, clientOut);
            long elapsed = Math.max(0L, System.currentTimeMillis() - startedAtMs);
            Log.d(TAG, "PROXY_SESSION: target=" + sessionTarget
                    + " mode=" + (upstreamSettings.enabled ? "UPSTREAM" : "DIRECT")
                    + " bytesOut=" + copiedBytes
                    + " latencyMs=" + elapsed);
        } catch (Exception e) {
            ProxyLogDeduper.warn(
                    TAG,
                    "session_failed:" + sessionTarget,
                    "PROXY_FAIL: session failed target=" + sessionTarget,
                    e,
                    LOG_DEDUP_WINDOW_MS
            );
            try {
                OutputStream fallbackOut = client.getOutputStream();
                writeSimpleError(fallbackOut, 502, "Bad Gateway", "Proxy forwarding error");
            } catch (Throwable ignored) {
            }
        }
    }

    private long forwardRequest(ResolvedRoute route, HttpRequest request, OutputStream clientOut) throws IOException {
        try (Socket remote = new Socket()) {
            remote.setTcpNoDelay(true);
            remote.setSoTimeout(SOCKET_TIMEOUT_MS);
            remote.connect(new InetSocketAddress(route.connectHost, route.connectPort), SOCKET_TIMEOUT_MS);

            OutputStream remoteOut = remote.getOutputStream();
            InputStream remoteIn = remote.getInputStream();

            StringBuilder outHead = new StringBuilder();
            outHead.append(request.method)
                    .append(' ')
                    .append(route.requestTarget)
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
                outHead.append(key).append(": ").append(header.getValue()).append("\r\n");
            }

            if (!hasHostHeader) {
                outHead.append("Host: ").append(route.originHost);
                if (route.originPort != 80) {
                    outHead.append(':').append(route.originPort);
                }
                outHead.append("\r\n");
            }
            outHead.append("Connection: close\r\n");

            if (upstreamSettings.enabled && upstreamSettings.basicAuthHeader != null && !upstreamSettings.basicAuthHeader.isEmpty()) {
                outHead.append("Proxy-Authorization: ").append(upstreamSettings.basicAuthHeader).append("\r\n");
            }

            outHead.append("\r\n");
            remoteOut.write(outHead.toString().getBytes(StandardCharsets.ISO_8859_1));
            if (request.body != null && request.body.length > 0) {
                remoteOut.write(request.body);
            }
            remoteOut.flush();

            return copyStream(remoteIn, clientOut);
        }
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

        String connectHost;
        int connectPort;
        String requestTarget;

        if (upstreamSettings.enabled) {
            connectHost = upstreamSettings.host;
            connectPort = upstreamSettings.port;
            if (uriToken.startsWith("http://") || uriToken.startsWith("https://")) {
                requestTarget = uriToken;
            } else {
                requestTarget = "http://" + originHost
                        + ((originPort == 80) ? "" : ":" + originPort)
                        + originPath;
            }
        } else {
            connectHost = originHost;
            connectPort = originPort;
            requestTarget = originPath;
        }

        return new ResolvedRoute(originHost, originPort, connectHost, connectPort, requestTarget);
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

    private long copyStream(InputStream source, OutputStream sink) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = source.read(buffer)) != -1) {
            sink.write(buffer, 0, read);
            total += read;
        }
        sink.flush();
        return total;
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
        }
    }

    private void closeQuietly(ServerSocket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
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

        ResolvedRoute(String originHost, int originPort, String connectHost, int connectPort, String requestTarget) {
            this.originHost = originHost;
            this.originPort = originPort;
            this.connectHost = connectHost;
            this.connectPort = connectPort;
            this.requestTarget = requestTarget;
        }
    }
}

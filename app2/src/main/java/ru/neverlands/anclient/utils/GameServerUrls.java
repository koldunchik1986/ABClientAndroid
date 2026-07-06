package ru.neverlands.anclient.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GameServerUrls {
    public static final String SERVER_DE = "DE";
    public static final String SERVER_KZ = "KZ";
    public static final String SERVER_NEVERLANDS_RU = "neverlands.ru";
    public static final String DEFAULT_SERVER_CODE = SERVER_DE;
    public static final String SERVER_DE_IP = "136.243.18.79";
    public static final String SERVER_KZ_IP = "213.148.10.84";
    public static final int SERVER_PING_PORT = 80;

    private static final String HOST_NEVERLANDS = "neverlands.ru";
    private static final String HOST_WWW_NEVERLANDS = "www.neverlands.ru";
    private static final String PREFS_NAME = "game_server_urls";
    private static final String KEY_SERVER_ENTRIES = "server_entries";
    private static final Object LOCK = new Object();

    private static List<ServerEntry> serverEntries = defaultServerEntries();
    private static boolean initialized;

    private GameServerUrls() {
    }

    public static final class ServerEntry {
        public final String code;
        public final String host;
        public final String loginFormServerCode;
        public final String title;

        private ServerEntry(String code, String host, String loginFormServerCode, String title) {
            this.code = normalizeEntryCode(code);
            this.host = normalizeHost(host);
            this.loginFormServerCode = loginFormServerCode == null ? "" : loginFormServerCode.trim();
            String safeTitle = title == null ? "" : title.trim();
            this.title = safeTitle.isEmpty() ? this.code : safeTitle;
        }

        public String displayLabel() {
            return title == null || title.trim().isEmpty() ? code : title;
        }
    }

    public static void initialize(Context context) {
        synchronized (LOCK) {
            if (context == null) {
                serverEntries = defaultServerEntries();
                initialized = true;
                return;
            }
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String raw = prefs.getString(KEY_SERVER_ENTRIES, "");
            if (raw == null || raw.trim().isEmpty()) {
                serverEntries = defaultServerEntries();
                prefs.edit().putString(KEY_SERVER_ENTRIES, editableTextForEntries(serverEntries)).apply();
            } else {
                try {
                    serverEntries = parseEditableServerListInternal(raw);
                } catch (IllegalArgumentException ignored) {
                    serverEntries = defaultServerEntries();
                }
            }
            initialized = true;
        }
    }

    public static List<ServerEntry> serverEntries() {
        synchronized (LOCK) {
            ensureInitializedLocked();
            return Collections.unmodifiableList(new ArrayList<>(serverEntries));
        }
    }

    public static String editableServerListText() {
        synchronized (LOCK) {
            ensureInitializedLocked();
            return editableTextForEntries(serverEntries);
        }
    }

    public static void saveEditableServerList(Context context, String editableText) {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        List<ServerEntry> parsed = parseEditableServerListInternal(editableText);
        synchronized (LOCK) {
            serverEntries = parsed;
            initialized = true;
            context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_SERVER_ENTRIES, editableTextForEntries(serverEntries))
                    .apply();
        }
    }

    public static void resetServerList(Context context) {
        synchronized (LOCK) {
            serverEntries = defaultServerEntries();
            initialized = true;
            if (context != null) {
                context.getApplicationContext()
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_SERVER_ENTRIES, editableTextForEntries(serverEntries))
                        .apply();
            }
        }
    }

    public static String normalizeServerCode(String serverCode) {
        String value = serverCode == null ? "" : serverCode.trim();
        if (value.isEmpty()) {
            return DEFAULT_SERVER_CODE;
        }
        String normalized = normalizeEntryCode(value);
        synchronized (LOCK) {
            ensureInitializedLocked();
            for (ServerEntry entry : serverEntries) {
                if (entry.code.equalsIgnoreCase(normalized)
                        || entry.host.equalsIgnoreCase(normalizeHost(value))) {
                    return entry.code;
                }
            }
        }
        if (SERVER_KZ.equals(normalized)) {
            return SERVER_KZ;
        }
        if (SERVER_DE.equals(normalized)) {
            return SERVER_DE;
        }
        if (SERVER_NEVERLANDS_RU.equalsIgnoreCase(normalized)) {
            return SERVER_NEVERLANDS_RU;
        }
        return DEFAULT_SERVER_CODE;
    }

    public static String loginFormServerCode(String serverCode) {
        return serverEntry(serverCode).loginFormServerCode;
    }

    public static String[] displayNames() {
        List<ServerEntry> entries = serverEntries();
        String[] result = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            result[i] = displayName(entries.get(i).code, null);
        }
        return result;
    }

    public static int displayIndex(String serverCode) {
        String normalized = normalizeServerCode(serverCode);
        List<ServerEntry> entries = serverEntries();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).code.equalsIgnoreCase(normalized)) {
                return i;
            }
        }
        return 0;
    }

    public static String codeForDisplayValue(String displayValue) {
        String raw = displayValue == null ? "" : displayValue.trim();
        if (raw.isEmpty()) {
            return DEFAULT_SERVER_CODE;
        }
        String normalized = raw.toUpperCase(Locale.ROOT);
        synchronized (LOCK) {
            ensureInitializedLocked();
            for (ServerEntry entry : serverEntries) {
                String codeUpper = entry.code.toUpperCase(Locale.ROOT);
                String labelUpper = entry.displayLabel().toUpperCase(Locale.ROOT);
                if (normalized.equals(codeUpper)
                        || normalized.equals(entry.host.toUpperCase(Locale.ROOT))
                        || normalized.startsWith(codeUpper + " (")
                        || normalized.startsWith(labelUpper + " (")
                        || normalized.contains("(" + entry.host.toUpperCase(Locale.ROOT) + ")")) {
                    return entry.code;
                }
            }
        }
        return normalizeServerCode(raw);
    }

    public static String serverIp(String serverCode) {
        return serverHost(serverCode);
    }

    public static String serverHost(String serverCode) {
        return serverEntry(serverCode).host;
    }

    public static boolean isSelectedServerHost(String host) {
        String lowerHost = host == null ? "" : host.toLowerCase(Locale.ROOT).trim();
        return lowerHost.equals(serverHost(currentServerCode()).toLowerCase(Locale.ROOT));
    }

    public static boolean isConfiguredServerHost(String host) {
        String lowerHost = host == null ? "" : host.toLowerCase(Locale.ROOT).trim();
        if (lowerHost.isEmpty()) {
            return false;
        }
        synchronized (LOCK) {
            ensureInitializedLocked();
            for (ServerEntry entry : serverEntries) {
                if (entry.host.equalsIgnoreCase(lowerHost)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String gameBaseUrl(String serverCode) {
        return "http://" + serverHost(serverCode);
    }

    public static String currentGameBaseUrl() {
        return gameBaseUrl(currentServerCode());
    }

    public static String displayName(String serverCode, Long pingMs) {
        ServerEntry entry = serverEntry(serverCode);
        String pingText;
        if (pingMs == null) {
            pingText = "... ms";
        } else if (pingMs < 0L) {
            pingText = "timeout";
        } else {
            pingText = pingMs + " ms";
        }
        return entry.displayLabel() + " (" + entry.host + ") - " + pingText;
    }

    public static long measureTcpPingMs(String serverCode, int timeoutMs) {
        String ip = serverHost(serverCode);
        long startedAt = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, SERVER_PING_PORT), Math.max(1, timeoutMs));
            return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        } catch (IOException e) {
            return -1L;
        }
    }

    public static String currentServerCode() {
        return AppVars.Profile == null ? DEFAULT_SERVER_CODE : normalizeServerCode(AppVars.Profile.GameServerCode);
    }

    public static String authBaseUrl(boolean proxyActive, String serverCode) {
        return gameBaseUrl(serverCode);
    }

    public static String alternateAuthBaseUrl(String currentBaseUrl) {
        try {
            URI uri = URI.create(currentBaseUrl == null ? "" : currentBaseUrl);
            String host = uri.getHost();
            if (HOST_WWW_NEVERLANDS.equalsIgnoreCase(host)) {
                return "http://" + HOST_NEVERLANDS;
            }
            if (HOST_NEVERLANDS.equalsIgnoreCase(host)) {
                return "http://" + HOST_WWW_NEVERLANDS;
            }
        } catch (Exception ignored) {
            return currentBaseUrl;
        }
        return currentBaseUrl;
    }

    public static String currentGameUrl(String pathOrQuery) {
        return gameUrl(currentServerCode(), pathOrQuery);
    }

    public static String gameUrl(String serverCode, String pathOrQuery) {
        String normalizedPath = normalizePath(pathOrQuery);
        if (isAbsoluteUrl(normalizedPath)) {
            return routeUrlToServer(serverCode, normalizedPath);
        }
        return gameBaseUrl(serverCode) + normalizedPath;
    }

    public static String wwwGameUrl(String serverCode, String pathOrQuery) {
        return gameUrl(serverCode, pathOrQuery);
    }

    public static String neverlandsCookieUrl() {
        return currentGameUrl("/");
    }

    public static String wwwNeverlandsCookieUrl() {
        return "http://" + HOST_WWW_NEVERLANDS + "/";
    }

    public static String canonicalNeverlandsCookieUrl() {
        return "http://" + HOST_NEVERLANDS + "/";
    }

    public static List<String> cookieUrls() {
        List<String> urls = new ArrayList<>();
        addUniqueUrl(urls, neverlandsCookieUrl());
        addUniqueUrl(urls, canonicalNeverlandsCookieUrl());
        addUniqueUrl(urls, wwwNeverlandsCookieUrl());
        return Collections.unmodifiableList(urls);
    }

    public static boolean isNeverlandsCookieDomain(String domain) {
        String lowerDomain = domain == null ? "" : domain.toLowerCase(Locale.ROOT);
        return lowerDomain.contains(HOST_NEVERLANDS)
                || isConfiguredServerHost(lowerDomain);
    }

    public static boolean isNeverlandsHost(String host) {
        String lowerHost = host == null ? "" : host.toLowerCase(Locale.ROOT).trim();
        return lowerHost.equals(HOST_NEVERLANDS)
                || lowerHost.endsWith("." + HOST_NEVERLANDS)
                || isConfiguredServerHost(lowerHost);
    }

    public static boolean isNeverlandsGameHost(String host) {
        String lowerHost = host == null ? "" : host.toLowerCase(Locale.ROOT).trim();
        return lowerHost.equals(HOST_NEVERLANDS)
                || lowerHost.equals(HOST_WWW_NEVERLANDS)
                || isConfiguredServerHost(lowerHost);
    }

    public static String routeUrlToCurrentServer(String rawUrl) {
        return routeUrlToServer(currentServerCode(), rawUrl);
    }

    public static String routeUrlToServer(String serverCode, String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return gameUrl(serverCode, "/");
        }
        String value = rawUrl.trim();
        if (!isAbsoluteUrl(value)) {
            return gameUrl(serverCode, value);
        }
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!isNeverlandsGameHost(host)) {
                return value;
            }
            String rawPath = uri.getRawPath();
            String rawQuery = uri.getRawQuery();
            String rawFragment = uri.getRawFragment();
            StringBuilder result = new StringBuilder(gameBaseUrl(serverCode));
            result.append(rawPath == null || rawPath.isEmpty() ? "/" : rawPath);
            if (rawQuery != null && !rawQuery.isEmpty()) {
                result.append('?').append(rawQuery);
            }
            if (rawFragment != null && !rawFragment.isEmpty()) {
                result.append('#').append(rawFragment);
            }
            return result.toString();
        } catch (Exception ignored) {
            return value;
        }
    }

    public static String normalizeNeverlandsUrlForCompare(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) {
            return "";
        }
        try {
            String value = rawUrl.replaceFirst("^https://", "http://");
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!isNeverlandsGameHost(host)) {
                return value.replaceFirst("^http://www\\.neverlands\\.ru", "http://neverlands.ru");
            }
            String rawPath = uri.getRawPath();
            String rawQuery = uri.getRawQuery();
            StringBuilder result = new StringBuilder("http://").append(HOST_NEVERLANDS);
            result.append(rawPath == null || rawPath.isEmpty() ? "/" : rawPath);
            if (rawQuery != null && !rawQuery.isEmpty()) {
                result.append('?').append(rawQuery);
            }
            return result.toString();
        } catch (Exception ignored) {
            return rawUrl.replaceFirst("^https://", "http://")
                    .replaceFirst("^http://www\\.neverlands\\.ru", "http://neverlands.ru");
        }
    }

    private static String normalizePath(String pathOrQuery) {
        String value = pathOrQuery == null ? "/" : pathOrQuery.trim();
        if (value.isEmpty()) {
            return "/";
        }
        if (isAbsoluteUrl(value)) {
            return value;
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    private static boolean isAbsoluteUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private static void addUniqueUrl(List<String> urls, String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        for (String existing : urls) {
            if (existing.equalsIgnoreCase(url)) {
                return;
            }
        }
        urls.add(url);
    }

    private static ServerEntry serverEntry(String serverCode) {
        String normalized = serverCode == null ? "" : normalizeEntryCode(serverCode);
        synchronized (LOCK) {
            ensureInitializedLocked();
            for (ServerEntry entry : serverEntries) {
                if (entry.code.equalsIgnoreCase(normalized)
                        || entry.host.equalsIgnoreCase(normalizeHost(serverCode))) {
                    return entry;
                }
            }
            return serverEntries.get(0);
        }
    }

    private static void ensureInitializedLocked() {
        if (!initialized || serverEntries == null || serverEntries.isEmpty()) {
            serverEntries = defaultServerEntries();
            initialized = true;
        }
    }

    private static List<ServerEntry> defaultServerEntries() {
        List<ServerEntry> defaults = new ArrayList<>();
        defaults.add(new ServerEntry(SERVER_DE, SERVER_DE_IP, "de", SERVER_DE));
        defaults.add(new ServerEntry(SERVER_KZ, SERVER_KZ_IP, "KZ", SERVER_KZ));
        defaults.add(new ServerEntry(SERVER_NEVERLANDS_RU, HOST_NEVERLANDS, "", SERVER_NEVERLANDS_RU));
        return defaults;
    }

    private static List<ServerEntry> parseEditableServerListInternal(String editableText) {
        List<ServerEntry> parsed = new ArrayList<>();
        Set<String> usedCodes = new LinkedHashSet<>();
        String[] lines = (editableText == null ? "" : editableText).split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0 || eq >= line.length() - 1) {
                throw new IllegalArgumentException("Line " + (i + 1) + ": expected code=host");
            }
            String code = normalizeEntryCode(line.substring(0, eq));
            if (!isValidCode(code)) {
                throw new IllegalArgumentException("Line " + (i + 1) + ": invalid server code");
            }
            String[] parts = line.substring(eq + 1).split("\\|", -1);
            String host = normalizeHost(parts.length > 0 ? parts[0] : "");
            if (host.isEmpty()) {
                throw new IllegalArgumentException("Line " + (i + 1) + ": host is empty");
            }
            String codeKey = code.toLowerCase(Locale.ROOT);
            if (!usedCodes.add(codeKey)) {
                throw new IllegalArgumentException("Line " + (i + 1) + ": duplicate server code " + code);
            }
            String formCode = parts.length > 1 ? parts[1].trim() : inferLoginFormServerCode(code);
            String title = parts.length > 2 ? parts[2].trim() : code;
            parsed.add(new ServerEntry(code, host, formCode, title));
        }
        return ensureRequiredDefaults(parsed);
    }

    private static List<ServerEntry> ensureRequiredDefaults(List<ServerEntry> parsed) {
        List<ServerEntry> result = new ArrayList<>(parsed == null ? Collections.emptyList() : parsed);
        addDefaultIfMissing(result, SERVER_DE);
        addDefaultIfMissing(result, SERVER_KZ);
        addDefaultIfMissing(result, SERVER_NEVERLANDS_RU);
        return result;
    }

    private static void addDefaultIfMissing(List<ServerEntry> entries, String code) {
        for (ServerEntry entry : entries) {
            if (entry.code.equalsIgnoreCase(code)) {
                return;
            }
        }
        for (ServerEntry fallback : defaultServerEntries()) {
            if (fallback.code.equalsIgnoreCase(code)) {
                entries.add(fallback);
                return;
            }
        }
    }

    private static String editableTextForEntries(List<ServerEntry> entries) {
        StringBuilder builder = new StringBuilder();
        for (ServerEntry entry : entries) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(entry.code).append('=').append(entry.host);
            if (!entry.loginFormServerCode.isEmpty()
                    || !entry.displayLabel().equals(entry.code)) {
                builder.append('|').append(entry.loginFormServerCode);
            }
            if (!entry.displayLabel().equals(entry.code)) {
                builder.append('|').append(entry.displayLabel());
            }
        }
        return builder.toString();
    }

    private static String inferLoginFormServerCode(String code) {
        String normalized = normalizeEntryCode(code);
        if (SERVER_DE.equals(normalized)) {
            return "de";
        }
        if (SERVER_KZ.equals(normalized)) {
            return "KZ";
        }
        return "";
    }

    private static boolean isValidCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        return !code.contains("=") && !code.contains("|") && !code.matches(".*\\s+.*");
    }

    private static String normalizeEntryCode(String code) {
        String value = code == null ? "" : code.trim();
        if (SERVER_DE.equalsIgnoreCase(value)) {
            return SERVER_DE;
        }
        if (SERVER_KZ.equalsIgnoreCase(value)) {
            return SERVER_KZ;
        }
        if (SERVER_NEVERLANDS_RU.equalsIgnoreCase(value)) {
            return SERVER_NEVERLANDS_RU;
        }
        return value;
    }

    private static String normalizeHost(String host) {
        String value = host == null ? "" : host.trim();
        if (value.isEmpty()) {
            return "";
        }
        value = value.replaceFirst("(?i)^https?://", "");
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        int colon = value.lastIndexOf(':');
        if (colon > 0 && value.indexOf(':') == colon) {
            value = value.substring(0, colon);
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

package ru.neverlands.abclient.manager;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import ru.neverlands.abclient.repository.ApiRepository;
import ru.neverlands.abclient.proxy.ProxyRuntimeManager;
import ru.neverlands.abclient.utils.AppVars;

/**
 * Единый менеджер данных кланов/клановых войн.
 *
 * Назначение:
 * - централизовать загрузку `clans.txt` и `wars.cgi`;
 * - хранить кеш текущих войн для UI и Auto-Босс;
 * - не дублировать сетевую и парсинговую логику в Activity/менеджерах.
 */
public final class ClanWarsManager {
    private static final String TAG = "ClanWarsManager";
    private static final String TRACE_PREFIX = "CLAN_WARS_TRACE";

    private static final String CLANS_URL = "http://service.neverlands.ru/info/clans.txt";
    private static final String WARS_URL = "http://neverlands.ru/modules/api/wars.cgi";

    private static final String INFO_DIR = "info";
    private static final String CLANS_FILE_NAME = "clans.txt";
    private static final String WARS_FILE_NAME = "wars.txt";
    private static final int WARS_SYNC_MAX_ATTEMPTS = 3;
    private static final long WARS_SYNC_RETRY_DELAY_BASE_MS = 1_000L;

    private static final String DATE_PATTERN = "dd.MM.yyyy HH:mm:ss";
    private static final TimeZone KIEV_TIME_ZONE = TimeZone.getTimeZone("Europe/Kiev");

    private static volatile ClanWarsManager instance;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final ArrayList<WarEntry> cachedWars = new ArrayList<>();
    private final ArrayList<ApiRepository.ApiCallback<List<WarEntry>>> pendingWarsSyncCallbacks = new ArrayList<>();
    private long lastWarsSyncAtMs = 0L;
    private boolean warsSyncInProgress = false;

    public static final class WarEntry {
        public final String clanTokenX;
        public final String clanNameX;
        public final int inclinationX;
        public final int clanLevelX;
        public final String clanTokenY;
        public final String clanNameY;
        public final int inclinationY;
        public final int clanLevelY;
        public final String warStatus;
        public final int warScoreX;
        public final int warScoreY;
        public final long startUnixSec;
        public final long stopUnixSec;

        public WarEntry(
                String clanTokenX,
                String clanNameX,
                int inclinationX,
                int clanLevelX,
                String clanTokenY,
                String clanNameY,
                int inclinationY,
                int clanLevelY,
                String warStatus,
                int warScoreX,
                int warScoreY,
                long startUnixSec,
                long stopUnixSec) {
            this.clanTokenX = clanTokenX;
            this.clanNameX = clanNameX;
            this.inclinationX = inclinationX;
            this.clanLevelX = clanLevelX;
            this.clanTokenY = clanTokenY;
            this.clanNameY = clanNameY;
            this.inclinationY = inclinationY;
            this.clanLevelY = clanLevelY;
            this.warStatus = warStatus;
            this.warScoreX = warScoreX;
            this.warScoreY = warScoreY;
            this.startUnixSec = startUnixSec;
            this.stopUnixSec = stopUnixSec;
        }
    }

    public static final class WarTableRow {
        public final String startText;
        public final String aggressorInclinationIconUrl;
        public final String aggressorClanIconUrl;
        public final String aggressorText;
        public final String score1Text;
        public final String score2Text;
        public final String opponentInclinationIconUrl;
        public final String opponentClanIconUrl;
        public final String opponentText;
        public final String endText;

        public WarTableRow(
                String startText,
                String aggressorInclinationIconUrl,
                String aggressorClanIconUrl,
                String aggressorText,
                String score1Text,
                String score2Text,
                String opponentInclinationIconUrl,
                String opponentClanIconUrl,
                String opponentText,
                String endText) {
            this.startText = startText;
            this.aggressorInclinationIconUrl = aggressorInclinationIconUrl;
            this.aggressorClanIconUrl = aggressorClanIconUrl;
            this.aggressorText = aggressorText;
            this.score1Text = score1Text;
            this.score2Text = score2Text;
            this.opponentInclinationIconUrl = opponentInclinationIconUrl;
            this.opponentClanIconUrl = opponentClanIconUrl;
            this.opponentText = opponentText;
            this.endText = endText;
        }
    }

    private ClanWarsManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static ClanWarsManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ClanWarsManager.class) {
                if (instance == null) {
                    instance = new ClanWarsManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Общий sync clans.txt.
     * Используется и в Контактах, и в модуле Кланы.
     */
    public void syncClanListAsync(ApiRepository.ApiCallback<String> callback) {
        if (!ensureProxyReadyForBackgroundSync(callback)) {
            return;
        }
        File destinationFile = getClansFile();
        ApiRepository.downloadFile(CLANS_URL, destinationFile, new ApiRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                Log.i(TAG, TRACE_PREFIX + " clans sync ok: file=" + destinationFile.getAbsolutePath());
                if (callback != null) {
                    callback.onSuccess(result);
                }
            }

            @Override
            public void onFailure(String message) {
                Log.w(TAG, TRACE_PREFIX + " clans sync failed: " + message);
                if (callback != null) {
                    callback.onFailure(message);
                }
            }
        });
    }

    /**
     * Sync текущих войн из wars.cgi + обновление in-memory кеша.
     */
    public void syncWarsAsync(ApiRepository.ApiCallback<List<WarEntry>> callback) {
        if (!ensureProxyReadyForBackgroundSync(callback)) {
            return;
        }
        synchronized (lock) {
            if (callback != null) {
                pendingWarsSyncCallbacks.add(callback);
            }
            if (warsSyncInProgress) {
                Log.d(TAG, TRACE_PREFIX + " wars sync join in-flight request");
                return;
            }
            warsSyncInProgress = true;
        }

        File destinationFile = getWarsFile();
        syncWarsAttempt(destinationFile, 1);
    }

    private void syncWarsAttempt(File destinationFile, int attempt) {
        String requestUrl = WARS_URL + (WARS_URL.contains("?") ? "&" : "?") + "_ts=" + System.currentTimeMillis();
        ApiRepository.downloadFile(requestUrl, destinationFile, new ApiRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                List<WarEntry> parsed = parseWarsFromFile(destinationFile);
                synchronized (lock) {
                    cachedWars.clear();
                    cachedWars.addAll(parsed);
                    lastWarsSyncAtMs = System.currentTimeMillis();
                }
                Log.i(TAG, TRACE_PREFIX + " wars sync ok: rows=" + parsed.size() + ", attempt=" + attempt);
                finishWarsSyncSuccess(parsed);
            }

            @Override
            public void onFailure(String message) {
                boolean retryable = isWarsRateLimitFailure(message);
                if (retryable && attempt < WARS_SYNC_MAX_ATTEMPTS) {
                    long retryDelayMs = WARS_SYNC_RETRY_DELAY_BASE_MS * attempt;
                    Log.w(TAG, TRACE_PREFIX + " wars sync retry: attempt=" + attempt
                            + "/" + WARS_SYNC_MAX_ATTEMPTS + ", delayMs=" + retryDelayMs
                            + ", reason=" + message);
                    mainHandler.postDelayed(() -> syncWarsAttempt(destinationFile, attempt + 1), retryDelayMs);
                    return;
                }
                Log.w(TAG, TRACE_PREFIX + " wars sync failed: attempt=" + attempt + ", reason=" + message);
                finishWarsSyncFailure(message);
            }
        });
    }

    private void finishWarsSyncSuccess(List<WarEntry> parsed) {
        ArrayList<ApiRepository.ApiCallback<List<WarEntry>>> callbacks = new ArrayList<>();
        synchronized (lock) {
            callbacks.addAll(pendingWarsSyncCallbacks);
            pendingWarsSyncCallbacks.clear();
            warsSyncInProgress = false;
        }
        for (ApiRepository.ApiCallback<List<WarEntry>> callback : callbacks) {
            if (callback != null) {
                callback.onSuccess(new ArrayList<>(parsed));
            }
        }
    }

    private void finishWarsSyncFailure(String message) {
        ArrayList<ApiRepository.ApiCallback<List<WarEntry>>> callbacks = new ArrayList<>();
        synchronized (lock) {
            callbacks.addAll(pendingWarsSyncCallbacks);
            pendingWarsSyncCallbacks.clear();
            warsSyncInProgress = false;
        }
        for (ApiRepository.ApiCallback<List<WarEntry>> callback : callbacks) {
            if (callback != null) {
                callback.onFailure(message);
            }
        }
    }

    private boolean isWarsRateLimitFailure(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains(" 535")
                || normalized.contains(" 536")
                || normalized.contains("server error or empty response: 535")
                || normalized.contains("server error or empty response: 536");
    }

    public List<WarEntry> getCachedWars() {
        synchronized (lock) {
            if (cachedWars.isEmpty()) {
                cachedWars.addAll(parseWarsFromFile(getWarsFile()));
            }
            return new ArrayList<>(cachedWars);
        }
    }

    public long getLastWarsSyncAtMs() {
        synchronized (lock) {
            return lastWarsSyncAtMs;
        }
    }

    /**
     * Проверка БД-режима Auto-Босс:
     * true, если clanToken цели (`c174.gif`) входит в любой текущий war-entry.
     */
    public boolean isClanTokenInCurrentWars(String clanToken) {
        String normalizedToken = normalizeClanToken(clanToken);
        if (normalizedToken.isEmpty()) {
            return false;
        }
        List<WarEntry> wars = getCachedWars();
        for (WarEntry war : wars) {
            if (normalizedToken.equals(war.clanTokenX) || normalizedToken.equals(war.clanTokenY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Готовые строки для табличного UI "Текущие войны".
     */
    public List<WarTableRow> buildWarsTableRows() {
        List<WarEntry> wars = getCachedWars();
        ArrayList<WarTableRow> rows = new ArrayList<>();
        for (WarEntry war : wars) {
            String aggressorText = safeTrim(war.clanNameX) + " [" + war.clanLevelX + "]";
            String opponentText = safeTrim(war.clanNameY) + " [" + war.clanLevelY + "]";
            rows.add(new WarTableRow(
                    formatUnixSeconds(war.startUnixSec),
                    getInclinationIconUrl(war.inclinationX),
                    getClanIconUrl(war.clanTokenX),
                    aggressorText,
                    String.valueOf(war.warScoreX),
                    String.valueOf(war.warScoreY),
                    getInclinationIconUrl(war.inclinationY),
                    getClanIconUrl(war.clanTokenY),
                    opponentText,
                    formatUnixSeconds(war.stopUnixSec)
            ));
        }
        return rows;
    }

    private List<WarEntry> parseWarsFromFile(File file) {
        ArrayList<WarEntry> parsed = new ArrayList<>();
        if (file == null || !file.exists()) {
            return parsed;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "windows-1251"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = safeTrim(line);
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split(",", -1);
                if (parts.length < 13) {
                    continue;
                }
                String clanTokenX = normalizeClanToken(parts[0]);
                String clanNameX = safeTrim(parts[1]);
                int inclinationX = parseIntSafe(parts[2]);
                int clanLevelX = parseIntSafe(parts[3]);
                String clanTokenY = normalizeClanToken(parts[4]);
                String clanNameY = safeTrim(parts[5]);
                int inclinationY = parseIntSafe(parts[6]);
                int clanLevelY = parseIntSafe(parts[7]);
                String warStatus = safeTrim(parts[8]);
                int warScoreX = parseIntSafe(parts[9]);
                int warScoreY = parseIntSafe(parts[10]);
                long startUnixSec = parseLongSafe(parts[11]);
                long stopUnixSec = parseLongSafe(parts[12]);

                parsed.add(new WarEntry(
                        clanTokenX,
                        clanNameX,
                        inclinationX,
                        clanLevelX,
                        clanTokenY,
                        clanNameY,
                        inclinationY,
                        clanLevelY,
                        warStatus,
                        warScoreX,
                        warScoreY,
                        startUnixSec,
                        stopUnixSec
                ));
            }
        } catch (Exception e) {
            Log.e(TAG, TRACE_PREFIX + " parse wars failed", e);
        }
        return parsed;
    }

    private File getInfoDir() {
        File root = appContext.getExternalFilesDir(null);
        if (root == null) {
            root = appContext.getFilesDir();
            Log.w(TAG, TRACE_PREFIX + " external files dir unavailable, fallback to internal files dir");
        }
        File infoDir = new File(root, INFO_DIR);
        if (!infoDir.exists() && !infoDir.mkdirs()) {
            Log.w(TAG, TRACE_PREFIX + " failed to create info dir: " + infoDir.getAbsolutePath());
        }
        return infoDir;
    }

    public File getClansFile() {
        return new File(getInfoDir(), CLANS_FILE_NAME);
    }

    public File getWarsFile() {
        return new File(getInfoDir(), WARS_FILE_NAME);
    }

    private String normalizeClanToken(String clanNumberOrToken) {
        String value = safeTrim(clanNumberOrToken).toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "";
        }
        if (!value.endsWith(".gif")) {
            value += ".gif";
        }
        return value;
    }

    private String getInclinationIconUrl(int inclination) {
        switch (inclination) {
            case 4:
                return "http://image.neverlands.ru/signs/chaoss.gif";
            case 3:
                return "http://image.neverlands.ru/signs/sumers.gif";
            case 2:
                return "http://image.neverlands.ru/signs/lights.gif";
            case 1:
                return "http://image.neverlands.ru/signs/darks.gif";
            default:
                return "";
        }
    }

    private String getClanIconUrl(String clanToken) {
        String token = normalizeClanToken(clanToken);
        if (token.isEmpty()) {
            return "";
        }
        return "http://image.neverlands.ru/signs/" + token;
    }

    private String formatUnixSeconds(long unixSeconds) {
        if (unixSeconds <= 0L) {
            return "-";
        }
        Date date = new Date(unixSeconds * 1000L);
        SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN, Locale.getDefault());
        format.setTimeZone(KIEV_TIME_ZONE);
        return format.format(date);
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(safeTrim(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseLongSafe(String value) {
        try {
            return Long.parseLong(safeTrim(value));
        } catch (Exception e) {
            return 0L;
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Проверяет готовность proxy runtime для фоновых HTTP-запросов (Кланы/Войны).
     *
     * Назначение:
     * - при включенном строгом прокси (`DoProxy/UseProxy`) не допускать direct egress;
     * - перед sync принудительно поднимать runtime (как в основных игровых запросах).
     *
     * Поведение:
     * - если прокси не обязателен — сразу `true`;
     * - если обязателен и не поднялся — отдаём понятную ошибку в callback и пишем trace.
     */
    private boolean ensureProxyReadyForBackgroundSync(ApiRepository.ApiCallback<?> callback) {
        boolean strictProxyRequired = ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile();
        if (!strictProxyRequired) {
            return true;
        }

        boolean proxyReady = ProxyRuntimeManager.ensureStarted(appContext, AppVars.Profile);
        if (proxyReady) {
            return true;
        }

        String reason = ProxyRuntimeManager.getLastStartError();
        if (reason == null || reason.trim().isEmpty()) {
            reason = "proxy runtime is not ready";
        }
        Log.w(TAG, TRACE_PREFIX + " sync blocked: " + reason);
        if (callback != null) {
            callback.onFailure("Proxy runtime error: " + reason);
        }
        return false;
    }
}

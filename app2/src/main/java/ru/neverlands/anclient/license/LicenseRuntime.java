package ru.neverlands.anclient.license;

import android.content.Context;

import java.security.SecureRandom;
import java.text.Normalizer;

import ru.neverlands.anclient.manager.AutoFunctionsManager;
import ru.neverlands.anclient.model.QuickActionType;
import ru.neverlands.anclient.model.UserConfig;
import ru.neverlands.anclient.utils.AppLog;

/**
 * Runtime-фасад для всех code paths, завязанных на лицензию.
 *
 * Зависимости:
 * - `LicenseManager` проверяет/создаёт `LicenseStatus` и `LicenseSession`.
 * - `LicenseFeature` сопоставляет `QuickActionType.actionKey` и подписи таймеров с feature-набором сессии.
 * - UI, auto-functions, proxy и OkHttp вызывают `requireSession(...)` / `isActionAllowed(...)`
 *   вместо прямого перечитывания `profile.reg`.
 */
public final class LicenseRuntime {
    private static final String TAG = "LicenseRuntime";
    private static final String CHAIN = "ANCLIENT_LICENSE";
    private static final LicenseRuntime INSTANCE = new LicenseRuntime();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Object lock = new Object();
    private Context appContext;
    private LicenseSession currentSession;
    private long activatedAtMs;

    private LicenseRuntime() {
    }

    public static LicenseRuntime getInstance() {
        return INSTANCE;
    }

    public void initialize(Context context) {
        if (context == null) {
            return;
        }
        synchronized (lock) {
            appContext = context.getApplicationContext();
        }
        AppLog.i(CHAIN, TAG, "LICENSE_RUNTIME_INIT");
    }

    public LicenseStatus validateAndActivate(Context context, String profileName) {
        Context safeContext = context != null ? context.getApplicationContext() : appContext;
        LicenseStatus status = LicenseManager.validateOrCreateRequest(safeContext, profileName);
        if (status.isAllowed() && status.getSession() != null) {
            // `currentSession` — единственный in-memory источник истины после входа.
            // Любая неуспешная проверка очищает его, чтобы background/network код завершался fail-closed.
            activate(status.getSession(), "validateAndActivate");
        } else {
            clear("validate_failed");
        }
        return status;
    }

    public LicenseStatus validateAndActivate(Context context, UserConfig profile) {
        if (profile == null) {
            return validateAndActivate(context, "");
        }
        Context safeContext = context != null ? context.getApplicationContext() : appContext;
        LicenseStatus status = LicenseManager.validateOrCreateRequest(safeContext, profile);
        if (status.isAllowed() && status.getSession() != null) {
            activate(status.getSession(), "validateAndActivate");
        } else {
            clear("validate_failed");
        }
        return status;
    }

    public LicenseSession ensureActiveForProfile(Context context, String profileName, String source) {
        String normalizedProfile = normalizeProfile(profileName);
        synchronized (lock) {
            // Переиспользуем сессию только если она относится к тому же normalized profile и не истекла.
            // Это не даёт MainActivity/foreground-service работать под stale-grant другого профиля.
            if (currentSession != null
                    && normalizedProfile.equals(currentSession.getProfileName())
                    && !currentSession.isExpired(System.currentTimeMillis())) {
                return currentSession;
            }
        }
        LicenseStatus status = validateAndActivate(context, normalizedProfile);
        LicenseSession session = status.getSession();
        if (session == null) {
            AppLog.w(CHAIN, TAG, "LICENSE_RUNTIME_NO_SESSION: source=" + safe(source)
                    + ", profile=" + normalizedProfile
                    + ", reason=" + status.getTitle());
        }
        return session;
    }

    public LicenseSession requireSession(String source) {
        LicenseSession expiredSession = null;
        synchronized (lock) {
            // Центральная fail-closed проверка. Вызывающие модули передают `source` только для диагностики;
            // само решение зависит от `currentSession` и `LicenseSession.expiresAt`.
            if (currentSession == null) {
                AppLog.w(CHAIN, TAG, "LICENSE_RUNTIME_MISSING: source=" + safe(source));
                return null;
            }
            if (currentSession.isExpired(System.currentTimeMillis())) {
                AppLog.w(CHAIN, TAG, "LICENSE_RUNTIME_EXPIRED: source=" + safe(source)
                        + ", profile=" + currentSession.getProfileName());
                expiredSession = currentSession;
                currentSession = null;
                activatedAtMs = 0L;
            } else {
                return currentSession;
            }
        }
        return refreshAfterExpiredSession(expiredSession, source);
    }

    public boolean isActionAllowed(QuickActionType type) {
        if (type == null || type == QuickActionType.NONE) {
            return true;
        }
        // `QuickActionType.getActionKey()` должен совпадать с feature-ключами, которые генерирует app3.
        // При отсутствии runtime-сессии `LicenseFeature` намеренно возвращает false.
        LicenseSession session = requireSession("action:" + type.getActionKey());
        return LicenseFeature.isActionAllowed(session, type);
    }

    public boolean hasFeature(String featureKey) {
        LicenseSession session = requireSession("feature:" + safe(featureKey));
        return session != null && session.hasFeature(featureKey);
    }

    public boolean isTimerAutoFunctionAllowed(String label) {
        LicenseSession session = requireSession("timer_auto_function:" + safe(label));
        return LicenseFeature.isTimerAutoFunctionAllowed(session, label);
    }

    public String[] filterAutoFunctionLabels(String[] labels) {
        LicenseSession session = requireSession("filter_auto_functions");
        return LicenseFeature.filterAutoFunctionLabels(session, labels);
    }

    public String describeCurrentSession() {
        synchronized (lock) {
            if (currentSession == null) {
                return "no-session";
            }
            return "profile=" + currentSession.getProfileName()
                    + ", tier=" + currentSession.getTier()
                    + ", features=" + currentSession.getEnabledFeatures().size()
                    + ", activeMs=" + (System.currentTimeMillis() - activatedAtMs);
        }
    }

    public void clear(String reason) {
        synchronized (lock) {
            currentSession = null;
            activatedAtMs = 0L;
        }
        AppLog.i(CHAIN, TAG, "LICENSE_RUNTIME_CLEAR: reason=" + safe(reason));
        syncAutoFunctionsWithLicense("clear:" + safe(reason));
    }

    private void activate(LicenseSession session, String source) {
        synchronized (lock) {
            currentSession = session;
            activatedAtMs = System.currentTimeMillis();
        }
        AppLog.i(CHAIN, TAG, "LICENSE_RUNTIME_ACTIVE: source=" + safe(source)
                + ", profile=" + session.getProfileName()
                + ", tier=" + session.getTier()
                + ", features=" + session.getEnabledFeatures().size()
                + ", capability=" + shortToken(session.getCapabilityKey()));
        syncAutoFunctionsWithLicense("activate:" + safe(source));
    }

    private LicenseSession refreshAfterExpiredSession(LicenseSession expiredSession, String source) {
        if (expiredSession == null) {
            return null;
        }
        Context contextSnapshot;
        synchronized (lock) {
            contextSnapshot = appContext;
        }
        if (contextSnapshot == null) {
            syncAutoFunctionsWithLicense("expired_no_context:" + safe(source));
            return null;
        }
        AppLog.i(CHAIN, TAG, "LICENSE_RUNTIME_REFRESH_AFTER_EXPIRE: source=" + safe(source)
                + ", profile=" + expiredSession.getProfileName());
        LicenseStatus status = validateAndActivate(contextSnapshot, expiredSession.getProfileName());
        LicenseSession refreshed = status.getSession();
        if (refreshed == null) {
            syncAutoFunctionsWithLicense("expired_no_session:" + safe(source));
        }
        return refreshed;
    }

    private void syncAutoFunctionsWithLicense(String reason) {
        Context contextSnapshot;
        synchronized (lock) {
            contextSnapshot = appContext;
        }
        if (contextSnapshot == null) {
            return;
        }
        try {
            AutoFunctionsManager.getInstance(contextSnapshot).disableUnavailableFeatures(reason);
        } catch (Exception e) {
            AppLog.w(CHAIN, TAG, "LICENSE_RUNTIME_AUTO_FLAGS_SYNC_FAILED: reason=" + safe(reason), e);
        }
    }

    static String newRuntimeNonce() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return LicenseCrypto.base64Url(bytes);
    }

    private static String normalizeProfile(String value) {
        String normalized = value == null ? "" : value.trim();
        return Normalizer.normalize(normalized, Normalizer.Form.NFC);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String shortToken(String value) {
        if (value == null || value.length() <= 12) {
            return safe(value);
        }
        return value.substring(0, 12);
    }
}

package ru.neverlands.anclient.handlers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.net.HttpCookie;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.neverlands.anclient.AuthManager;
import ru.neverlands.anclient.model.AuthResult;
import ru.neverlands.anclient.model.UserConfig;
import ru.neverlands.anclient.network.NetworkClient;
import ru.neverlands.anclient.proxy.CookiesManager;
import ru.neverlands.anclient.proxy.ProxyRuntimeManager;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.CryptoUtils;

/**
 * Выполняет автоматический перезаход после HTML-ошибки оборванного игрового сеанса.
 *
 * Handler переиспользует существующий AuthManager/cookie/proxy pipeline и не отправляет
 * собственные raw login-запросы в обход штатного контура.
 */
public final class SessionReloginHandler {
    private static final String TAG = "SessionReloginHandler";
    public static final String CHAIN = "session_relogin";

    private SessionReloginHandler() {
    }

    public interface Callback {
        void onReloginSuccess(List<HttpCookie> cookies);

        void onReloginFallbackRequired(String reason);
    }

    public static void start(Context context, UserConfig profile, Callback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        if (context == null) {
            reject(callback, mainHandler, "context_missing");
            return;
        }

        Credentials credentials = resolveCredentials(context, profile);
        if (!credentials.approved) {
            reject(callback, mainHandler, credentials.rejectReason);
            return;
        }

        Context appContext = context.getApplicationContext();
        AppLog.i(CHAIN, TAG, "SESSION_RELOGIN APPROVED: start auth flow for profile=" + profile.UserNick);

        boolean proxyReady = ProxyRuntimeManager.ensureStarted(appContext, profile);
        if (!proxyReady) {
            String reason = ProxyRuntimeManager.getLastStartError();
            reject(callback, mainHandler, "proxy_start_failed:" + (reason == null ? "" : reason));
            return;
        }

        NetworkClient.invalidateInstance();
        AppVars.lastCookies = null;
        NetworkClient.clearCookies();

        mainHandler.post(() -> CookiesManager.clear(value -> executeAuthorize(credentials, callback, mainHandler)));
    }

    private static Credentials resolveCredentials(Context context, UserConfig profile) {
        if (profile == null) {
            return Credentials.rejected("profile_missing");
        }
        if (isEmpty(profile.UserNick)) {
            return Credentials.rejected("nick_missing");
        }
        Credentials runtime = resolveRuntimeCredentials(profile);
        if (runtime.approved) {
            AppLog.i(CHAIN, TAG, "SESSION_RELOGIN APPROVED: runtime credentials available");
            return runtime;
        }
        if (profile.isEncrypted) {
            return resolveEncryptedSavedCredentials(context, profile);
        }
        if (!profile.UserAutoLogon) {
            return Credentials.rejected("autologon_disabled");
        }
        if (isEmpty(profile.UserPassword)) {
            return Credentials.rejected("password_missing");
        }
        return Credentials.approved(
                profile.UserNick,
                profile.UserPassword,
                profile.UserPasswordFlash == null ? "" : profile.UserPasswordFlash
        );
    }

    private static Credentials resolveRuntimeCredentials(UserConfig profile) {
        String runtimePassword = AppVars.RuntimeAuthGamePassword;
        if (isEmpty(runtimePassword)) {
            return Credentials.rejected("runtime_password_missing");
        }
        String profileId = profile.id == null ? "" : profile.id;
        String profileNick = profile.UserNick == null ? "" : profile.UserNick;
        String runtimeProfileId = AppVars.RuntimeAuthProfileId == null ? "" : AppVars.RuntimeAuthProfileId;
        String runtimeNick = AppVars.RuntimeAuthUserNick == null ? "" : AppVars.RuntimeAuthUserNick;
        boolean sameProfile = (!isEmpty(profileId) && profileId.equals(runtimeProfileId))
                || (!isEmpty(profileNick) && profileNick.equalsIgnoreCase(runtimeNick));
        if (!sameProfile) {
            return Credentials.rejected("runtime_profile_mismatch");
        }
        return Credentials.approved(
                profile.UserNick,
                runtimePassword,
                AppVars.RuntimeAuthFlashPassword == null ? "" : AppVars.RuntimeAuthFlashPassword
        );
    }

    private static Credentials resolveEncryptedSavedCredentials(Context context, UserConfig profile) {
        if (context == null) {
            return Credentials.rejected("encrypted_profile_context_missing");
        }
        String profileId = profile.id == null ? "" : profile.id;
        if (isEmpty(profileId)) {
            return Credentials.rejected("encrypted_profile_id_missing");
        }
        String encryptionPassword = context.getSharedPreferences("login_ui_state", Context.MODE_PRIVATE)
                .getString("encrypted_login_password_" + profileId, "");
        if (isEmpty(encryptionPassword)) {
            return Credentials.rejected("encrypted_profile_password_not_saved");
        }
        try {
            String gamePassword = CryptoUtils.decrypt(profile.UserPassword, encryptionPassword);
            String flashPassword = isEmpty(profile.UserPasswordFlash)
                    ? ""
                    : CryptoUtils.decrypt(profile.UserPasswordFlash, encryptionPassword);
            AppLog.i(CHAIN, TAG, "SESSION_RELOGIN APPROVED: encrypted saved key decrypted");
            return Credentials.approved(profile.UserNick, gamePassword, flashPassword);
        } catch (Exception e) {
            AppLog.w(CHAIN, TAG, "SESSION_RELOGIN REJECTED: encrypted decrypt failed", e);
            return Credentials.rejected("encrypted_profile_decrypt_failed");
        }
    }

    private static void executeAuthorize(Credentials credentials, Callback callback, Handler mainHandler) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "session-relogin-auth");
            thread.setDaemon(true);
            return thread;
        });
        executor.execute(() -> {
            AuthResult result = new AuthManager().authorize(
                    credentials.username,
                    credentials.password,
                    credentials.flashPassword
            );
            mainHandler.post(() -> handleAuthResult(result, callback));
        });
        executor.shutdown();
    }

    private static void handleAuthResult(AuthResult result, Callback callback) {
        if (result == null) {
            rejectNow(callback, "auth_result_null");
            return;
        }
        if (result.isCaptchaRequired()) {
            rejectNow(callback, "captcha_required");
            return;
        }
        if (!result.isSuccess()) {
            String errorMessage = result.getErrorMessage();
            rejectNow(callback, "auth_failed:" + (errorMessage == null ? "" : errorMessage));
            return;
        }
        List<HttpCookie> cookies = result.getCookies();
        if (cookies == null || cookies.isEmpty()) {
            rejectNow(callback, "auth_success_without_cookies");
            return;
        }

        AppLog.i(CHAIN, TAG, "SESSION_RELOGIN APPROVED: auth success, cookies=" + cookies.size());
        if (callback != null) {
            callback.onReloginSuccess(cookies);
        }
    }

    private static void reject(Callback callback, Handler mainHandler, String reason) {
        mainHandler.post(() -> rejectNow(callback, reason));
    }

    private static void rejectNow(Callback callback, String reason) {
        AppLog.w(CHAIN, TAG, "SESSION_RELOGIN REJECTED: " + reason);
        if (callback != null) {
            callback.onReloginFallbackRequired(reason);
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class Credentials {
        final boolean approved;
        final String username;
        final String password;
        final String flashPassword;
        final String rejectReason;

        private Credentials(boolean approved, String username, String password, String flashPassword, String rejectReason) {
            this.approved = approved;
            this.username = username;
            this.password = password;
            this.flashPassword = flashPassword;
            this.rejectReason = rejectReason;
        }

        static Credentials approved(String username, String password, String flashPassword) {
            return new Credentials(true, username, password, flashPassword, "");
        }

        static Credentials rejected(String reason) {
            return new Credentials(false, "", "", "", reason);
        }
    }
}

package ru.neverlands.anclient.handlers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.webkit.CookieManager;

import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;

import ru.neverlands.anclient.LoginActivity;
import ru.neverlands.anclient.license.LicenseRuntime;
import ru.neverlands.anclient.network.NetworkClient;
import ru.neverlands.anclient.proxy.CookiesManager;
import ru.neverlands.anclient.proxy.ProxyRuntimeManager;
import ru.neverlands.anclient.service.AutoModeForegroundService;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.GameServerUrls;

/**
 * Выход из аккаунта: серверный logout + полная очистка локальной сессии + возврат на экран входа.
 *
 * <p>Выделено из {@code MainActivity} в рамках D6. Блок изолирован: сетевая часть вообще не
 * зависит от состояния Activity, а от самой Activity нужны только переход на
 * {@link LoginActivity} и её завершение.</p>
 *
 * <p>Порядок (сохранён 1:1 с прежней реализацией):</p>
 * <ol>
 *   <li>best-effort {@code GET /exit.php} с cookie текущей сессии и браузерным Referer;</li>
 *   <li>очистка локальной сессии: WebView-cookie, OkHttp cookie-jar, runtime-креденшелы, лицензия;</li>
 *   <li>синхронизация foreground-сервиса и переход на экран входа с очисткой back stack.</li>
 * </ol>
 */
public final class LogoutFlowHandler {

    private static final String TAG = "LogoutFlowHandler";

    private static final String LOGOUT_PATH = "/exit.php";
    private static final String LOGOUT_REFERER_PATH = "/game.php";
    private static final int LOGOUT_HTTP_TIMEOUT_MS = 10_000;

    private LogoutFlowHandler() {
    }

    /**
     * Запускает полный сценарий выхода.
     *
     * <p>Сетевой запрос выполняется в отдельном потоке, финализация — в UI-потоке.</p>
     *
     * @param activity         текущая Activity (нужна для навигации и {@code runOnUiThread})
     * @param onBeforeNavigate вызывается в UI-потоке перед очисткой и переходом;
     *                         сюда владелец выставляет свой флаг «идёт выход»
     */
    public static void startLogout(Activity activity, Runnable onBeforeNavigate) {
        if (activity == null) {
            AppLog.w(TAG, "LOGOUT_FLOW: activity is null, skip");
            return;
        }
        AppLog.i(TAG, "LOGOUT_FLOW: started from navigation drawer");
        new Thread(() -> {
            performLogoutRequestBestEffort();
            activity.runOnUiThread(() -> finalizeLogoutAndOpenLogin(activity, onBeforeNavigate));
        }, "logout-flow").start();
    }

    /**
     * Серверный выход: {@code GET /exit.php} с заголовками, как у браузера.
     *
     * <p>Best-effort: любая сетевая ошибка не должна мешать локальному выходу.</p>
     */
    private static void performLogoutRequestBestEffort() {
        HttpURLConnection connection = null;
        try {
            String logoutUrl = GameServerUrls.currentGameUrl(LOGOUT_PATH);
            String logoutReferer = GameServerUrls.currentGameUrl(LOGOUT_REFERER_PATH);
            URL url = new URL(logoutUrl);
            Proxy activeProxy = ProxyRuntimeManager.getActiveJavaProxyOrNull();
            if (activeProxy == null && ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile()) {
                AppLog.e(TAG, "PROXY_FAIL: strict proxy enabled and runtime proxy unavailable, skip server logout request");
                return;
            }

            connection = activeProxy != null
                    ? (HttpURLConnection) url.openConnection(activeProxy)
                    : (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(LOGOUT_HTTP_TIMEOUT_MS);
            connection.setReadTimeout(LOGOUT_HTTP_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("Referer", logoutReferer);
            connection.setRequestProperty("User-Agent", AppVars.BROWSER_USER_AGENT);

            String cookie = CookieManager.getInstance().getCookie(logoutUrl);
            if (cookie == null || cookie.isEmpty()) {
                cookie = CookieManager.getInstance().getCookie(GameServerUrls.neverlandsCookieUrl());
            }
            if ((cookie == null || cookie.isEmpty()) && url.getHost() != null) {
                cookie = CookiesManager.obtain(url.getHost());
            }
            if (cookie != null && !cookie.isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
            } else {
                AppLog.w(TAG, "LOGOUT_FLOW: cookie is empty for server logout request");
            }

            int responseCode = connection.getResponseCode();
            String location = connection.getHeaderField("Location");
            AppLog.i(TAG, "LOGOUT_FLOW: exit.php responseCode=" + responseCode
                    + ", location=" + (location == null ? "" : location));
        } catch (Exception e) {
            AppLog.w(TAG, "LOGOUT_FLOW: server logout request failed", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Немедленный локальный выход <b>без</b> серверного запроса.
     *
     * <p>Используется, когда восстановить сеанс не удалось ({@code SESSION_RELOGIN_FALLBACK}):
     * сессия на сервере уже мертва, поэтому {@code exit.php} дёргать бессмысленно.
     * Вызывать только из UI-потока.</p>
     */
    public static void forceLogoutToLogin(Activity activity, Runnable onBeforeNavigate) {
        if (activity == null) {
            AppLog.w(TAG, "LOGOUT_FLOW: activity is null, skip forced logout");
            return;
        }
        AppLog.i(TAG, "LOGOUT_FLOW: forced logout without server request");
        finalizeLogoutAndOpenLogin(activity, onBeforeNavigate);
    }

    /**
     * Локальная очистка и возврат на {@link LoginActivity}. Выполняется в UI-потоке.
     */
    private static void finalizeLogoutAndOpenLogin(Activity activity, Runnable onBeforeNavigate) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (onBeforeNavigate != null) {
            onBeforeNavigate.run();
        }

        clearLocalSession(activity);

        AutoModeForegroundService.syncServiceState(activity, "logout_to_login");
        Intent intent = new Intent(activity, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }

    /**
     * Полная очистка локальной сессии: runtime-креденшелы, cookie обоих хранилищ, лицензия.
     *
     * <p>Вынесено отдельно, чтобы этот же сброс можно было переиспользовать вне сценария
     * выхода (например, при смене профиля).</p>
     */
    public static void clearLocalSession(Context context) {
        AppVars.lastCookies = null;
        AppVars.clearRuntimeAuthCredentials();
        NetworkClient.clearCookies();
        CookiesManager.clear();
        LicenseRuntime.getInstance().clear("logout_to_login");
        try {
            CookieManager manager = CookieManager.getInstance();
            manager.removeSessionCookies(null);
            manager.removeAllCookies(null);
            manager.flush();
        } catch (Throwable t) {
            AppLog.w(TAG, "LOGOUT_FLOW: WebView cookie cleanup failed", t);
        }
    }
}

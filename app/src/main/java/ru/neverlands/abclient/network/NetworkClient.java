package ru.neverlands.abclient.network;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;
import ru.neverlands.abclient.utils.AppLog;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import ru.neverlands.abclient.proxy.ProxyRuntimeManager;
import ru.neverlands.abclient.utils.RuntimeNetTrace;

public class NetworkClient {
    private static final String TAG = "NetworkClient";
    private static final int DIRECT_TIMEOUT_SECONDS = 30;
    private static final int PROXY_TIMEOUT_SECONDS = 60;

    private static OkHttpClient instance;
    private static CookieManager cookieManager;
    private static String runtimeSignature = "";

    public static synchronized OkHttpClient getInstance() {
        String currentSignature = ProxyRuntimeManager.getRuntimeSignature();
        if (instance == null || !currentSignature.equals(runtimeSignature)) {
            retireCurrentClient("runtime signature changed");
            AppLog.i(TAG, "PROXY_BINDING: rebuilding OkHttp client, signature=" + currentSignature);
            cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

            boolean strictProxyRequired = ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile();
            Proxy proxy = ProxyRuntimeManager.getActiveJavaProxyOrNull();
            boolean proxyRuntimeActive = proxy != null;
            int timeoutSeconds = (strictProxyRequired || proxyRuntimeActive)
                    ? PROXY_TIMEOUT_SECONDS
                    : DIRECT_TIMEOUT_SECONDS;

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .cookieJar(new JavaNetCookieJar(cookieManager));
            AppLog.i(TAG, "NET_TIMEOUT: mode="
                    + ((strictProxyRequired || proxyRuntimeActive) ? "proxy" : "direct")
                    + ", connect/read/write=" + timeoutSeconds + "s"
                    + ", strictProxyRequired=" + strictProxyRequired
                    + ", proxyRuntimeActive=" + proxyRuntimeActive);
            if (proxy != null) {
                builder.proxy(proxy);
                AppLog.i(TAG, "PROXY_BINDING: OkHttp proxy enabled");
                RuntimeNetTrace.push("OKHTTP", "route=proxy state=enabled");
            } else {
                if (strictProxyRequired) {
                    // Жесткий anti-leak: при включенном прокси запрещаем direct egress и даем
                    // заведомо нерабочий loopback endpoint, чтобы запрос не ушел напрямую наружу.
                    Proxy blockedProxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 1));
                    builder.proxy(blockedProxy);
                    AppLog.e(TAG, "PROXY_FAIL: strict proxy enabled but runtime proxy is null; direct egress blocked");
                    RuntimeNetTrace.push("PROXY_FAIL", "scope=okhttp strict=1 route=direct blocked=1");
                } else {
                    AppLog.w(TAG, "PROXY_BINDING: OkHttp proxy is null, using direct client");
                    RuntimeNetTrace.push("OKHTTP", "route=direct state=fallback");
                }
            }

            instance = builder.build();
            runtimeSignature = currentSignature;
        }
        return instance;
    }

    public static CookieManager getCookieManager() {
        // Убедимся, что cookieManager инициализирован
        if (cookieManager == null) {
            getInstance();
        }
        return cookieManager;
    }
    /**
     * Clears cookies used by OkHttp JavaNetCookieJar.
     */
    public static synchronized void clearCookies() {
        getCookieManager().getCookieStore().removeAll();
    }

    /**
     * Принудительно сбрасывает текущий OkHttp-инстанс.
     *
     * Зависимости:
     * - вызывается после старта/перезапуска proxy runtime,
     *   чтобы следующий {@link #getInstance()} пересобрал клиента с новым proxy endpoint.
     */
    public static synchronized void invalidateInstance() {
        retireCurrentClient("explicit invalidation");
        runtimeSignature = "";
        AppLog.i(TAG, "PROXY_BINDING: OkHttp instance invalidated");
    }

    private static void retireCurrentClient(String reason) {
        if (instance == null) {
            return;
        }
        try {
            instance.connectionPool().evictAll();
            AppLog.i(TAG, "PROXY_BINDING: retired idle OkHttp connections, reason=" + reason);
        } catch (Exception e) {
            AppLog.w(TAG, "PROXY_BINDING: failed to evict idle OkHttp connections, reason=" + reason, e);
        } finally {
            instance = null;
        }
    }
}

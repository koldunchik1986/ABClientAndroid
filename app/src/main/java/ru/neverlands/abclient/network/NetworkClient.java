package ru.neverlands.abclient.network;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;
import android.util.Log;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import ru.neverlands.abclient.proxy.ProxyRuntimeManager;
import ru.neverlands.abclient.utils.RuntimeNetTrace;

public class NetworkClient {
    private static final String TAG = "NetworkClient";

    private static OkHttpClient instance;
    private static CookieManager cookieManager;
    private static String runtimeSignature = "";

    public static synchronized OkHttpClient getInstance() {
        String currentSignature = ProxyRuntimeManager.getRuntimeSignature();
        if (instance == null || !currentSignature.equals(runtimeSignature)) {
            Log.i(TAG, "PROXY_BINDING: rebuilding OkHttp client, signature=" + currentSignature);
            cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .cookieJar(new JavaNetCookieJar(cookieManager));

            Proxy proxy = ProxyRuntimeManager.getActiveJavaProxyOrNull();
            if (proxy != null) {
                builder.proxy(proxy);
                Log.i(TAG, "PROXY_BINDING: OkHttp proxy enabled");
                RuntimeNetTrace.push("OKHTTP", "proxy enabled");
            } else {
                if (ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile()) {
                    // Жесткий anti-leak: при включенном прокси запрещаем direct egress и даем
                    // заведомо нерабочий loopback endpoint, чтобы запрос не ушел напрямую наружу.
                    Proxy blockedProxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 1));
                    builder.proxy(blockedProxy);
                    Log.e(TAG, "PROXY_FAIL: strict proxy enabled but runtime proxy is null; direct egress blocked");
                    RuntimeNetTrace.push("PROXY_FAIL", "strict=1 okHttp direct blocked");
                } else {
                    Log.w(TAG, "PROXY_BINDING: OkHttp proxy is null, using direct client");
                    RuntimeNetTrace.push("OKHTTP", "direct mode");
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
        instance = null;
        runtimeSignature = "";
        Log.i(TAG, "PROXY_BINDING: OkHttp instance invalidated");
    }
}

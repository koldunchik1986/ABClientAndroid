package ru.neverlands.abclient.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;

import java.util.concurrent.Executor;

public class WebViewProxyHelper {
    private static final String TAG = "WebViewProxyHelper";

    public static void setWebViewProxy(String proxyHost, int proxyPort, Runnable onProxySetCallback) {
        ProxyConfig proxyConfig = new ProxyConfig.Builder()
                .addProxyRule("http://" + proxyHost + ":" + proxyPort)
                .build();

        Executor executor = new MainThreadExecutor();

        ProxyController.getInstance().setProxyOverride(proxyConfig, executor, () -> {
            Log.d(TAG, "WebView proxy override set successfully.");
            if (onProxySetCallback != null) {
                onProxySetCallback.run();
            }
        });
    }

    public static void clearWebViewProxy() {
        Executor executor = new MainThreadExecutor();
        ProxyController.getInstance().clearProxyOverride(executor, () -> {
            Log.d(TAG, "WebView proxy override cleared.");
        });
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable command) {
            handler.post(command);
        }
    }
}
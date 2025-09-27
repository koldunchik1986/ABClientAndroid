package ru.neverlands.abclient;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.navigation.NavigationView;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import ru.neverlands.abclient.databinding.ActivityMainBinding;
import ru.neverlands.abclient.model.UserConfig;
import ru.neverlands.abclient.proxy.CookiesManager;
import ru.neverlands.abclient.utils.AppLogger;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Chat;

/**
 * Основная активность приложения.
 * Аналог FormMain.cs в оригинальном приложении.
 */
public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    private Timer timer;
    private boolean isExiting = false;

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case AppVars.ACTION_ADD_CHAT_MESSAGE:
                    String message = intent.getStringExtra("message");
                    if (message != null) {
                        Chat.addMessageToChat(message);
                    }
                    break;
                case AppVars.ACTION_WEBVIEW_LOAD_URL:
                    String url = intent.getStringExtra("url");
                    if (url != null && binding.appBarMain.contentMain.webView != null) {
                        binding.appBarMain.contentMain.webView.loadUrl(url);
                    }
                    break;
                case AppVars.ACTION_STOP_AUTOFISH:
                    // TODO: Implement ViewModel call to stop auto-fish
                    Toast.makeText(context, "Авторыбалка остановлена", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };
    
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Настройка тулбара
        Toolbar toolbar = binding.appBarMain.toolbar;
        setSupportActionBar(toolbar);
        if (AppVars.Profile != null && AppVars.Profile.UserNick != null) {
            getSupportActionBar().setTitle(AppVars.Profile.UserNick);
        }
        
        // Настройка бокового меню
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);

        // Установка версии в заголовке бокового меню
        View headerView = navigationView.getHeaderView(0);
        TextView navHeaderTitle = headerView.findViewById(R.id.nav_header_title);
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            navHeaderTitle.setText("v" + versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            navHeaderTitle.setText("");
        }
        
        // Настройка WebView
        WebView webView = binding.appBarMain.contentMain.webView;
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        //webSettings.setAppCacheEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Add Javascript Interface
        webView.addJavascriptInterface(new ru.neverlands.abclient.bridge.WebAppInterface(this), "AndroidBridge");

        // Enable cookies including third-party for frames/chat
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        
        // Настройка WebViewClient для перехвата запросов
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false; // Позволяем WebView обрабатывать URL
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Обработка завершения загрузки страницы
                AppLogger.write("Page loaded: " + url);
                view.evaluateJavascript("javascript:(function() { " +
                    "var frameset = document.getElementsByTagName('frameset')[0];" +
                    "if (frameset) { frameset.rows = '*, 0'; }" +
                    "})()", null);
            }
        });
        
        // Настройка прокси для WebView
        setupProxy();

        // Запуск прокси-сервиса только если включен в профиле
        ((ABClientApplication) getApplication()).startProxyService();

        AppVars.NextCheckNoConnection = new Date();

        // Запуск таймера для обновления времени
        startTimer();
        
        // Загрузка фреймсета игры (включает нижний чат и верхнюю панель)
        webView.loadUrl("http://neverlands.ru/main.php");

        // Настройка и загрузка чата
        WebView chatMsgWebView = binding.appBarMain.contentMain.chatMsgWebview;
        WebSettings chatMsgWebSettings = chatMsgWebView.getSettings();
        chatMsgWebSettings.setJavaScriptEnabled(true);
        chatMsgWebSettings.setDomStorageEnabled(true);
        chatMsgWebSettings.setDatabaseEnabled(true);
        chatMsgWebSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(chatMsgWebView, true);
        chatMsgWebView.loadUrl("http://neverlands.ru/ch/msg.php");

        WebView chatUsersWebView = binding.appBarMain.contentMain.chatUsersWebview;
        WebSettings chatUsersWebSettings = chatUsersWebView.getSettings();
        chatUsersWebSettings.setJavaScriptEnabled(true);
        chatUsersWebSettings.setDomStorageEnabled(true);
        chatUsersWebSettings.setDatabaseEnabled(true);
        chatUsersWebSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(chatUsersWebView, true);
        chatUsersWebView.loadUrl("http://neverlands.ru/ch.php?lo=1");
        chatUsersWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript("javascript:(function() { " +
                    "var frameset = document.getElementsByTagName('frameset')[0];" +
                    "if (frameset) { frameset.cols = '0, *'; }" +
                    "})()", null);
            }
        });

        WebView chatButtonsWebView = binding.appBarMain.contentMain.chatButtonsWebview;
        WebSettings chatButtonsWebSettings = chatButtonsWebView.getSettings();
        chatButtonsWebSettings.setJavaScriptEnabled(true);
        chatButtonsWebSettings.setDomStorageEnabled(true);
        chatButtonsWebSettings.setDatabaseEnabled(true);
        chatButtonsWebSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(chatButtonsWebView, true);
        chatButtonsWebView.loadUrl("http://neverlands.ru/ch/but.php");
    }
    
    @Override
    protected void onDestroy() {
        ru.neverlands.abclient.utils.DebugLogger.log("MainActivity: onDestroy() called.");
        stopTimer();

        if (isExiting) {
            ((ABClientApplication) getApplication()).stopProxyService();
        }

        // Уничтожаем все WebView, чтобы избежать утечек памяти
        destroyWebView(binding.appBarMain.contentMain.webView);
        destroyWebView(binding.appBarMain.contentMain.chatMsgWebview);
        destroyWebView(binding.appBarMain.contentMain.chatUsersWebview);
        destroyWebView(binding.appBarMain.contentMain.chatButtonsWebview);

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(AppVars.ACTION_ADD_CHAT_MESSAGE);
        filter.addAction(AppVars.ACTION_WEBVIEW_LOAD_URL);
        filter.addAction(AppVars.ACTION_STOP_AUTOFISH);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }

    private void destroyWebView(WebView webView) {
        if (webView != null) {
            // Отсоединяем WebView от его родителя
            android.view.ViewParent parent = webView.getParent();
            if (parent instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) parent).removeView(webView);
            }
            webView.stopLoading();
            webView.getSettings().setJavaScriptEnabled(false);
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
        }
    }
    
    @Override
    public void onBackPressed() {
        DrawerLayout drawer = binding.drawerLayout;
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (binding.appBarMain.contentMain.webView.canGoBack()) {
            binding.appBarMain.contentMain.webView.goBack();
        } else {
            if (AppVars.DoPromptExit) {
                showExitConfirmationDialog();
            } else {
                super.onBackPressed();
            }
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_settings) {
            // Открытие настроек
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.nav_home) {
            binding.appBarMain.contentMain.webView.loadUrl("http://neverlands.ru/");
        } else if (id == R.id.nav_chat) {
            binding.appBarMain.contentMain.webView.loadUrl("http://neverlands.ru/ch.php?lo=1");
        } else if (id == R.id.nav_map) {
            binding.appBarMain.contentMain.webView.loadUrl("http://neverlands.ru/map.php");
        } else if (id == R.id.nav_inventory) {
            binding.appBarMain.contentMain.webView.loadUrl("http://neverlands.ru/main.php?get_id=33&act=10");
        } else if (id == R.id.nav_profile) {
            binding.appBarMain.contentMain.webView.loadUrl("http://neverlands.ru/main.php?get_id=33&act=1");
        } else if (id == R.id.nav_settings) {
            // Открытие настроек
        } else if (id == R.id.nav_logs) {
            Intent intent = new Intent(this, LogsActivity.class);
            startActivity(intent);
        }
        
        DrawerLayout drawer = binding.drawerLayout;
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
    
    /**
     * Настройка прокси для WebView
     */
    private void setupProxy() {
        // Настройка прокси для WebView
        System.setProperty("http.proxyHost", AppVars.LocalProxyAddress);
        System.setProperty("http.proxyPort", String.valueOf(AppVars.LocalProxyPort));
        
        // Не очищаем cookies после авторизации, чтобы сохранить сессию
    }
    
    /**
     * Запуск таймера для обновления времени
     */
    private void startTimer() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    updateClock();
                    checkConnection();
                });
            }
        }, 0, 1000);
    }
    
    /**
     * Остановка таймера
     */
    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
    
    /**
     * Обновление часов
     */
    private void updateClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        binding.appBarMain.contentMain.statusBar.clockTextView.setText(sdf.format(new Date()));
    }
    
    /**
     * Обновление серверного времени
     * @param serverDateTime серверное время
     */
    public void updateServerTime(Date serverDateTime) {
        AppVars.ServerDateTime = serverDateTime;
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        binding.appBarMain.contentMain.statusBar.serverTimeTextView.setText(sdf.format(serverDateTime));
    }
    
    /**
     * Проверка соединения
     */
    private void checkConnection() {
        if (System.currentTimeMillis() > AppVars.NextCheckNoConnection.getTime()) {
            AppVars.NextCheckNoConnection = new Date(System.currentTimeMillis() + 5 * 60 * 1000);
            binding.appBarMain.contentMain.webView.loadUrl("http://www.neverlands.ru/main.php");
        }
    }
    
    /**
     * Добавление адреса в строку статуса
     * @param address адрес
     */
    public void addAddressToStatusString(String address) {
        binding.appBarMain.contentMain.statusBar.statusTextView.setText(address);
    }
    
    /**
     * Удаление адреса из строки статуса
     * @param address адрес
     */
    public void removeAddressFromStatusString(String address) {
        if (binding.appBarMain.contentMain.statusBar.statusTextView.getText().toString().equals(address)) {
            binding.appBarMain.contentMain.statusBar.statusTextView.setText("");
        }
    }
    
    /**
     * Обновление информации о сохраненном трафике
     * @param bytes количество байт
     */
    public void updateSavedTraffic(int bytes) {
        String text = binding.appBarMain.contentMain.statusBar.trafficTextView.getText().toString();
        int savedBytes = 0;
        try {
            savedBytes = Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
        }
        savedBytes += bytes;
        binding.appBarMain.contentMain.statusBar.trafficTextView.setText(String.valueOf(savedBytes));
    }
    
    /**
     * Обновление информации о сохраненном трафике (потокобезопасная версия)
     * @param bytes количество байт
     */
    public void updateSavedTrafficSafe(int bytes) {
        runOnUiThread(() -> updateSavedTraffic(bytes));
    }
    
    /**
     * Показ диалога подтверждения выхода
     */
    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Выход")
                .setMessage("Вы действительно хотите выйти из приложения?")
                .setPositiveButton("Да", (dialog, which) -> {
                    isExiting = true;
                    finish();
                })
                .setNegativeButton("Нет", null)
                .show();
    }
}
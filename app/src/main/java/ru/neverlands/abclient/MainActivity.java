package ru.neverlands.abclient;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.io.ByteArrayInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import ru.neverlands.abclient.databinding.ActivityMainBinding;
import ru.neverlands.abclient.model.UserConfig;
import ru.neverlands.abclient.proxy.CookiesManager;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Log;

/**
 * Основная активность приложения.
 * Аналог FormMain.cs в оригинальном приложении.
 */
public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    private Timer timer;
    private boolean isExiting = false;
    
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Настройка тулбара
        Toolbar toolbar = binding.appBarMain.toolbar;
        setSupportActionBar(toolbar);
        
        // Настройка бокового меню
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        
        // Настройка WebView
        WebView webView = binding.appBarMain.contentMain.webView;
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAppCacheEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        
        // Настройка WebViewClient для перехвата запросов
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false; // Позволяем WebView обрабатывать URL
            }
            
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // Здесь можно перехватывать запросы, но мы используем прокси-сервер
                return super.shouldInterceptRequest(view, request);
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Обработка завершения загрузки страницы
                Log.write("Page loaded: " + url);
            }
        });
        
        // Настройка прокси для WebView
        setupProxy();
        
        // Сохранение ссылки на WebView в глобальных переменных
        AppVars.MainWebView = webView;
        
        // Запуск прокси-сервиса
        ((ABClientApplication) getApplication()).startProxyService();
        
        // Запуск таймера для обновления времени
        startTimer();
        
        // Загрузка начальной страницы
        webView.loadUrl("http://www.neverlands.ru/");
    }
    
    @Override
    protected void onDestroy() {
        stopTimer();
        
        // Остановка прокси-сервиса при выходе из приложения
        if (isExiting) {
            ((ABClientApplication) getApplication()).stopProxyService();
        }
        
        super.onDestroy();
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
            binding.appBarMain.contentMain.webView.loadUrl("http://www.neverlands.ru/");
        } else if (id == R.id.nav_chat) {
            binding.appBarMain.contentMain.webView.loadUrl("http://www.neverlands.ru/ch.php?lo=1");
        } else if (id == R.id.nav_map) {
            binding.appBarMain.contentMain.webView.loadUrl("http://www.neverlands.ru/map.php");
        } else if (id == R.id.nav_inventory) {
            binding.appBarMain.contentMain.webView.loadUrl("http://www.neverlands.ru/main.php?get_id=33&act=10");
        } else if (id == R.id.nav_profile) {
            binding.appBarMain.contentMain.webView.loadUrl("http://www.neverlands.ru/main.php?get_id=33&act=1");
        } else if (id == R.id.nav_settings) {
            // Открытие настроек
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
        
        // Очистка кэша WebView
        binding.appBarMain.contentMain.webView.clearCache(true);
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
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
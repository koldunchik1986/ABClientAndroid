package ru.neverlands.abclient;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
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
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import ru.neverlands.abclient.bridge.WebAppInterface;
import ru.neverlands.abclient.databinding.ActivityMainBinding;
import ru.neverlands.abclient.manager.RoomManager;
import ru.neverlands.abclient.model.UserConfig;
import ru.neverlands.abclient.proxy.CookiesManager;
import ru.neverlands.abclient.utils.AppLogger;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Chat;
import ru.neverlands.abclient.utils.Russian;

/**
 * Основная активность приложения.
 * Аналог FormMain.cs в оригинальном приложении.
 */
public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    private Timer timer;
    private boolean isExiting = false;
    private boolean isRoomManagerStarted = false;

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
                    Toast.makeText(context, "Авторыбалка остановлена", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppVars.init(this);
        AppVars.mainActivity = new WeakReference<>(this);
        
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        Toolbar toolbar = binding.appBarMain.toolbar;
        setSupportActionBar(toolbar);
        if (AppVars.Profile != null && AppVars.Profile.UserNick != null) {
            getSupportActionBar().setTitle(AppVars.Profile.UserNick);
        }
        
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);

        View headerView = navigationView.getHeaderView(0);
        TextView navHeaderTitle = headerView.findViewById(R.id.nav_header_title);
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            navHeaderTitle.setText("v" + versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            navHeaderTitle.setText("");
        }

        // Настраиваем и загружаем WebView
        setupWebViews();
        loadInitialUrls();

        AppVars.NextCheckNoConnection = new Date();
        startTimer();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebViews() {
        // Настройка всех WebView
        WebView webView = binding.appBarMain.contentMain.webView;
        WebView chatMsgWebView = binding.appBarMain.contentMain.chatMsgWebview;
        WebView chatUsersWebView = binding.appBarMain.contentMain.chatUsersWebview;
        WebView chatButtonsWebView = binding.appBarMain.contentMain.chatButtonsWebview;

        CustomWebViewClient customWebViewClient = new CustomWebViewClient();

        setupWebView(webView, customWebViewClient);
        setupWebView(chatMsgWebView, customWebViewClient);
        setupWebView(chatUsersWebView, customWebViewClient);
        setupWebView(chatButtonsWebView, customWebViewClient);

        // Внедряем cookies, полученные после авторизации
        if (AppVars.lastCookies != null && !AppVars.lastCookies.isEmpty()) {
            // Хирургически удаляем дубликаты, особенно "watermark", сохраняя только последний.
            java.util.List<java.net.HttpCookie> filteredCookies = new java.util.ArrayList<>();
            java.util.Set<String> names = new java.util.HashSet<>();
            // Итерируем в обратном порядке, чтобы сохранить последний из дубликатов
            for (int i = AppVars.lastCookies.size() - 1; i >= 0; i--) {
                java.net.HttpCookie cookie = AppVars.lastCookies.get(i);
                if (!names.contains(cookie.getName())) {
                    filteredCookies.add(0, cookie); // Добавляем в начало, чтобы сохранить порядок
                    names.add(cookie.getName());
                }
            }

            CookieManager cookieManager = CookieManager.getInstance();
            String url = "http://neverlands.ru"; // Устанавливаем cookies для основного домена
            for (java.net.HttpCookie cookie : filteredCookies) {
                String cookieString = cookie.getName() + "=" + cookie.getValue() + "; domain=" + cookie.getDomain();
                cookieManager.setCookie(url, cookieString);
            }
            cookieManager.flush();
            AppVars.lastCookies = null; // Очищаем после использования
        }
    }

    private void loadInitialUrls() {
        WebView webView = binding.appBarMain.contentMain.webView;
        WebView chatMsgWebView = binding.appBarMain.contentMain.chatMsgWebview;
        WebView chatUsersWebView = binding.appBarMain.contentMain.chatUsersWebview;
        WebView chatButtonsWebView = binding.appBarMain.contentMain.chatButtonsWebview;

        // Загрузка URL
        webView.loadUrl("http://neverlands.ru/main.php");
        chatMsgWebView.loadUrl("http://neverlands.ru/ch/msg.php");
        chatUsersWebView.loadUrl("http://neverlands.ru/ch.php?lo=1");
        chatButtonsWebView.loadUrl("http://neverlands.ru/ch/but.php");
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

    private void setupWebView(WebView webView, WebViewClient client) {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setSupportMultipleWindows(true);

        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(client);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.e("JS_CONSOLE", consoleMessage.message() + " -- From line "
                        + consoleMessage.lineNumber() + " of "
                        + consoleMessage.sourceId());
                return true;
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                // Эта логика перехватывает ссылки, которые должны открыться в новом окне (target="_blank")
                // и принудительно загружает их в текущем WebView.
                WebView tempWebView = new WebView(MainActivity.this);
                tempWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        // Получив URL, загружаем его в основном WebView
                        binding.appBarMain.contentMain.webView.loadUrl(url);
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(tempWebView);
                resultMsg.sendToTarget();
                return true;
            }
        });
    }


    
    @Override
    protected void onDestroy() {
        ru.neverlands.abclient.utils.DebugLogger.log("MainActivity: onDestroy() called.");
        stopTimer();
        RoomManager.stopTracing();

        if (isExiting) {
            // ((ABClientApplication) getApplication()).stopProxyService();
        }

        // Уничтожаем все WebView, чтобы избежать утечек памяти
        destroyWebView(binding.appBarMain.contentMain.webView);
        destroyWebView(binding.appBarMain.contentMain.chatMsgWebview);
        destroyWebView(binding.appBarMain.contentMain.chatUsersWebview);
        destroyWebView(binding.appBarMain.contentMain.chatButtonsWebview);

        super.onDestroy();
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
        } else if (id == R.id.action_snapshot) {
            takeSnapshot();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void takeSnapshot() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        boolean mainSuccess = false;
        boolean chatSuccess = false;

        if (AppVars.lastMainPhpResponse != null) {
            String fileName = "HtmlLog_Main_" + timeStamp + ".txt";
            String html = Russian.getString(AppVars.lastMainPhpResponse);
            mainSuccess = ru.neverlands.abclient.utils.DataManager.writeStringToFile("Logs/" + fileName, html);
        }

        if (AppVars.lastChatMsgResponse != null) {
            String fileName = "HtmlLog_Chat_" + timeStamp + ".txt";
            String html = Russian.getString(AppVars.lastChatMsgResponse);
            chatSuccess = ru.neverlands.abclient.utils.DataManager.writeStringToFile("Logs/" + fileName, html);
        }

        if (mainSuccess || chatSuccess) {
            Toast.makeText(this, "Снимки кода сохранены в папке Logs", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Ошибка: нет данных для сохранения", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.nav_home) {
            binding.appBarMain.contentMain.webView.loadUrl("http://neverlands.ru/");

        } else if (id == R.id.nav_map) {
            binding.appBarMain.contentMain.webView.loadUrl("http://neverlands.ru/map.php");
        } else if (id == R.id.nav_inventory) {
            binding.appBarMain.contentMain.webView.loadUrl("http://neverlands.ru/main.php?get_id=33&act=10");
        } else if (id == R.id.nav_profile) {
            binding.appBarMain.contentMain.webView.loadUrl("http://neverlands.ru/main.php?get_id=33&act=1");
        } else if (id == R.id.nav_settings) {
            // Открытие настроек
        } else if (id == R.id.nav_contacts) {
            Intent intent = new Intent(this, ContactsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_logs) {
            Intent intent = new Intent(this, LogsActivity.class);
            startActivity(intent);
        }
        
        DrawerLayout drawer = binding.drawerLayout;
        drawer.closeDrawer(GravityCompat.START);
        return true;
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

    public void updateRoom(List<RoomManager.MenuItem> pvList, String travmText, List<RoomManager.MenuItem> travmList) {
        runOnUiThread(() -> {
            Spinner pvSpinner = binding.appBarMain.pvSpinner;
            Spinner travmSpinner = binding.appBarMain.travmSpinner;

            if (pvList != null) {
                List<String> pvTitles = new ArrayList<>();
                for (RoomManager.MenuItem item : pvList) {
                    pvTitles.add(item.title);
                }
                ArrayAdapter<String> pvAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, pvTitles);
                pvAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                pvSpinner.setAdapter(pvAdapter);
                pvSpinner.setEnabled(pvList.size() > 0);
            }

            if (travmList != null) {
                List<String> travmTitles = new ArrayList<>();
                travmTitles.add(travmText);
                for (RoomManager.MenuItem item : travmList) {
                    travmTitles.add(item.title);
                }
                ArrayAdapter<String> travmAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, travmTitles);
                travmAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                travmSpinner.setAdapter(travmAdapter);
                travmSpinner.setEnabled(travmList.size() > 0);
            }
        });
    }

    public void addMessageToChat(String message) {
        runOnUiThread(() -> {
            if (message != null) {
                Chat.addMessageToChat(message);
            }
        });
    }

    private class CustomWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            AppLogger.write("Page loaded: " + url);

            String script = "javascript:(function() { " +
                    "if (typeof top.start !== 'function') { top.start = function() {}; }" +
                    "if (typeof top.save_scroll_p !== 'function') { top.save_scroll_p = function() {}; }" +
                    "})()";
            view.evaluateJavascript(script, null);

            if (url.endsWith("main.php")) {
                view.evaluateJavascript("javascript:(function() { var frameset = document.getElementsByTagName('frameset')[0]; if (frameset) { frameset.rows = '*, 0'; } })()", null);
                if (!isRoomManagerStarted) {
                    RoomManager.startTracing(MainActivity.this);
                    isRoomManagerStarted = true;
                }
            } else if (url.contains("ch.php")) {
                view.evaluateJavascript("javascript:(function() { var frameset = document.getElementsByTagName('frameset')[0]; if (frameset) { frameset.cols = '0, *'; } })()", null);
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url != null && url.startsWith("http://neverlands.ru/pinfo.cgi")) {
                Intent intent = new Intent(MainActivity.this, PinfoActivity.class);
                intent.putExtra("url", url);
                startActivity(intent);
                return true; // Мы обработали этот URL
            }
            return false; // Позволяем WebView загрузить URL
        }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                final String url = request.getUrl().toString();
                ru.neverlands.abclient.utils.DebugLogger.log("Intercepting request: " + url);

                if (url.contains("main.php?get_id=") || url.contains("main.php?mselect=")) {
                    return null; // Let the WebView handle it.
                }

                String fileName = Uri.parse(url).getPath();
                if (fileName != null && fileName.startsWith("/")) {
                    fileName = fileName.substring(1);
                }

                try {
                    byte[] data = readAssetFile(fileName);
                    String mimeType = getMimeTypeFromUrl(url);

                    if (fileName.equals("ch/ch_list.js")) {
                        String js = Russian.getString(data);

                        // Создаем мост для совместимости со старым кодом, который вызывает window.external
                        String bridgeScript = "window.external = window.AndroidBridge;\n";

                        // Добавляем массив ChatListU, если он есть
                        if (AppVars.chatListU != null) {
                            js = "var ChatListU = new Array(" + AppVars.chatListU + ");\n" + js;
                        }

                        // Собираем все вместе
                        js = bridgeScript + js;
                        data = Russian.getBytes(js);
                    }

                    return new WebResourceResponse(mimeType, "UTF-8", new ByteArrayInputStream(data));
                } catch (IOException e) {
                    // Файл не найден в assets, продолжаем
                }

                // Попытка загрузки из дискового кэша
                byte[] cachedData = ru.neverlands.abclient.proxy.DiskCacheManager.get(url);
                if (cachedData != null) {
                    String mimeType = getMimeTypeFromUrl(url);
                    // Применяем фильтры к кэшированным данным так же, как к сетевым
                    byte[] processedData = ru.neverlands.abclient.postfilter.Filter.process(url, cachedData);
                    return new WebResourceResponse(mimeType, "windows-1251", new ByteArrayInputStream(processedData));
                }

                try {
                    // 1. Создание соединения
                    URL urlObj = new URL(url);
                    HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();

                    // 2. Настройка заголовков запроса
                    Map<String, String> requestHeaders = request.getRequestHeaders();
                    ru.neverlands.abclient.utils.DebugLogger.log("Request Headers for " + url + ": " + requestHeaders.toString());
                    for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                    connection.setRequestProperty("Cookie", CookiesManager.obtain(url));

                    // 3. Получение ответа
                    InputStream inputStream = connection.getInputStream();
                    String contentType = connection.getContentType();
                    String encoding = connection.getContentEncoding();

                    ru.neverlands.abclient.utils.DebugLogger.log("Response Headers for " + url + ": " + connection.getHeaderFields().toString());

                    // Получаем MIME-тип
                    String mimeType = "text/plain";
                    if (contentType != null) {
                        if (contentType.contains(";")) {
                            mimeType = contentType.split(";")[0].trim();
                        } else {
                            mimeType = contentType.trim();
                        }
                    }

                    byte[] data;
                    // 4. Чтение и обработка ответа
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    data = baos.toByteArray();

                    ru.neverlands.abclient.utils.DebugLogger.log("Response Body for " + url + ": " + new String(data));

                    if (isCacheable(url)) {
                        ru.neverlands.abclient.proxy.DiskCacheManager.put(url, data);
                    }

                    if ("gzip".equalsIgnoreCase(encoding)) {
                        data = decompressGzip(data);
                        encoding = null; // Мы его распаковали
                    }

                    if (url.contains("ch.php?lo=1")) {
                        ru.neverlands.abclient.utils.DebugLogger.log("ch.php?lo=1 - Raw data before Russian.getString: " + new String(data, "windows-1251"));
                        String html = Russian.getString(data);
                        ru.neverlands.abclient.utils.DebugLogger.log("ch.php?lo=1 - HTML after Russian.getString: " + html);
                        Pattern pattern = Pattern.compile("var ChatListU = new Array\\((.*)\\);", Pattern.DOTALL);
                        Matcher matcher = pattern.matcher(html);
                        if (matcher.find()) {
                            AppVars.chatListU = matcher.group(1);
                            ru.neverlands.abclient.utils.DebugLogger.log("ch.php?lo=1 - Extracted ChatListU: " + AppVars.chatListU);
                        }
                    }

                    if (contentType != null && contentType.contains("text/html")) {
                        data = injectJsFix(data, url);
                    }

                    // Применяем другие фильтры, если необходимо
                    data = ru.neverlands.abclient.postfilter.Filter.process(url, data);

                    ru.neverlands.abclient.utils.DebugLogger.log("Final processedData for " + url + ": " + new String(data, "windows-1251"));

                    // 5. Возвращаем обработанный ответ
                    return new WebResourceResponse(mimeType, "windows-1251", new ByteArrayInputStream(data));

                } catch (IOException e) {
                    ru.neverlands.abclient.utils.DebugLogger.log("Error intercepting request: " + url + " - " + e.getMessage());
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
                }
            }
    }

    private byte[] readAssetFile(String fileName) throws IOException {
        AssetManager assetManager = getAssets();
        InputStream inputStream = assetManager.open(fileName);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    private String getMimeTypeFromUrl(String url) {
        if (url.endsWith(".css")) return "text/css";
        if (url.endsWith(".js")) return "application/javascript";
        if (url.endsWith(".jpg") || url.endsWith(".jpeg")) return "image/jpeg";
        if (url.endsWith(".png")) return "image/png";
        if (url.endsWith(".gif")) return "image/gif";
        if (url.endsWith(".ico")) return "image/x-icon";
        if (url.endsWith(".swf")) return "application/x-shockwave-flash";
        if (url.contains(".php") || url.endsWith("/") || !url.substring(url.lastIndexOf("/") + 1).contains(".")) return "text/html";
        return "text/plain";
    }

    private byte[] decompressGzip(byte[] compressedData) throws IOException {
        if (compressedData == null || compressedData.length == 0) {
            return compressedData;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzis = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    private boolean isCacheable(String url) {
        String lowerUrl = url.toLowerCase();
        return lowerUrl.endsWith(".gif") || lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") ||
               lowerUrl.endsWith(".png") || lowerUrl.endsWith(".swf") || lowerUrl.endsWith(".ico") ||
               lowerUrl.endsWith(".css") || lowerUrl.contains(".js") ||
               // Также кэшируем страницы чата и главную
               lowerUrl.contains("neverlands.ru/ch.php") || lowerUrl.contains("neverlands.ru/main.php");
    }

    private byte[] injectJsFix(byte[] body, String url) {
        try {
            if (body == null || body.length == 0) {
                Log.w(TAG, "InjectJsFix: body for " + url + " is empty!");
                return body;
            }

            String html = new String(body, "windows-1251");

            String fix = "<script type=\"text/javascript\">" +
                "if (typeof top.start == 'undefined') { top.start = function() {}; }" +
                "if (typeof window.chatlist_build == 'undefined') { window.chatlist_build = function() {}; }" +
                "if (typeof window.get_by_id == 'undefined') { window.get_by_id = function(id) { return document.getElementById(id); }; }" +
                "if (typeof top.save_scroll_p == 'undefined') { top.save_scroll_p = function() {}; }" +
                "if (typeof window.ins_HP == 'undefined') { window.ins_HP = function() {}; }" +
                "if (typeof window.slots_inv == 'undefined') { window.slots_inv = function() {}; }" +
                "if (typeof window.compl_view == 'undefined') { window.compl_view = function() {}; }" +
                "if (typeof window.view_t == 'undefined') { window.view_t = function() {}; }" +
                "if (typeof top.ch_refresh_n == 'undefined') { top.ch_refresh_n = function() {}; }" +
                "if (typeof window.ButClick == 'undefined') { window.ButClick = function() {}; }" +
                "</script>";

            String newHtml;
            if (html.toLowerCase().contains("<head>")) {
                newHtml = html.replaceFirst("(?i)<head>", "<head>" + fix);
            } else {
                newHtml = fix + html;
            }
            
            return newHtml.getBytes("windows-1251");

        } catch (Exception e) {
            Log.e(TAG, "Failed to inject JS fix for " + url, e);
            return body; // Возвращаем оригинал в случае ошибки
        }
    }
}
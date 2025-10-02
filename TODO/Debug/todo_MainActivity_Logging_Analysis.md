# Анализ логирования в MainActivity.java

## Обзор

Данный документ содержит анализ существующего логирования в файле `MainActivity.java` и предложения по добавлению нового логирования для более детальной отладки жизненного цикла Activity, работы WebView, обработки куки и других ключевых взаимодействий.

## Константа TAG

`private static final String TAG = "MainActivity";`

## Существующее логирование по функциям/методам

### `onCreate(Bundle savedInstanceState)`
*   **Тип:** `e.printStackTrace()`
*   **Сообщение:** Стек вызовов при `PackageManager.NameNotFoundException`.
*   **Назначение:** Выводит информацию об ошибке получения версии пакета.

### `setupWebView(WebView webView, WebViewClient client)` (в `WebChromeClient.onConsoleMessage`)
*   **Тип:** `Log.e`
*   **Тег:** `"JS_CONSOLE"`
*   **Сообщение:** Сообщения из JavaScript-консоли.
*   **Назначение:** Логирует ошибки и предупреждения из JavaScript, выполняющегося в WebView.

### `WebViewClient.onPageFinished(WebView view, String url)` (анонимный класс)
*   **Тип:** `AppLogger.write`
*   **Сообщение:** `"Page loaded: " + url`
*   **Назначение:** Логирует завершение загрузки страницы в WebView с использованием кастомного логгера.

### `onDestroy()`
*   **Тип:** `ru.neverlands.abclient.utils.DebugLogger.log`
*   **Сообщение:** `"MainActivity: onDestroy() called."`
*   **Назначение:** Логирует вызов метода `onDestroy()` с использованием кастомного логгера.

## Предлагаемое дополнительное логирование

Для более полного понимания поведения `MainActivity` и его взаимодействия с другими компонентами, предлагается добавить следующие `Log.d` сообщения:

```java
// В onCreate(Bundle savedInstanceState)
@Override
protected void onCreate(Bundle savedInstanceState) {
    Log.d(TAG, "MainActivity onCreate started.");
    System.setProperty("http.proxyHost", AppVars.LocalProxyAddress);
    System.setProperty("http.proxyPort", String.valueOf(AppVars.LocalProxyPort));
    Log.d(TAG, "System proxy set to " + AppVars.LocalProxyAddress + ":" + AppVars.LocalProxyPort);

    super.onCreate(savedInstanceState);
    
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    
    Toolbar toolbar = binding.appBarMain.toolbar;
    setSupportActionBar(toolbar);
    if (AppVars.Profile != null && AppVars.Profile.UserNick != null) {
        getSupportActionBar().setTitle(AppVars.Profile.UserNick);
        Log.d(TAG, "Toolbar title set to UserNick: " + AppVars.Profile.UserNick);
    } else {
        Log.d(TAG, "Toolbar title not set (UserNick is null).");
    }
    
    // ... (Настройка DrawerLayout и NavigationView)

    try {
        String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        navHeaderTitle.setText("v" + versionName);
        Log.d(TAG, "App version displayed: v" + versionName);
    } catch (PackageManager.NameNotFoundException e) {
        Log.e(TAG, "Error getting package version name", e); // Используем Log.e вместо e.printStackTrace()
        navHeaderTitle.setText("");
    }

    ((ABClientApplication) getApplication()).startProxyService();
    Log.d(TAG, "ProxyService started.");

    setupAndLoadWebViews();
    Log.d(TAG, "WebViews setup and loaded.");

    AppVars.NextCheckNoConnection = new Date();
    startTimer();
    Log.d(TAG, "Timer started.");
    Log.d(TAG, "MainActivity onCreate finished.");
}

// В setupAndLoadWebViews()
@SuppressLint("SetJavaScriptEnabled")
private void setupAndLoadWebViews() {
    Log.d(TAG, "setupAndLoadWebViews started.");
    // ... (Объявления WebView)

    WebViewClient simpleWebViewClient = new WebViewClient() {
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            AppLogger.write("Page loaded: " + url); // Сохраняем кастомный логгер
            Log.d(TAG, "WebViewClient: Page finished loading: " + url);

            String script = "javascript:(function() { " +
                    "if (typeof top.start !== 'function') { top.start = function() {}; }" +
                    "if (typeof top.save_scroll_p !== 'function') { top.save_scroll_p = function() {}; }" +
                    "})()";
            view.evaluateJavascript(script, null);
            Log.d(TAG, "WebViewClient: JavaScript evaluated for URL: " + url);

            if (url.endsWith("main.php")) {
                view.evaluateJavascript("javascript:(function() { var frameset = document.getElementsByTagName('frameset')[0]; if (frameset) { frameset.rows = '*, 0'; } })()", null);
                Log.d(TAG, "WebViewClient: Applied frameset adjustment for main.php");
            } else if (url.contains("ch.php")) {
                view.evaluateJavascript("javascript:(function() { var frameset = document.getElementsByTagName('frameset')[0]; if (frameset) { frameset.cols = '0, *'; } })()", null);
                Log.d(TAG, "WebViewClient: Applied frameset adjustment for ch.php");
            }
        }
        
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Log.d(TAG, "WebViewClient: Intercepting request for URL: " + request.getUrl().toString());
            // Здесь можно добавить более детальное логирование для поведения кэша,
            // если WebViewClient участвует в принятии решений о кэшировании.
            return super.shouldInterceptRequest(view, request);
        }
    };

    setupWebView(webView, simpleWebViewClient);
    setupWebView(chatMsgWebView, simpleWebViewClient);
    setupWebView(chatUsersWebView, simpleWebViewClient);
    setupWebView(chatButtonsWebView, simpleWebViewClient);
    Log.d(TAG, "All WebViews setup.");

    // Внедряем cookies, полученные после авторизации
    if (AppVars.lastCookies != null && !AppVars.lastCookies.isEmpty()) {
        Log.d(TAG, "Attempting to inject " + AppVars.lastCookies.size() + " cookies from AppVars.lastCookies.");
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
        String url = "http://neverlands.ru";
        for (java.net.HttpCookie cookie : filteredCookies) {
            String cookieString = cookie.getName() + "=" + cookie.getValue() + "; domain=" + cookie.getDomain();
            cookieManager.setCookie(url, cookieString);
            Log.d(TAG, "Injected cookie: " + cookieString + " for URL: " + url);
        }
        cookieManager.flush();
        Log.d(TAG, "CookieManager flushed after injection.");
        AppVars.lastCookies = null;
        Log.d(TAG, "AppVars.lastCookies cleared.");
    } else {
        Log.d(TAG, "No cookies to inject from AppVars.lastCookies.");
    }

    webView.loadUrl("http://neverlands.ru/main.php");
    chatMsgWebView.loadUrl("http://neverlands.ru/ch/msg.php");
    chatUsersWebView.loadUrl("http://neverlands.ru/ch.php?lo=1");
    chatButtonsWebView.loadUrl("http://neverlands.ru/ch/but.php");
    Log.d(TAG, "Initial URLs loaded into WebViews.");
    Log.d(TAG, "setupAndLoadWebViews finished.");
}

// В onResume()
@Override
protected void onResume() {
    super.onResume();
    Log.d(TAG, "MainActivity onResume called. Registering broadcast receiver.");
    IntentFilter filter = new IntentFilter();
    filter.addAction(AppVars.ACTION_ADD_CHAT_MESSAGE);
    filter.addAction(AppVars.ACTION_WEBVIEW_LOAD_URL);
    filter.addAction(AppVars.ACTION_STOP_AUTOFISH);
    LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter);
}

// В onPause()
@Override
protected void onPause() {
    super.onPause();
    Log.d(TAG, "MainActivity onPause called. Unregistering broadcast receiver.");
    LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
}

// В onDestroy()
@Override
protected void onDestroy() {
    ru.neverlands.abclient.utils.DebugLogger.log("MainActivity: onDestroy() called."); // Сохраняем кастомный логгер
    Log.d(TAG, "MainActivity onDestroy called. Stopping timer and destroying WebViews.");
    stopTimer();

    if (isExiting) {
        ((ABClientApplication) getApplication()).stopProxyService();
        Log.d(TAG, "ProxyService stopped due to app exiting.");
    } else {
        Log.d(TAG, "ProxyService not stopped (app not exiting). ");
    }

    // Уничтожаем все WebView, чтобы избежать утечек памяти
    destroyWebView(binding.appBarMain.contentMain.webView);
    destroyWebView(binding.appBarMain.contentMain.chatMsgWebview);
    destroyWebView(binding.appBarMain.contentMain.chatUsersWebview);
    destroyWebView(binding.appBarMain.contentMain.chatButtonsWebview);
    Log.d(TAG, "All WebViews destroyed.");

    super.onDestroy();
    Log.d(TAG, "MainActivity onDestroy finished.");
}

// В BroadcastReceiver.onReceive()
private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "BroadcastReceiver onReceive: Action = " + action);
        if (action == null) return;

        switch (action) {
            case AppVars.ACTION_ADD_CHAT_MESSAGE:
                String message = intent.getStringExtra("message");
                if (message != null) {
                    Chat.addMessageToChat(message);
                    Log.d(TAG, "BroadcastReceiver: Added chat message: " + message);
                } else {
                    Log.d(TAG, "BroadcastReceiver: Received ACTION_ADD_CHAT_MESSAGE with null message.");
                }
                break;
            case AppVars.ACTION_WEBVIEW_LOAD_URL:
                String url = intent.getStringExtra("url");
                if (url != null && binding.appBarMain.contentMain.webView != null) {
                    binding.appBarMain.contentMain.webView.loadUrl(url);
                    Log.d(TAG, "BroadcastReceiver: WebView loading URL: " + url);
                }
                break;
            case AppVars.ACTION_STOP_AUTOFISH:
                Toast.makeText(context, "Авторыбалка остановлена", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "BroadcastReceiver: Autofish stopped.");
                break;
            default:
                Log.d(TAG, "BroadcastReceiver: Unhandled action: " + action);
                break;
        }
    }
};

// В startTimer()
private void startTimer() {
    Log.d(TAG, "startTimer called.");
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

// В stopTimer()
private void stopTimer() {
    if (timer != null) {
        timer.cancel();
        timer = null;
        Log.d(TAG, "Timer stopped.");
    } else {
        Log.d(TAG, "stopTimer called, but timer was null.");
    }
}

// В checkConnection()
private void checkConnection() {
    if (System.currentTimeMillis() > AppVars.NextCheckNoConnection.getTime()) {
        AppVars.NextCheckNoConnection = new Date(System.currentTimeMillis() + 5 * 60 * 1000);
        binding.appBarMain.contentMain.webView.loadUrl("http://www.neverlands.ru/main.php");
        Log.d(TAG, "checkConnection: No connection detected, reloading main.php. Next check in 5 minutes.");
    } else {
        // Log.d(TAG, "checkConnection: Connection check not due yet."); // Слишком много логов, включать при необходимости
    }
}

// В takeSnapshot()
private void takeSnapshot() {
    Log.d(TAG, "takeSnapshot called.");
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    boolean mainSuccess = false;
    boolean chatSuccess = false;

    if (AppVars.lastMainPhpResponse != null) {
        String fileName = "HtmlLog_Main_" + timeStamp + ".txt";
        String html = Russian.getString(AppVars.lastMainPhpResponse);
        mainSuccess = ru.neverlands.abclient.utils.DataManager.writeStringToFile("Logs/" + fileName, html);
        Log.d(TAG, "Snapshot: Main HTML written to " + fileName + ". Success: " + mainSuccess);
    } else {
        Log.d(TAG, "Snapshot: No main HTML response to save.");
    }

    if (AppVars.lastChatMsgResponse != null) {
        String fileName = "HtmlLog_Chat_" + timeStamp + ".txt";
        String html = Russian.getString(AppVars.lastChatMsgResponse);
        chatSuccess = ru.neverlands.abclient.utils.DataManager.writeStringToFile("Logs/" + fileName, html);
        Log.d(TAG, "Snapshot: Chat HTML written to " + fileName + ". Success: " + chatSuccess);
    } else {
        Log.d(TAG, "Snapshot: No chat HTML response to save.");
    }

    if (mainSuccess || chatSuccess) {
        Toast.makeText(this, "Снимки кода сохранены в папке Logs", Toast.LENGTH_SHORT).show();
    } else {
        Toast.makeText(this, "Ошибка: нет данных для сохранения", Toast.LENGTH_SHORT).show();
    }
}

// В onNavigationItemSelected(@NonNull MenuItem item)
@Override
public boolean onNavigationItemSelected(@NonNull MenuItem item) {
    int id = item.getItemId();
    Log.d(TAG, "Navigation item selected: " + getResources().getResourceEntryName(id));
    String urlToLoad = null;
    
    if (id == R.id.nav_home) {
        urlToLoad = "http://neverlands.ru/";
    } else if (id == R.id.nav_map) {
        urlToLoad = "http://neverlands.ru/map.php";
    } else if (id == R.id.nav_inventory) {
        urlToLoad = "http://neverlands.ru/main.php?get_id=33&act=10";
    } else if (id == R.id.nav_profile) {
        urlToLoad = "http://neverlands.ru/main.php?get_id=33&act=1";
    } else if (id == R.id.nav_settings) {
        Log.d(TAG, "Navigation: Settings item selected (no URL load).");
        // Открытие настроек
    } else if (id == R.id.nav_logs) {
        Intent intent = new Intent(this, LogsActivity.class);
        startActivity(intent);
        Log.d(TAG, "Navigation: Logs item selected. Starting LogsActivity.");
    }
    
    if (urlToLoad != null) {
        binding.appBarMain.contentMain.webView.loadUrl(urlToLoad);
        Log.d(TAG, "Navigation: WebView loading URL: " + urlToLoad);
    }
    
    DrawerLayout drawer = binding.drawerLayout;
    drawer.closeDrawer(GravityCompat.START);
    return true;
}

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
import java.net.URLConnection;
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
import ru.neverlands.abclient.manager.ContactsManager;
import ru.neverlands.abclient.databinding.ActivityMainBinding;
import ru.neverlands.abclient.manager.RoomManager;
import ru.neverlands.abclient.model.UserConfig;
import ru.neverlands.abclient.proxy.CookiesManager;
import ru.neverlands.abclient.utils.AppLogger;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Chat;
import ru.neverlands.abclient.utils.Russian;
import ru.neverlands.abclient.webview.WebViewRequestInterceptor;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "MainActivity";
    public ActivityMainBinding binding;
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
        isRoomManagerStarted = false;
        AppVars.init(this);
        ContactsManager.initialize(this);
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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                String versionName = getPackageManager().getPackageInfo(getPackageName(), PackageManager.PackageInfoFlags.of(0)).versionName;
                navHeaderTitle.setText("v" + versionName);
            } else {
                String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                navHeaderTitle.setText("v" + versionName);
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            navHeaderTitle.setText("");
        }

        setupWebViews();
        loadInitialUrls();

        AppVars.NextCheckNoConnection = new Date(System.currentTimeMillis());
        startTimer();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebViews() {
        WebView webView = binding.appBarMain.contentMain.webView;
        WebView chatMsgWebView = binding.appBarMain.contentMain.chatMsgWebview;
        WebView chatUsersWebView = binding.appBarMain.contentMain.chatUsersWebview;
        WebView chatButtonsWebView = binding.appBarMain.contentMain.chatButtonsWebview;

        CustomWebViewClient customWebViewClient = new CustomWebViewClient();

        setupWebView(webView, customWebViewClient);
        setupWebView(chatMsgWebView, customWebViewClient);
        setupWebView(chatUsersWebView, customWebViewClient);
        setupWebView(chatButtonsWebView, customWebViewClient);

        if (AppVars.lastCookies != null && !AppVars.lastCookies.isEmpty()) {
            java.util.List<java.net.HttpCookie> filteredCookies = new java.util.ArrayList<>();
            java.util.Set<String> names = new java.util.HashSet<>();
            for (int i = AppVars.lastCookies.size() - 1; i >= 0; i--) {
                java.net.HttpCookie cookie = AppVars.lastCookies.get(i);
                if (!names.contains(cookie.getName())) {
                    filteredCookies.add(0, cookie);
                    names.add(cookie.getName());
                }
            }

            CookieManager cookieManager = CookieManager.getInstance();
            String url = "http://neverlands.ru";
            for (java.net.HttpCookie cookie : filteredCookies) {
                String cookieString = cookie.getName() + "=" + cookie.getValue() + "; domain=" + cookie.getDomain();
                cookieManager.setCookie(url, cookieString);
            }
            cookieManager.flush();
            AppVars.lastCookies = null;
        }
    }

    private void loadInitialUrls() {
        WebView webView = binding.appBarMain.contentMain.webView;
        WebView chatMsgWebView = binding.appBarMain.contentMain.chatMsgWebview;
        WebView chatUsersWebView = binding.appBarMain.contentMain.chatUsersWebview;
        WebView chatButtonsWebView = binding.appBarMain.contentMain.chatButtonsWebview;

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
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
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
                WebView tempWebView = new WebView(MainActivity.this);
                tempWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        binding.appBarMain.contentMain.webView.loadUrl(request.getUrl().toString());
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

        destroyWebView(binding.appBarMain.contentMain.webView);
        destroyWebView(binding.appBarMain.contentMain.chatMsgWebview);
        destroyWebView(binding.appBarMain.contentMain.chatUsersWebview);
        destroyWebView(binding.appBarMain.contentMain.chatButtonsWebview);

        super.onDestroy();
    }

    private void destroyWebView(WebView webView) {
        if (webView != null) {
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
            return true;
        } else if (id == R.id.action_snapshot) {
            takeSnapshot();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void takeSnapshot() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date(System.currentTimeMillis()));
        boolean mainSuccess = false;
        boolean chatSuccess = false;
        boolean chatUsersSuccess = false;

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

        binding.appBarMain.contentMain.chatUsersWebview.evaluateJavascript(
                "(function() { return '<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>'; })();",
                html -> {
                    String fileName = "HtmlLog_ChatUsers_" + timeStamp + ".txt";
                    ru.neverlands.abclient.utils.DataManager.writeStringToFile("Logs/" + fileName, html);
                });

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
        } else if (id == R.id.nav_quick_actions) {
            ru.neverlands.abclient.ui.QuickActionsBottomSheet.newInstance(null).show(getSupportFragmentManager(), "QuickActions");
        } else if (id == R.id.nav_settings) {
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
    
    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
    
    private void updateClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        binding.appBarMain.contentMain.statusBar.clockTextView.setText(sdf.format(new Date(System.currentTimeMillis())));
    }
    
    public void updateServerTime(Date serverDateTime) {
        AppVars.ServerDateTime = serverDateTime;
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        binding.appBarMain.contentMain.statusBar.serverTimeTextView.setText(sdf.format(serverDateTime));
    }
    
    private void checkConnection() {
        if (System.currentTimeMillis() > AppVars.NextCheckNoConnection.getTime()) {
            AppVars.NextCheckNoConnection = new Date(System.currentTimeMillis() + 5 * 60 * 1000);
            binding.appBarMain.contentMain.webView.loadUrl("http://neverlands.ru/main.php");
        }
    }
    
    public void addAddressToStatusString(String address) {
        binding.appBarMain.contentMain.statusBar.statusTextView.setText(address);
    }
    
    public void removeAddressFromStatusString(String address) {
        if (binding.appBarMain.contentMain.statusBar.statusTextView.getText().toString().equals(address)) {
            binding.appBarMain.contentMain.statusBar.statusTextView.setText("");
        }
    }
    
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
    
    public void updateSavedTrafficSafe(int bytes) {
        runOnUiThread(() -> updateSavedTraffic(bytes));
    }
    
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

            String jsFix =
                "window.external = window.AndroidBridge;" +
                "if (typeof top.start == 'undefined') { top.start = function() {}; }" +
                "if (typeof window.chatlist_build == 'undefined') { window.chatlist_build = function() {}; }" +
                "if (typeof window.get_by_id == 'undefined') { window.get_by_id = function(id) { return document.getElementById(id); }; }" +
                "if (typeof top.save_scroll_p == 'undefined') { top.save_scroll_p = function() {}; }" +
                "if (typeof window.ins_HP == 'undefined') { window.ins_HP = function() {}; }" +
                "if (typeof window.cha_HP == 'undefined') { window.cha_HP = function() {}; }" +
                "if (typeof window.slots_inv == 'undefined') { window.slots_inv = function() {}; }" +
                "if (typeof window.compl_view == 'undefined') { window.compl_view = function() {}; }" +
                "if (typeof window.view_t == 'undefined') { window.view_t = function() {}; }" +
                "if (typeof top.ch_refresh_n == 'undefined') { top.ch_refresh_n = function() {}; }" +
                "if (typeof window.ButClick == 'undefined') { window.ButClick = function() {}; }" +
                "if (typeof top.frames == 'undefined' || !top.frames['main_top']) { " +
                "  if (typeof top.frames == 'undefined') { top.frames = {}; } " +
                "  if (!top.frames['ch_buttons']) { top.frames['ch_buttons'] = { set location(url) { AndroidBridge.loadFrame('ch_buttons', url); } }; } " +
                "  if (!top.frames['ch_refr']) { top.frames['ch_refr'] = { set location(url) { AndroidBridge.loadFrame('ch_refr', url); } }; } " +
                "  if (!top.frames['ch_list']) { top.frames['ch_list'] = { set location(url) { AndroidBridge.loadFrame('ch_list', url); } }; } " +
                "  if (!top.frames['chmain']) { top.frames['chmain'] = { set location(url) { AndroidBridge.loadFrame('chmain', url); } }; } " +
                "  if (!top.frames['main_top']) { top.frames['main_top'] = { " +
                "    set location(url) { AndroidBridge.loadFrame('main_top', url); }, " +
                "    innerHeight: 800, " +
                "    innerWidth: 600, " +
                "    document: { " +
                "      write: function(s) { document.write(s); }, " +
                "      getElementById: function(id) { return document.getElementById(id); } " +
                "    } " +
                "  }; } " +
                "}" +
                "if (top.frames && top.frames['main_top']) { top.frames['main_top'].innerHeight = 800; top.frames['main_top'].innerWidth = 600; }";

            view.evaluateJavascript(jsFix, null);

            if (url.endsWith("main.php")) {
                view.evaluateJavascript("javascript:(function() { var frameset = document.getElementsByTagName('frameset')[0]; if (frameset) { frameset.rows = '*\n, 0'; } })()", null);
                if (!isRoomManagerStarted) {
                    ru.neverlands.abclient.manager.RoomManager.startTracing(MainActivity.this);
                    isRoomManagerStarted = true;
                }
            } else if (url.contains("ch.php")) {
                view.evaluateJavascript("javascript:(function() { var frameset = document.getElementsByTagName('frameset')[0]; if (frameset) { frameset.cols = '0, *'; } })()", null);
            }
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return WebViewRequestInterceptor.intercept(request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (request.getUrl().toString() != null && request.getUrl().toString().startsWith("http://neverlands.ru/pinfo.cgi")) {
                Intent intent = new Intent(MainActivity.this, PinfoActivity.class);
                intent.putExtra("url", request.getUrl().toString());
                startActivity(intent);
                return true;
            }
            return false;
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
               lowerUrl.contains("neverlands.ru/ch.php") || lowerUrl.contains("neverlands.ru/main.php");
    }

    private byte[] injectJsFix(byte[] body, String url, String contentType) {
        try {
            if (body == null || body.length == 0) {
                Log.w(TAG, "InjectJsFix: body for " + url + " is empty!");
                return body;
            }

            String jsFix =
                "window.external = window.AndroidBridge;" +
                "if (typeof top.start == 'undefined') { top.start = function() {}; }" +
                "if (typeof window.chatlist_build == 'undefined') { window.chatlist_build = function() {}; }" +
                "if (typeof window.get_by_id == 'undefined') { window.get_by_id = function(id) { return document.getElementById(id); }; }" +
                "if (typeof top.save_scroll_p == 'undefined') { top.save_scroll_p = function() {}; }" +
                "if (typeof window.ins_HP == 'undefined') { window.ins_HP = function() {}; }" +
                "if (typeof window.cha_HP == 'undefined') { window.cha_HP = function() {}; }" +
                "if (typeof window.slots_inv == 'undefined') { window.slots_inv = function() {}; }" +
                "if (typeof window.compl_view == 'undefined') { window.compl_view = function() {}; }" +
                "if (typeof window.view_t == 'undefined') { window.view_t = function() {}; }" +
                "if (typeof top.ch_refresh_n == 'undefined') { top.ch_refresh_n = function() {}; }" +
                "if (typeof window.ButClick == 'undefined') { window.ButClick = function() {}; }" +
                "if (typeof top.frames == 'undefined' || !top.frames['main_top']) { " +
                "  if (typeof top.frames == 'undefined') { top.frames = {}; } " +
                "  if (!top.frames['ch_buttons']) { top.frames['ch_buttons'] = { set location(url) { AndroidBridge.loadFrame('ch_buttons', url); } }; } " +
                "  if (!top.frames['ch_refr']) { top.frames['ch_refr'] = { set location(url) { AndroidBridge.loadFrame('ch_refr', url); } }; } " +
                "  if (!top.frames['ch_list']) { top.frames['ch_list'] = { set location(url) { AndroidBridge.loadFrame('ch_list', url); } }; } " +
                "  if (!top.frames['chmain']) { top.frames['chmain'] = { set location(url) { AndroidBridge.loadFrame('chmain', url); } }; } " +
                "  if (!top.frames['main_top']) { top.frames['main_top'] = { " +
                "    set location(url) { AndroidBridge.loadFrame('main_top', url); }, " +
                "    innerHeight: 800, " +
                "    innerWidth: 600, " +
                "    document: { " +
                "      write: function(s) { document.write(s); }, " +
                "      getElementById: function(id) { return document.getElementById(id); } " +
                "    } " +
                "  }; } " +
                "}" +
                "if (top.frames && top.frames['main_top']) { top.frames['main_top'].innerHeight = 800; top.frames['main_top'].innerWidth = 600; }";

            if (contentType != null && contentType.contains("text/html")) {
                String html = new String(body, "windows-1251");
                String fix = "<script type=\"text/javascript\">" + jsFix + "</script>";

                String newHtml;
                if (html.toLowerCase().contains("<head>")) {
                    newHtml = html.replaceFirst("(?i)<head>", "<head>" + fix);
                } else {
                    newHtml = fix + html;
                }
                return newHtml.getBytes("windows-1251");
            } else if (contentType != null && contentType.contains("application/javascript")) {
                String js = new String(body, "windows-1251");
                return (jsFix + js).getBytes("windows-1251");
            }

            return body;

        } catch (Exception e) {
            Log.e(TAG, "Failed to inject JS fix for " + url, e);
            return body;
        }
    }
}

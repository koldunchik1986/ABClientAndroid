package ru.neverlands.abclient.manager;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ru.neverlands.abclient.R;
import ru.neverlands.abclient.bridge.WebAppInterface;
import ru.neverlands.abclient.manager.ContactsManager;
import ru.neverlands.abclient.model.Contact;
import ru.neverlands.abclient.webview.WebViewRequestInterceptor;

/**
 * Менеджер вкладок (аналог TabControl в C# версии ABClient).
 * Управляет системой горизонтальных вкладок:
 * - Вкладка 0 (Основная): main.php + чат — не закрывается
 * - Вкладки 1+ (Вспомогательные): pinfo, форум и т.д. — можно закрывать
 */
public class TabManager {
    private static final String TAG = "TabManager";

    /** Типы вкладок для определения набора кнопок */
    public enum TabType {
        FORUM,    // Форум: Назад, Обновить, Копировать URL
        PINFO,    // PINFO: Обновить, Добавить в контакты, Закрыть
        OTHER     // Другие: только закрытие
    }

    /** Информация о вспомогательной вкладке */
    public static class TabInfo {
        public String title;
        public String url;
        public WebView webView;
        public View contentView; // корневой View вкладки
        public TabType tabType;  // тип вкладки для определения кнопок

        public TabInfo(String title, String url, WebView webView, View contentView, TabType tabType) {
            this.title = title;
            this.url = url;
            this.webView = webView;
            this.contentView = contentView;
            this.tabType = tabType;
        }
    }

    private final Context context;
    private final TabLayout tabLayout;
    private final View mainContent;           // LinearLayout основной вкладки
    private final FrameLayout secondaryContainer; // контейнер для вспомогательных вкладок
    private final List<TabInfo> secondaryTabs = new ArrayList<>();
    private int currentTabIndex = 0; // 0 = основная, 1+ = вспомогательные

    // Инициализация менеджера вкладок и базовой "Основной" вкладки.
    public TabManager(Context context, TabLayout tabLayout, View mainContent, FrameLayout secondaryContainer) {
        this.context = context;
        this.tabLayout = tabLayout;
        this.mainContent = mainContent;
        this.secondaryContainer = secondaryContainer;

        // Добавляем основную вкладку
        TabLayout.Tab mainTab = tabLayout.newTab();
        mainTab.setText("Основная");
        tabLayout.addTab(mainTab);

        // Слушатель переключения вкладок
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                switchToTab(position);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    /**
     * Открыть URL в новой вспомогательной вкладке.
     * Если вкладка с таким URL уже открыта — переключиться на неё и обновить.
     *
     * @param url   URL для загрузки
     * @param title Заголовок вкладки
     */
    // Открытие URL в новой вкладке (или фокус уже существующей).
    public void openTab(String url, String title) {
        // ==================== ДЕКОДИРОВАНИЕ КИРИЛЛИЧЕСКИХ НИКОВ (внутри TabManager) ====================
        // Аналог NickDecode из HelperConverters.cs и openInNewTab из MainActivity
        if ("PINFO".equals(title) && url != null) {
            try {
                String decoded = URLDecoder.decode(url, "windows-1251");
                int idx = decoded.indexOf("pinfo.cgi?");
                if (idx != -1) {
                    String nick = decoded.substring(idx + 10);
                    nick = nick.replace("|", " ").replace("%20", " ").replace("%2B", "+");
                    title = nick;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error decoding nick", e);
                title = "PINFO"; // fallback
            }
        }
        // =================================================================================================

        // Нормализуем URL (убираем www для корректного сравнения)
        String normalizedUrl = normalizeUrl(url);
        
        // Проверяем, не открыта ли уже такая вкладка
        for (int i = 0; i < secondaryTabs.size(); i++) {
            TabInfo tab = secondaryTabs.get(i);
            String tabNormalizedUrl = normalizeUrl(tab.url);
            if (tabNormalizedUrl != null && tabNormalizedUrl.equals(normalizedUrl)) {
                Log.d(TAG, "openTab: вкладка уже открыта, переключаемся и обновляем: " + url);
                // Переключаемся на вкладку
                tabLayout.selectTab(tabLayout.getTabAt(i + 1));
                // Обновляем страницу
                if (tab.webView != null) {
                    tab.webView.reload();
                }
                return;
            }
        }

        Log.d(TAG, "openTab: открываем новую вкладку: " + title + " -> " + url);

        // Создаём View для вкладки
        LayoutInflater inflater = LayoutInflater.from(context);
        View contentView = inflater.inflate(R.layout.tab_secondary, secondaryContainer, false);
        WebView webView = contentView.findViewById(R.id.secondary_webview);

        // Настраиваем WebView
        setupSecondaryWebView(webView);

        // Добавляем View в контейнер (скрытым)
        contentView.setVisibility(View.GONE);
        secondaryContainer.addView(contentView);

        // Загружаем URL
        webView.loadUrl(url);

        // Создаём кастомный вид для вкладки с кнопкой закрытия
        View tabView = LayoutInflater.from(context).inflate(R.layout.tab_item_with_close, null);
        TextView titleView = tabView.findViewById(R.id.tab_item_title);
        ImageButton closeButton = tabView.findViewById(R.id.tab_item_close);
        
        titleView.setText(title);

        // ==================== ИСПРАВЛЕНИЕ БАГА С КНОПКАМИ ЗАКРЫТИЯ ====================
        // Кнопка закрытия теперь всегда находит правильную позицию даже после закрытия других вкладок
        final View finalTabView = tabView; // захватываем ссылку на сам View
        closeButton.setOnClickListener(v -> {
            Log.d(TAG, "Клик по кнопке закрытия вкладки");
            // Ищем текущую позицию по customView
            int position = -1;
            for (int i = 0; i < tabLayout.getTabCount(); i++) {
                TabLayout.Tab tab = tabLayout.getTabAt(i);
                if (tab != null && tab.getCustomView() == finalTabView) {
                    position = i;
                    break;
                }
            }
            if (position > 0) {
                closeTab(position);
            }
        });
        // =========================================================================================

        // Создаём TabInfo
        TabType tabType = determineTabType(url, title);
        TabInfo tabInfo = new TabInfo(title, url, webView, contentView, tabType);
        secondaryTabs.add(tabInfo);

        // Настраиваем кнопки панели действий
        setupActionButtons(contentView, tabInfo);

        // Добавляем кастомный Tab в TabLayout
        TabLayout.Tab newTab = tabLayout.newTab();
        newTab.setCustomView(tabView);
        tabLayout.addTab(newTab);

        // Переключаемся на новую вкладку
        tabLayout.selectTab(newTab);
    }

    /**
     * Определить тип вкладки по URL и заголовку.
     */
    // Определяем тип вкладки для набора кнопок действий.
    private TabType determineTabType(String url, String title) {
        String host = "";
        if (url != null) {
            try {
                Uri parsed = Uri.parse(url);
                host = parsed != null && parsed.getHost() != null
                        ? parsed.getHost().toLowerCase(Locale.ROOT)
                        : "";
            } catch (Exception ignored) {
            }
        }
        if ("forum.neverlands.ru".equals(host)) {
            return TabType.FORUM;
        }
        if ("PINFO".equals(title) || (("neverlands.ru".equals(host) || host.endsWith(".neverlands.ru"))
                && url != null && url.toLowerCase(Locale.ROOT).contains("pinfo"))) {
            return TabType.PINFO;
        }
        return TabType.OTHER;
    }

    /**
     * Настроить кнопки панели действий в зависимости от типа вкладки.
     */
    // Настройка кнопок панели действий в зависимости от типа вкладки.
    private void setupActionButtons(View contentView, TabInfo tabInfo) {
        View actionBar = contentView.findViewById(R.id.action_buttons_bar);
        if (actionBar == null) {
            Log.w(TAG, "setupActionButtons: action bar not found");
            return;
        }

        ImageButton btn1 = actionBar.findViewById(R.id.action_button_1);
        ImageButton btn2 = actionBar.findViewById(R.id.action_button_2);
        ImageButton btn3 = actionBar.findViewById(R.id.action_button_3);
        ImageButton btn4 = actionBar.findViewById(R.id.action_button_4);

        btn1.setVisibility(View.VISIBLE);
        btn2.setVisibility(View.VISIBLE);
        btn3.setVisibility(View.VISIBLE);
        if (btn4 != null) {
            btn4.setVisibility(View.VISIBLE);
        }

        switch (tabInfo.tabType) {
            case FORUM:
                // Форум: Назад, Обновить, Копировать URL
                btn1.setImageResource(R.drawable.ic_back);
                btn1.setOnClickListener(v -> {
                    if (tabInfo.webView != null && tabInfo.webView.canGoBack()) {
                        tabInfo.webView.goBack();
                    }
                });
                btn1.post(() -> {
                    boolean canGoBack = tabInfo.webView != null && tabInfo.webView.canGoBack();
                    btn1.setImageResource(canGoBack ? R.drawable.ic_back : R.drawable.ic_back_disabled);
                    btn1.setEnabled(canGoBack);
                    btn1.setAlpha(canGoBack ? 1.0f : 0.5f);
                });

                btn2.setImageResource(R.drawable.ic_refresh);
                btn2.setOnClickListener(v -> {
                    if (tabInfo.webView != null) {
                        tabInfo.webView.reload();
                    }
                });

                btn3.setImageResource(R.drawable.ic_copy);
                btn3.setOnClickListener(v -> {
                    if (tabInfo.url != null) {
                        copyToClipboard(tabInfo.url);
                    }
                });
                if (btn4 != null) {
                    btn4.setVisibility(View.GONE);
                }
                break;

            case PINFO:
                // PINFO: Обновить, Добавить в контакты, Закрыть
                btn1.setImageResource(R.drawable.ic_refresh);
                btn1.setOnClickListener(v -> {
                    if (tabInfo.webView != null) {
                        tabInfo.webView.reload();
                    }
                });

                btn2.setImageResource(R.drawable.ic_add_contact);
                btn2.setOnClickListener(v -> {
                    addToContacts(tabInfo.title);
                });

                btn3.setImageResource(R.drawable.ic_compas);
                btn3.setOnClickListener(v -> {
                    startCompassFromPinfoTab(tabInfo);
                });
                if (btn4 != null) {
                    btn4.setImageResource(R.drawable.ic_close);
                    btn4.setOnClickListener(v -> {
                        closeCurrentTab();
                    });
                }
                break;

            case OTHER:
            default:
                // Для других вкладок - только кнопка закрытия
                btn1.setVisibility(View.GONE);
                btn2.setVisibility(View.GONE);
                btn3.setVisibility(View.GONE);
                if (btn4 != null) {
                    btn4.setImageResource(R.drawable.ic_close);
                    btn4.setOnClickListener(v -> {
                        closeCurrentTab();
                    });
                } else {
                    btn3.setVisibility(View.VISIBLE);
                    btn3.setImageResource(R.drawable.ic_close);
                    btn3.setOnClickListener(v -> {
                        closeCurrentTab();
                    });
                }
                break;
        }
    }

    /**
     * Скопировать текст в буфер обмена.
     */
    // Скопировать URL/текст в буфер.
    private void startCompassFromPinfoTab(TabInfo tabInfo) {
        String nick = resolvePinfoNick(tabInfo);
        if (nick == null || nick.trim().isEmpty()) {
            Toast.makeText(context, "Не удалось определить ник для Компаса", Toast.LENGTH_SHORT).show();
            return;
        }
        AutoFunctionsManager.getInstance(context)
                .startSettingsCompassTargetSearch(nick.trim(), "pinfo_tab_compas_button");
        Toast.makeText(context, "Компас: автопоиск " + nick, Toast.LENGTH_SHORT).show();
    }

    private String resolvePinfoNick(TabInfo tabInfo) {
        if (tabInfo == null) {
            return null;
        }
        String title = tabInfo.title == null ? "" : tabInfo.title.trim();
        if (!title.isEmpty() && !"PINFO".equalsIgnoreCase(title)) {
            return title;
        }
        String url = tabInfo.url == null ? "" : tabInfo.url;
        if (url.isEmpty()) {
            return null;
        }
        try {
            Uri uri = Uri.parse(url);
            String query = uri.getQuery();
            if (query != null && !query.trim().isEmpty()) {
                return URLDecoder.decode(query, "windows-1251").trim();
            }
            int index = url.toLowerCase(Locale.ROOT).indexOf("pinfo.cgi?");
            if (index >= 0) {
                String encodedNick = url.substring(index + "pinfo.cgi?".length());
                int amp = encodedNick.indexOf('&');
                if (amp >= 0) {
                    encodedNick = encodedNick.substring(0, amp);
                }
                return URLDecoder.decode(encodedNick, "windows-1251").trim();
            }
        } catch (Exception e) {
            Log.w(TAG, "resolvePinfoNick failed: url=" + tabInfo.url, e);
        }
        return null;
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("URL", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, "URL скопирован в буфер", Toast.LENGTH_SHORT).show();
    }

    /**
     * Добавить игрока в контакты.
     */
    // Добавить ник в список контактов (через ContactsManager).
    private void addToContacts(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            Toast.makeText(context, "Неизвестный игрок", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.d(TAG, "addToContacts: " + playerName);
        Toast.makeText(context, "Добавление " + playerName + "...", Toast.LENGTH_SHORT).show();
        
        ContactsManager.addContact(context, playerName, new ContactsManager.ContactOperationCallback() {
            @Override
            public void onSuccess(Contact contact) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(context, contact.nick + " добавлен в контакты", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onFailure(String message) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(context, "Ошибка: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Закрыть вспомогательную вкладку по позиции в TabLayout.
     * Позиция 0 — основная (нельзя закрыть).
     *
     * @param tabPosition позиция в TabLayout (1+)
     */
    // Закрытие вкладки по позиции в TabLayout (1+).
    public void closeTab(int tabPosition) {
        if (tabPosition <= 0 || tabPosition > secondaryTabs.size()) {
            Log.w(TAG, "closeTab: невозможно закрыть вкладку " + tabPosition);
            return;
        }

        int secondaryIndex = tabPosition - 1;
        TabInfo tabInfo = secondaryTabs.get(secondaryIndex);

        Log.d(TAG, "closeTab: закрываем вкладку " + tabPosition + ": " + tabInfo.title);

        // Уничтожаем WebView
        if (tabInfo.webView != null) {
            tabInfo.webView.stopLoading();
            tabInfo.webView.destroy();
        }

        // Удаляем View из контейнера
        if (tabInfo.contentView != null) {
            secondaryContainer.removeView(tabInfo.contentView);
        }

        // Удаляем из списка
        secondaryTabs.remove(secondaryIndex);

        // Удаляем Tab из TabLayout
        TabLayout.Tab tab = tabLayout.getTabAt(tabPosition);
        if (tab != null) {
            tabLayout.removeTab(tab);
        }

        // Переключаемся на предыдущую вкладку
        int newPosition = Math.max(0, tabPosition - 1);
        TabLayout.Tab prevTab = tabLayout.getTabAt(newPosition);
        if (prevTab != null) {
            tabLayout.selectTab(prevTab);
        }
    }

    /**
     * Нормализовать URL для корректного сравнения.
     * Убирает www, слеши в конце, и приводит к одному формату.
     * Для форума - сравнивает только домен.
     */
    // Нормализация URL для сравнения (убрать www/слеши).
    private String normalizeUrl(String url) {
        if (url == null) return null;
        url = url.replace("www.", "");

        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        try {
            Uri parsed = Uri.parse(url);
            String host = parsed != null && parsed.getHost() != null
                    ? parsed.getHost().toLowerCase(Locale.ROOT)
                    : "";
            if ("forum.neverlands.ru".equals(host)) {
                return "forum.neverlands.ru";
            }
        } catch (Exception ignored) {
        }

        return url;
    }

    /**
     * Закрыть текущую активную вспомогательную вкладку.
     */
    // Закрытие текущей вкладки (если это не основная).
    public void closeCurrentTab() {
        if (currentTabIndex > 0) {
            closeTab(currentTabIndex);
        }
    }

    /**
     * Переключиться на вкладку по позиции.
     *
     * @param position 0 = основная, 1+ = вспомогательные
     */
    // Переключение UI между основной и вторичными вкладками.
    private void switchToTab(int position) {
        Log.d(TAG, "switchToTab: " + position);
        currentTabIndex = position;

        if (position == 0) {
            // Основная вкладка
            mainContent.setVisibility(View.VISIBLE);
            secondaryContainer.setVisibility(View.GONE);
            // Скрываем все вспомогательные
            for (TabInfo tab : secondaryTabs) {
                if (tab.contentView != null) {
                    tab.contentView.setVisibility(View.GONE);
                }
            }
        } else {
            // Вспомогательная вкладка
            mainContent.setVisibility(View.GONE);
            secondaryContainer.setVisibility(View.VISIBLE);

            // Скрываем все вспомогательные
            for (TabInfo tab : secondaryTabs) {
                if (tab.contentView != null) {
                    tab.contentView.setVisibility(View.GONE);
                }
            }

            // Показываем нужную
            int secondaryIndex = position - 1;
            if (secondaryIndex >= 0 && secondaryIndex < secondaryTabs.size()) {
                TabInfo activeTab = secondaryTabs.get(secondaryIndex);
                if (activeTab.contentView != null) {
                    activeTab.contentView.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    /**
     * Получить текущий индекс вкладки.
     */
    // Текущая выбранная вкладка (0 = основная).
    public int getCurrentTabIndex() {
        return currentTabIndex;
    }

    /**
     * Получить количество вспомогательных вкладок.
     */
    // Количество вторичных вкладок.
    public int getSecondaryTabCount() {
        return secondaryTabs.size();
    }

    /**
     * Настройка WebView для вспомогательной вкладки.
     * Аналогично setupWebView в MainActivity, но без чата и фильтров.
     */
    @SuppressLint("SetJavaScriptEnabled")
    // Настройка WebView для вторичной вкладки (без чата).
    private void setupSecondaryWebView(WebView webView) {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Добавляем JS-мост
        WebAppInterface webAppInterface = new WebAppInterface(context);
        webView.addJavascriptInterface(webAppInterface, "AndroidBridge");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return WebViewRequestInterceptor.intercept(request);
            }

            private boolean handleSecondaryUrlLoading(WebView view, String url) {
                Log.d(TAG, "shouldOverrideUrlLoading secondary: " + url);
                if (url == null || url.isEmpty()) {
                    return false;
                }

                String host = "";
                try {
                    Uri parsedUri = Uri.parse(url);
                    host = parsedUri != null && parsedUri.getHost() != null
                            ? parsedUri.getHost().toLowerCase(Locale.ROOT)
                            : "";
                } catch (Exception ignored) {
                }
                boolean isNeverlandsHost = "neverlands.ru".equals(host) || host.endsWith(".neverlands.ru");
                boolean isForumHost = "forum.neverlands.ru".equals(host);
                String lowerUrl = url.toLowerCase(Locale.ROOT);

                if (isForumHost) {
                    updateTabUrl(view, url);
                }

                if (!isForumHost
                        && isNeverlandsHost
                        && (lowerUrl.contains("pinfo")
                        || lowerUrl.contains("ch.php")
                        || lowerUrl.contains("log.php")
                        || lowerUrl.contains("fight")
                        || lowerUrl.contains("pname")
                        || lowerUrl.contains("pbots"))) {

                    String title = "Новая вкладка";
                    if (lowerUrl.contains("pinfo")) title = "PINFO";
                    else if (lowerUrl.contains("ch.php")) title = "Комната";
                    else if (lowerUrl.contains("log.php") || lowerUrl.contains("fight")) title = "Бой";
                    else if (lowerUrl.contains("pname")) title = "Персонаж";
                    else if (lowerUrl.contains("pbots")) title = "Боты";

                    openTab(url, title);
                    return true;
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request != null && request.getUrl() != null
                        ? request.getUrl().toString()
                        : null;
                return handleSecondaryUrlLoading(view, url);
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleSecondaryUrlLoading(view, url);
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "onPageFinished secondary: " + url);
                
                // Для форума НЕ обновляем URL здесь, т.к. shouldOverrideUrlLoading уже делает это корректно
                // Для остальных - обновляем
                String currentUrl = view.getUrl();
                if (currentUrl != null && !currentUrl.isEmpty()) {
                    TabInfo tabInfo = findTabByWebView(view);
                    if (tabInfo != null && tabInfo.tabType != TabType.FORUM) {
                        updateTabUrl(view, currentUrl);
                    }
                }
                
                // Обновляем состояние кнопки Назад
                updateBackButtonState(view);
                
                // Инъекция JavaScript для перехвата кликов по ссылкам
                injectClickInterceptor(view);
            }
        });
    }

    /**
     * Обновить состояние кнопки Назад (активна/неактивна).
     */
    // Обновляет доступность кнопки "Назад" в панели действий.
    private void updateBackButtonState(WebView webView) {
        TabInfo tabInfo = findTabByWebView(webView);
        if (tabInfo == null || tabInfo.contentView == null) return;
        
        View actionBar = tabInfo.contentView.findViewById(R.id.action_buttons_bar);
        if (actionBar == null) return;
        
        ImageButton btnBack = actionBar.findViewById(R.id.action_button_1);
        if (btnBack == null) return;
        
        boolean canGoBack = webView.canGoBack();
        btnBack.setImageResource(canGoBack ? R.drawable.ic_back : R.drawable.ic_back_disabled);
        btnBack.setEnabled(canGoBack);
        btnBack.setAlpha(canGoBack ? 1.0f : 0.5f);
    }

    /**
     * Найти TabInfo по WebView.
     */
    // Поиск TabInfo по WebView.
    private TabInfo findTabByWebView(WebView webView) {
        for (TabInfo tab : secondaryTabs) {
            if (tab.webView == webView) {
                return tab;
            }
        }
        return null;
    }

    /**
     * Обновить URL вкладки при навигации.
     */
    // Синхронизация URL в TabInfo при навигации.
    private void updateTabUrl(WebView webView, String url) {
        for (TabInfo tab : secondaryTabs) {
            if (tab.webView == webView) {
                tab.url = url;
                Log.d(TAG, "updateTabUrl: обновлен URL для вкладки " + tab.title + " -> " + url);
                break;
            }
        }
    }
    
    /**
     * Инъекция JavaScript для перехвата кликов по ссылкам во вторичных вкладках.
     * Для форума - НЕ перехватываем, пусть открывается внутри.
     */
    // JS-инъекция для перехвата кликов и открытия специальных ссылок во вкладках.
    private void injectClickInterceptor(WebView view) {
        String script = 
            "(function() {" +
            "  if (window._clickInterceptorInjected) return;" +
            "  window._clickInterceptorInjected = true;" +
            "  console.log('Click interceptor injected in secondary');" +
            "  " +
            "  document.addEventListener('click', function(e) {" +
            "    var target = e.target;" +
            "    while (target) {" +
            "      if (target.tagName === 'A' && target.href) {" +
            "        var href = target.href;" +
            "        console.log('Link clicked: ' + href);" +
            "        // Перехватываем ТОЛЬКО не форумные ссылки" +
            "        if (href.indexOf('forum.neverlands.ru') === -1 &&" +
            "            (href.indexOf('pinfo.cgi') !== -1 ||" +
            "             href.indexOf('ch.php') !== -1 ||" +
            "             href.indexOf('log.php') !== -1 ||" +
            "             href.indexOf('fight') !== -1 ||" +
            "             href.indexOf('pname.cgi') !== -1 ||" +
            "             href.indexOf('pbots.cgi') !== -1)) {" +
            "          console.log('Opening new tab for: ' + href);" +
            "          e.preventDefault();" +
            "          e.stopPropagation();" +
            "          var title = 'Новая вкладка';" +
            "          if (href.indexOf('pinfo.cgi') !== -1 || href.indexOf('pinfo') !== -1) title = 'PINFO';" +
            "          else if (href.indexOf('ch.php') !== -1) title = 'Комната';" +
            "          else if (href.indexOf('log.php') !== -1 || href.indexOf('fight') !== -1) title = 'Бой';" +
            "          else if (href.indexOf('pname.cgi') !== -1) title = 'Персонаж';" +
            "          else if (href.indexOf('pbots.cgi') !== -1) title = 'Боты';" +
            "          if (window.AndroidBridge) {" +
            "            window.AndroidBridge.openInNewTab(href, title);" +
            "          }" +
            "          return;" +
            "        }" +
            "      }" +
            "      target = target.parentElement;" +
            "    }" +
            "  }, true);" +
            "})()";
        view.evaluateJavascript(script, null);
    }

    /**
     * Уничтожить все вспомогательные вкладки (вызывается при onDestroy).
     */
    // Уничтожить все вторичные вкладки и их WebView.
    public void destroyAll() {
        for (TabInfo tab : secondaryTabs) {
            if (tab.webView != null) {
                tab.webView.stopLoading();
                tab.webView.destroy();
            }
        }
        secondaryTabs.clear();
    }
}

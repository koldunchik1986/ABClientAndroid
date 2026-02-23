package ru.neverlands.abclient.manager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import ru.neverlands.abclient.R;
import ru.neverlands.abclient.bridge.WebAppInterface;
import ru.neverlands.abclient.webview.WebViewRequestInterceptor;

/**
 * Менеджер вкладок (аналог TabControl в C# версии ABClient).
 * Управляет системой горизонтальных вкладок:
 * - Вкладка 0 (Основная): main.php + чат — не закрывается
 * - Вкладки 1+ (Вспомогательные): pinfo, форум и т.д. — можно закрывать
 */
public class TabManager {
    private static final String TAG = "TabManager";

    /** Информация о вспомогательной вкладке */
    public static class TabInfo {
        public String title;
        public String url;
        public WebView webView;
        public View contentView; // корневой View вкладки

        public TabInfo(String title, String url, WebView webView, View contentView) {
            this.title = title;
            this.url = url;
            this.webView = webView;
            this.contentView = contentView;
        }
    }

    private final Context context;
    private final TabLayout tabLayout;
    private final View mainContent;           // LinearLayout основной вкладки
    private final FrameLayout secondaryContainer; // контейнер для вспомогательных вкладок
    private final List<TabInfo> secondaryTabs = new ArrayList<>();
    private int currentTabIndex = 0; // 0 = основная, 1+ = вспомогательные

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
    public void openTab(String url, String title) {
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
        final int tabPosition = secondaryTabs.size() + 1;
        closeButton.setOnClickListener(v -> {
            Log.d(TAG, "Клик по кнопке закрытия вкладки " + tabPosition);
            closeTab(tabPosition);
        });

        // Создаём TabInfo
        TabInfo tabInfo = new TabInfo(title, url, webView, contentView);
        secondaryTabs.add(tabInfo);

        // Добавляем кастомный Tab в TabLayout
        TabLayout.Tab newTab = tabLayout.newTab();
        newTab.setCustomView(tabView);
        tabLayout.addTab(newTab);

        // Переключаемся на новую вкладку
        tabLayout.selectTab(newTab);
    }

    /**
     * Закрыть вспомогательную вкладку по позиции в TabLayout.
     * Позиция 0 — основная (нельзя закрыть).
     *
     * @param tabPosition позиция в TabLayout (1+)
     */
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
    private String normalizeUrl(String url) {
        if (url == null) return null;
        url = url.replace("www.", "");
        
        // Убираем завершающий слеш для корректного сравнения
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        
        // Для форума - возвращаем только домен
        if (url.contains("forum.neverlands.ru")) {
            return "forum.neverlands.ru";
        }
        
        return url;
    }

    /**
     * Закрыть текущую активную вспомогательную вкладку.
     */
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
    public int getCurrentTabIndex() {
        return currentTabIndex;
    }

    /**
     * Получить количество вспомогательных вкладок.
     */
    public int getSecondaryTabCount() {
        return secondaryTabs.size();
    }

    /**
     * Настройка WebView для вспомогательной вкладки.
     * Аналогично setupWebView в MainActivity, но без чата и фильтров.
     */
    @SuppressLint("SetJavaScriptEnabled")
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
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "onPageFinished secondary: " + url);
                // Инъекция JavaScript для перехвата кликов по ссылкам
                injectClickInterceptor(view);
            }
        });
    }
    
    /**
     * Инъекция JavaScript для перехвата кликов по ссылкам во вторичных вкладках.
     */
    private void injectClickInterceptor(WebView view) {
        String script = 
            "(function() {" +
            "  if (window._clickInterceptorInjected) return;" +
            "  window._clickInterceptorInjected = true;" +
            "  " +
            "  document.addEventListener('click', function(e) {" +
            "    var target = e.target;" +
            "    while (target) {" +
            "      if (target.tagName === 'A' && target.href) {" +
            "        var href = target.href;" +
            "        if (href.indexOf('forum.neverlands.ru') !== -1 ||" +
            "            href.indexOf('pinfo.cgi') !== -1 ||" +
            "            href.indexOf('ch.php') !== -1 ||" +
            "            href.indexOf('log.php') !== -1 ||" +
            "            href.indexOf('fight') !== -1 ||" +
            "            href.indexOf('pname.cgi') !== -1 ||" +
            "            href.indexOf('pbots.cgi') !== -1) {" +
            "          e.preventDefault();" +
            "          e.stopPropagation();" +
            "          var title = 'Новая вкладка';" +
            "          if (href.indexOf('forum.neverlands.ru') !== -1) title = 'Форум';" +
            "          else if (href.indexOf('pinfo.cgi') !== -1) title = 'PINFO';" +
            "          else if (href.indexOf('ch.php') !== -1) title = 'Комната';" +
            "          else if (href.indexOf('log.php') !== -1 || href.indexOf('fight') !== -1) title = 'Бой';" +
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

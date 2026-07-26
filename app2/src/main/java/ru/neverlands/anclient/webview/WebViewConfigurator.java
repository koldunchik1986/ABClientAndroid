package ru.neverlands.anclient.webview;

import android.annotation.SuppressLint;
import android.os.Build;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;

/**
 * Единая точка настройки игровых WebView (D2).
 *
 * Зачем создан:
 * - До рефакторинга настройки WebView дублировались в трёх местах:
 *   {@code MainActivity.setupWebView(...)}, {@code TabManager.setupSecondaryWebView(...)}
 *   и {@code Navigator} (мини-карта). Наборы флагов разошлись: только в {@code MainActivity}
 *   были включены опасные {@code setAllowFileAccessFromFileURLs} и
 *   {@code setAllowUniversalAccessFromFileURLs}.
 * - Согласно AGENTS п.8 правки внесены в единый контур, а не добавлен ещё один параллельный.
 *
 * Что НЕ входит в этот класс:
 * - Мини-карта навигатора ({@code Navigator.navigatorMiniMapView}) настраивается отдельно:
 *   это единственный WebView, который реально работает с {@code file:///android_asset/}
 *   (см. {@code loadDataWithBaseURL(..., "file:///android_asset/", ...)}), поэтому у него
 *   принципиально другой профиль доступа к файлам и он намеренно не переводится сюда.
 *
 * Статус hardening (обновлено 2026-07-26):
 * - В D2 здесь были сняты {@code setAllowFileAccessFromFileURLs} и
 *   {@code setAllowUniversalAccessFromFileURLs}. После этого пользователь сообщил о поломке
 *   авторизации/сессии, поэтому оба флага <b>возвращены</b> к исходному поведению до
 *   получения live-логов. Приоритет — рабочий вход, а не гипотетическое ужесточение.
 * - Повторно снимать их можно только после подтверждения, что регрессия сессии вызвана
 *   не ими (см. {@code TODO2/todo_DebugApp.md}, раздел про регрессию сессии).
 */
public final class WebViewConfigurator {

    private static final String TAG = "WebViewConfigurator";

    /** Профиль настройки: разные WebView требуют разного набора возможностей. */
    public enum Profile {
        /** Основные игровые фреймы в {@code MainActivity} (нужны БД и всплывающие окна). */
        MAIN_GAME,
        /** Вторичные вкладки в {@code TabManager} (без БД и множественных окон). */
        SECONDARY_TAB
    }

    private WebViewConfigurator() {
    }

    /**
     * Применяет согласованный набор настроек к игровому WebView.
     *
     * @param webView настраиваемый WebView
     * @param profile профиль (см. {@link Profile})
     */
    @SuppressLint("SetJavaScriptEnabled")
    @SuppressWarnings("deprecation")
    public static void applyGameSettings(WebView webView, Profile profile) {
        if (webView == null) {
            AppLog.w(TAG, "applyGameSettings: webView is null, profile=" + profile);
            return;
        }
        WebSettings webSettings = webView.getSettings();

        // Движок игры полностью построен на JS — отключить нельзя.
        webSettings.setJavaScriptEnabled(true);

        // ============================================================================
        // КРИТИЧНО ДЛЯ СЕССИИ: единый User-Agent для WebView и всех нативных HTTP-путей.
        //
        // Проблема (логи logs/Critical/20260726_14_*):
        //   AuthManager (OkHttp) получал PHPSESSID с UA "Mozilla/5.0 (Windows NT 10.0 ...)",
        //   а WebView сразу после этого слал запросы с тем же PHPSESSID, но со своим родным
        //   UA "Mozilla/5.0 (Linux; Android 14; <модель>; wv) ... Mobile Safari".
        //   Сервер видит одну сессию из двух разных браузеров и отвечает страницей
        //   «Сеанс работы прерван» с причиной «Попытка войти в другом окне браузера».
        //
        // Дополнительно это закрывает требование AGENTS п.4 (анти-детект): родной UA WebView
        // содержит маркер "wv" и модель устройства, то есть прямо выдаёт неофициальный клиент.
        //
        // Все нативные пути (AuthManager, NeverApi, ApiRepository, прямые запросы MainActivity,
        // WebViewRequestInterceptor) уже используют AppVars.BROWSER_USER_AGENT — приводим
        // WebView к тому же значению, чтобы UA совпадал на всех запросах сессии.
        // ============================================================================
        webSettings.setUserAgentString(AppVars.BROWSER_USER_AGENT);

        webSettings.setAllowFileAccess(true);

        // ВОЗВРАЩЕНО к исходному поведению (диагностика регрессии сессии, 2026-07-26).
        //
        // В D2 эти два флага были сняты как «бесполезные для http-страниц». После этого
        // пользователь сообщил о поломке авторизации/сессии. Пока причина не подтверждена
        // логами, поведение WebView возвращено 1:1 к состоянию до рефакторинга —
        // безопасность не должна ломать рабочий вход.
        //
        // Снимать повторно только после live-подтверждения, что регрессия вызвана не ими.
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);

        webSettings.setDomStorageEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        // ВНИМАНИЕ (осознанное решение, не упущение):
        // игра работает по http:// (в манифесте включён usesCleartextTraffic), а часть внешних
        // страниц (форум/pinfo) может отдаваться по https и подтягивать http-ресурсы.
        // Понижение режима до COMPATIBILITY_MODE способно сломать их отрисовку,
        // поэтому режим сохранён прежним и вынесен сюда как единая точка изменения.
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        if (profile == Profile.MAIN_GAME) {
            webSettings.setDatabaseEnabled(true);
            // Требуется для window.open(...) на игровых страницах.
            webSettings.setSupportMultipleWindows(true);
        }

        // Анти-детект: по умолчанию WebView обращается к сервису автозаполнения Google.
        // В логах прокси это видно как `CONNECT content-autofill.googleapis.com:443` —
        // посторонний трафик в том же канале, что и игровой, плюс лишний внешний признак.
        // Игре автозаполнение не нужно ни в одном профиле, поэтому выключаем его целиком.
        webSettings.setSaveFormData(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Исключаем и сам WebView, и все вложенные поля: иначе система всё равно
            // собирает структуру формы и обращается к autofill-сервису.
            webView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // ВАЖНО: строка лога должна отражать РЕАЛЬНОЕ состояние флагов.
        // Ранее здесь было захардкожено "universalFileAccess=disabled", из-за чего лог
        // продолжал показывать старое значение после возврата флагов и мешал диагностике.
        AppLog.d(TAG, "applyGameSettings: profile=" + profile
                + ", allowFileAccess=" + webSettings.getAllowFileAccess()
                + ", universalFileAccess=" + webSettings.getAllowUniversalAccessFromFileURLs()
                + ", mixedContent=" + webSettings.getMixedContentMode()
                + ", userAgent=" + webSettings.getUserAgentString());
    }
}

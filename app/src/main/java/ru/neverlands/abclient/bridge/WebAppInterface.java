package ru.neverlands.abclient.bridge;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import ru.neverlands.abclient.MainActivity;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.manager.ContactsManager;
import ru.neverlands.abclient.model.AutoboiState;
import ru.neverlands.abclient.model.Prims;
import ru.neverlands.abclient.proxy.CookiesManager;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

/**
 * Класс-мост (bridge) для взаимодействия между JavaScript в WebView и нативным кодом Android.
 * Методы, аннотированные @JavascriptInterface, могут быть вызваны из JS.
 * В JS этот объект доступен как `AndroidBridge`.
 */
public class WebAppInterface {
    private static final String BG_TRACE_PREFIX = "[BG_TRACE]";
    Context mContext;

    /** Конструктор, инициализирующий контекст. */
    public WebAppInterface(Context c) {
        mContext = c;
    }

    private MainActivity getMainActivityOrNull() {
        if (AppVars.mainActivity == null) return null;
        return AppVars.mainActivity.get();
    }

    /** Показывает всплывающее сообщение (Toast) из веб-страницы. */
    @JavascriptInterface
    public void showToast(String toast) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
    }

    /**
     * Возвращает ID класса контакта (друг, враг, нейтрал).
     * Вызывается из ch_list.js для определения цвета ника.
     * @param name Ник персонажа.
     * @return ID класса.
     */
    @JavascriptInterface
    public String GetClassIdOfContact(String name) {
        return String.valueOf(ContactsManager.getClassIdOfContact(name));
    }

    /**
     * C# parity: сообщает map.js, включена ли Авто-Рыбалка.
     */
    @JavascriptInterface
    public boolean IsAutoFish() {
        try {
            if (mContext != null) {
                return AutoFunctionsManager.getInstance(mContext).isAutoFishEnabled();
            }
        } catch (Exception e) {
            Log.w("WebAppInterface", "IsAutoFish failed", e);
        }
        return AppVars.Profile != null && AppVars.Profile.AutoFish;
    }

    /**
     * C# parity: map.js передаёт текущую массу инвентаря перед забросом.
     */
    @JavascriptInterface
    public void SetAutoFishMassa(String massa) {
        AppVars.AutoFishMassa = massa == null ? "" : massa.trim();
    }

    /**
     * C# parity (`ScriptManager.SetNeverTimer`): map.js передаёт остаток таймера в миллисекундах
     * (переменная `time_left_sec`), а клиент фиксирует абсолютное время следующего действия.
     *
     * Зависимости:
     * - `app/src/main/assets/js/map.js`: `window.external.SetNeverTimer(time_left_sec)` в `timerst(lp)`;
     * - `AppVars.NeverTimer`: общий cooldown-гейт для авто-функций в `MainPhp`;
     * - `MainPhp`: проверки вида `System.currentTimeMillis() > AppVars.NeverTimer`.
     *
     * Поведение:
     * - вход < 0 нормализуется в 0, чтобы не уводить таймер в прошлое;
     * - `NeverTimer` сохраняется как `текущее_время + остаток_мс` (как в ПК-версии: `DateTime.Now.AddMilliseconds`).
     */
    @JavascriptInterface
    public void SetNeverTimer(long msLeft) {
        if (msLeft < 0L) {
            msLeft = 0L;
        }
        AppVars.NeverTimer = System.currentTimeMillis() + msLeft;
    }

    /**
     * C# parity (`CheckPri`): выбирает первую подходящую приманку (остаток > 4) и
     * возвращает `" CHECKED"` для вставки в HTML radio.
     */
    @JavascriptInterface
    public String CheckPri(String namePri, int myst) {
        if (AppVars.Profile == null) {
            return "";
        }
        if (AppVars.PriSelected || myst <= 4 || namePri == null || namePri.isEmpty()) {
            return "";
        }

        if (isPrimEnabledByName(namePri)) {
            AppVars.PriSelected = true;
            AppVars.NamePri = namePri;
            AppVars.ValPri = myst;
            String primId = resolvePrimIdByName(namePri);
            if (primId != null) {
                AppVars.AutoFishLikeId = primId;
                AppVars.AutoFishLikeVal = String.valueOf(myst);
            }
            return " CHECKED";
        }
        return "";
    }

    /**
     * C# parity: map.js сигнализирует, что капчи нет и можно сразу жать "Ловить".
     * На Android делаем тот же шаг через JS-клик по `fishbutton`.
     */
    @JavascriptInterface
    public void SetFishNoCaptchaReady() {
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            if (activity.binding == null || activity.binding.appBarMain == null
                    || activity.binding.appBarMain.contentMain == null
                    || activity.binding.appBarMain.contentMain.webView == null) {
                return;
            }
            activity.binding.appBarMain.contentMain.webView.evaluateJavascript(
                    "(function(){"
                            + "try{"
                            + "  var btn=document.getElementById('fishbutton');"
                            + "  if(btn&&typeof btn.click==='function'){btn.click(); return 'button';}"
                            + "  if(typeof FishStart==='function' && typeof ingr!=='undefined' && ingr && ingr.length>2){"
                            + "    FishStart(ingr[2],0); return 'fishstart';"
                            + "  }"
                            + "}catch(e){return 'error:'+e;}"
                            + "return 'miss';"
                            + "})()",
                    value -> Log.d("WebAppInterface", "SetFishNoCaptchaReady: " + value));
        });
    }

    /**
     * C# parity (`InsertGuaDiv`): вставляет блок Guamod под captcha в fish-окне.
     */
    @JavascriptInterface
    public String InsertGuaDiv(String code) {
        if (AppVars.Profile != null && AppVars.Profile.DoGuamod) {
            return "<br><img src=http://image.neverlands.ru/1x1.gif width=1 height=8><br>"
                    + "<span id=guamod3><font class=nickname><font color=#004A7F><b>* * * *</b></font></font></span>";
        }
        return "";
    }

    /**
     * C# parity: уведомление map.js о перегрузе массы при рыбалке.
     */
    @JavascriptInterface
    public void FishOverload() {
        Toast.makeText(mContext, "Перегруз: рыбалка может остановиться", Toast.LENGTH_SHORT).show();
    }

    private boolean isPrimEnabledByName(String namePri) {
        if (AppVars.Profile == null || namePri == null) {
            return false;
        }
        int mask = AppVars.Profile.FishEnabledPrims;
        if ("Хлеб".equalsIgnoreCase(namePri)) return (mask & Prims.Bread) != 0;
        if ("Червяк".equalsIgnoreCase(namePri)) return (mask & Prims.Worm) != 0;
        if ("Крупный червяк".equalsIgnoreCase(namePri)) return (mask & Prims.BigWorm) != 0;
        if ("Опарыш".equalsIgnoreCase(namePri)) return (mask & Prims.Stink) != 0;
        if ("Мотыль".equalsIgnoreCase(namePri)) return (mask & Prims.Fly) != 0;
        if ("Блесна".equalsIgnoreCase(namePri)) return (mask & Prims.Light) != 0;
        if ("Донка".equalsIgnoreCase(namePri)) return (mask & Prims.Donka) != 0;
        if ("Мормышка".equalsIgnoreCase(namePri)) return (mask & Prims.Morm) != 0;
        if ("Заговоренная блесна".equalsIgnoreCase(namePri)) return (mask & Prims.HiFlight) != 0;
        return false;
    }

    private String resolvePrimIdByName(String namePri) {
        if (namePri == null) return null;
        if ("Хлеб".equalsIgnoreCase(namePri)) return "38";
        if ("Червяк".equalsIgnoreCase(namePri)) return "39";
        if ("Крупный червяк".equalsIgnoreCase(namePri)) return "40";
        if ("Опарыш".equalsIgnoreCase(namePri)) return "41";
        if ("Мотыль".equalsIgnoreCase(namePri)) return "42";
        if ("Блесна".equalsIgnoreCase(namePri)) return "43";
        if ("Донка".equalsIgnoreCase(namePri)) return "44";
        if ("Мормышка".equalsIgnoreCase(namePri)) return "45";
        if ("Заговоренная блесна".equalsIgnoreCase(namePri)) return "46";
        return null;
    }

    // --- Методы для проверки отображения кнопок быстрых действий --- //
    // --- Логика портирована из ScriptManager.cs --- //

    /**
     * Проверяет, нужно ли отображать кнопку быстрого действия.
     * @param login Ник цели.
     * @param wmlabQ HTML-код кнопки.
     * @return HTML-код кнопки, если ее нужно показать, иначе - пустая строка.
     */
    @JavascriptInterface
    public String CheckQuick(String login, String wmlabQ) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return wmlabQ;
    }

    @JavascriptInterface
    public String CheckFastAttack(String login, String wmlabFA) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttack ? wmlabFA : "";
    }

    @JavascriptInterface
    public String CheckFastAttackBlood(String login, String wmlabFAB) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackBlood ? wmlabFAB : "";
    }

    @JavascriptInterface
    public String CheckFastAttackUltimate(String login, String wmlabFAU) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackUltimate ? wmlabFAU : "";
    }

    @JavascriptInterface
    public String CheckFastAttackClosedUltimate(String login, String wmlabFACU) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackClosedUltimate ? wmlabFACU : "";
    }

    @JavascriptInterface
    public String CheckFastAttackFist(String login, String wmlabFAF) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackFist ? wmlabFAF : "";
    }

    @JavascriptInterface
    public String CheckFastAttackClosedFist(String login, String wmlabFACF) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackClosedFist ? wmlabFACF : "";
    }

    @JavascriptInterface
    public String CheckFastAttackPortal(String login, String wmlabFP) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackPortal ? wmlabFP : "";
    }

    @JavascriptInterface
    public String CheckFastAttackClosed(String login, String wmlabFC) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackClosed ? wmlabFC : "";
    }

    @JavascriptInterface
    public String CheckFastAttackPoison(String login, String wmlabFAP) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackPoison ? wmlabFAP : "";
    }

    @JavascriptInterface
    public String CheckFastAttackStrong(String login, String wmlabFAS) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackStrong ? wmlabFAS : "";
    }

    @JavascriptInterface
    public String CheckFastAttackNevid(String login, String wmlabFAN) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackNevid ? wmlabFAN : "";
    }

    @JavascriptInterface
    public String CheckFastAttackFog(String login, String wmlabFAFG) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackFog ? wmlabFAFG : "";
    }

    @JavascriptInterface
    public String CheckFastAttackZas(String login, String wmlabFAZ) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackZas ? wmlabFAZ : "";
    }

    @JavascriptInterface
    public String CheckFastAttackTotem(String login, String wmlabFTOT) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackTotem ? wmlabFTOT : "";
    }

    // --- Методы для выполнения быстрых действий --- //

    @JavascriptInterface
    public void Quick(String login) {
        Toast.makeText(mContext, "Quick: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttack(String login) {
        Toast.makeText(mContext, "FastAttack: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackBlood(String login) {
        Toast.makeText(mContext, "FastAttackBlood: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackUltimate(String login) {
        Toast.makeText(mContext, "FastAttackUltimate: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackClosedUltimate(String login) {
        Toast.makeText(mContext, "FastAttackClosedUltimate: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackFist(String login) {
        Toast.makeText(mContext, "FastAttackFist: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackClosedFist(String login) {
        Toast.makeText(mContext, "FastAttackClosedFist: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackPortal(String login) {
        Toast.makeText(mContext, "FastAttackPortal: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackClosed(String login) {
        Toast.makeText(mContext, "FastAttackClosed: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackPoison(String login) {
        Toast.makeText(mContext, "FastAttackPoison: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackStrong(String login) {
        Toast.makeText(mContext, "FastAttackStrong: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackNevid(String login) {
        Toast.makeText(mContext, "FastAttackNevid: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackFog(String login) {
        Toast.makeText(mContext, "FastAttackFog: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackZas(String login) {
        Toast.makeText(mContext, "FastAttackZas: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackTotem(String login) {
        Toast.makeText(mContext, "FastAttackTotem: " + login, Toast.LENGTH_SHORT).show();
    }


    @JavascriptInterface
    public void showSmiles(int index) {
        // TODO: Implement a native smiles dialog
        Toast.makeText(mContext, "Show smiles: " + index, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public String chatFilter(String message) {
        // Пропуск входящих сообщений чата через Java‑фильтр (XP/лут/системные).
        String safeMessage = message == null ? "" : message;
        Log.d("ChatFilter", safeMessage);
        return ru.neverlands.abclient.utils.ChatFilter.filter(safeMessage);
    }

    @JavascriptInterface
    public void chatUpdated() {
        // Сигнал из JS: чат обновился (нужен для автоответов).
        Log.d("WebAppInterface", "Chat updated");
        ru.neverlands.abclient.utils.Chat.chatUpdated();
    }

    @JavascriptInterface
    public void chatAddMsg(String message) {
        // Добавление сообщения в окно чата (вызов JS add_msg в chatMsgWebview).
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        String safeMessage = message == null ? "" : message;
        safeMessage = adjustClanMarkers(safeMessage);
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = gson.toJson(safeMessage);
        activity.runOnUiThread(() -> {
            if (activity.binding != null && activity.binding.appBarMain != null
                    && activity.binding.appBarMain.contentMain != null
                    && activity.binding.appBarMain.contentMain.chatMsgWebview != null) {
                activity.binding.appBarMain.contentMain.chatMsgWebview
                        .evaluateJavascript("if (typeof add_msg === 'function') { add_msg(" + json + "); }", null);
            }
        });
    }

    @JavascriptInterface
    public void chatSetLmid(String lmid) {
        // Обновление lmid (last message id) в форме чата.
        String safe = lmid == null ? "" : lmid;
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = gson.toJson(safe);
        evalChatButtonsJs("if(document.FBT && document.FBT.lmid){document.FBT.lmid.value=" + json + ";}");
    }

    @JavascriptInterface
    public void chatRefreshN() {
        // Мягкий запрос на скорое обновление чата (top.ch_refresh_n).
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        activity.runOnUiThread(activity::requestChatRefreshSoon);
    }

    @JavascriptInterface
    public void chatClearInput() {
        evalChatButtonsJs("if(document.FBT && document.FBT.text){document.FBT.text.value='';document.FBT.text.focus();}");
    }

    @JavascriptInterface
    public void chatSubmit(String action, String method, String data) {
        // Отправка сообщения чата: формируем POST и грузим в скрытый ch_refr WebView.
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        String url = action == null ? "" : action;
        if (!url.startsWith("http")) {
            url = "http://neverlands.ru/" + url.replaceFirst("^/+", "");
        }
        String safeMethod = method == null ? "POST" : method.toUpperCase();
        String payload = data == null ? "" : data;
        payload = recodeUrlEncoded(payload, Charset.forName("UTF-8"), Charset.forName("windows-1251"));
        Log.d("WebAppInterface", "chatSubmit: " + safeMethod + " " + url + " dataLen=" + payload.length());
        final String baseUrl = url;
        final String finalPayload = payload;
        if ("GET".equals(safeMethod)) {
            activity.runOnUiThread(() -> {
                String getUrl = baseUrl;
                if (!finalPayload.isEmpty()) {
                    getUrl = getUrl + (getUrl.contains("?") ? "&" : "?") + finalPayload;
                }
                activity.loadChatRefrUrl(getUrl);
            });
            return;
        }

        // POST выполняем в фоне, ответ парсим: add_msg / set_lmid.
        new Thread(() -> {
            ChatPostResult result = postChatMessage(baseUrl, finalPayload);
            if (result == null) {
                MainActivity fallbackActivity = getMainActivityOrNull();
                if (fallbackActivity != null) {
                    fallbackActivity.runOnUiThread(() -> fallbackActivity.postChatRefrUrl(baseUrl, finalPayload));
                }
                return;
            }
            if (result.lmid != null && !result.lmid.isEmpty()) {
                chatSetLmid(result.lmid);
            }
            if (!result.messages.isEmpty()) {
                for (String msg : result.messages) {
                    if (msg != null && !msg.isEmpty()) {
                        chatAddMsg(msg);
                    }
                }
            }
            chatClearInput();
            MainActivity refreshActivity = getMainActivityOrNull();
            if (refreshActivity != null) {
                refreshActivity.runOnUiThread(refreshActivity::requestChatRefreshSoon);
            }
        }, "chat-submit").start();
    }

    private static class ChatPostResult {
        final List<String> messages = new ArrayList<>();
        String lmid;
    }

    // POST в ch.php: возвращает кусок JS с add_msg/set_lmid, парсим вручную.
    private ChatPostResult postChatMessage(String url, String payload) {
        HttpURLConnection connection = null;
        try {
            URL target = new URL(url);
            connection = (HttpURLConnection) target.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=windows-1251");
            connection.setRequestProperty("Accept-Encoding", "identity");

            String wvCookie = CookieManager.getInstance().getCookie(url);
            if (wvCookie != null && !wvCookie.isEmpty()) {
                connection.setRequestProperty("Cookie", wvCookie);
            } else {
                String cookie = CookiesManager.obtain(target.getHost());
                if (cookie != null && !cookie.isEmpty()) {
                    connection.setRequestProperty("Cookie", cookie);
                }
            }

            byte[] body = Russian.getBytes(payload == null ? "" : payload);
            connection.setRequestProperty("Content-Length", String.valueOf(body.length));
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body);
                os.flush();
            }

            int code = connection.getResponseCode();
            byte[] bytes;
            try (InputStream responseStream = code >= 400 && connection.getErrorStream() != null
                    ? connection.getErrorStream()
                    : connection.getInputStream()) {
                bytes = readAllBytes(responseStream);
            }

            String contentEncoding = connection.getContentEncoding();
            if ("gzip".equalsIgnoreCase(contentEncoding) && bytes.length > 2
                    && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b) {
                bytes = decompressGzip(bytes);
            }
            if (bytes.length > 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b) {
                bytes = decompressGzip(bytes);
            }

            Map<String, List<String>> headers = connection.getHeaderFields();
            applySetCookies(target, headers);

            String response = new String(bytes, Charset.forName("windows-1251"));
            ChatPostResult result = parseChatPostResponse(response);
            Log.d("WebAppInterface", "chatSubmit: response bytes=" + bytes.length
                    + " addMsg=" + result.messages.size()
                    + " lmid=" + (result.lmid == null ? "" : result.lmid));
            return result;
        } catch (Exception e) {
            Log.e("WebAppInterface", "chatSubmit: POST failed", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void applySetCookies(URL url, Map<String, List<String>> headers) {
        if (headers == null) return;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && "Set-Cookie".equalsIgnoreCase(entry.getKey())) {
                List<String> values = entry.getValue();
                if (values == null) continue;
                for (String sc : values) {
                    CookiesManager.assign(url.getHost(), sc);
                    CookieManager.getInstance().setCookie(url.getProtocol() + "://" + url.getHost(), sc);
                }
                CookieManager.getInstance().flush();
                break;
            }
        }
    }

    private ChatPostResult parseChatPostResponse(String html) {
        ChatPostResult result = new ChatPostResult();
        if (html == null || html.isEmpty()) return result;
        int idx = 0;
        while (idx >= 0 && idx < html.length()) {
            idx = html.indexOf("add_msg", idx);
            if (idx < 0) break;
            int p = html.indexOf('(', idx);
            if (p < 0) break;
            String msg = extractJsStringArg(html, p + 1);
            if (msg != null) {
                result.messages.add(msg);
            }
            idx = p + 1;
        }
        int lidx = 0;
        while (lidx >= 0 && lidx < html.length()) {
            lidx = html.indexOf("set_lmid", lidx);
            if (lidx < 0) break;
            int p = html.indexOf('(', lidx);
            if (p < 0) break;
            String lmid = extractJsTokenArg(html, p + 1);
            if (lmid != null && !lmid.isEmpty()) {
                result.lmid = lmid;
            }
            lidx = p + 1;
        }
        return result;
    }

    private String extractJsStringArg(String text, int start) {
        int i = start;
        int len = text.length();
        while (i < len && Character.isWhitespace(text.charAt(i))) i++;
        if (i >= len) return null;
        char quote = text.charAt(i);
        if (quote != '\'' && quote != '"') return null;
        i++;
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (; i < len; i++) {
            char c = text.charAt(i);
            if (escape) {
                if (c == 'n') sb.append('\n');
                else if (c == 'r') sb.append('\r');
                else if (c == 't') sb.append('\t');
                else sb.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == quote) {
                return sb.toString();
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private String extractJsTokenArg(String text, int start) {
        int i = start;
        int len = text.length();
        while (i < len && Character.isWhitespace(text.charAt(i))) i++;
        if (i >= len) return null;
        char c = text.charAt(i);
        if (c == '\'' || c == '"') {
            return extractJsStringArg(text, i);
        }
        StringBuilder sb = new StringBuilder();
        for (; i < len; i++) {
            c = text.charAt(i);
            if (c == ')' || c == ';' || Character.isWhitespace(c)) break;
            sb.append(c);
        }
        return sb.toString();
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

    private byte[] decompressGzip(byte[] compressed) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzis = new GZIPInputStream(new java.io.ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = gzis.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
        }
        return baos.toByteArray();
    }

    private String recodeUrlEncoded(String payload, Charset from, Charset to) {
        if (payload == null || payload.isEmpty()) return payload;
        try {
            String[] pairs = payload.split("&", -1);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pairs.length; i++) {
                String pair = pairs[i];
                int eq = pair.indexOf('=');
                String key = eq >= 0 ? pair.substring(0, eq) : pair;
                String val = eq >= 0 ? pair.substring(eq + 1) : "";
                String keyDecoded = URLDecoder.decode(key, from.name());
                String valDecoded = URLDecoder.decode(val, from.name());
                String keyEncoded = URLEncoder.encode(keyDecoded, to.name());
                String valEncoded = URLEncoder.encode(valDecoded, to.name());
                sb.append(keyEncoded);
                if (eq >= 0) {
                    sb.append('=').append(valEncoded);
                }
                if (i < pairs.length - 1) sb.append('&');
            }
            return sb.toString();
        } catch (Exception e) {
            return payload;
        }
    }

    private void evalChatButtonsJs(String js) {
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (activity.binding != null && activity.binding.appBarMain != null
                    && activity.binding.appBarMain.contentMain != null
                    && activity.binding.appBarMain.contentMain.chatButtonsWebview != null) {
                activity.binding.appBarMain.contentMain.chatButtonsWebview.evaluateJavascript(js, null);
            }
        });
    }

    private String jsQuote(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    @JavascriptInterface
    public void chatFocus() {
        // Фокус на поле ввода чата (chat buttons).
        evalChatButtonsJs("if(document.FBT && document.FBT.text){document.FBT.text.focus();}");
    }

    @JavascriptInterface
    public void chatAppend(String text) {
        // Добавить текст в поле ввода чата (например, смайлы).
        String t = jsQuote(text);
        evalChatButtonsJs("if(document.FBT && document.FBT.text){document.FBT.text.value += '" + t + "';document.FBT.text.focus();}");
    }

    @JavascriptInterface
    public void chatSayPrivate(String nick) {
        // Вставка адресата в поле ввода (учёт %clan/%pair/%private).
        String raw = nick == null ? "" : nick;
        boolean isPair = raw.startsWith("%%%");
        boolean isClan = !isPair && raw.startsWith("%%");
        boolean isPrivate = !isPair && !isClan && raw.startsWith("%");
        if (isPair) raw = raw.substring(3);
        else if (isClan) raw = raw.substring(2);
        else if (isPrivate) raw = raw.substring(1);
        String prefix = isPair ? "%pair%" : (isClan ? "%clan%" : "");
        String n = jsQuote(raw);
        String p = jsQuote(prefix);
        evalChatButtonsJs("if(document.FBT && document.FBT.text){var v=document.FBT.text.value;if(v.length<255) document.FBT.text.value='" + p + "%<" + n + "> '+v;document.FBT.text.focus();}");
    }

    @JavascriptInterface
    public void chatSayTo(String nick) {
        // Вставка "<Nick> " в поле ввода (обычный чат).
        String n = jsQuote(nick);
        evalChatButtonsJs("if(document.FBT && document.FBT.text){var v=document.FBT.text.value;if(v.length<255) document.FBT.text.value='<" + n + "> '+v;document.FBT.text.focus();}");
    }

    @JavascriptInterface
    public void chatClearChat() {
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (activity.binding != null && activity.binding.appBarMain != null
                    && activity.binding.appBarMain.contentMain != null
                    && activity.binding.appBarMain.contentMain.chatMsgWebview != null) {
                activity.binding.appBarMain.contentMain.chatMsgWebview
                        .evaluateJavascript("if(document.getElementById('msg')){document.getElementById('msg').innerHTML='';}", null);
            }
        });
        chatFocus();
    }

    @JavascriptInterface
    public void chatRefreshNow() {
        // Немедленное обновление чата (top.ch_refresh).
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        activity.runOnUiThread(activity::requestChatRefreshNow);
    }

    @JavascriptInterface
    public void chatClanPrivate() {
        evalChatButtonsJs("if(document.FBT && document.FBT.text){document.FBT.text.value='%clan% ' + document.FBT.text.value;document.FBT.text.focus();}");
    }

    private String adjustClanMarkers(String message) {
        if (message == null || message.isEmpty()) return message;
        String lower = message.toLowerCase();
        String updated = message;
        if (lower.contains("pair:") || lower.contains("%<pair>")) {
            updated = updated
                    .replaceAll("(?i)(<span\\s+alt=\")%(?!%)", "$1%%%")
                    .replaceAll("(?i)(<span\\s+title=\")%(?!%)", "$1%%%")
                    .replaceAll("(?i)(<span\\s+alt=')%(?!%)", "$1%%%")
                    .replaceAll("(?i)(<span\\s+title=')%(?!%)", "$1%%%");
        } else if (lower.contains("clan:") || lower.contains("%<clan>")) {
            updated = updated
                    .replaceAll("(?i)(<span\\s+alt=\")%(?!%)", "$1%%")
                    .replaceAll("(?i)(<span\\s+title=\")%(?!%)", "$1%%")
                    .replaceAll("(?i)(<span\\s+alt=')%(?!%)", "$1%%")
                    .replaceAll("(?i)(<span\\s+title=')%(?!%)", "$1%%");
        }
        return updated;
    }

    @JavascriptInterface
    public void chatChangeChatSpeed() {
        // Переключение скорости опроса чата (10/30/60 сек).
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        int current = activity.getChatRefreshSeconds();
        int next = (current == 10) ? 30 : (current == 30 ? 60 : 10);
        activity.setChatRefreshSeconds(next);
        evalChatButtonsJs("if(document.FBT && document.FBT.spchat){document.FBT.spchat.src='http://image.neverlands.ru/chat/bb_" + next + ".gif';" +
                "document.FBT.spchat.alt='Скорость обновления (раз в " + next + " секунд)';" +
                "document.FBT.spchat.title='Скорость обновления (раз в " + next + " секунд)';}");
    }

    @JavascriptInterface
    public void chatChangeChatSetup() {
        // Переключение режима чата (все/личные/выкл).
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        int current = activity.getChatFyo();
        int next = (current == 0) ? 1 : (current == 1 ? 2 : 0);
        activity.setChatFyo(next);
        String img;
        String alt;
        if (next == 1) {
            img = "http://image.neverlands.ru/chat/bb3_me.gif";
            alt = "Режим чата (Показывать только личные сообщения)";
        } else if (next == 2) {
            img = "http://image.neverlands.ru/chat/bb3_none.gif";
            alt = "Режим чата (Не показывать сообщения)";
        } else {
            img = "http://image.neverlands.ru/chat/bb3_all.gif";
            alt = "Режим чата (Показывать все сообщения)";
        }
        evalChatButtonsJs("if(document.FBT){if(document.FBT.fyo) document.FBT.fyo.value=" + next + ";" +
                "if(document.FBT.schat){document.FBT.schat.src='" + img + "';document.FBT.schat.alt='" + alt + "';document.FBT.schat.title='" + alt + "';}}");
    }

    @JavascriptInterface
    public void chatChangeLatrus() {
        // Переключение LAT<->RUS (транслит) для чата.
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        boolean next = !activity.isChatLatrus();
        activity.setChatLatrus(next);
        String img = next ? "http://image.neverlands.ru/chat/bb4_ac.gif" : "http://image.neverlands.ru/chat/bb4_nc.gif";
        String alt = next ? "LAT <-> RUS (Транслит включён)" : "LAT <-> RUS (Транслит выключен)";
        evalChatButtonsJs("top.latrus=" + (next ? "1" : "0") + ";" +
                "if(document.FBT && document.FBT.lrchat){document.FBT.lrchat.src='" + img + "';document.FBT.lrchat.alt='" + alt + "';document.FBT.lrchat.title='" + alt + "';}");
    }

    @JavascriptInterface
    public void AutoSelect() {
        Log.d("WebAppInterface", BG_TRACE_PREFIX + " AutoSelect called");
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        activity.runOnUiThread(activity::requestAutoSelect);
    }

    @JavascriptInterface
    public void AutoTurn() {
        Log.d("WebAppInterface", BG_TRACE_PREFIX + " AutoTurn called");
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        activity.runOnUiThread(activity::requestAutoTurn);
    }

    @JavascriptInterface
    public void AutoBoi() {
        Log.d("WebAppInterface", "AutoBoi called, current state: " + AppVars.Autoboi);
        boolean enable = AppVars.Autoboi != AutoboiState.AutoboiOn;
        try {
            if (mContext != null) {
                AutoFunctionsManager.getInstance(mContext).setAutoFightEnabled(enable);
            } else {
                AppVars.Autoboi = enable ? AutoboiState.AutoboiOn : AutoboiState.AutoboiOff;
                if (AppVars.Profile != null) {
                    AppVars.Profile.LezDoAutoboi = enable;
                }
            }
            Log.d("WebAppInterface", BG_TRACE_PREFIX + " AutoBoi toggled: enable=" + enable
                    + ", appVarsAutoboi=" + AppVars.Autoboi);
        } catch (Exception e) {
            Log.e("WebAppInterface", BG_TRACE_PREFIX + " AutoBoi toggle failed", e);
        }
    }

    @JavascriptInterface
    public void processFightHtml(String html) {
        Log.d("WebAppInterface", BG_TRACE_PREFIX + " processFightHtml called"
                + ", htmlLen=" + (html == null ? 0 : html.length()));
        if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            AppVars.mainActivity.get().getFightViewModel().processFightHtml(html);
        }
    }

    @JavascriptInterface
    public void AutoUd() {
        Log.d("WebAppInterface", BG_TRACE_PREFIX + " AutoUd called");
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        activity.runOnUiThread(activity::requestAutoTurn);
    }

    @JavascriptInterface
    public void ResetCure() {
        Log.d("WebAppInterface", "ResetCure called");
        // Сброс состояния "восстановление" — возвращаем автобой в активное состояние
        // Аналог ResetCure() в C# ScriptManager.cs
        if (AppVars.Autoboi == AutoboiState.Restoring || AppVars.Autoboi == AutoboiState.Timeout) {
            AppVars.Autoboi = AutoboiState.AutoboiOn;
            Log.d("WebAppInterface", "ResetCure: Autoboi reset to AutoboiOn");
        }
    }

    @JavascriptInterface
    public void ResetLastBoiTimer() {
        Log.d("WebAppInterface", "ResetLastBoiTimer called");
        AppVars.LastBoiTimer = new java.util.Date();
    }

    @JavascriptInterface
    public String XodButtonElapsedTime() {
        long millis = System.currentTimeMillis() - AppVars.LastBoiTimer.getTime();
        return " ход " + ru.neverlands.abclient.utils.ConverterUtils.timeSpanToString(millis) + " ";
    }

    @JavascriptInterface
    public String InfoToolTip(String name, String alt) {
        Log.d("WebAppInterface", "InfoToolTip: " + name);
        return alt;
    }

    @JavascriptInterface
    public int BulkSellOldArg1() {
        // TODO: Implement bulk sell logic
        return 0;
    }

    @JavascriptInterface
    public int BulkSellOldArg2() {
        // TODO: Implement bulk sell logic
        return 0;
    }

    @JavascriptInterface
    public void TraceDrinkPotion(String nick, String potion) {
        Log.d("WebAppInterface", "TraceDrinkPotion: " + nick + ", " + potion);
    }

    @JavascriptInterface
    public void startBulkSell(String thing, String price, String link) {
        Log.d("WebAppInterface", "Start bulk sell: " + thing);
        // TODO: Set AppVars and trigger refresh
    }

    @JavascriptInterface
    public void startBulkDrop(String thing, String price) {
        Log.d("WebAppInterface", "Start bulk drop: " + thing);
        // TODO: Set AppVars and trigger refresh
    }

    @JavascriptInterface
    public void showHpMaTimers(String s, float curHP, int maxHP, float intHP, float curMA, int maxMA, float intMA) {
        if (intHP > 0f) {
            AppVars.PersIntHP = intHP;
        }
        if (intMA > 0f) {
            AppVars.PersIntMA = intMA;
        }
        Log.d("WebAppInterface", "showHpMaTimers: hp=" + curHP + "/" + maxHP
                + " ma=" + curMA + "/" + maxMA
                + " intHP=" + intHP + " intMA=" + intMA);
    }

    @JavascriptInterface
    public void startBulkOldSell(String name, String price) {
        // TODO: Pass this data to a ViewModel
        System.out.println("Bulk sell: " + name + " for " + price);
    }

    @JavascriptInterface
    public void loadFrame(String frameName, String url) {
        // Замена frameset‑навигации: перенаправляем в нужный WebView.
        Log.d("WebAppInterface", "loadFrame: " + frameName + " to " + url);
        if (AppVars.mainActivity == null || AppVars.mainActivity.get() == null) {
            return;
        }

        // Ensure the URL is absolute
        if (!url.startsWith("http")) {
            url = "http://neverlands.ru/" + url.replaceFirst("^/+", "");
        }

        final String finalUrl = url;
        AppVars.mainActivity.get().runOnUiThread(() -> {
            switch (frameName) {
                case "main_top":
                    AppVars.mainActivity.get().binding.appBarMain.contentMain.webView.loadUrl(finalUrl);
                    break;
                case "ch_list":
                    AppVars.mainActivity.get().binding.appBarMain.contentMain.chatUsersWebview.loadUrl(finalUrl);
                    break;
                case "ch_buttons":
                    AppVars.mainActivity.get().binding.appBarMain.contentMain.chatButtonsWebview.loadUrl(finalUrl);
                    break;
                case "chmain":
                    AppVars.mainActivity.get().binding.appBarMain.contentMain.chatMsgWebview.loadUrl(finalUrl);
                    break;
                case "ch_refr":
                    AppVars.url_ch_refr = finalUrl;
                    Log.d("WebAppInterface", "loadFrame: ch_refr to " + finalUrl);
                    AppVars.mainActivity.get().loadChatRefrUrl(finalUrl);
                    break;
                default:
                    // Load in the main webview by default
                    AppVars.mainActivity.get().binding.appBarMain.contentMain.webView.loadUrl(finalUrl);
                    break;
            }
        });
    }

    /**
     * Открыть URL в новой вспомогательной вкладке.
     * Аналог CreateNewTab в C# версии.
     * @param url URL для загрузки
     * @param title Заголовок вкладки
     */
    @JavascriptInterface
    public void openInNewTab(String url, String title) {
        Log.d("WebAppInterface", "openInNewTab: " + title + " -> " + url);
        
        // Всегда используем MainActivity для открытия вкладки
        if (AppVars.mainActivity == null || AppVars.mainActivity.get() == null) {
            return;
        }
        
        AppVars.mainActivity.get().runOnUiThread(() -> {
            AppVars.mainActivity.get().openInNewTab(url, title);
        });
    }

    @JavascriptInterface
    public void redirectToUrl(String url) {
        // Приводим ссылку к абсолютной, иначе WebView.loadUrl(...) её не откроет и перехватчик не сработает.
        String finalUrl = url;
        if (finalUrl != null && !finalUrl.startsWith("http")) {
            finalUrl = "http://neverlands.ru/" + finalUrl.replaceFirst("^/+", "");
        }

        Log.d("WebAppInterface", "redirectToUrl: " + finalUrl);
        
        // Используем локальный broadcast, чтобы его получил MainActivity (регистрируется через LocalBroadcastManager)
        if (mContext != null) {
            Intent intent = new Intent(AppVars.ACTION_WEBVIEW_LOAD_URL);
            intent.putExtra("url", finalUrl);
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        }

        // Дополнительно — прямой вызов, если активити доступна (страховка от пропуска broadcast)
        MainActivity activity = getMainActivityOrNull();
        if (activity != null) {
            final String toLoad = finalUrl;
            activity.runOnUiThread(() -> activity.getMainWebView().loadUrl(toLoad));
        }
    }
}

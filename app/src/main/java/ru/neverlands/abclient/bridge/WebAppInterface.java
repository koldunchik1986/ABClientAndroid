package ru.neverlands.abclient.bridge;

import android.content.Context;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import ru.neverlands.abclient.manager.ContactsManager;
import ru.neverlands.abclient.utils.AppVars;

/**
 * Класс-мост (bridge) для взаимодействия между JavaScript в WebView и нативным кодом Android.
 * Методы, аннотированные @JavascriptInterface, могут быть вызваны из JS.
 * В JS этот объект доступен как `AndroidBridge`.
 */
public class WebAppInterface {
    Context mContext;

    /** Конструктор, инициализирующий контекст. */
    public WebAppInterface(Context c) {
        mContext = c;
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
        // TODO: Implement chat message parsing and event handling
        Log.d("ChatFilter", message);
        return message; // Return message unmodified for now
    }

    @JavascriptInterface
    public void chatUpdated() {
        // TODO: Trigger UI refresh if needed
        Log.d("WebAppInterface", "Chat updated");
    }

    @JavascriptInterface
    public void AutoSelect() {
        Log.d("WebAppInterface", "AutoSelect called");
        // TODO: Implement logic to show best move
    }

    @JavascriptInterface
    public void AutoTurn() {
        Log.d("WebAppInterface", "AutoTurn called");
        // TODO: Implement logic to perform best move
    }

    @JavascriptInterface
    public void AutoBoi() {
        Log.d("WebAppInterface", "AutoBoi called");
        // TODO: Toggle auto-battle state
    }

    @JavascriptInterface
    public void AutoUd() {
        Log.d("WebAppInterface", "AutoUd called");
        // TODO: Implement this
    }

    @JavascriptInterface
    public void ResetCure() {
        Log.d("WebAppInterface", "ResetCure called");
        // TODO: Implement this
    }

    @JavascriptInterface
    public void ResetLastBoiTimer() {
        Log.d("WebAppInterface", "ResetLastBoiTimer called");
        // TODO: Implement this
    }

    @JavascriptInterface
    public String XodButtonElapsedTime() {
        // TODO: Implement timer logic
        return " ход (0:00) ";
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
        // TODO: Pass this data to a ViewModel
        System.out.println("HP: " + curHP + "/" + maxHP + ", MP: " + curMA + "/" + maxMA);
    }

    @JavascriptInterface
    public void startBulkOldSell(String name, String price) {
        // TODO: Pass this data to a ViewModel
        System.out.println("Bulk sell: " + name + " for " + price);
    }
}

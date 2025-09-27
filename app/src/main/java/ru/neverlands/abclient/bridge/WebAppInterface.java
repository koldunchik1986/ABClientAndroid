package ru.neverlands.abclient.bridge;

import android.content.Context;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

public class WebAppInterface {
    Context mContext;

    /** Instantiate the interface and set the context */
    public WebAppInterface(Context c) {
        mContext = c;
    }

    /** Show a toast from the web page */
    @JavascriptInterface
    public void showToast(String toast) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
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

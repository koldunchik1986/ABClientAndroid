package com.neverlands.anlc.webview.inventory

import android.webkit.JavascriptInterface
import android.util.Log
import com.neverlands.anlc.data.local.AppVars
import com.neverlands.anlc.ui.main.MainViewModel // Import MainViewModel

class InventoryInterface(private val viewModel: MainViewModel) { // Add viewModel to constructor

        @JavascriptInterface
    fun startBulkSell(thing: String, price: String, link: String) {
        Log.d("InventoryInterface", "startBulkSell() called with thing: $thing, price: $price, link: $link")
        val p = price.toIntOrNull()
        if (p == null) {
            Log.e("InventoryInterface", "Failed to parse price: $price")
            return
        }
        AppVars.bulkSellThing = thing
        AppVars.bulkSellPrice = p
        AppVars.bulkSellSum = 0 // C# sets this to 0
        viewModel.reloadWebView() // Trigger reload
    }

        @JavascriptInterface
    fun startBulkOldSell(name: String, price: String) {
        Log.d("InventoryInterface", "startBulkOldSell() called with name: $name, price: $price")
        AppVars.bulkSellOldName = name
        AppVars.bulkSellOldPrice = price

        // TODO: Implement actual logic for iterating through AppVars.ShopList and calling WriteChatMsgSafe.
        // This requires porting AppVars.ShopList and ShopEntry class, and WriteChatMsgSafe.
    }

    @JavascriptInterface
    fun startBulkDrop(thing: String, price: String) {
        Log.d("InventoryInterface", "startBulkDrop() called with thing: $thing, price: $price")
        AppVars.bulkDropThing = thing
        AppVars.bulkDropPrice = price
        viewModel.reloadWebView() // Trigger reload
    }

    @JavascriptInterface
    fun bulkSellOldArg1(): Int {
        Log.d("InventoryInterface", "bulkSellOldArg1() called")
        if (AppVars.bulkSellOldName.isEmpty()) {
            return 0
        }

        val pars = AppVars.bulkSellOldScript.split(',')
        if (pars.isEmpty()) {
            return 0
        }

        val a1 = pars[0].trim()
        val result = a1.toIntOrNull()

        if (result != null) {
            // TODO: Implement WriteChatMsgSafe. For now, just log.
            Log.d("InventoryInterface", "Сдача ${AppVars.bulkSellOldName} (ID:$a1) за ${AppVars.bulkSellOldPrice}NV...")
            return result
        }

        return 0
    }

    @JavascriptInterface
    fun bulkSellOldArg2(): String {
        Log.d("InventoryInterface", "bulkSellOldArg2() called")
        if (AppVars.bulkSellOldName.isEmpty()) {
            return ""
        }

        val pars = AppVars.bulkSellOldScript.split(',')
        if (pars.size < 2) { // Ensure there's a second part
            return ""
        }

        val a2 = pars[1].trim(' ', '\'')
        return a2
    }
}
package com.neverlands.anlc.webview

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.util.Log
import com.neverlands.anlc.data.local.ProfileManager
import com.neverlands.anlc.data.local.AppVars
import com.neverlands.anlc.data.local.MapManager
import com.neverlands.anlc.data.local.model.Prims
import com.neverlands.anlc.data.local.model.HerbCell
import com.neverlands.anlc.ui.main.MainViewModel
import com.neverlands.anlc.data.local.ChatProcessor
import com.neverlands.anlc.webview.map.MapInterface
import com.neverlands.anlc.webview.chat.ChatInterface
import com.neverlands.anlc.webview.fight.FightInterface
import com.neverlands.anlc.webview.inventory.InventoryInterface
import com.neverlands.anlc.webview.herb.HerbInterface
import com.neverlands.anlc.webview.general.GeneralInterface
import java.util.Date
import java.util.Locale

/**
 * Класс, предоставляющий интерфейс JavaScript для взаимодействия с нативным Android-кодом.
 * Методы, аннотированные @JavascriptInterface, будут доступны из JavaScript в WebView.
 */
class JavaScriptInterface(
    private val webView: WebView,
    private val viewModel: MainViewModel,
    private val chatProcessor: ChatProcessor
) {

    init {
        // Add categorized interfaces to the WebView
        webView.addJavascriptInterface(MapInterface(), "MapInterface")
        webView.addJavascriptInterface(ChatInterface(), "ChatInterface")
        webView.addJavascriptInterface(FightInterface(viewModel), "FightInterface")
        webView.addJavascriptInterface(InventoryInterface(viewModel), "InventoryInterface")
        webView.addJavascriptInterface(HerbInterface(), "HerbInterface")
        webView.addJavascriptInterface(GeneralInterface(viewModel), "GeneralInterface")
    }
}
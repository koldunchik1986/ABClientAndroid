package com.neverlands.anlc.webview.fight

import android.webkit.JavascriptInterface
import android.util.Log
import com.neverlands.anlc.data.local.ProfileManager
import com.neverlands.anlc.data.local.AppVars
import com.neverlands.anlc.ui.main.MainViewModel
import java.util.Date
import java.util.Locale

class FightInterface(
    private val viewModel: MainViewModel
) {

    @JavascriptInterface
    fun autoSelect() {
        Log.d("FightInterface", "autoSelect() called")
        // TODO: Implement actual logic for auto-selecting fight actions.
        // This involves parsing the fight frame HTML, determining optimal moves,
        // and manipulating WebView DOM elements or sending appropriate requests.
        // This is a complex task requiring detailed knowledge of game mechanics and UI.
    }

    @JavascriptInterface
    fun autoTurn() {
        Log.d("FightInterface", "autoTurn() called")
        // TODO: Implement actual logic for auto-turning in combat.
        // This involves parsing the fight frame HTML, determining the next action,
        // and invoking JavaScript functions like "AutoSubmit" with the correct arguments.
        // This is a complex task requiring detailed knowledge of game mechanics and UI.
    }

    @JavascriptInterface
    fun autoBoi() {
        Log.d("FightInterface", "autoBoi() called")
        viewModel.autoBoi()
    }

    @JavascriptInterface
    fun resetLastBoiTimer() {
        Log.d("FightInterface", "resetLastBoiTimer() called")
        AppVars.lastBoiTimer = Date()
    }

    @JavascriptInterface
    fun resetCure() {
        Log.d("FightInterface", "resetCure() called")
        viewModel.resetCure()
    }

    @JavascriptInterface
    fun fastAttack(nick: String) {
        Log.d("FightInterface", "fastAttack() called with nick: $nick")
        // TODO: Implement actual attack logic (fetching user info, waiting for fight end, etc.).
        // This is a complex task involving network requests, polling, and interaction
        // with game mechanics. For now, we'll just reload the WebView.
        viewModel.reloadWebView() // Simulate reloading the frame after an attack
    }

    @JavascriptInterface
    fun checkFastAttack(nick: String, str: String): String {
        Log.d("FightInterface", "checkFastAttack() called with nick: $nick, str: $str")
        val profile = ProfileManager.getCurrentProfile()
        return if (nick.equals(profile?.userNick, ignoreCase = true)) {
            ""
        } else {
            if (profile?.doShowFastAttack == true) str else ""
        }
    }

    @JavascriptInterface
    fun fastAttackBlood(nick: String) {
        Log.d("FightInterface", "fastAttackBlood() called with nick: $nick")
        // TODO: Implement actual attack logic (fetching user info, waiting for fight end, etc.).
        // This is a complex task involving network requests, polling, and interaction
        // with game mechanics. For now, we'll just reload the WebView.
        viewModel.reloadWebView() // Simulate reloading the frame after an attack
    }

    @JavascriptInterface
    fun checkFastAttackBlood(nick: String, str: String): String {
        Log.d("FightInterface", "checkFastAttackBlood() called with nick: $nick, str: $str")
        val profile = ProfileManager.getCurrentProfile()
        return if (nick.equals(profile?.userNick, ignoreCase = true)) {
            ""
        } else {
            if (profile?.doShowFastAttackBlood == true) str else ""
        }
    }

    @JavascriptInterface
    fun fastAttackUltimate(nick: String) {
        Log.d("FightInterface", "fastAttackUltimate() called with nick: $nick")
        // TODO: Implement actual attack logic (fetching user info, waiting for fight end, etc.).
        // This is a complex task involving network requests, polling, and interaction
        // with game mechanics. For now, we'll just reload the WebView.
        viewModel.reloadWebView() // Simulate reloading the frame after an attack
    }

    @JavascriptInterface
    fun fastAttackClosedUltimate(nick: String) {
        Log.d("FightInterface", "fastAttackClosedUltimate() called with nick: $nick")
        // TODO: Implement actual attack logic (fetching user info, waiting for fight end, etc.).
        // This is a complex task involving network requests, polling, and interaction
        // with game mechanics. For now, we'll just reload the WebView.
        viewModel.reloadWebView() // Simulate reloading the frame after an attack
    }

    @JavascriptInterface
    fun checkFastAttackUltimate(nick: String, str: String): String {
        Log.d("FightInterface", "checkFastAttackUltimate() called with nick: $nick, str: $str")
        val profile = ProfileManager.getCurrentProfile()
        return if (nick.equals(profile?.userNick, ignoreCase = true)) {
            ""
        } else {
            if (profile?.doShowFastAttackUltimate == true) str else ""
        }
    }

    @JavascriptInterface
    fun checkFastAttackClosedUltimate(nick: String, str: String): String {
        Log.d("FightInterface", "checkFastAttackClosedUltimate() called with nick: $nick, str: $str")
        val profile = ProfileManager.getCurrentProfile()
        return if (nick.equals(profile?.userNick, ignoreCase = true)) {
            ""
        } else {
            if (profile?.doShowFastAttackClosedUltimate == true) str else ""
        }
    }

    @JavascriptInterface
    fun fastAttackClosed(nick: String) {
        Log.d("FightInterface", "fastAttackClosed() called with nick: $nick")
        // TODO: Implement actual attack logic (fetching user info, waiting for fight end, etc.).
        // This is a complex task involving network requests, polling, and interaction
        // with game mechanics. For now, we'll just reload the WebView.
        viewModel.reloadWebView() // Simulate reloading the frame after an attack
    }

    @JavascriptInterface
    fun checkFastAttackClosed(nick: String, str: String): String {
        Log.d("FightInterface", "checkFastAttackClosed() called with nick: $nick, str: $str")
        val profile = ProfileManager.getCurrentProfile()
        return if (nick.equals(profile?.userNick, ignoreCase = true)) {
            ""
        } else {
            if (profile?.doShowFastAttackClosed == true) str else ""
        }
    }

    @JavascriptInterface
    fun fastAttackFist(nick: String) {
        Log.d("FightInterface", "fastAttackFist() called with nick: $nick")
        // TODO: Implement actual attack logic (fetching user info, waiting for fight end, etc.).
        // This is a complex task involving network requests, polling, and interaction
        // with game mechanics. For now, we'll just reload the WebView.
        viewModel.reloadWebView() // Simulate reloading the frame after an attack
    }

    @JavascriptInterface
    fun checkFastAttackFist(nick: String, str: String): String {
        Log.d("FightInterface", "checkFastAttackFist() called with nick: $nick, str: $str")
        val profile = ProfileManager.getCurrentProfile()
        return if (nick.equals(profile?.userNick, ignoreCase = true)) {
            ""
        } else {
            if (profile?.doShowFastAttackFist == true) str else ""
        }
    }

    @JavascriptInterface
    fun fastAttackClosedFist(nick: String) {
        Log.d("FightInterface", "fastAttackClosedFist() called with nick: $nick")
        // TODO: Implement actual attack logic (fetching user info, waiting for fight end, etc.).
        // This is a complex task involving network requests, polling, and interaction
        // with game mechanics. For now, we'll just reload the WebView.
        viewModel.reloadWebView() // Simulate reloading the frame after an attack
    }

    @JavascriptInterface
    fun checkFastAttackClosedFist(nick: String, str: String): String {
        Log.d("FightInterface", "checkFastAttackClosedFist() called with nick: $nick, str: $str")
        val profile = ProfileManager.getCurrentProfile()
        return if (nick.equals(profile?.userNick, ignoreCase = true)) {
            ""
        } else {
            if (profile?.doShowFastAttackClosedFist == true) str else ""
        }
    }

    @JavascriptInterface
    fun fastAttackOpenNevid() {
        Log.d("FightInterface", "fastAttackOpenNevid() called")
        // TODO: Implement actual attack logic (fetching user info, waiting for fight end, etc.).
        // This is a complex task involving network requests, polling, and interaction
        // with game mechanics. For now, we'll just reload the WebView.
        viewModel.reloadWebView() // Simulate reloading the frame after an attack
    }

    @JavascriptInterface
    fun checkFastAttackOpenNevid(str: String): String {
        Log.d("FightInterface", "checkFastAttackOpenNevid() called with str: $str")
        val profile = ProfileManager.getCurrentProfile()
        return if (profile?.doShowFastAttackOpenNevid == true) str else ""
    }

    @JavascriptInterface
    fun checkFastAttackPoison(nick: String, str: String): String {
        Log.d("FightInterface", "checkFastAttackPoison() called with nick: $nick, str: $str")
        val profile = ProfileManager.getCurrentProfile()
        return if (nick.equals(profile?.userNick, ignoreCase = true)) {
            ""
        } else {
            if (profile?.doShowFastAttackPoison == true) str else ""
        }
    }

    @JavascriptInterface
    fun checkFastAttackStrong(nick: String, str: String): String {
        Log.d("FightInterface", "checkFastAttackStrong() called with nick: $nick, str: $str")
        val profile = ProfileManager.getCurrentProfile()
        return if (nick.equals(profile?.userNick, ignoreCase = true)) {
            ""
        } else {
            if (profile?.doShowFastAttackStrong == true) str else ""
        }
    }

    @JavascriptInterface
    fun fastAttackNevid(nick: String) {
        Log.d("FightInterface", "fastAttackNevid() called with nick: $nick")
        // TODO: Implement actual attack logic (fetching user info, waiting for fight end, etc.).
        // This is a complex task involving network requests, polling, and interaction
        // with game mechanics. For now, we'll just reload the WebView.
        viewModel.reloadWebView() // Simulate reloading the frame after an attack
    }

    @JavascriptInterface
    fun checkFastAttackNevid(nick: String, str: String): String {
        Log.d("FightInterface", "checkFastAttackNevid() called with nick: $nick, str: $str")
        val profile = ProfileManager.getCurrentProfile()
        return if (nick.equals(profile?.userNick, ignoreCase = true)) {
            ""
        } else {
            if (profile?.doShowFastAttackNevid == true) str else ""
        }
    }

    @JavascriptInterface
    fun fastAttackFog(nick: String) {
        Log.d("FightInterface", "fastAttackFog() called with nick: $nick")
        // TODO: Implement actual attack logic (fetching user info, waiting for fight end, etc.).
        // This is a complex task involving network requests, polling, and interaction
        // with game mechanics. For now, we'll just reload the WebView.
        viewModel.reloadWebView() // Simulate reloading the frame after an attack
    }

    @JavascriptInterface
    fun checkFastAttackFog(nick: String, str: String): String {
        Log.d("FightInterface", "checkFastAttackFog() called with nick: $nick, str: $str")
        val profile = ProfileManager.getCurrentProfile()
        return if (nick.equals(profile?.userNick, ignoreCase = true)) {
            ""
        } else {
            if (profile?.doShowFastAttackFog == true) str else ""
        }
    }

    @JavascriptInterface
    fun fastAttackZas(nick: String) {
        Log.d("FightInterface", "fastAttackZas() called with nick: $nick")
        // TODO: Implement actual attack logic (fetching user info, waiting for fight end, etc.).
        // This is a complex task involving network requests, polling, and interaction
        // with game mechanics. For now, we'll just reload the WebView.
        viewModel.reloadWebView() // Simulate reloading the frame after an attack
    }

    @JavascriptInterface
    fun checkFastAttackZas(nick: String, str: String): String {
        Log.d("FightInterface", "checkFastAttackZas() called with nick: $nick, str: $str")
        val profile = ProfileManager.getCurrentProfile()
        return if (nick.equals(profile?.userNick, ignoreCase = true)) {
            ""
        } else {
            if (profile?.doShowFastAttackZas == true) str else ""
        }
    }

    @JavascriptInterface
    fun fastAttackTotem(nick: String) {
        Log.d("FightInterface", "fastAttackTotem() called with nick: $nick")
        // TODO: Implement actual attack logic (fetching user info, waiting for fight end, etc.).
        // This is a complex task involving network requests, polling, and interaction
        // with game mechanics. For now, we'll just reload the WebView.
        viewModel.reloadWebView() // Simulate reloading the frame after an attack
    }

    @JavascriptInterface
    fun checkFastAttackTotem(nick: String, str: String): String {
        Log.d("FightInterface", "checkFastAttackTotem() called with nick: $nick, str: $str")
        val profile = ProfileManager.getCurrentProfile()
        return if (nick.equals(profile?.userNick, ignoreCase = true)) {
            ""
        } else {
            if (profile?.doShowFastAttackTotem == true) str else ""
        }
    }

    @JavascriptInterface
    fun fastAttackPoison(nick: String) {
        Log.d("FightInterface", "fastAttackPoison() called with nick: $nick")
        viewModel.reloadWebView() // Simulate reloading the frame after an attack
    }

    @JavascriptInterface
    fun fastAttackStrong(nick: String) {
        Log.d("FightInterface", "fastAttackStrong() called with nick: $nick")
        viewModel.reloadWebView() // Simulate reloading the frame after an attack
    }

    @JavascriptInterface
    fun fastAttackPortal(nick: String) {
        Log.d("FightInterface", "fastAttackPortal() called with nick: $nick")
        // TODO: Implement actual logic (calls FastStartSafe and ReloadMainFrame in C#).
        // This is a complex task involving network requests, polling, and interaction
        // with game mechanics. For now, we'll just reload the WebView.
        viewModel.reloadWebView() // Simulate reloading the frame after an attack
    }

}
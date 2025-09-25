package com.neverlands.anlc.webview.general

import android.webkit.JavascriptInterface
import android.util.Log
import com.neverlands.anlc.data.local.ProfileManager
import com.neverlands.anlc.data.local.AppVars
import com.neverlands.anlc.data.local.MapManager
import com.neverlands.anlc.data.local.model.Prims
import com.neverlands.anlc.ui.main.MainViewModel
import java.util.Date
import java.util.Locale

class GeneralInterface(
    private val viewModel: MainViewModel
) {

    @JavascriptInterface
    fun doHideMiniMap(): Boolean {
        val profile = ProfileManager.getCurrentProfile()
        return profile?.mapShowMiniMap?.not() ?: true
    }

    @JavascriptInterface
    fun mapText(): String {
        return MapManager.getMapText()
    }

    @JavascriptInterface
    fun setFishNoCaptchaReady() {
        Log.d("GeneralInterface", "setFishNoCaptchaReady() called")
        AppVars.fishNoCaptchaReady = true
    }

    @JavascriptInterface
    fun fishOverload() {
        Log.d("GeneralInterface", "fishOverload() called")
        viewModel.fishOverload()
    }

    @JavascriptInterface
    fun isAutoFish(): Boolean {
        Log.d("GeneralInterface", "isAutoFish() called")
        return ProfileManager.getCurrentProfile()?.fishAuto ?: false
    }

    @JavascriptInterface
    fun insertGuaDiv(code: String): String {
        Log.d("GeneralInterface", "insertGuaDiv() called with code: $code")
        AppVars.codeAddress = code
        val profile = ProfileManager.getCurrentProfile()
        return if (profile != null && profile.doGuamod) {
            AppVars.fightLink = "????"
            "<br><img src=http://image.neverlands.ru/1x1.gif width=1 height=8><br><span id=guamod3><font class=nickname><font color=#004A7F><b>* * * *</b></font></font></span>"
        } else {
            ""
        }
    }

    @JavascriptInterface
    fun setAutoFishMassa(massa: String) {
        Log.d("GeneralInterface", "setAutoFishMassa() called with massa: $massa")
        AppVars.autoFishMassa = massa
    }

    @JavascriptInterface
    fun checkPri(namePri: String, myst: Int): String {
        Log.d("GeneralInterface", "checkPri() called with namePri: $namePri, myst: $myst")
        val profile = ProfileManager.getCurrentProfile()
        if (AppVars.priSelected || myst <= 4) {
            return ""
        }

        profile?.let {
            val fishEnabledPrims = it.fishEnabledPrims
            val primsValue: Int

            when (namePri.toLowerCase(Locale.ROOT)) {
                "хлеб" -> primsValue = Prims.Bread
                "червяк" -> primsValue = Prims.Worm
                "крупный червяк" -> primsValue = Prims.BigWorm
                "опарыш" -> primsValue = Prims.Stink
                "мотыль" -> primsValue = Prims.Fly
                "блесна" -> primsValue = Prims.Light
                "донка" -> primsValue = Prims.Donka
                "мормышка" -> primsValue = Prims.Morm
                "заговоренная блесна" -> primsValue = Prims.HiFlight
                else -> return ""
            }

            if ((fishEnabledPrims and primsValue) != 0) {
                AppVars.priSelected = true
                AppVars.namePri = namePri
                AppVars.valPri = myst
                return " CHECKED"
            }
        }
        return ""
    }

    @JavascriptInterface
    fun xodButtonElapsedTime(): String {
        Log.d("GeneralInterface", "xodButtonElapsedTime() called")
        val diff = Date().time - AppVars.lastBoiTimer.time
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return String.format(Locale.US, " ход %d:%02d:%02d ", hours, minutes % 60, seconds % 60)
    }

    @JavascriptInterface
    fun infoToolTip(img: String, alt: String): String {
        Log.d("GeneralInterface", "infoToolTip() called with img: $img, alt: $alt")
        // Simplified implementation for now
        return "<div><img src=\"http://image.neverlands.ru/weapon/$img\" alt=\"$alt\"></div>"
    }

    @JavascriptInterface
    fun checkQuick(nick: String, str: String): String {
        Log.d("GeneralInterface", "checkQuick() called with nick: $nick, str: $str")
        val profile = ProfileManager.getCurrentProfile()
        return if (profile != null && nick.equals(profile.userNick, ignoreCase = true)) {
            ""
        } else {
            str
        }
    }

    @JavascriptInterface
    fun quick(nick: String) {
        Log.d("GeneralInterface", "quick() called with nick: $nick")
        // TODO: Implement actual UI for quick actions (e.g., show a dialog or start a new activity).
        // This would involve creating an Android dialog or fragment to display
        // quick action options for the given nick.
    }

    @JavascriptInterface
    fun usersOnline(): String {
        Log.d("GeneralInterface", "usersOnline() called")
        return if (!AppVars.usersOnline.isNullOrEmpty()) {
            String.format(Locale.US,
                "<td rowspan=3><div><img src=http://image.neverlands.ru/1x1.gif width=8 height=1><font class=hpfont>[<font color=#ACAAA3>&nbsp;<b>%s</b>&nbsp;</font>]</font></div></td>",
                AppVars.usersOnline)
        } else {
            ""
        }
    }

    @JavascriptInterface
    fun showOverWarning(): Boolean {
        Log.d("GeneralInterface", "showOverWarning() called")
        val profile = ProfileManager.getCurrentProfile()
        return profile?.showOverWarning ?: false
    }

    @JavascriptInterface
    fun setNeverTimer(msec: Int) {
        Log.d("GeneralInterface", "setNeverTimer() called with msec: $msec")
        AppVars.neverTimer = Date(System.currentTimeMillis() + msec)
    }

    @JavascriptInterface
    fun showHpMaTimers(inner: String, curHP: Double, maxHP: Int, intHP: Double, curMA: Double, maxMA: Int, intMA: Double): String {
        Log.d("GeneralInterface", "showHpMaTimers() called with inner: $inner, curHP: $curHP, maxHP: $maxHP, intHP: $intHP, curMA: $curMA, maxMA: $maxMA, intMA: $intMA")
        val sb = StringBuilder("<FONT class=hpfont>: ")
        sb.append("[<font color=#bb0000>")
        sb.append("<b>${curHP.toInt()}</b>")
        sb.append("/")
        sb.append("<b>$maxHP</b>")

        var seconds = ((maxHP - curHP) * intHP / maxHP).toInt()
        if (seconds > 0) {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val remainingSeconds = seconds % 60
            sb.append(String.format(Locale.US, " (<b>%02d:%02d:%02d</b>)", hours, minutes, remainingSeconds))
        }

        sb.append("</font>]")

        sb.append(" | ")

        sb.append("[<font color=#336699>")
        sb.append("<b>${curMA.toInt()}</b>")
        sb.append("/")
        sb.append("<b>$maxMA</b>")

        seconds = ((maxMA - curMA) * intMA / maxMA).toInt()
        if (seconds > 0) {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val remainingSeconds = seconds % 60
            sb.append(String.format(Locale.US, " (<b>%02d:%02d:%02d</b>)", hours, minutes, remainingSeconds))
        }

        sb.append("</font>]</font>")

        return sb.toString()
    }

    @JavascriptInterface
    fun getClassIdOfContact(nick: String): Int {
        Log.d("GeneralInterface", "getClassIdOfContact() called with nick: $nick")
        val profile = ProfileManager.getCurrentProfile()
        return profile?.contacts?.get(nick.toLowerCase(Locale.ROOT))?.classId ?: -1
    }
}
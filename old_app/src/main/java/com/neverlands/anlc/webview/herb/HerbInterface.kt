package com.neverlands.anlc.webview.herb

import android.webkit.JavascriptInterface
import android.util.Log
import com.neverlands.anlc.data.local.ProfileManager
import com.neverlands.anlc.data.local.AppVars
import com.neverlands.anlc.data.local.model.HerbCell

class HerbInterface {

    @JavascriptInterface
    fun herbsList(list: String) {
        Log.d("HerbInterface", "herbsList() called with list: $list")
        val profile = ProfileManager.getCurrentProfile()
        if (profile != null) {
            val updatedInTicks = System.currentTimeMillis()
            val mapLocation = profile.mapLocation

            if (profile.herbCells.containsKey(mapLocation)) {
                profile.herbCells[mapLocation]?.herbs = list
                profile.herbCells[mapLocation]?.updatedInTicks = updatedInTicks
            } else {
                val herbCell = HerbCell(
                    regNum = mapLocation,
                    herbs = list,
                    updatedInTicks = updatedInTicks
                )
                profile.herbCells[mapLocation] = herbCell
            }
            ProfileManager.saveCurrentProfile()
        }
    }

    @JavascriptInterface
    fun isHerbAutoCut(herb: String): Boolean {
        Log.d("HerbInterface", "isHerbAutoCut() called with herb: $herb")
        val profile = ProfileManager.getCurrentProfile()
        if (!AppVars.doHerbAutoCut) {
            return false
        }
        return profile?.herbsAutoCut?.contains(herb) ?: false
    }

    @JavascriptInterface
    fun herbCut(name: String) {
        Log.d("HerbInterface", "herbCut() called with name: $name")
        // TODO: Implement actual logic (update chat, call TraceCut).
        // This method is commented out in the C# source, so its full functionality
        // is not clear without further investigation.
    }

    @JavascriptInterface
    fun doHerbAutoCut(): Boolean {
        Log.d("HerbInterface", "doHerbAutoCut() called")
        // TODO: Implement actual logic (check Key.KeyFile.IsPay() and CheckTied()).
        // This method is commented out in the C# source, so its full functionality
        // is not clear without further investigation.
        return AppVars.doHerbAutoCut
    }

    @JavascriptInterface
    fun traceCut(herb: String) {
        Log.d("HerbInterface", "traceCut() called with herb: $herb")
        // TODO: Implement actual logic (format message, calculate nextTime, add AppTimer, save profile).
        // This is a complex method involving date/time calculations, AppVars.Profile.ServDiff,
        // GetShift() method, and updating chat/timers.
    }

    @JavascriptInterface
    fun traceCutID(herbid: String) {
        Log.d("HerbInterface", "traceCutID() called with herbid: $herbid")
        // TODO: Implement actual logic. This method is almost identical to traceCut().
        // It involves date/time calculations, AppVars.Profile.ServDiff, GetShift() method,
        // and updating chat/timers.
    }
}
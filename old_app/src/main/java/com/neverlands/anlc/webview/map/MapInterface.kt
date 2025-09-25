package com.neverlands.anlc.webview.map

import android.webkit.JavascriptInterface
import android.util.Log
import com.neverlands.anlc.data.local.MapManager
import com.neverlands.anlc.data.local.ProfileManager
import java.util.Locale

class MapInterface {

    @JavascriptInterface
    fun getCellDivText(x: Int, y: Int, scale: Int, link: String, showmove: Boolean, isframe: Boolean): String {
        return MapManager.getCellDivText(x, y, scale, link, showmove, isframe)
    }

    @JavascriptInterface
    fun getCellAltText(x: Int, y: Int, scale: Int): String {
        return MapManager.getCellAltText(x, y, scale)
    }

    @JavascriptInterface
    fun isCellExists(x: Int, y: Int): Boolean {
        return MapManager.isCellExists(x, y)
    }

    @JavascriptInterface
    fun isCellInPath(x: Int, y: Int): Boolean {
        return MapManager.isCellInPath(x, y)
    }

    @JavascriptInterface
    fun getCellLabel(x: Int, y: Int): String {
        val coor = MapManager.makePosition(x, y)
        if (!MapManager.locations.containsKey(coor)) {
            return ""
        }

        val regnum = MapManager.locations[coor]?.regNum
        if (regnum == null) {
            return ""
        }

        if (!MapManager.cells.containsKey(regnum)) {
            return ""
        }

        val result = MapManager.cells[regnum]?.tooltip
        return result ?: ""
    }

    @JavascriptInterface
    fun getRegionBorders(framelabel: String, x: Int, y: Int): String {
        val profile = ProfileManager.getCurrentProfile()
        if (profile == null || !profile.mapDrawRegion) {
            return "00001"
        }

        val sb = StringBuilder("00000")

        val label = getCellLabel(x, y)
        if (label.equals(framelabel, ignoreCase = true)) {
            sb.setCharAt(4, '1')
        }

        var nlabel = getCellLabel(x, y - 1)
        if ((label.equals(framelabel, ignoreCase = true) || nlabel.equals(framelabel, ignoreCase = true)) &&
            !label.equals(nlabel, ignoreCase = true)) {
            sb.setCharAt(0, '1')
        }

        nlabel = getCellLabel(x - 1, y)
        if ((label.equals(framelabel, ignoreCase = true) || nlabel.equals(framelabel, ignoreCase = true)) &&
            !label.equals(nlabel, ignoreCase = true)) {
            sb.setCharAt(1, '1')
        }

        nlabel = getCellLabel(x + 1, y)
        if ((label.equals(framelabel, ignoreCase = true) || nlabel.equals(framelabel, ignoreCase = true)) &&
            !label.equals(nlabel, ignoreCase = true)) {
            sb.setCharAt(2, '1')
        }

        nlabel = getCellLabel(x, y + 1)
        if ((label.equals(framelabel, ignoreCase = true) || nlabel.equals(framelabel, ignoreCase = true)) &&
            !label.equals(nlabel, ignoreCase = true)) {
            sb.setCharAt(3, '1')
        }

        return sb.toString()
    }

    @JavascriptInterface
    fun genMoveLink(x: Int, y: Int): String {
        return MapManager.genMoveLink(x, y)
    }

    @JavascriptInterface
    fun makeVisit(x: Int, y: Int) {
        MapManager.makeVisit(x, y)
    }

    @JavascriptInterface
    fun getHalfMapWidth(): Int {
        val profile = ProfileManager.getCurrentProfile()
        return (profile?.mapBigWidth?.minus(1))?.div(2) ?: 5
    }

    @JavascriptInterface
    fun getHalfMapHeight(): Int {
        val profile = ProfileManager.getCurrentProfile()
        return (profile?.mapBigHeight?.minus(1))?.div(2) ?: 5
    }

    @JavascriptInterface
    fun getMapScale(): Int {
        val profile = ProfileManager.getCurrentProfile()
        return profile?.mapBigScale ?: 100
    }
}
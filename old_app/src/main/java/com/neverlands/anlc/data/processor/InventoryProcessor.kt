package com.neverlands.anlc.data.processor

import com.neverlands.anlc.data.model.InvEntry
import com.neverlands.anlc.util.HelperStrings

object InventoryProcessor {

    fun processInventory(html: String): String {
        val invList = mutableListOf<InvEntry>()

        val patternStartInv = "</b></font></td></tr>"
        var pos = html.indexOf(patternStartInv)
        if (pos == -1) {
            return html
        }

        pos += patternStartInv.length
        val posStartInv = pos

        while (true) {
            val parrernStartTr = "<tr><td bgcolor=#F5F5F5>"
            if (pos + parrernStartTr.length > html.length || !html.substring(pos, pos + parrernStartTr.length).startsWith(parrernStartTr)) {
                break
            }

            val parrernEndTr = "<td bgcolor=#FCFAF3><img src=http://image.neverlands.ru/1x1.gif width=5 height=1></td></tr></table></td></tr></table></td></tr>"
            var posEnd = html.indexOf(parrernEndTr, pos)
            if (posEnd == -1) {
                val parrernEndTrShort = "<img src=http://image.neverlands.ru/1x1.gif width=1 height=5></td></tr></table></td></tr>"
                posEnd = html.indexOf(parrernEndTrShort, pos)
                if (posEnd == -1) {
                    break // Or return html, for safety
                }
                posEnd += parrernEndTrShort.length
            } else {
                posEnd += parrernEndTr.length
            }

            val htmlEntry = html.substring(pos, posEnd)
            
            val name = HelperStrings.subString(htmlEntry, "<font class=nickname><b> ", "</b>") ?: ""
            val img = HelperStrings.subString(htmlEntry, " src=http://", " ") ?: ""
            val properties = "" // TODO: Implement property parsing

            val invEntry = InvEntry(htmlEntry, name, img, properties)
            invList.add(invEntry)
            pos = posEnd
        }

        // For now, just return the original html
        return html
    }
}

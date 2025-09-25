package com.anlc.client.inventory

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.*

object HtmlParser {

    fun parseInvEntry(html: String): InvEntry {
        val doc: Document = Jsoup.parse(html)

        // Helper function to extract substring like C# HelperStrings.SubString
        fun String.substringBetween(start: String, end: String): String? {
            val startIndex = this.indexOf(start)
            if (startIndex == -1) return null
            val endIndex = this.indexOf(end, startIndex + start.length)
            if (endIndex == -1) return null
            return this.substring(startIndex + start.length, endIndex)
        }

        val wearLink = html.substringBetween("<input type=button class=invbut onclick=\"location='", "'\" value=\"Надеть\">") ?: ""

        var pssThing = ""
        var pssPrice = 0
        var pssLink = ""
        val possl = html.indexOf("Продать за", ignoreCase = true)
        if (possl != -1) {
            val pssStart = html.lastIndexOf("<input", possl, ignoreCase = true)
            if (pssStart != -1) {
                val pssEnd = html.indexOf('>', possl)
                if (pssEnd != -1) {
                    val pssSubHtml = html.substring(pssStart, pssEnd - pssStart + 1)
                    pssThing = pssSubHtml.substringBetween("продать < ", " >") ?: ""
                    val pssPriceString = pssSubHtml.substringBetween("> за ", " NV")
                    pssLink = pssSubHtml.substringBetween("location='", "' ") ?: ""
                    if (pssPriceString != null) {
                        pssPrice = pssPriceString.toIntOrNull() ?: 0
                    }
                }
            }
        }

        var dropThing = html.substringBetween("if(top.DeleteTrue('", "))") ?: ""
        var dropLink = ""
        var dropPrice = ""
        if (dropThing.isNotEmpty()) {
            val str = "if(top.DeleteTrue('$dropThing')) { location='"
            dropLink = html.substringBetween(str, "'") ?: ""
            dropPrice = html.substringBetween("Цена: <b>", " NV</b>") ?: ""
        }

        val name = html.substringBetween("<font class=nickname><b> ", "</b>") ?: ""

        var expirible = false
        var expired = false
        val sg = html.substringBetween("<font color=#cc0000>Срок годности: ", "</font>")
        if (!sg.isNullOrEmpty()) {
            expirible = true
            val sp = sg.split('.', ' ', ':')
            if (sp.size > 4) {
                val day = sp[0].toIntOrNull() ?: 0
                val month = sp[1].toIntOrNull() ?: 0
                val year = sp[2].toIntOrNull() ?: 0
                val hour = sp[3].toIntOrNull() ?: 0
                val minute = sp[4].toIntOrNull() ?: 0

                if (day != 0 && month != 0 && year != 0) {
                    val calendar = Calendar.getInstance()
                    calendar.set(year, month - 1, day, hour, minute, 0) // Month is 0-indexed
                    val exptime = calendar.time

                    // Assuming AppVars.ServerDateTime is current time for now.
                    // This will need to be properly handled later.
                    val serverDateTime = Date() // Placeholder for AppVars.ServerDateTime
                    val oneDayLater = Calendar.getInstance().apply {
                        time = exptime
                        add(Calendar.DAY_OF_MONTH, 1)
                    }.time

                    expired = serverDateTime.after(oneDayLater)
                }
            }
        }

        var properties = ""
        var dolg = ""
        var dolgOne = 0
        var dolgTwo = 0

        // This part is complex due to the nested HTML structure and conditional parsing
        // based on AppVars.Profile.DoInvPackDolg. For initial porting, we'll simplify
        // and focus on extracting the raw properties string.
        val prefix = "<font color=#000000>требования</font></div></td></tr><tr><td bgcolor=#FCFAF3><img src=http://image.neverlands.ru/1x1.gif width=5 height=1></td><td bgcolor=#FCFAF3 width=50%><font class=nickname><b> " + name + "</b><br><font class=weaponch>"
        val propHtml = html.substringBetween(prefix, "<br></td><td bgcolor=#FCFAF3><img src=http://image.neverlands.ru/1x1.gif width=5 height=1></td><td bgcolor=#FCFAF3><img src=http://image.neverlands.ru/1x1.gif width=1 height=1></td><td bgcolor=#FCFAF3><img src=http://image.neverlands.ru/1x1.gif width=5 height=1></td><td bgcolor=#FCFAF3 width=50%><font class=weaponch>")

        if (!propHtml.isNullOrEmpty()) {
            val par = propHtml.split("<br>", "</td><td bgcolor=#FCFAF3><img src=http://image.neverlands.ru/1x1.gif width=5 height=1></td><td bgcolor=#B9A05C><img src=http://image.neverlands.ru/1x1.gif width=1 height=1></td><td bgcolor=#FCFAF3><img src=http://image.neverlands.ru/1x1.gif width=5 height=1></td><td bgcolor=#FCFAF3 width=50%><font class=weaponch>")
                .filter { it.isNotBlank() }

            val sb = StringBuilder()
            for (index in 1 until par.size) {
                val part = par[index]
                if (part.contains("Цена: <b>", ignoreCase = true) || part.contains("Материал: <b>", ignoreCase = true)) {
                    continue
                }

                // Simplified dolg parsing for now. AppVars.Profile.DoInvPackDolg logic will be handled elsewhere.
                val dolgMatch = part.substringBetween("Долговечность: <b>", "</b>")
                if (dolgMatch != null) {
                    dolg = dolgMatch
                    val parD = dolg.split('/')
                    if (parD.size == 2) {
                        dolgOne = parD[0].toIntOrNull() ?: 0
                        dolgTwo = parD[1].toIntOrNull() ?: 0
                    }
                } else {
                    if (sb.isNotEmpty()) {
                        sb.append('|')
                    }
                    sb.append(part)
                }
            }
            properties = sb.toString()
        }


        val img = html.substringBetween(" src=http://", " ") ?: ""
        val levelString = html.substringBetween("<br>Уровень: <b>", "</b>") ?: ""
        val level = levelString.toIntOrNull() ?: 0

        var countButton = 0
        var pos = 0
        while (pos != -1) {
            pos = html.indexOf("<input type=button", pos, ignoreCase = true)
            if (pos != -1) {
                countButton++
                pos += "<input type=button".length
            }
        }

        return InvEntry(
            name = name,
            wearLink = wearLink,
            dropThing = dropThing,
            dropLink = dropLink,
            dropPrice = dropPrice,
            pssThing = pssThing,
            pssLink = pssLink,
            pssPrice = pssPrice,
            img = img,
            level = level,
            dolg = dolg,
            properties = properties,
            dolgOne = dolgOne,
            dolgTwo = dolgTwo,
            countButton = countButton,
            expired = expired,
            expirible = expirible,
            rawHtml = html
        )
    }

    fun parseParsedDressed(html: String): ParsedDressed {
        val doc: Document = Jsoup.parse(html)

        // Helper function to extract substring like C# HelperStrings.SubString
        fun String.substringBetween(start: String, end: String): String? {
            val startIndex = this.indexOf(start)
            if (startIndex == -1) return null
            val endIndex = this.indexOf(end, startIndex + start.length)
            if (endIndex == -1) return null
            return this.substring(startIndex + start.length, endIndex)
        }

        var valid = false
        var wid = ""
        var vcod = ""
        var empty1 = false
        var empty2 = false
        var inRightSlot = false // This property's logic is in IsWear1/2, not directly from parsing
        var hand1 = ""
        var hand2 = ""
        val slist = mutableListOf<String>()
        val dlist = mutableListOf<String>()

        val slotsinv = html.substringBetween("slots_inv(", ");")
        val slotspla = html.substringBetween("slots_pla(", ");")

        val targetSlots = slotsinv ?: slotspla

        if (!targetSlots.isNullOrEmpty()) {
            val pslots = targetSlots.split(',')
            if (pslots.size >= 6 || (slotspla != null && pslots.size >= 5)) { // Adjusted for slots_pla
                val slmain = pslots[2].split('@')
                if (slmain.size >= 13) {
                    if (slotsinv != null) {
                        val slwid = pslots[3].split('@')
                        if (slwid.size >= 3) {
                            wid = slwid[2]
                        }

                        val slvcod = pslots[4].split('@')
                        if (slvcod.size >= 3) {
                            vcod = slvcod[2]
                        }
                    }

                    val sldlg = if (slotsinv != null) pslots[5].split('@') else pslots[3].split('@') // Adjusted for slots_pla
                    if (sldlg.size >= 13 || (slotspla != null && sldlg.size >= 13)) { // Adjusted for slots_pla

                        val slhand1 = slmain[2].split(':')
                        if (slhand1.size >= 2) {
                            hand1 = slhand1[1]
                            empty1 = hand1.startsWith("Слот", ignoreCase = true)
                            var curdlg1 = ""
                            var maxdlg1 = ""
                            if (!empty1) {
                                curdlg1 = sldlg[2]
                                maxdlg1 = slhand1[2].split('|')[7]
                            }
                            if (!empty1) {
                                slist.add(hand1)
                                dlist.add("$curdlg1/$maxdlg1")
                            }
                        }

                        val slhand2 = slmain[12].split(':')
                        if (slhand2.size >= 2) {
                            hand2 = slhand2[1]
                            empty2 = hand2.startsWith("Слот", ignoreCase = true)
                            var curdlg2 = ""
                            var maxdlg2 = ""
                            if (!empty2) {
                                curdlg2 = sldlg[12]
                                maxdlg2 = slhand2[2].split('|')[7]
                            }
                            if (!empty2) {
                                slist.add(hand2)
                                dlist.add("$curdlg2/$maxdlg2")
                            }
                        }
                        valid = true
                    }
                }
            }
        }

        return ParsedDressed(
            valid = valid,
            wid = wid,
            vcod = vcod,
            empty1 = empty1,
            empty2 = empty2,
            inRightSlot = inRightSlot,
            hand1 = hand1,
            hand2 = hand2,
            slist = slist,
            dlist = dlist
        )
    }
}

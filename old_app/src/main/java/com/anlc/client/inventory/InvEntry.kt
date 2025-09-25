package com.anlc.client.inventory

import java.util.*

data class InvEntry(
    val name: String,
    val wearLink: String,
    val dropThing: String,
    val dropLink: String,
    val dropPrice: String,
    val pssThing: String,
    val pssLink: String,
    val pssPrice: Int,
    val img: String,
    val level: Int,
    val dolg: String,
    val properties: String,
    val dolgOne: Int,
    val dolgTwo: Int,
    val countButton: Int,
    val expired: Boolean,
    val expirible: Boolean,
    val rawHtml: String,
    var count: Int = 1 // Mutable for stacking
) : Comparable<InvEntry> {

    fun inc() {
        count++
    }

    fun isExpired(): Boolean {
        return expirible && expired
    }

    fun toHtml(): String {
        var workHtml = rawHtml // Start with the original raw HTML

        // 1. Expired Item Styling
        if (isExpired()) {
            workHtml = workHtml.replace("bgcolor=#F5F5F5", "bgcolor=#F5E5E5")
            workHtml = workHtml.replace("bgcolor=#FFFFFF", "bgcolor=#F5E5E5")
            workHtml = workHtml.replace("bgcolor=#FCFAF3", "bgcolor=#F5E5E5")
            workHtml = workHtml.replace("bgcolor=#D8CDAF", "bgcolor=#F5D5D5")

            // Insert "ПРОСРОЧЕНО!" text
            val ps = workHtml.indexOf("<font class=nickname><b> ")
            if (ps != -1) {
                workHtml = workHtml.substring(0, ps) +
                        "<font class=nickname><font color=#cc0000><b>ПРОСРОЧЕНО!</b></font></font> " +
                        workHtml.substring(ps)
            }
        }

        // 2. Stacking Count Display
        if (count > 1) {
            val pos = workHtml.indexOf("<font class=nickname><b> ", ignoreCase = true)
            if (pos != -1) {
                val posEnd = workHtml.indexOf("</b>", pos, ignoreCase = true)
                if (posEnd != -1) {
                    val countString = " (${count} шт.)"
                    workHtml = workHtml.substring(0, posEnd) + countString + workHtml.substring(posEnd)
                }
            }
        }

        return workHtml
    }

    // This method will not directly modify HTML in Android, but will provide data for UI rendering
    fun addBulkSellForUi(): InvEntry {
        // In Android, this logic will determine if a "bulk sell" option should be available
        // and provide the necessary data for the UI to construct the action.
        return this.copy()
    }

    // This method will not directly modify HTML in Android, but will provide data for UI rendering
    fun addBulkDeleteForUi(): InvEntry {
        // In Android, this logic will determine if a "bulk delete" option should be available
        // and provide the necessary data for the UI to construct the action.
        return this.copy()
    }

    override fun compareTo(other: InvEntry): Int {
        var result = name.compareTo(other.name)
        if (result != 0) return result

        result = img.compareTo(other.img)
        if (result != 0) return result

        if ((!expirible && other.expirible) || (expirible && !other.expirible)) {
            return expirible.compareTo(other.expirible)
        }

        if (expirible && other.expirible) {
            if ((!expired && other.expired) || (expired && !other.expired)) {
                return expired.compareTo(other.expired)
            }
        }

        result = level.compareTo(other.level)
        if (result != 0) return result

        result = countButton.compareTo(other.countButton)
        if (result != 0) return result

        result = properties.compareTo(other.properties)
        if (result != 0) return result

        return 0
    }

    // Helper for durability comparison, mirroring C# CompareDolg
    fun compareDolg(other: InvEntry): Int {
        val isFull = dolgOne == dolgTwo
        val isFullOther = other.dolgOne == other.dolgTwo
        var result = isFull.compareTo(isFullOther)
        if (result != 0) return result

        result = dolgOne.compareTo(other.dolgOne)
        if (result != 0) return result

        result = dolgTwo.compareTo(other.dolgTwo)
        return result
    }
}

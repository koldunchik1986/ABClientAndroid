package com.anlc.client.trade

import com.anlc.client.profile.ProfileSettings // Assuming ProfileSettings will contain TorgTabl
import java.lang.StringBuilder
import java.util.Locale

class TorgListManager {

    private var table: Array<TorgPair>? = null

    var trigger: Boolean = false
    var triggerBuy: Boolean = false
    var messageThanks: String = ""
    var messageNoMoney: String = ""
    var uidThing: String = ""

    fun parse(torgString: String): Boolean {
        if (torgString.isEmpty()) {
            return false
        }

        val newTorgList = mutableListOf<TorgPair>()

        // Replace Environment.NewLine, spaces, (0) with (*0), (- with (*
        val work = torgString.replace("\r\n", "")
            .replace("\n", "")
            .replace(" ", "")
            .replace("(0)", "(*0)")
            .replace("(-", "(*")

        val sp = work.split('-', '(', ')', '*', ',')
        var i = 0
        while (i < sp.size) {
            val lowValue = sp[i].toIntOrNull() ?: return false
            i++
            if (i >= sp.size) return false

            val highValue = sp[i].toIntOrNull() ?: return false
            i++

            if (lowValue > highValue) return false

            if (i >= sp.size) return false
            if (sp[i].isNotEmpty()) return false // Should be empty string between highValue and price
            i++

            if (i >= sp.size) return false
            val price = sp[i].toIntOrNull() ?: return false
            i++

            if (price < 0) return false

            val torgPair = TorgPair(lowValue, highValue, -price) // Bonus is negative in C#
            newTorgList.add(torgPair)

            if (i >= sp.size) {
                break
            }

            i++ // Skip the next separator
        }

        if (newTorgList.isEmpty()) {
            return false
        }

        table = newTorgList.toTypedArray()
        return true
    }

    fun calculate(price: Int): Int {
        val currentTable = table ?: return 0 // Return 0 if table is not parsed

        for (torgPair in currentTable) {
            if (price >= torgPair.priceLow && price <= torgPair.priceHi) {
                return price + torgPair.bonus
            }
        }

        var bonus = 0
        var diffMin = Int.MAX_VALUE

        for (torgPair in currentTable) {
            if (price < torgPair.priceLow) {
                continue
            }

            val diff = price - torgPair.priceLow
            if (diff >= diffMin) {
                continue
            }

            diffMin = diff
            bonus = price + torgPair.bonus
        }

        return bonus
    }

    fun doFilter(
        message: String,
        thing: String,
        thingLevel: String,
        price: Int,
        tablePrice: Int,
        thingRealDolg: Int,
        thingFullDolg: Int,
        price90: Int,
        profileSettings: ProfileSettings // Pass ProfileSettings for TorgTabl
    ): String {
        var result = message
        result = result.replace("{таблица}", profileSettings.torgTabl)
        result = result.replace("{вещь}", thing)
        result = result.replace("{вещьур}", thingLevel)
        result = result.replace("{вещьдолг}", "${thingRealDolg}/${thingFullDolg}")
        result = result.replace("{цена}", price.toString())
        result = result.replace("{минцена}", tablePrice.toString())
        result = result.replace("{цена90}", price90.toString())
        return result
    }
}

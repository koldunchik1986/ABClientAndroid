package com.neverlands.anlc.data.local

import android.content.Context
import android.util.Xml
import com.neverlands.anlc.data.model.map.AbcCell
import com.neverlands.anlc.data.model.map.Cell
import com.neverlands.anlc.data.model.map.MapBot
import com.neverlands.anlc.data.model.map.Position
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Color

object MapManager {

    private lateinit var context: Context

    internal val locations = mutableMapOf<String, Position>()
    private val invLocations = mutableMapOf<String, String>() // Equivalent to InvLocation in C#
    internal val cells = mutableMapOf<String, Cell>()
    private val abcCells = mutableMapOf<String, AbcCell>()

    fun init(context: Context) {
        this.context = context
        addRegions()
        loadMap(context.assets.open("map.xml"))
        loadAbcMap(context.assets.open("abcells.xml"))
    }

    private fun addRegions() {
        addRegion("1", 952, 954)
        addRegion("2", 982, 954)
        addRegion("3", 1012, 954)
        addRegion("13", 1042, 954)
        addRegion("4", 952, 973)
        addRegion("5", 982, 973)
        addRegion("6", 1012, 973)
        addRegion("14", 1042, 973)
        addRegion("7", 952, 992)
        addRegion("8", 982, 992)
        addRegion("9", 1012, 992)
        addRegion("15", 1042, 992)
        addRegion("10", 952, 1011)
        addRegion("11", 982, 1011)
        addRegion("12", 1012, 1011)
        addRegion("16", 1042, 1011)
        addRegion("17", 922, 954)
        addRegion("18", 922, 973)
        addRegion("19", 922, 992)
        addRegion("20", 922, 1011)
        addRegion("21", 922, 1030)
        addRegion("22", 922, 1049)
        addRegion("23", 952, 1030)
        addRegion("24", 952, 1049)
        addRegion("25", 982, 1030)
        addRegion("26", 982, 1049)
        addRegion("27", 1012, 1030)
        addRegion("28", 1012, 1049)
        addRegion("29", 1042, 1030)
        addRegion("30", 1042, 1049)
        addRegion("31", 1072, 954)
        addRegion("32", 1072, 973)
        addRegion("33", 1072, 992)
        addRegion("34", 1072, 1011)
        addRegion("35", 1072, 1030)
        addRegion("36", 1072, 1049)
    }

    private fun addRegion(region: String, xmin: Int, ymin: Int) {
        var number = 1
        val xmax = xmin + 29
        val ymax = ymin + 18
        for (y in ymin..ymax) {
            for (x in xmin..xmax) {
                val h = makePosition(x, y)
                val l = makeRegNum(region, number)
                val p = Position(x, y, l)
                locations[h] = p
                invLocations[l] = h
                number++
            }
        }
    }

    private fun makeRegNum(reg: String, k: Int): String {
        return String.format(Locale.US, "%s-%03d", reg, k)
    }

    internal fun makePosition(x: Int, y: Int): String {
        return String.format(Locale.US, "%d/%d_%d", y, x, y)
    }

    private fun loadMap(inputStream: InputStream) {
        inputStream.use {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)

            var eventType = parser.eventType
            var currentCell: Cell? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName) {
                            "cell" -> {
                                val cellNumber = parser.getAttributeValue(null, "cellNumber") ?: ""
                                currentCell = Cell(
                                    cellNumber = cellNumber,
                                    hasFish = parser.getAttributeValue(null, "hasFish")?.toBoolean() ?: false,
                                    hasWater = parser.getAttributeValue(null, "hasWater")?.toBoolean() ?: false,
                                    herbGroup = parser.getAttributeValue(null, "herbGroup") ?: "",
                                    name = parser.getAttributeValue(null, "name") ?: "",
                                    tooltip = parser.getAttributeValue(null, "tooltip") ?: "",
                                    updated = parser.getAttributeValue(null, "updated") ?: "",
                                    nameUpdated = parser.getAttributeValue(null, "nameUpdated") ?: "",
                                    costUpdated = parser.getAttributeValue(null, "costUpdated") ?: "",
                                    cost = parser.getAttributeValue(null, "cost")?.toIntOrNull() ?: 0
                                )
                                cells[cellNumber] = currentCell
                            }
                            "bots" -> {
                                currentCell?.let {
                                    val mapBot = MapBot(
                                        name = parser.getAttributeValue(null, "name") ?: "",
                                        minLevel = parser.getAttributeValue(null, "minLevel")?.toIntOrNull() ?: 0,
                                        maxLevel = parser.getAttributeValue(null, "maxLevel")?.toIntOrNull() ?: 0,
                                        c = parser.getAttributeValue(null, "c") ?: "",
                                        d = parser.getAttributeValue(null, "d") ?: ""
                                    )
                                    it.mapBots.add(mapBot)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        // No specific action needed for end tags for now
                    }
                }
                eventType = parser.next()
            }
        }
    }

    private fun loadAbcMap(inputStream: InputStream) {
        inputStream.use {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)

            var eventType = parser.eventType
            val dateFormat = SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US) // Example: 11/28/2013 8:20:01 PM

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName) {
                            "cell" -> {
                                val regNum = parser.getAttributeValue(null, "regnum") ?: ""
                                val label = parser.getAttributeValue(null, "label") ?: ""
                                val cost = parser.getAttributeValue(null, "cost")?.toIntOrNull() ?: 0
                                val visitedStr = parser.getAttributeValue(null, "visited")
                                val verifiedStr = parser.getAttributeValue(null, "verified")

                                val visited = if (visitedStr != null && visitedStr != "1/1/0001 12:00:00 AM") {
                                    try { dateFormat.parse(visitedStr) } catch (e: Exception) { null }
                                } else { null }

                                val verified = if (verifiedStr != null && verifiedStr != "1/1/0001 12:00:00 AM") {
                                    try { dateFormat.parse(verifiedStr) } catch (e: Exception) { null }
                                } else { null }

                                val abcCell = AbcCell(regNum, label, cost, visited, verified)
                                abcCells[regNum] = abcCell
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        // No specific action needed for end tags for now
                    }
                }
                eventType = parser.next()
            }
        }
    }

    fun isCellExists(x: Int, y: Int): Boolean {
        val coor = makePosition(x, y)
        return locations.containsKey(coor)
    }

    fun makeVisit(x: Int, y: Int) {
        val coor = makePosition(x, y)
        val regNum = locations[coor]?.regNum
        if (regNum != null) {
            abcCells[regNum]?.visited = Date()
            saveAbcMap()
        }
    }

    internal fun saveAbcMap() {
        try {
            val outputStream = context.openFileOutput("abcells.xml", Context.MODE_PRIVATE)
            val writer = outputStream.bufferedWriter()
            val serializer = Xml.newSerializer()
            serializer.setOutput(writer)
            serializer.startDocument("UTF-8", true)
            serializer.startTag("", "cells")

            val dateFormat = SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US)

            abcCells.values.forEach { abcCell ->
                serializer.startTag("", "cell")
                serializer.attribute("", "regnum", abcCell.regNum)
                serializer.attribute("", "label", abcCell.label)
                serializer.attribute("", "cost", abcCell.cost.toString())
                abcCell.visited?.let { serializer.attribute("", "visited", dateFormat.format(it)) }
                abcCell.verified?.let { serializer.attribute("", "verified", dateFormat.format(it)) }
                serializer.endTag("", "cell")
            }

            serializer.endTag("", "cells")
            serializer.endDocument()
            writer.flush()
            writer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun genMoveLink(x: Int, y: Int): String {
        val coor = makePosition(x, y)
        val regNum = locations[coor]?.regNum
        return if (regNum != null) {
            "http://www.neverlands.ru/main.php?get_id=56&act=10&go=move&vcode=0&r=" + regNum
        } else {
            ""
        }
    }

    fun getCellAltText(x: Int, y: Int, scale: Int): String {
        val coor = makePosition(x, y)
        val regNum = locations[coor]?.regNum
        if (regNum != null) {
            val cell = cells[regNum]
            if (cell != null) {
                val sb = StringBuilder()
                sb.append(cell.tooltip)
                if (cell.botsTooltip.isNotEmpty()) {
                    sb.append("\n")
                    sb.append(cell.botsTooltip)
                }
                // TODO: Add HerbCell logic later
                if (cell.cost > 0) {
                    sb.append("\nВремя: ").append(cell.cost)
                }
                return sb.toString()
            }
        }
        return ""
    }

    fun isCellInPath(x: Int, y: Int): Boolean {
        // TODO: Full implementation requires porting the complex C# MapPath class and its pathfinding algorithm,
        // which involves Map.InvLocation, Map.MakePosition, Map.Location, Map.Cells,
        // and Map.Teleports. This is a significant task.
        return false
    }

    fun getCellDivText(x: Int, y: Int, scale: Int, link: String, showmove: Boolean, isframe: Boolean): String {
        val coor = makePosition(x, y)
        val regNum = locations[coor]?.regNum
        if (regNum != null) {
            val cell = cells[regNum]
            if (cell != null) {
                val sb = StringBuilder()
                sb.append("<div style=\"position:relative; left:2px; top:2px; width:90px; height:90px; padding:2px; text-shadow:1px 1px 1px, -1px -1px 1px, -1px 1px 1px, 1px -1px 1px; font-family:Tahoma; font-size:9px; font-weight:bold; text-decoration:none;\">")
                sb.append("<span style=\"font-size:11px; color:#FFFFFF\">${cell.cellNumber}</span>")
                sb.append("<br><span style=\"color:#C0C0C0\">${cell.tooltip}</span>")

                // TODO: Add more complex logic for fish, water, bots, herbs, visited status
                // This involves porting HexColorCost, HexColorVisited, AppVars.AutoMoving,
                // AppVars.AutoMovingMapPath, AppVars.Profile.ServDiff, HerbCell, AbcCell logic.

                sb.append("</div>")
                return sb.toString()
            }
        }
        return ""
    }

    fun getMapText(): String {
        // TODO: Implement actual logic for MapText, including CheckTied() and AppVars.AutoMoving related logic.
        // This is a complex method involving network requests, parsing, and updating global application state.
        // For now, we'll return a simplified string.
        if (AppVars.autoMoving && AppVars.autoMovingJumps > 0) {
            return "Пункт назначения: ${AppVars.autoMovingDestination}<br>Еще переходов: ${AppVars.autoMovingJumps}" +
                    if (AppVars.doSearchBox) "<br>Ищем клад..." else ""
        }
        return "Перемещаемся на соседнюю клетку..."
    }

    // Helper functions for color calculations (ported from C# Map.cs)
    private fun interpolateComponent(a: Int, b: Int, p: Double): Int {
        return (a * (1 - p) + b * p).toInt()
    }

    private fun colorInterpolate(a: Int, b: Int, p: Double): Int { // Corrected to take Ints
        val r = interpolateComponent(Color.red(a), Color.red(b), p)
        val g = interpolateComponent(Color.green(a), Color.green(b), p)
        val bl = interpolateComponent(Color.blue(a), Color.blue(b), p)
        return Color.rgb(r, g, bl)
    }

    private fun colorCost(cost: Int): Int { // Corrected to return Int
        return when (cost) {
            0 -> Color.parseColor("#808080") // DarkGray
            in 1..30 -> Color.parseColor("#90EE90") // LightGreen
            in 31..40 -> Color.parseColor("#FFFF00") // Yellow
            else -> Color.parseColor("#FF0000") // Red (for cost >= 60)
        }
    }

    private fun colorVisited(hours: Double): Int { // Corrected to return Int
        return when {
            hours < 0.0 -> Color.parseColor("#90EE90") // LightGreen
            hours < 1.0 -> colorInterpolate(Color.parseColor("#90EE90"), Color.parseColor("#FFFF00"), hours / 1.0)
            hours < 6.0 -> colorInterpolate(Color.parseColor("#FFFF00"), Color.parseColor("#FF0000"), (hours - 1.0) / 5.0)
            else -> Color.parseColor("#FF0000") // Red
        }
    }

    private fun hexColorCost(cost: Int): String {
        val color = colorCost(cost)
        return String.format("#%06X", (0xFFFFFF and color))
    }

    private fun hexColorVisited(hours: Double): String {
        val color = colorVisited(hours)
        return String.format("#%06X", (0xFFFFFF and color))
    }
}
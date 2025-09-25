package com.neverlands.anlc.data.model.map

data class Cell(
    var cellNumber: String = "",
    var hasFish: Boolean = false,
    var hasWater: Boolean = false,
    var herbGroup: String = "",
    var name: String = "",
    var updated: String = "",
    var nameUpdated: String = "",
    var costUpdated: String = "",
    var tooltip: String = "",
    var cost: Int = 0,
    var botsTooltip: String = "",
    var minBotLevel: Int = 0,
    var maxBotLevel: Int = 0,
    val mapBots: MutableList<MapBot> = mutableListOf()
)

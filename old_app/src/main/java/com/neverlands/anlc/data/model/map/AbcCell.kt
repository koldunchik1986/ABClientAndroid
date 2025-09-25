package com.neverlands.anlc.data.model.map

import java.util.Date

data class AbcCell(
    var regNum: String = "",
    var label: String = "",
    var cost: Int = 0,
    var visited: Date? = null,
    var verified: Date? = null
)

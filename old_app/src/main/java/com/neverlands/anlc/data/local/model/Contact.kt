package com.neverlands.anlc.data.local.model

import java.util.Date

data class Contact(
    val name: String,
    var classId: Int,
    var toolId: Int,
    var comments: String,
    var tracing: Boolean,
    var sign: String = "",
    var align: String = "",
    var clan: String = "",
    var level: String = "",
    var location: String = "",
    var treeNode: String = "",
    var parent: String = "",
    var isMolch: Boolean = false,
    var isOnline: Boolean = false,
    var lastUpdated: Date? = null,
    var nextCheck: Date? = null
)
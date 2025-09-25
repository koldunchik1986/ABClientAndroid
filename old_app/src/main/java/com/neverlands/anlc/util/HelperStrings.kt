package com.neverlands.anlc.util

object HelperStrings {

    fun subString(source: String, start: String, end: String): String? {
        val startIndex = source.indexOf(start)
        if (startIndex == -1) {
            return null
        }
        val endIndex = source.indexOf(end, startIndex + start.length)
        if (endIndex == -1) {
            return null
        }
        return source.substring(startIndex + start.length, endIndex)
    }
}
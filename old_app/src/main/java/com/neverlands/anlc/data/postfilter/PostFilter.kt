package com.neverlands.anlc.data.postfilter

object PostFilter {

    fun fixEncoding(html: String): String {
        // This is a very brittle approach, but it's the only way to fix the encoding issues.
        // The garbled string for "Надеть" is ""
        // The garbled string for "Удалить" is ""
        var result = html.replace("value=\"\"", "value=\"Надеть\"")
        result = result.replace("value=\"\"", "value=\"Удалить\"")
        return result
    }
}
package com.neverlands.anlc.data.remote

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    private var currentLogFile: File? = null // New property

    fun setLogFile(context: Context, filename: String) {
        currentLogFile = File(context.filesDir, filename)
        // Optionally, clear the file if it exists from a previous run
        currentLogFile?.writeText("")
    }

    fun log(context: Context, text: String) {
        try {
            val file = currentLogFile ?: File(context.filesDir, "auth_log.txt") // Use currentLogFile if set
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            file.appendText("[$timestamp] $text\n\n")
        } catch (e: Exception) {
            // Ignore exceptions during logging
        }
    }
}
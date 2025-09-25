package com.neverlands.anlc.forms

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.neverlands.anlc.R
import java.io.File

class LogViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        val logTextView = findViewById<TextView>(R.id.textview_log)
        val shareButton = findViewById<Button>(R.id.button_share_log)

        val logFileName = intent.getStringExtra("log_file_name") ?: "auth_log.txt"
        val logFile = File(filesDir, logFileName)

        if (logFile.exists()) {
            logTextView.text = logFile.readText()
        } else {
            logTextView.text = "Лог-файл не найден."
        }

        shareButton.setOnClickListener {
            shareLogFile(logFile)
        }
    }

    private fun shareLogFile(logFile: File) {
        if (!logFile.exists()) {
            // Handle case where log file doesn't exist
            return
        }

        val logUri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            logFile
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, logUri)
            type = "text/plain"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Отправить лог через:"))
    }
}

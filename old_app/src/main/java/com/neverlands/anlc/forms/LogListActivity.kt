package com.neverlands.anlc.forms

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.neverlands.anlc.R
import java.io.File

class LogListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_list)

        val logListView = findViewById<ListView>(R.id.log_list_view)

        val logDir = filesDir
        val logFiles = logDir.listFiles { file -> file.name.endsWith(".txt") }
            ?.map { it.name } ?: emptyList()

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, logFiles)
        logListView.adapter = adapter

        logListView.setOnItemClickListener { _, _, position, _ ->
            val selectedLog = logFiles[position]
            val intent = Intent(this, LogViewerActivity::class.java)
            intent.putExtra("log_file_name", selectedLog)
            startActivity(intent)
        }
    }
}

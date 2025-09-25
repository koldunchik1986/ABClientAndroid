package com.neverlands.anlc.data.local

import android.content.Context
import android.util.Log
import com.neverlands.anlc.data.local.model.Profile
import com.neverlands.anlc.data.local.AppVars
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ChatManager {

    private const val CONST_LOG_CHAT_FORMAT = "chat%02d%02d%02d.html"
    private const val CONST_ERROR_LOG_SAVE_TITLE = "Ошибка сохранения чата"

    private val answersCollection = mutableListOf<String>()
    private val chatBody = StringBuilder()
    private lateinit var logPath: File
    private lateinit var context: Context
    private lateinit var currentProfile: Profile

    // Using Windows-1251 encoding as identified from C# AppConsts.cs
    private val RUSSIAN_CODE_PAGE_ENCODING = Charset.forName("windows-1251")

    var lastChanged: Date = Date()
        private set

    var critical: Boolean = false

    

    fun addAnswer(message: String) {
        if (message.isBlank()) {
            return
        }
        synchronized(answersCollection) {
            answersCollection.add(message)
        }
    }

    fun getAnswer(): String {
        if (critical || (Date().time - lastChanged.time < 3000)) { // 3000 ms = 3 seconds
            return ""
        }

        synchronized(answersCollection) {
            if (answersCollection.isEmpty()) {
                return ""
            }
            val message = answersCollection.removeAt(0)
            lastChanged = Date()
            return message
        }
    }

    fun init(context: Context, profile: Profile) {
        this.context = context
        this.currentProfile = profile
        // Android's equivalent of Application.StartupPath + Profile.FileName + AppConsts.LogsDir
        logPath = File(context.filesDir, "${profile.userNick}/logs")
    }

    fun addStringToChat(message: String) {
        if (message.isBlank()) {
            return
        }

        if (chatBody.isNotEmpty()) {
            chatBody.append("<br>") // AppConsts.HtmlBr
        }
        chatBody.append(message)

        if (currentProfile.ChatKeepLog) { // Assuming AppVars.profile is accessible and has ChatKeepLog
            var writer: OutputStreamWriter? = null
            try {
                if (!logPath.exists()) {
                    logPath.mkdirs() // Create directories if they don't exist
                }

                val logFile = File(logPath, getLogName())
                val append = logFile.exists() // Append if file exists, otherwise create new

                val fos = FileOutputStream(logFile, append)
                writer = OutputStreamWriter(fos, RUSSIAN_CODE_PAGE_ENCODING)

                if (!append) {
                    // Write HTML header for new log file
                    writer.write(
                        "<HTML>" +
                        "<META Content=\"text/html; Charset=windows-1251\" Http-Equiv=Content-type>" +
                        "<HEAD>" +
                        "<LINK href=\"http://www.neverlands.ru/ch/chat.css\" rel=STYLESHEET type=text/css>" +
                        "</HEAD>" +
                        "<BODY LeftMargin=2 TopMargin=2 RightMargin=2 MarginHeight=2 MarginWidth=2 BgColor=#F5F5F5>"
                    )
                }

                if (append) {
                    writer.write("<br>") // AppConsts.HtmlBr
                }
                writer.write(message)

            } catch (e: Exception) {
                Log.e("ChatManager", "${CONST_ERROR_LOG_SAVE_TITLE}: ${e.message}", e)
                // In C#, it used MessageBox.Show. Here, we just log the error.
            } finally {
                writer?.flush()
                writer?.close()
            }
        }
    }

    private fun getLogName(): String {
        val dateFormat = SimpleDateFormat("yyMMdd", Locale.US) // Format for YYMMDD
        val date = Date()
        val year = dateFormat.format(date).substring(0, 2).toInt() // Get last two digits of year
        val month = dateFormat.format(date).substring(2, 4).toInt()
        val day = dateFormat.format(date).substring(4, 6).toInt()

        return String.format(Locale.US, CONST_LOG_CHAT_FORMAT, year, month, day)
    }

    // Method to retrieve the current chat body for UI display
    fun getChatBody(): String {
        return chatBody.toString()
    }

    // Method to clear the chat body (e.g., on new game session)
    fun clearChatBody() {
        chatBody.clear()
    }
}

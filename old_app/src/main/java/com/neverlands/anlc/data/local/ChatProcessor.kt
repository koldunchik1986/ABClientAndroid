package com.neverlands.anlc.data.local

import android.util.Log
import com.neverlands.anlc.ui.main.MainViewModel
import com.neverlands.anlc.data.local.AppVars
import com.neverlands.anlc.data.local.ProfileManager
import java.util.Locale
import java.util.regex.Pattern

class ChatProcessor(private val viewModel: MainViewModel) {

    // Helper function to replicate C# HelperStrings.SubString
    private fun subString(source: String, startString: String, endString: String): String {
        val startIndex = source.indexOf(startString)
        if (startIndex == -1) {
            return ""
        }
        val endIndex = source.indexOf(endString, startIndex + startString.length)
        if (endIndex == -1) {
            return ""
        }
        return source.substring(startIndex + startString.length, endIndex)
    }

    fun chatFilter(message: String): String {
        var processedMessage = message

        // Porting XP parsing logic
        val xpstr = subString(processedMessage, "Получено <font color=#CC0000>боевого</font> опыта: <b><font color=#CC0000>", "</font></b>.")
        if (xpstr.isNotBlank()) {
            val xp = xpstr.toLongOrNull()
            if (xp != null) {
                // In C#, it called AppVars.MainForm.UpdateXPInc. In Android, we'll use ViewModel.
                // viewModel.updateXPInc(xp) // Assuming such a method exists in MainViewModel
                Log.d("ChatProcessor", "XP gained: $xp")
            }
        }

        // Porting item drop parsing logic
        val thingstr = subString(processedMessage, "Результат обыска бота: <B>", "</B>.")
        if (thingstr.isNotBlank()) {
            val timestr = subString(processedMessage, "<font class=chattime>&nbsp;", "&nbsp;</font> <font color=000000><B><font color=#CC0000>Внимание!</font> Системная информация.</B> Результат обыска бота: ")
            if (timestr.isNotBlank()) {
                val thinglist = mutableListOf<String>()
                val pattern = Pattern.compile("«([^»]+)»")
                val matcher = pattern.matcher(thingstr)
                while (matcher.find()) {
                    thinglist.add(matcher.group(1))
                }

                if (thinglist.isNotEmpty()) {
                    // In C#, it called AppVars.MainForm.UpdateThingInc. In Android, use ViewModel.
                    // viewModel.updateThingInc(timestr, thinglist) // Assuming such a method exists
                    Log.d("ChatProcessor", "Items dropped at $timestr: $thinglist")
                }
            }
        }

        // Porting combat log processing
        if (processedMessage.contains("<font color=#000000><b>Системная информация.</b></font> Поединок завершён.", ignoreCase = true)) {
            // This is a complex section involving AppVars.LastBoiLog, AppVars.LastBoiSostav, etc.
            // For now, just log that combat ended.
            Log.d("ChatProcessor", "Combat ended detected.")
            // TODO: Implement full combat log parsing and modification
        }

        // Porting auto-answer and sound logic
        val posSpanEnd = processedMessage.indexOf(">" + (ProfileManager.getCurrentProfile()?.userNick ?: "") + "</SPAN>", ignoreCase = true)
        if (posSpanEnd != -1) {
            val strSpanStart = "<SPAN title=\""
            val strSpanEnd = ">"
            val fromNick = subString(processedMessage, strSpanStart, strSpanEnd).trimStart('%')
            var answer = ""
            if (fromNick.isNotBlank() && !fromNick.equals(ProfileManager.getCurrentProfile()?.userNick, ignoreCase = true)) {
                // EventSounds.PlaySndMsg() // Needs porting
                Log.d("ChatProcessor", "Message from other user: $fromNick")

                val istoclan = processedMessage.contains(" > clan: ", ignoreCase = true)
                val istopair = processedMessage.contains(" > pair: ", ignoreCase = true)

                if (ProfileManager.getCurrentProfile()?.doAutoAnswer ?: false) {
                    // AutoAnswerMachine.GetNextAnswer() // Needs porting
                    answer = "%" + fromNick + " " + "[Auto-reply placeholder]"
                    if (istoclan) {
                        ChatManager.addAnswer("%clan%$answer")
                    } else if (istopair) {
                        ChatManager.addAnswer("%pair%$answer")
                    } else {
                        ChatManager.addAnswer(answer)
                    }
                    Log.d("ChatProcessor", "Auto-reply added: $answer")
                }
            }
        }

        // Porting chat levels and signs logic
        if (ProfileManager.getCurrentProfile()?.doChatLevels ?: false) {
            // This is complex and involves ChatUsersManager. Needs porting.
            Log.d("ChatProcessor", "DoChatLevels is true, but logic not fully ported.")
            // TODO: Implement chat levels and signs logic
        }

        // Porting other message replacements (pair/clan tags, log links)
        if (processedMessage.contains("pair:", ignoreCase = true)) {
            processedMessage = processedMessage.replace("<SPAN title=\"%%", "<SPAN title=\"%%%")
        } else if (processedMessage.contains("clan:", ignoreCase = true)) {
            processedMessage = processedMessage.replace("<SPAN title=\"%%", "<SPAN title=\"%%%")
        }

        // Log links (e.g., [[[log_id]]])
        var pos1: Int
        var pos2: Int
        do {
            pos1 = processedMessage.indexOf("[[[")
            if (pos1 == -1) break

            pos2 = processedMessage.indexOf("]]]", pos1)
            if (pos2 == -1) break

            val sorig = processedMessage.substring(pos1 + 3, pos2)
            var msg = ""
            if (!sorig.contains(":")) {
                msg = "<a href=\"http://www.neverlands.ru/logs.fcg?fid=$sorig\" onclick=\"window.open(this.href);\">лог</a> боя"
            }
            processedMessage = processedMessage.substring(0, pos1) + msg + processedMessage.substring(pos2 + 3)
        } while (true)

        ChatManager.addStringToChat(processedMessage)
        return processedMessage
    }
}
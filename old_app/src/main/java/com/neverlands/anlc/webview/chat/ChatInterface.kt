package com.neverlands.anlc.webview.chat

import android.webkit.JavascriptInterface
import android.util.Log
import com.neverlands.anlc.data.local.ProfileManager
import com.neverlands.anlc.data.local.AppVars
import java.util.Date

class ChatInterface {

    @JavascriptInterface
    fun changeChatSize(size: Int) {
        Log.d("ChatInterface", "changeChatSize() called with size: $size")
        ProfileManager.getCurrentProfile()?.let { profile ->
            profile.chatHeight = size
            ProfileManager.saveCurrentProfile()
        }
    }

    @JavascriptInterface
    fun changeChatSpeed(delay: Int) {
        Log.d("ChatInterface", "changeChatSpeed() called with delay: $delay")
        ProfileManager.getCurrentProfile()?.let { profile ->
            profile.chatDelay = delay
            ProfileManager.saveCurrentProfile()
        }
    }

    @JavascriptInterface
    fun changeChatMode(mode: Int) {
        Log.d("ChatInterface", "changeChatMode() called with mode: $mode")
        ProfileManager.getCurrentProfile()?.let { profile ->
            profile.chatMode = mode
            ProfileManager.saveCurrentProfile()
        }
    }

    @JavascriptInterface
    fun chatUpdated() {
        Log.d("ChatInterface", "chatUpdated() called")
        AppVars.lastChatChanged = Date()
        AppVars.chatCritical = false
    }

    @JavascriptInterface
    fun chatFilter(message: String): String {
        Log.d("ChatInterface", "chatFilter() called with message: $message")
        // TODO: Implement actual chat filtering logic.
        // This is a complex method involving parsing chat messages for XP, item drops,
        // fight logs, auto-answering, chat user levels/signs, and string replacements.
        // It requires porting several C# helper classes and logic.
        // For now, we'll just return the original message.
        return message
    }
}
package ru.neverlands.abclient.utils;

import android.util.Log;

// Placeholder for chat functionality
public class Chat {
    private static final String TAG = "Chat";

    // This would eventually add a message to the main chat UI
    public static void addMessageToChat(String message) {
        Log.i(TAG, "addMessageToChat: " + message);
        // In a real implementation, this would likely call a method on a ViewModel
        // or send a broadcast to the UI thread.
    }

    // This would eventually send a message to the game server
    public static void addAnswer(String message) {
        Log.i(TAG, "addAnswer: " + message);
        // In a real implementation, this would queue a message to be sent.
    }
}

package ru.neverlands.abclient.utils;

import android.util.Log;

import ru.neverlands.abclient.MainActivity;

// Placeholder for chat functionality
public class Chat {
    private static final String TAG = "Chat";

    // This would eventually add a message to the main chat UI
    public static void addMessageToChat(String message) {
        Log.i(TAG, "addMessageToChat: " + message);
        try {
            MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
            if (activity == null) return;
            String safe = message == null ? "" : message;
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String json = gson.toJson(safe);
            activity.runOnUiThread(() -> {
                if (activity.binding != null && activity.binding.appBarMain != null
                        && activity.binding.appBarMain.contentMain != null
                        && activity.binding.appBarMain.contentMain.chatMsgWebview != null) {
                    activity.binding.appBarMain.contentMain.chatMsgWebview
                            .evaluateJavascript("if (typeof add_msg === 'function') { add_msg(" + json + "); }", null);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "addMessageToChat failed", e);
        }
    }

    // This would eventually send a message to the game server
    public static void addAnswer(String message) {
        Log.i(TAG, "addAnswer: " + message);
        // In a real implementation, this would queue a message to be sent.
    }
}

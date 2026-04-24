package ru.neverlands.abclient.postfilter;

import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Chat;

final class MainPhpChatBridge {

    private MainPhpChatBridge() {
    }

    static String buildServerChatTimeHtml() {
        return FightAuto.buildServerChatTimeHtml();
    }

    static void sendInventoryChatMessage(String messageHtml) {
        if (AppVars.getContext() == null || messageHtml == null || messageHtml.isEmpty()) {
            return;
        }
        if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            Chat.addMessageToChat(messageHtml);
            return;
        }
        Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        intent.putExtra("message", messageHtml);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
    }
}

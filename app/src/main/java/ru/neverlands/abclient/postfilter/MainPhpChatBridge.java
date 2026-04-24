package ru.neverlands.abclient.postfilter;

import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Chat;

/**
 * Мост локальных chat-сообщений для main.php handlers.
 *
 * Источник выноса: MainPhp.buildServerChatTimeHtml() и MainPhp.sendInventoryChatMessage(...).
 * Зависимости: FightAuto для server timestamp, AppVars.mainActivity/Chat для прямого UI-добавления,
 * LocalBroadcastManager + AppVars.ACTION_ADD_CHAT_MESSAGE для фонового добавления сообщения.
 */
final class MainPhpChatBridge {

    private MainPhpChatBridge() {
    }

    /**
     * Возвращает HTML timestamp в том же формате, что и боевой модуль FightAuto.
     * Важно: единый формат нужен для сообщений авто-функций в чат.
     */
    static String buildServerChatTimeHtml() {
        return FightAuto.buildServerChatTimeHtml();
    }

    /**
     * Отправляет уже собранный HTML (`messageHtml`) в локальный чат.
     *
     * Порядок зависимостей:
     * - AppVars.getContext()==null или пустой messageHtml: skip без побочных эффектов.
     * - AppVars.mainActivity.get()!=null: прямой Chat.addMessageToChat(messageHtml).
     * - иначе LocalBroadcastManager с action AppVars.ACTION_ADD_CHAT_MESSAGE и extra `message`.
     */
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

package ru.neverlands.anclient.utils;

import ru.neverlands.anclient.utils.AppLog;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.os.Handler;
import android.os.Looper;

import ru.neverlands.anclient.MainActivity;

public class Chat {
    private static final String TAG = "Chat";
    private static final ConcurrentLinkedQueue<String> ANSWERS = new ConcurrentLinkedQueue<>();
    private static final Object LOG_LOCK = new Object();
    private static long lastChanged = System.currentTimeMillis();
    private static boolean critical = false;
    private static long lastAnswerTime = 0;
    private static String chatLogFileName;
    private static volatile String lastSystemChatMessage = "";
    private static volatile long lastSystemChatMessageAtMs = 0L;
    // Очередь сообщений, ожидающих отправки при готовности ChatWebView
    // Зависимость: используется в sendChatMessage для retry при недоступности WebView
    private static final ConcurrentLinkedQueue<String> PENDING_MESSAGES = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<String> PENDING_DISPLAY_MESSAGES = new ConcurrentLinkedQueue<>();
    private static final long RETRY_DELAY_MS = 500L;
    private static volatile boolean chatWebViewRetryScheduled = false;
    private static volatile boolean chatDisplayRetryScheduled = false;
    // Лог чата — один файл в день: YYYYMMDD_chat.html
    // Используется для формирования имени файла в addStringToChat/getCurrentLogPath.
    private static final SimpleDateFormat LOG_TS_FORMAT = new SimpleDateFormat("yyyyMMdd", Locale.US);
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final Runnable SEND_RUNNABLE = new Runnable() {
        @Override
        public void run() {
            attemptSendAutoAnswer();
        }
    };
    private static final Runnable RETRY_PENDING_MESSAGES_RUNNABLE = new Runnable() {
        @Override
        public void run() {
            retryPendingMessages();
        }
    };
    private static final Runnable RETRY_PENDING_DISPLAY_MESSAGES_RUNNABLE = new Runnable() {
        @Override
        public void run() {
            retryPendingDisplayMessages();
        }
    };

    /**
     * Возвращает последнее системное сообщение из чата (очищенное от HTML, обрезанное по длине).
     *
     * Зависимости:
     * - обновляется в addStringToChat(...) и addMessageToChat(...), где проходит весь чатовый поток;
     * - используется в AutoModeForegroundService для расширения foreground-уведомления.
     */
    public static String getLastSystemChatMessage() {
        return lastSystemChatMessage;
    }

    /**
     * Временная метка последнего обновления системного сообщения чата.
     *
     * Зависимости:
     * - выставляется вместе с getLastSystemChatMessage() внутри captureSystemChatMessage(...);
     * - используется в AutoModeForegroundService для отображения времени сообщения.
     */
    public static long getLastSystemChatMessageAtMs() {
        return lastSystemChatMessageAtMs;
    }

    /**
     * Выделяет системные сообщения ("Системная информация") и сохраняет краткую строку для UI-статуса.
     * Логика не влияет на боевой/чатовый pipeline: только дополнительный канал состояния.
     */
    private static void captureSystemChatMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (!lower.contains("системная информация")) {
            return;
        }

        String normalized = message
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isEmpty()) {
            return;
        }
        if (normalized.length() > 220) {
            normalized = normalized.substring(0, 217) + "...";
        }

        lastSystemChatMessage = normalized;
        lastSystemChatMessageAtMs = System.currentTimeMillis();
    }

    // Вызывается после добавления сообщений в чат (JS add_msg).
    // Снимает критическое состояние и планирует автоответы.
    public static void chatUpdated() {
        lastChanged = System.currentTimeMillis();
        critical = false;
        scheduleAutoAnswer();
        retryPendingDisplayMessages();
    }

    // Критическое состояние блокирует автоответы (аналог C# Critical).
    public static void setCritical(boolean value) {
        critical = value;
    }

    // Очередь автоответов (аналог MyChat.AnswersCollection).
    public static void addAnswer(String message) {
        if (message == null || message.isEmpty()) return;
        ANSWERS.add(message);
        scheduleAutoAnswer();
    }

    // Получить следующий автоответ (учитывает Critical и LastChanged).
    public static String getAnswer() {
        if (critical || (System.currentTimeMillis() - lastChanged) < 3000) {
            return "";
        }
        String msg = ANSWERS.poll();
        if (msg == null) return "";
        lastChanged = System.currentTimeMillis();
        return msg;
    }

    // Планирование отправки автоответа с задержкой (чтобы не спамить сервер).
    public static void scheduleAutoAnswer() {
        HANDLER.removeCallbacks(SEND_RUNNABLE);
        HANDLER.postDelayed(SEND_RUNNABLE, 3000);
    }

    // Попытка отправить автоответ, если условия позволяют.
    private static void attemptSendAutoAnswer() {
        if (AppVars.Profile == null || !AppVars.Profile.DoAutoAnswer) return;
        if (critical) return;
        long now = System.currentTimeMillis();
        if (now - lastChanged < 3000) {
            scheduleAutoAnswer();
            return;
        }
        if (now - lastAnswerTime < 3000) {
            scheduleAutoAnswer();
            return;
        }
        MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
        if (activity == null) return;
        String msg = ANSWERS.poll();
        if (msg == null || msg.isEmpty()) return;
        lastAnswerTime = now;
        lastChanged = now;
        sendChatMessage(activity, msg);
    }

    // Отправка текста в форму чата (через chatButtonsWebview).
    private static void sendChatMessage(MainActivity activity, String message) {
        String safe = message == null ? "" : message;
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = gson.toJson(safe);
        AppLog.d(TAG, "sendChatMessage request: len=" + safe.length()
                + ", clanPrefix=" + safe.startsWith("%clan%")
                + ", privatePrefix=" + safe.startsWith("%<")
                + ", pairPrefix=" + safe.startsWith("%pair%"));
        activity.runOnUiThread(() -> {
            if (activity.binding != null && activity.binding.appBarMain != null
                    && activity.binding.appBarMain.contentMain != null
                    && activity.binding.appBarMain.contentMain.chatButtonsWebview != null) {
                String js = "if(document.FBT&&document.FBT.text){document.FBT.text.value=" + json + ";document.FBT.submit();}";
                activity.binding.appBarMain.contentMain.chatButtonsWebview.evaluateJavascript(js, null);
                AppLog.d(TAG, "sendChatMessage evaluateJavascript: submitted");
                FileLogger.log("[Chat.sendChatMessage] Message delivered via WebView: " + safe.substring(0, Math.min(80, safe.length())));
                // После успешной отправки, попытаться отправить ожидающие сообщения
                if (!PENDING_MESSAGES.isEmpty()) {
                    retryPendingMessages();
                }
            } else {
                AppLog.w(TAG, "sendChatMessage dropped: chatButtonsWebview is not ready, adding to retry queue");
                FileLogger.log("[Chat.sendChatMessage] WebView not ready, queued: " + safe.substring(0, Math.min(80, safe.length())));
                // Добавить сообщение в очередь для повторной попытки
                PENDING_MESSAGES.offer(safe);
                // Запланировать retry если ещё не запланирован
                if (!chatWebViewRetryScheduled) {
                    chatWebViewRetryScheduled = true;
                    HANDLER.postDelayed(RETRY_PENDING_MESSAGES_RUNNABLE, RETRY_DELAY_MS);
                }
            }
        });
    }

    /**
     * Попыт отправить ожидающие сообщения из очереди.
     * Вызывается либо после успешной отправки нового сообщения,
     * либо по истечении RETRY_DELAY_MS.
     * 
     * Зависимости:
     * - PENDING_MESSAGES: очередь ожидающих сообщений
     * - sendChatMessage: рекурсивно отправляет каждое сообщение
     */
    private static void retryPendingMessages() {
        MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
        if (activity == null || PENDING_MESSAGES.isEmpty()) {
            chatWebViewRetryScheduled = false;
            return;
        }
        
        if (activity.binding != null && activity.binding.appBarMain != null
                && activity.binding.appBarMain.contentMain != null
                && activity.binding.appBarMain.contentMain.chatButtonsWebview != null) {
            // WebView готов - отправляем ожидающие сообщения
            String message;
            int sentCount = 0;
            while ((message = PENDING_MESSAGES.poll()) != null && sentCount < 5) {
                AppLog.d(TAG, "retryPendingMessages: sending from queue, remaining=" + PENDING_MESSAGES.size());
                FileLogger.log("[Chat.retryPendingMessages] Retrying queued message (" + (sentCount + 1) + "): " + 
                    message.substring(0, Math.min(80, message.length())));
                sendChatMessage(activity, message);
                sentCount++;
            }
            chatWebViewRetryScheduled = false;
        } else {
            // WebView всё ещё не готов - запланировать новый retry
            AppLog.d(TAG, "retryPendingMessages: chatButtonsWebview still not ready, queued messages=" + PENDING_MESSAGES.size());
            FileLogger.log("[Chat.retryPendingMessages] WebView still not ready, " + PENDING_MESSAGES.size() + " messages waiting");
            if (!chatWebViewRetryScheduled) {
                chatWebViewRetryScheduled = true;
                HANDLER.postDelayed(RETRY_PENDING_MESSAGES_RUNNABLE, RETRY_DELAY_MS);
            }
        }
    }

    /**
     * Немедленная отправка сообщения в игровой чат.
     *
     * Назначение:
     * - использовать из системных менеджеров (например, UnderAttack), где нужен
     *   прямой аналог C# `WriteMessageToChat`, а не очередь автоответов.
     *
     * Зависимости:
     * - активный `MainActivity` и `chatButtonsWebview`,
     * - JS-форма `document.FBT` в нижнем фрейме чата.
     */
    public static void sendMessageToServer(String message) {
        if (message == null || message.trim().isEmpty()) return;
        MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
        if (activity == null) {
            AppLog.w(TAG, "sendMessageToServer: activity is null, skip");
            FileLogger.log("[Chat.sendMessageToServer] FAILED: activity is null");
            return;
        }
        String trimmed = message.trim();
        AppLog.d(TAG, "sendMessageToServer: len=" + trimmed.length()
                + ", clanPrefix=" + trimmed.startsWith("%clan%")
                + ", privatePrefix=" + trimmed.startsWith("%<")
                + ", pairPrefix=" + trimmed.startsWith("%pair%"));
        FileLogger.log("[Chat.sendMessageToServer] Sending: " + trimmed.substring(0, Math.min(100, trimmed.length())));
        long now = System.currentTimeMillis();
        lastChanged = now;
        lastAnswerTime = now;
        sendChatMessage(activity, trimmed);
    }

    // Вставка сообщения в окно чата (chatMsgWebview) через add_msg JS.
    private static void schedulePendingDisplayMessagesRetry() {
        if (chatDisplayRetryScheduled) return;
        chatDisplayRetryScheduled = true;
        HANDLER.postDelayed(RETRY_PENDING_DISPLAY_MESSAGES_RUNNABLE, RETRY_DELAY_MS);
    }

    private static String buildChatPreview(String message) {
        if (message == null) return "null";
        String normalized = message.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 120 ? normalized.substring(0, 117) + "..." : normalized;
    }

    private static void queuePendingDisplayMessage(String message, String reason) {
        if (message == null || message.isEmpty()) return;
        PENDING_DISPLAY_MESSAGES.offer(message);
        AppLog.w(TAG, TAG, "display queued: reason=" + reason
                + ", pending=" + PENDING_DISPLAY_MESSAGES.size()
                + ", preview=" + buildChatPreview(message));
        schedulePendingDisplayMessagesRetry();
    }

    private static void deliverMessageToChatWebView(MainActivity activity, String message, boolean fromQueue) {
        if (activity == null
                || activity.binding == null
                || activity.binding.appBarMain == null
                || activity.binding.appBarMain.contentMain == null
                || activity.binding.appBarMain.contentMain.chatMsgWebview == null) {
            queuePendingDisplayMessage(message, activity == null ? "activity_null" : "chat_webview_not_ready");
            return;
        }

        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = gson.toJson(message);
        activity.binding.appBarMain.contentMain.chatMsgWebview.evaluateJavascript(
                "(function(){if(typeof add_msg==='function'){add_msg(" + json + ");return 'ready';}return 'pending';})()",
                result -> {
                    boolean delivered = result != null && result.contains("ready");
                    if (delivered) {
                        AppLog.d(TAG, TAG, "display delivered: fromQueue=" + fromQueue
                                + ", pending=" + PENDING_DISPLAY_MESSAGES.size()
                                + ", preview=" + buildChatPreview(message));
                        if (!PENDING_DISPLAY_MESSAGES.isEmpty()) {
                            schedulePendingDisplayMessagesRetry();
                        }
                        return;
                    }

                    AppLog.w(TAG, TAG, "display deferred: fromQueue=" + fromQueue
                            + ", result=" + result
                            + ", preview=" + buildChatPreview(message));
                    queuePendingDisplayMessage(message, "add_msg_not_ready");
                });
    }

    private static void retryPendingDisplayMessages() {
        chatDisplayRetryScheduled = false;
        String message = PENDING_DISPLAY_MESSAGES.poll();
        if (message == null || message.isEmpty()) return;

        MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
        if (activity == null) {
            queuePendingDisplayMessage(message, "retry_without_activity");
            return;
        }
        activity.runOnUiThread(() -> deliverMessageToChatWebView(activity, message, true));
    }

    public static void addMessageToChat(String message) {
        String safe = message == null ? "" : message;
        AppLog.i(TAG, TAG, "addMessageToChat: " + buildChatPreview(safe));
        try {
            captureSystemChatMessage(safe);
            MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
            if (activity == null) {
                queuePendingDisplayMessage(safe, "entry_without_activity");
                return;
            }
            activity.runOnUiThread(() -> deliverMessageToChatWebView(activity, safe, false));
        } catch (Exception e) {
            AppLog.e(TAG, TAG, "addMessageToChat failed", e);
            queuePendingDisplayMessage(safe, "exception");
        }
    }

    private static String buildDailyChatLogFileName(Date date) {
        return LOG_TS_FORMAT.format(date) + "_chat.html";
    }

    private static String resolveDailyChatLogFileNameLocked() {
        String expectedFileName = buildDailyChatLogFileName(new Date());
        if (chatLogFileName == null || !chatLogFileName.equals(expectedFileName)) {
            String previousFileName = chatLogFileName;
            chatLogFileName = expectedFileName;
            if (previousFileName != null && !previousFileName.equals(expectedFileName)) {
                AppLog.i(TAG, "chat log rotated: " + previousFileName + " -> " + expectedFileName);
            }
        }
        return chatLogFileName;
    }

    public static void addStringToChat(String message) {
        if (message == null || message.isEmpty()) return;
        captureSystemChatMessage(message);
        // Логируем чат только если включено хранение логов.
        if (AppVars.Profile == null || !AppVars.Profile.ChatKeepLog) return;
        if (AppVars.getContext() == null) return;
        synchronized (LOG_LOCK) {
            try {
                String dailyLogFileName = resolveDailyChatLogFileNameLocked();
                String nick = AppVars.Profile != null ? AppVars.Profile.UserNick : "unknown";
                if (nick == null || nick.isEmpty()) nick = "unknown";
                String safeNick = nick.replaceAll("[/\\\\:*?\"<>|]", "_");
                // Чат — игровая история пользователя, поэтому пишем в files/info/<nick>/,
                // а не в files/Logs, который полностью очищается настройкой debug-логов.
                File file = GameInfoStorage.resolveChatLogFile(safeNick, dailyLogFileName);
                if (file == null) return;
                boolean newFile = !file.exists();
                try (FileOutputStream fos = new FileOutputStream(file, true)) {
                    if (newFile) {
                        // Шапка HTML-лога с css чата (как в ПК версии).
                        String header =
                                "<HTML>" +
                                "<META Content=\"text/html; Charset=utf-8\" Http-Equiv=Content-type>" +
                                "<HEAD>" +
                                "<LINK href=\"http://www.neverlands.ru/ch/chat.css\" rel=STYLESHEET type=text/css>" +
                                "</HEAD>" +
                                "<BODY LeftMargin=2 TopMargin=2 RightMargin=2 MarginHeight=2 MarginWidth=2 BgColor=#F5F5F5>";
                        fos.write((header + "\n").getBytes());
                    } else {
                        fos.write("<BR>\n".getBytes());
                    }
                    fos.write((message + "\n").getBytes());
                }
            } catch (Exception e) {
                AppLog.e(TAG, "addStringToChat failed", e);
            }
        }
    }

    public static String getCurrentLogPath() {
        if (AppVars.Profile == null) return "";
        String nick = AppVars.Profile.UserNick != null ? AppVars.Profile.UserNick : "unknown";
        if (nick.isEmpty()) nick = "unknown";
        String safeNick = nick.replaceAll("[/\\\\:*?\"<>|]", "_");
        // Путь показывается пользователю в статистике; он должен совпадать с текущей
        // директорией хранения игровых данных files/info/<nick>/.
        // Текущий дневной лог: YYYYMMDD_chat.html
        String fileName;
        synchronized (LOG_LOCK) {
            fileName = resolveDailyChatLogFileNameLocked();
        }
        File file = GameInfoStorage.resolveChatLogFile(safeNick, fileName);
        return file == null ? "" : file.getAbsolutePath();
    }
}

package ru.neverlands.abclient.utils;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.os.Handler;
import android.os.Looper;

import ru.neverlands.abclient.MainActivity;

public class Chat {
    private static final String TAG = "Chat";
    private static final ConcurrentLinkedQueue<String> ANSWERS = new ConcurrentLinkedQueue<>();
    private static final Object LOG_LOCK = new Object();
    private static long lastChanged = System.currentTimeMillis();
    private static boolean critical = false;
    private static long lastAnswerTime = 0;
    private static String chatLogFileName;
    private static final SimpleDateFormat LOG_TS_FORMAT = new SimpleDateFormat("yyyyMMdd", Locale.US);
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final Runnable SEND_RUNNABLE = new Runnable() {
        @Override
        public void run() {
            attemptSendAutoAnswer();
        }
    };

    public static void chatUpdated() {
        lastChanged = System.currentTimeMillis();
        critical = false;
        scheduleAutoAnswer();
    }

    public static void setCritical(boolean value) {
        critical = value;
    }

    public static void addAnswer(String message) {
        if (message == null || message.isEmpty()) return;
        ANSWERS.add(message);
        scheduleAutoAnswer();
    }

    public static String getAnswer() {
        if (critical || (System.currentTimeMillis() - lastChanged) < 3000) {
            return "";
        }
        String msg = ANSWERS.poll();
        if (msg == null) return "";
        lastChanged = System.currentTimeMillis();
        return msg;
    }

    public static void scheduleAutoAnswer() {
        HANDLER.removeCallbacks(SEND_RUNNABLE);
        HANDLER.postDelayed(SEND_RUNNABLE, 3000);
    }

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

    private static void sendChatMessage(MainActivity activity, String message) {
        String safe = message == null ? "" : message;
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = gson.toJson(safe);
        activity.runOnUiThread(() -> {
            if (activity.binding != null && activity.binding.appBarMain != null
                    && activity.binding.appBarMain.contentMain != null
                    && activity.binding.appBarMain.contentMain.chatButtonsWebview != null) {
                String js = "if(document.FBT&&document.FBT.text){document.FBT.text.value=" + json + ";document.FBT.submit();}";
                activity.binding.appBarMain.contentMain.chatButtonsWebview.evaluateJavascript(js, null);
            }
        });
    }

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

    public static void addStringToChat(String message) {
        if (message == null || message.isEmpty()) return;
        if (AppVars.Profile == null || !AppVars.Profile.ChatKeepLog) return;
        if (AppVars.getContext() == null) return;
        synchronized (LOG_LOCK) {
            try {
                if (chatLogFileName == null) {
                    chatLogFileName = LOG_TS_FORMAT.format(new Date()) + "_chat.html";
                }
                String nick = AppVars.Profile != null ? AppVars.Profile.UserNick : "unknown";
                if (nick == null || nick.isEmpty()) nick = "unknown";
                String safeNick = nick.replaceAll("[/\\\\:*?\"<>|]", "_");
                File baseLogs = AppVars.getLogsDir();
                if (baseLogs == null) {
                    baseLogs = new File(AppVars.getContext().getFilesDir(), "Logs");
                }
                File userDir = new File(baseLogs, safeNick);
                if (!userDir.exists()) userDir.mkdirs();
                File file = new File(userDir, chatLogFileName);
                boolean newFile = !file.exists();
                try (FileOutputStream fos = new FileOutputStream(file, true)) {
                    if (newFile) {
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
                Log.e(TAG, "addStringToChat failed", e);
            }
        }
    }

    public static String getCurrentLogPath() {
        if (AppVars.Profile == null) return "";
        String nick = AppVars.Profile.UserNick != null ? AppVars.Profile.UserNick : "unknown";
        if (nick.isEmpty()) nick = "unknown";
        String safeNick = nick.replaceAll("[/\\\\:*?\"<>|]", "_");
        File baseLogs = AppVars.getLogsDir();
        if (baseLogs == null && AppVars.getContext() != null) {
            baseLogs = new File(AppVars.getContext().getFilesDir(), "Logs");
        }
        if (baseLogs == null) return "";
        String fileName = chatLogFileName;
        if (fileName == null) {
            fileName = LOG_TS_FORMAT.format(new Date()) + "_chat.html";
        }
        return new File(new File(baseLogs, safeNick), fileName).getAbsolutePath();
    }
}

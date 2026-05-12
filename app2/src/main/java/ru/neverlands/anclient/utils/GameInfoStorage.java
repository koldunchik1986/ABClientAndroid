package ru.neverlands.anclient.utils;

import android.content.Context;

import java.io.File;

/**
 * Единая точка путей для игровых user-data ANClient.
 *
 * files/Logs остаётся debug/runtime зоной и полностью чистится кнопкой "Очистить логи".
 * files/info/<nick>/ хранит игровые чат/статистику/profile-scoped кеши и
 * не участвует в очистке Logs.
 */
public final class GameInfoStorage {
    public static final String CHAT_LOG_SUFFIX = "_chat.html";
    public static final String STAT_LOG_SUFFIX = "_stat.txt";

    private static final String DEFAULT_PROFILE_DIR = "profile";

    private GameInfoStorage() {
    }

    public static File resolveChatLogFile(String nick, String fileName) {
        File userDir = resolveUserInfoDir(nick);
        if (userDir == null) {
            return null;
        }
        return new File(userDir, sanitizeFileName(fileName));
    }

    public static File resolveStatFile(String nick, String dateYmd) {
        File userDir = resolveUserInfoDir(nick);
        if (userDir == null) {
            return null;
        }
        String safeDate = sanitizeFileName(dateYmd == null ? "" : dateYmd.trim());
        if (safeDate.isEmpty()) {
            safeDate = "unknown";
        }
        return new File(userDir, safeDate + STAT_LOG_SUFFIX);
    }

    public static File resolveUserInfoSubDir(String nick, String childDirName) {
        File userDir = resolveUserInfoDir(nick);
        if (userDir == null) {
            return null;
        }
        String safeChildDirName = sanitizeFileName(childDirName);
        if (safeChildDirName.isEmpty()) {
            safeChildDirName = DEFAULT_PROFILE_DIR;
        }
        File childDir = new File(userDir, safeChildDirName);
        return ensureDir(childDir) ? childDir : null;
    }

    public static File resolveLegacyUserLogsDir(String nick) {
        // Только read fallback для старых установок; при очистке Logs миграция не выполняется.
        File logsRoot = resolveLogsRoot();
        return logsRoot == null ? null : new File(logsRoot, sanitizeProfileName(nick));
    }

    public static File resolveLegacyProfileStatFile(String profileKey) {
        // Старый корневой путь Logs/<profile>_stat.txt читается до первой успешной очистки Logs.
        File logsRoot = resolveLogsRoot();
        return logsRoot == null ? null : new File(logsRoot, sanitizeFileName(profileKey) + STAT_LOG_SUFFIX);
    }

    public static File resolveLegacyDailyStatFile(String dateYmd) {
        // Старый дневной путь Logs/<date>_stat.txt сохраняем только как fallback чтения.
        File logsRoot = resolveLogsRoot();
        return logsRoot == null ? null : new File(logsRoot, sanitizeFileName(dateYmd) + STAT_LOG_SUFFIX);
    }

    public static String sanitizeProfileName(String nick) {
        String value = nick == null ? "" : nick.trim();
        value = value.replaceAll("[/\\\\:*?\"<>|]", "_");
        return value.isEmpty() ? DEFAULT_PROFILE_DIR : value;
    }

    public static File resolveUserInfoDir(String nick) {
        File infoRoot = resolveInfoRoot();
        if (infoRoot == null) {
            return null;
        }
        File userDir = new File(infoRoot, sanitizeProfileName(nick));
        if (!ensureDir(userDir)) {
            return null;
        }
        return userDir;
    }

    private static File resolveInfoRoot() {
        File infoRoot = AppVars.getInfoDir();
        if (infoRoot != null) {
            return infoRoot;
        }
        Context context = AppVars.getContext();
        if (context == null) {
            return null;
        }
        File root = context.getExternalFilesDir(null);
        if (root == null) {
            root = context.getFilesDir();
        }
        infoRoot = new File(root, "info");
        return ensureDir(infoRoot) ? infoRoot : null;
    }

    private static File resolveLogsRoot() {
        File logsRoot = AppVars.getLogsDir();
        if (logsRoot != null) {
            return logsRoot;
        }
        Context context = AppVars.getContext();
        if (context == null) {
            return null;
        }
        File fallback = new File(context.getFilesDir(), "Logs");
        return ensureDir(fallback) ? fallback : null;
    }

    private static boolean ensureDir(File dir) {
        return dir != null && (dir.exists() || dir.mkdirs());
    }

    private static String sanitizeFileName(String fileName) {
        String value = fileName == null ? "" : fileName.trim();
        value = value.replaceAll("[/\\\\:*?\"<>|]", "_");
        return value;
    }
}

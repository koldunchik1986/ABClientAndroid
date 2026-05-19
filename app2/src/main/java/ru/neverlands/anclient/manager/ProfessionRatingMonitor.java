package ru.neverlands.anclient.manager;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ru.neverlands.anclient.info.ProfessionRatingRepository;
import ru.neverlands.anclient.postfilter.MainPhp;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.Chat;

/**
 * Проверяет воскресные Top 10 weekly-рейтингов и публикует локальные чат-уведомления.
 */
public final class ProfessionRatingMonitor {
    private static final String TAG = "ProfessionRatingMonitor";
    private static final String TRACE_CHAIN = "PROF_RATING_TRACE";
    private static final String PREFS_NAME = "profession_rating_monitor";
    private static final String KEY_LAST_CHECK_HOUR = "last_check_hour";
    private static final long RETRY_AFTER_FAILURE_MS = 10 * 60 * 1000L;

    private static volatile boolean checkRunning;
    private static volatile long lastCheckStartedAtMs;

    private ProfessionRatingMonitor() {
    }

    public static void maybeCheck(Context context) {
        if (context == null || AppVars.Profile == null) {
            return;
        }
        String nick = resolveCurrentNick();
        if (nick.isEmpty()) {
            return;
        }

        Date serverDate = resolveServerDate();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(serverDate);
        if (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY || calendar.get(Calendar.HOUR_OF_DAY) < 15) {
            return;
        }

        String hourKey = new SimpleDateFormat("yyyyMMddHH", Locale.US).format(serverDate);
        String preferenceKey = KEY_LAST_CHECK_HOUR + "_"
                + Integer.toHexString(ProfessionRatingRepository.normalizeNick(nick).hashCode());
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (hourKey.equals(prefs.getString(preferenceKey, ""))) {
            return;
        }

        long now = System.currentTimeMillis();
        if (checkRunning || now - lastCheckStartedAtMs < RETRY_AFTER_FAILURE_MS) {
            return;
        }

        checkRunning = true;
        lastCheckStartedAtMs = now;
        Context appContext = context.getApplicationContext();
        new Thread(() -> runCheck(appContext, nick, hourKey, preferenceKey), "prof-rating-monitor").start();
    }

    private static void runCheck(Context context, String currentNick, String hourKey, String preferenceKey) {
        boolean anySuccess = false;
        int hitCount = 0;
        try {
            String normalizedNick = ProfessionRatingRepository.normalizeNick(currentNick);
            List<ProfessionRatingRepository.Category> categories = ProfessionRatingRepository.getCategories();
            for (ProfessionRatingRepository.Category category : categories) {
                try {
                    ProfessionRatingRepository.RatingTable table = ProfessionRatingRepository.loadRating(category.id, true);
                    anySuccess = true;
                    ProfessionRatingRepository.RatingEntry hit = findTopTenHit(table, normalizedNick);
                    if (hit != null) {
                        hitCount++;
                        postHitNotification(category, hit);
                    }
                } catch (Exception error) {
                    AppLog.w(TRACE_CHAIN, TAG, "WEEKLY_RATING_CHECK_FAILED: id=" + category.id
                            + ", title=" + category.title, error);
                }
            }
            if (anySuccess) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(preferenceKey, hourKey)
                        .apply();
            }
            AppLog.i(TRACE_CHAIN, TAG, "WEEKLY_RATING_CHECK_DONE: nick=" + currentNick
                    + ", hour=" + hourKey + ", hits=" + hitCount + ", anySuccess=" + anySuccess);
        } finally {
            checkRunning = false;
        }
    }

    private static ProfessionRatingRepository.RatingEntry findTopTenHit(
            ProfessionRatingRepository.RatingTable table,
            String normalizedNick) {
        if (table == null || normalizedNick.isEmpty()) {
            return null;
        }
        int limit = Math.min(10, table.entries.size());
        for (int i = 0; i < limit; i++) {
            ProfessionRatingRepository.RatingEntry entry = table.entries.get(i);
            if (normalizedNick.equals(ProfessionRatingRepository.normalizeNick(entry.nick))) {
                return entry;
            }
        }
        return null;
    }

    private static void postHitNotification(
            ProfessionRatingRepository.Category category,
            ProfessionRatingRepository.RatingEntry entry) {
        String message = MainPhp.buildServerChatTimeHtmlExternal()
                + "<font color=#333399><b>[ANClient]</b>:</font> "
                + "Вы попали в рейтинг " + escapeHtml(category.title)
                + " под № " + entry.rank
                + ". Не забудьте забрать награду!";
        Chat.addMessageToChat(message);
        AppLog.i(TRACE_CHAIN, TAG, "WEEKLY_RATING_TOP10_NOTIFY: category=" + category.title
                + ", rank=" + entry.rank + ", nick=" + entry.nick);
    }

    private static String resolveCurrentNick() {
        String nick = AppVars.Profile != null && AppVars.Profile.UserNick != null
                ? AppVars.Profile.UserNick.trim()
                : "";
        if (nick.isEmpty()) {
            nick = AppVars.RuntimeAuthUserNick == null ? "" : AppVars.RuntimeAuthUserNick.trim();
        }
        return nick;
    }

    private static Date resolveServerDate() {
        long serverMs = System.currentTimeMillis();
        if (AppVars.Profile != null && AppVars.Profile.ServDiff != Long.MIN_VALUE) {
            serverMs -= AppVars.Profile.ServDiff;
        } else if (AppVars.ServerDateTime != null) {
            serverMs = AppVars.ServerDateTime.getTime();
        }
        return new Date(serverMs);
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

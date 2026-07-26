package ru.neverlands.anclient.utils;

import ru.neverlands.anclient.utils.AppLog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Event-driven обработчик для немедленного отклика на объявление боя.
 * 
 * Назначение:
 * - Обработать событие "новый бой объявлен" в реальном времени (<100ms)
 * - Проверить условия безопасности (captcha, guard'ы, VCode)
 * - Запустить первый автоход немедленно, вместо ожидания polling'а (24+ сек)
 * 
 * Архитектура:
 * - Универсальный модуль (не зависит от конкретного Activity/Fragment)
 * - Вызывается из управляющих файлов (FightViewModel, FightAuto и т.д.)
 * - Делегирует работу специализированным компонентам
 * 
 * Пример использования:
 *   FightAnnounceHandler.onFightAnnounced(
 *       fighterNick,
 *       captchaVisible,
 *       () -> mainActivity.requestImmediateAutoTurnOnFightAnnounce()
 *   );
 */
public final class FightAnnounceHandler {
    private static final String TAG = "FightAnnounceHandler";
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private FightAnnounceHandler() {
    }

    /**
     * Обработчик события "объявлен новый бой".
     * 
     * Выполняет все проверки и инициирует event-driven отправку первого хода.
     * 
     * @param fighterNickname Имя противника (для логирования)
     * @param isCaptchaVisible Видна ли captcha диалог
     * @param onApprovedCallback Callback для запуска немедленного хода (вызывается если все проверки пройдены)
     */
    public static void onFightAnnounced(
            @Nullable String fighterNickname,
            boolean isCaptchaVisible,
            @NonNull Runnable onApprovedCallback) {
        onFightAnnounced(fighterNickname, isCaptchaVisible, onApprovedCallback, 0);
    }

    private static void onFightAnnounced(
            @Nullable String fighterNickname,
            boolean isCaptchaVisible,
            @NonNull Runnable onApprovedCallback,
            int retryAttempt) {
        String traceMsg = "[FIGHT_ANNOUNCE_EVENT] fighter=" + fighterNickname + ", captcha=" + isCaptchaVisible;
        AppLog.d(TAG, TAG, traceMsg);
        
        // === ПРОВЕРКА 1: Captcha видна ===
        if (isCaptchaVisible) {
            String captchaMsg = "[FIGHT_ANNOUNCE_BLOCKED] captcha visible, delaying turn submit";
            AppLog.w(TAG, TAG, captchaMsg);
            return;
        }
        
        // === ПРОВЕРКА 2: Guard'ы и условия безопасности ===
        if (!ForcedActionGuard.shouldForceActionAdvanced(
                "fight_turn",  // actionName
                true,          // uiForegroundLikely: может быть true если пользователь видит экран
                true,          // fightLikelyActive: мы уже знаем что бой активен
                true,          // hasFightMarkers: бой объявлен
                false)) {      // hasPendingFinishLink: нет ещё finish-link
            String guardMsg = "[FIGHT_ANNOUNCE_BLOCKED] guard conditions not met";
            AppLog.w(TAG, TAG, guardMsg);
            // Retry через короткий промежуток времени
            scheduleRetryAfterMs(fighterNickname, onApprovedCallback, 500, retryAttempt + 1);
            return;
        }
        
        // === ПРОВЕРКА 3: VCode доступен ===
        String vcode = SessionManager.getInstance().getValidVCodeForAction("fight_turn");
        if (vcode == null) {
            String vcodeMsg = "[FIGHT_ANNOUNCE_BLOCKED] no valid vcode available";
            AppLog.w(TAG, TAG, vcodeMsg);
            // Retry через промежуток для получения нового VCode
            scheduleRetryAfterMs(fighterNickname, onApprovedCallback, 800, retryAttempt + 1);
            return;
        }
        
        // === ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ ===
        SessionContext ctx = SessionManager.getInstance().getCurrentContext();
        long vcodeAgeMs = (ctx != null) ? ctx.getAgeMs() : 0L;
        String approvedMsg = "[FIGHT_ANNOUNCE_APPROVED] all checks passed, triggering immediate auto-turn, vcode age=" + 
                vcodeAgeMs + "ms";
        AppLog.i(TAG, TAG, approvedMsg);
        
        try {
            onApprovedCallback.run();
        } catch (Exception e) {
            String errorMsg = "[FIGHT_ANNOUNCE_ERROR] callback failed: " + e.getMessage();
            AppLog.e(TAG, errorMsg, e);
        }
    }

    /**
     * Запланировать повторную попытку через указанное время.
     * 
     * @param callback Callback для повторного вызова
     * @param delayMs Время задержки в миллисекундах
     */
    private static void scheduleRetryAfterMs(
            @Nullable String fighterNickname,
            @NonNull Runnable callback,
            long delayMs,
            int retryAttempt) {
        if (retryAttempt > MAX_RETRY_ATTEMPTS) {
            AppLog.w(TAG, TAG, "[FIGHT_ANNOUNCE_REJECTED] retry limit reached, attempts="
                    + MAX_RETRY_ATTEMPTS);
            return;
        }
        String msg = "[FIGHT_ANNOUNCE_RETRY_SCHEDULED] delayMs=" + delayMs
                + ", attempt=" + retryAttempt;
        AppLog.i(TAG, TAG, msg);

        // Использовать Handler для асинхронной повторной попытки
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(() -> {
            boolean captchaVisible = isBlockingFightCaptchaVisible();
            String retryMsg = "[FIGHT_ANNOUNCE_RETRY_TRIGGERED] attempting again after " + delayMs
                    + "ms, attempt=" + retryAttempt + ", captcha=" + captchaVisible;
            AppLog.i(TAG, TAG, retryMsg);
            onFightAnnounced(fighterNickname, captchaVisible, callback, retryAttempt);
        }, delayMs);
    }

    private static boolean isBlockingFightCaptchaVisible() {
        if (!AppVars.IsFightCaptchaDialogVisible) {
            return false;
        }
        try {
            ru.neverlands.anclient.MainActivity activity = AppVars.mainActivity != null
                    ? AppVars.mainActivity.get() : null;
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()
                    && activity.isActiveAlchemyCaptchaDialog()) {
                return false;
            }
        } catch (Exception ignored) {
            // Fail safe: an unavailable activity must not bypass an active fight captcha.
        }
        return true;
    }
}

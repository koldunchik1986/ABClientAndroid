package ru.neverlands.anclient.license;

import android.content.Context;

import ru.neverlands.anclient.model.UserConfig;
import ru.neverlands.anclient.utils.AppLog;

/**
 * Обёртка над `LicenseRuntime`, которую вызывает экран логина.
 *
 * Вынесено отдельно от `LoginActivity`: UI-код только решает, показывать ли dialog по `LicenseStatus`,
 * а этот handler отвечает за проверку profile/null и за license-логи.
 */
public final class LicenseValidationHandler {
    private static final String TAG = "LicenseValidation";
    private static final String CHAIN = "ANCLIENT_LICENSE";

    private LicenseValidationHandler() {
    }

    public static LicenseStatus validateBeforeLogin(Context context, UserConfig profile) {
        if (context == null) {
            AppLog.w(CHAIN, TAG, "[LICENSE_REJECTED: context missing]");
            return LicenseStatus.blocked(
                    "Лицензия не проверена",
                    "Не удалось проверить лицензию: отсутствует Context.",
                    "",
                    ""
            );
        }
        if (profile == null) {
            AppLog.w(CHAIN, TAG, "[LICENSE_REJECTED: profile missing]");
            return LicenseStatus.blocked(
                    "Лицензия не проверена",
                    "Не выбран профиль для проверки лицензии.",
                    "",
                    ""
            );
        }
        if (profile.UserNick == null || profile.UserNick.trim().isEmpty()) {
            AppLog.w(CHAIN, TAG, "[LICENSE_REJECTED: profile name empty]");
            return LicenseStatus.blocked(
                    "Лицензия не проверена",
                    "Сначала создайте профиль с именем персонажа.",
                    "",
                    ""
            );
        }

        // `profile.UserNick` — nick, который используется для поиска `nickHash` в `ANREG2`
        // и для сравнения `profileName` в `ANREG1`. Успешный результат также активирует runtime-сессию.
        LicenseStatus status = LicenseRuntime.getInstance().validateAndActivate(context, profile);
        if (status.isAllowed()) {
            AppLog.i(CHAIN, TAG, "[LICENSE_APPROVED: profile=" + profile.UserNick + "]");
        } else {
            AppLog.w(CHAIN, TAG, "[LICENSE_REJECTED: profile=" + profile.UserNick
                    + ", request=" + status.getRequestPath()
                    + ", license=" + status.getLicensePath() + "]");
        }
        return status;
    }
}

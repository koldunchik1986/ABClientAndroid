package ru.neverlands.anclient.license;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ru.neverlands.anclient.model.QuickActionType;

/**
 * Общий словарь features для app2 runtime и app3 license issuer.
 *
 * Важно для отладки:
 * - feature-ключи — lower-case значения `QuickActionType.getActionKey()`;
 * - `limited` соответствует public/free набору общедоступных функций;
 * - `full` для индивидуального grant разворачивается во все quick actions плюс `FEATURE_CLANS`;
 * - `publicFeatures` дополнительно проходит через {@link #removeNonPublicFeatures(Set)}:
 *   `anti_captcha` и `auto_cut` нельзя открыть общим bundle даже если администратор
 *   ошибочно указал public `full`;
 * - custom CSV grants принимаются как есть после нормализации.
 */
public final class LicenseFeature {
    public static final String TIER_FULL = "full";
    public static final String TIER_LIMITED = "limited";
    public static final String TIER_CUSTOM = "custom";
    public static final String FEATURE_CLANS = "clans";
    public static final String FEATURE_ANTI_CAPTCHA = "anti_captcha";
    public static final String FEATURE_AUTO_CUT = QuickActionType.AUTO_CUT.getActionKey();
    public static final String FEATURE_NONE = "none";

    private static final LinkedHashSet<String> LIMITED_FEATURES = buildLimitedFeatures();

    private LicenseFeature() {
    }

    public static Set<String> expandFeatureSpec(String featureSpec) {
        String spec = normalize(featureSpec);
        // Семантика grant: пустой grant spec означает full-доступ, потому что default-режим
        // `issue` в app3 выдаёт full для запрошенного nick. Public features используют
        // `expandPublicFeatureSpec(...)`, где пустое значение означает отсутствие public-доступа.
        if (spec.isEmpty() || TIER_FULL.equals(spec)) {
            return allQuickActionFeatures();
        }
        if (FEATURE_NONE.equals(spec) || "off".equals(spec) || "empty".equals(spec)) {
            return Collections.emptySet();
        }
        if (TIER_LIMITED.equals(spec) || "free".equals(spec) || "basic".equals(spec)) {
            return limitedFeatures();
        }

        LinkedHashSet<String> result = new LinkedHashSet<>();
        String[] parts = spec.split("[,;|\\s]+");
        for (String part : parts) {
            String token = normalize(part);
            if (token.isEmpty()) {
                continue;
            }
            if (TIER_FULL.equals(token)) {
                return allQuickActionFeatures();
            }
            if (FEATURE_NONE.equals(token) || "off".equals(token) || "empty".equals(token)) {
                continue;
            }
            if (TIER_LIMITED.equals(token) || "free".equals(token) || "basic".equals(token)) {
                result.addAll(LIMITED_FEATURES);
                continue;
            }
            result.add(token);
        }
        if (result.isEmpty()) {
            result.addAll(LIMITED_FEATURES);
        }
        return Collections.unmodifiableSet(result);
    }

    public static Set<String> expandPublicFeatureSpec(String featureSpec) {
        String spec = normalize(featureSpec);
        // Семантика public bundle: empty/none/off/empty не должны случайно превратиться в full.
        // Этот метод используется только для `ANREG2.publicFeatures`.
        // Важный инвариант лицензирования: Anti-Captcha и Авто-Травник не являются
        // общедоступными функциями. Они доступны только через индивидуальный `full` grant
        // или custom grant с ключами `anti_captcha`/`auto_cut`.
        if (spec.isEmpty() || FEATURE_NONE.equals(spec) || "off".equals(spec) || "empty".equals(spec)) {
            return Collections.emptySet();
        }
        return removeNonPublicFeatures(expandFeatureSpec(spec));
    }

    public static String deriveTier(String featureSpec, Set<String> enabledFeatures) {
        String spec = normalize(featureSpec);
        Set<String> safeFeatures = enabledFeatures == null ? Collections.emptySet() : enabledFeatures;
        if ((spec.isEmpty() || TIER_FULL.equals(spec)) && safeFeatures.containsAll(allQuickActionFeatures())) {
            return TIER_FULL;
        }
        if (TIER_LIMITED.equals(spec) || "free".equals(spec) || "basic".equals(spec)) {
            return TIER_LIMITED;
        }
        if (safeFeatures.containsAll(allQuickActionFeatures())) {
            return TIER_FULL;
        }
        if (LIMITED_FEATURES.containsAll(safeFeatures) && safeFeatures.containsAll(LIMITED_FEATURES)) {
            return TIER_LIMITED;
        }
        return TIER_CUSTOM;
    }

    public static boolean isActionAllowed(LicenseSession session, QuickActionType type) {
        if (type == null || type == QuickActionType.NONE) {
            return true;
        }
        return session != null && session.hasFeature(type.getActionKey());
    }

    public static boolean isTimerAutoFunctionAllowed(LicenseSession session, String label) {
        String actionKey = actionKeyForTimerAutoFunction(label);
        // Таймеры хранят человекочитаемые русские подписи, а лицензия использует action keys.
        // Маппер ниже связывает UI-labels из `AppTimerManager` с grants.
        return !actionKey.isEmpty() && session != null && session.hasFeature(actionKey);
    }

    public static String[] filterAutoFunctionLabels(LicenseSession session, String[] labels) {
        if (labels == null || labels.length == 0) {
            return new String[0];
        }
        List<String> result = new ArrayList<>();
        for (String label : labels) {
            if (isTimerAutoFunctionAllowed(session, label)) {
                result.add(label);
            }
        }
        return result.toArray(new String[0]);
    }

    public static String actionKeyForTimerAutoFunction(String label) {
        String value = label == null ? "" : label.trim();
        if ("Авто-Бой".equals(value)) return QuickActionType.AUTO_FIGHT.getActionKey();
        if ("Авто-Рыбалка".equals(value)) return QuickActionType.AUTO_FISH.getActionKey();
        if ("Авто-Охота".equals(value)) return QuickActionType.AUTO_SKIN.getActionKey();
        if ("Авто-Питьё".equals(value) || "Авто-Питье".equals(value)) return QuickActionType.AUTO_DRINK.getActionKey();
        if ("Авто-Клад".equals(value)) return QuickActionType.AUTO_TREASURE.getActionKey();
        if ("Авто-Травник".equals(value)) return QuickActionType.AUTO_CUT.getActionKey();
        if ("Авто-Босс".equals(value) || "Авто-Боссы".equals(value)) return QuickActionType.AUTO_BOSS.getActionKey();
        if ("Анти-Captcha".equals(value) || "Анти-Капча".equals(value)) return QuickActionType.AUTO_CAPTCHA.getActionKey();
        return "";
    }

    public static Set<String> limitedFeatures() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(LIMITED_FEATURES));
    }

    private static Set<String> removeNonPublicFeatures(Set<String> source) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (source != null) {
            for (String feature : source) {
                String normalized = normalize(feature);
                if (normalized.isEmpty()
                        || FEATURE_ANTI_CAPTCHA.equals(normalized)
                        || FEATURE_AUTO_CUT.equals(normalized)) {
                    continue;
                }
                result.add(normalized);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public static Set<String> allQuickActionFeatures() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (QuickActionType type : QuickActionType.values()) {
            if (type == QuickActionType.NONE) {
                continue;
            }
            String actionKey = normalize(type.getActionKey());
            if (!actionKey.isEmpty()) {
                result.add(actionKey);
            }
        }
        result.add(FEATURE_CLANS);
        return Collections.unmodifiableSet(result);
    }

    private static LinkedHashSet<String> buildLimitedFeatures() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(QuickActionType.AUTO_FIGHT.getActionKey());
        result.add(QuickActionType.AUTO_FISH.getActionKey());
        result.add(QuickActionType.AUTO_SKIN.getActionKey());
        result.add(QuickActionType.AUTO_MOVING.getActionKey());
        result.add(QuickActionType.AUTO_COMPASS.getActionKey());
        result.add(QuickActionType.QUICK_ACTIONS.getActionKey());
        result.add(QuickActionType.TIMERS.getActionKey());
        result.add(QuickActionType.OPEN_CONTACTS.getActionKey());
        result.add(QuickActionType.REFRESH_CONTACTS.getActionKey());
        result.add(QuickActionType.OPEN_STATS.getActionKey());
        result.add(QuickActionType.OPEN_PINFO.getActionKey());
        result.add(FEATURE_CLANS);
        // Anti-Captcha и AutoCut намеренно не входят в limited/public набор:
        // - Anti-Captcha использует платную внешнюю интеграцию;
        // - Авто-Травник — premium automation, которая выдаётся только индивидуальным
        //   full grant или custom grant `auto_cut` с нужным сроком.
        return result;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

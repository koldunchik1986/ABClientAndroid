package ru.neverlands.anclient.license;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Неизменяемый runtime-снимок, который создаёт `LicenseManager` после проверки подписи и привязок.
 *
 * Зависимости полей для отладки:
 * - `enabledFeatures` — фактический allow-list, который проверяет `LicenseRuntime`.
 * - `tier` и `featureSpec` — диагностические метки; не использовать их как security-решение.
 * - `devicePublicKeySha256` / `fingerprintHash` заполнены для bound grants и пустые для public-only доступа.
 * - `capabilityKey` смешивает подписанные поля лицензии с `runtimeNonce` для трассировки сессии в логах.
 */
public final class LicenseSession {
    private final String licenseId;
    private final String requestId;
    private final String profileName;
    private final String licensePath;
    private final String tier;
    private final String featureSpec;
    private final Set<String> enabledFeatures;
    private final long issuedAt;
    private final long expiresAt;
    private final String devicePublicKeySha256;
    private final String fingerprintHash;
    private final String appSignatureSha256;
    private final String capabilityKey;
    private final String runtimeNonce;

    public LicenseSession(String licenseId,
                          String requestId,
                          String profileName,
                          String licensePath,
                          String tier,
                          String featureSpec,
                          Set<String> enabledFeatures,
                          long issuedAt,
                          long expiresAt,
                          String devicePublicKeySha256,
                          String fingerprintHash,
                          String appSignatureSha256,
                          String capabilityKey,
                          String runtimeNonce) {
        this.licenseId = safe(licenseId);
        this.requestId = safe(requestId);
        this.profileName = safe(profileName);
        this.licensePath = safe(licensePath);
        this.tier = safe(tier);
        this.featureSpec = safe(featureSpec);
        this.enabledFeatures = immutableSet(enabledFeatures);
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.devicePublicKeySha256 = safe(devicePublicKeySha256);
        this.fingerprintHash = safe(fingerprintHash);
        this.appSignatureSha256 = safe(appSignatureSha256);
        this.capabilityKey = safe(capabilityKey);
        this.runtimeNonce = safe(runtimeNonce);
    }

    public String getLicenseId() {
        return licenseId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getProfileName() {
        return profileName;
    }

    public String getLicensePath() {
        return licensePath;
    }

    public String getTier() {
        return tier;
    }

    public String getFeatureSpec() {
        return featureSpec;
    }

    public Set<String> getEnabledFeatures() {
        return enabledFeatures;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public String getDevicePublicKeySha256() {
        return devicePublicKeySha256;
    }

    public String getFingerprintHash() {
        return fingerprintHash;
    }

    public String getAppSignatureSha256() {
        return appSignatureSha256;
    }

    public String getCapabilityKey() {
        return capabilityKey;
    }

    public String getRuntimeNonce() {
        return runtimeNonce;
    }

    public boolean isExpired(long nowMs) {
        return expiresAt > 0L && nowMs > expiresAt;
    }

    public boolean isFull() {
        return LicenseFeature.TIER_FULL.equalsIgnoreCase(tier);
    }

    public boolean hasFeature(String featureKey) {
        if (featureKey == null || featureKey.trim().isEmpty()) {
            return false;
        }
        // Держим сравнение feature нормализованным, потому что app3 принимает CSV/custom specs.
        // Канонические ключи всё равно приходят из `QuickActionType.getActionKey()`.
        return enabledFeatures.contains(featureKey.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private static Set<String> immutableSet(Set<String> source) {
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        if (source != null) {
            for (String item : source) {
                String value = item == null ? "" : item.trim().toLowerCase(java.util.Locale.ROOT);
                if (!value.isEmpty()) {
                    copy.add(value);
                }
            }
        }
        return Collections.unmodifiableSet(copy);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

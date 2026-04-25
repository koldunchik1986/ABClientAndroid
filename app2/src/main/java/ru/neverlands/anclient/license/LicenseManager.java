package ru.neverlands.anclient.license;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import ru.neverlands.anclient.model.UserConfig;
import ru.neverlands.anclient.utils.AppLog;

/**
 * Главная точка принятия решения по offline-лицензии ANClient.
 *
 * Карта для отладки:
 * - `validateOrCreateRequest(...)` вызывается перед входом и при повторной проверке из `LicenseRuntime`.
 * - `request.txt` — зашифрованный конверт `ANREQ1`, подписанный приватным ключом устройства из `DeviceKeyStore`.
 * - `profile.reg` может быть legacy-формата `ANREG1` для одного профиля/устройства или текущим общим bundle `ANREG2` фиксированного размера.
 * - успешная проверка создаёт `LicenseSession`; от неё зависят все UI/network/auto guard-проверки.
 */
public final class LicenseManager {
    private static final String TAG = "LicenseManager";
    private static final String CHAIN = "ANCLIENT_LICENSE";
    private static final String APP_ID = "ru.neverlands.anclient";
    private static final String REQUEST_PREFIX = "ANREQ1:";
    private static final String LICENSE_PREFIX = "ANREG1:";
    private static final String LICENSE_BUNDLE_PREFIX = "ANREG2:";
    private static final String PROFILE_REG = "profile.reg";
    private static final String REQUEST_TXT = "request.txt";
    private static final String BUNDLE_ROOT_CHAIN = "ROOT";
    private static final String LICENSE_STATE_PREFS = "anclient_license_state";

    private LicenseManager() {
    }

    public static LicenseStatus validateOrCreateRequest(Context context, String profileName) {
        return validateOrCreateRequestInternal(context, profileName, null);
    }

    public static LicenseStatus validateOrCreateRequest(Context context, UserConfig profile) {
        return validateOrCreateRequestInternal(context, profile == null ? "" : profile.UserNick, profile);
    }

    public static LicenseStatus createRequest(Context context, UserConfig profile) {
        return createRequestInternal(context, profile == null ? "" : profile.UserNick, profile);
    }

    private static LicenseStatus createRequestInternal(Context context, String profileName, UserConfig profileDiagnostics) {
        if (context == null) {
            return LicenseStatus.blocked(
                    "Ошибка лицензирования",
                    "Не удалось создать request.txt: отсутствует Context.",
                    "",
                    ""
            );
        }

        String profileNick = normalizeNickIdentity(profileName);
        String profileDirName = normalizeProfileDirName(profileNick);
        File infoRoot = resolveInfoRoot(context);
        File profileDir = resolveProfileDir(context, profileDirName);
        File requestFile = new File(profileDir, REQUEST_TXT);
        File profileLicenseFile = new File(profileDir, PROFILE_REG);
        File sharedLicenseFile = new File(infoRoot, PROFILE_REG);
        File licenseFile = profileLicenseFile.exists() ? profileLicenseFile : sharedLicenseFile;

        try {
            ensureDir(profileDir);
            KeyPair deviceKeyPair = DeviceKeyStore.getOrCreate(context);
            DeviceFingerprintProvider.DeviceFingerprint fingerprint =
                    DeviceFingerprintProvider.collect(context, deviceKeyPair.getPublic());
            AdminPublicKeys adminPublicKeys = AdminPublicKeys.load(context);
            writeRequestFile(context, requestFile, profileNick, profileDiagnostics, deviceKeyPair, fingerprint, adminPublicKeys);
            return blocked(
                    "Запрос лицензии создан",
                    "request.txt пересоздан для профиля \"" + profileNick + "\". Его можно отправить администратору даже если общий profile.reg уже установлен.",
                    requestFile,
                    licenseFile
            );
        } catch (Exception e) {
            AppLog.e(CHAIN, TAG, "LICENSE_REQUEST_CREATE_ERROR: profile=" + profileNick, e);
            return blocked(
                    "Ошибка лицензирования",
                    "Не удалось создать request.txt: " + safeError(e),
                    requestFile,
                    licenseFile
            );
        }
    }

    private static LicenseStatus validateOrCreateRequestInternal(Context context, String profileName, UserConfig profileDiagnostics) {
        if (context == null) {
            return LicenseStatus.blocked(
                    "Ошибка лицензирования",
                    "Не удалось подготовить или проверить лицензию: отсутствует Context.",
                    "",
                    ""
            );
        }
        String profileNick = normalizeNickIdentity(profileName);
        String profileDirName = normalizeProfileDirName(profileNick);
        // Структура файлов:
        // - `infoRoot/profile.reg` — общий bundle `ANREG2` для всех nick-профилей.
        // - `infoRoot/<profile>/profile.reg` — профильный override / legacy-путь.
        // - `infoRoot/<profile>/request.txt` пересоздаётся только для выбранного nick-профиля.
        File infoRoot = resolveInfoRoot(context);
        File profileDir = resolveProfileDir(context, profileDirName);
        File requestFile = new File(profileDir, REQUEST_TXT);
        File profileLicenseFile = new File(profileDir, PROFILE_REG);
        File sharedLicenseFile = new File(infoRoot, PROFILE_REG);
        File licenseFile = profileLicenseFile.exists() ? profileLicenseFile : sharedLicenseFile;

        try {
            ensureDir(profileDir);
            // Эти зависимости собираются вместе, потому что `app3 issue` подписывает grant
            // относительно тех же значений: `devicePublicKeySha256`, `fingerprintHash`,
            // `packageName` и `appSignatureSha256`.
            KeyPair deviceKeyPair = DeviceKeyStore.getOrCreate(context);
            DeviceFingerprintProvider.DeviceFingerprint fingerprint =
                    DeviceFingerprintProvider.collect(context, deviceKeyPair.getPublic());
            LicenseAppIdentity appIdentity = LicenseAppIdentity.collect(context);
            AdminPublicKeys adminPublicKeys = AdminPublicKeys.load(context);

            if (licenseFile.exists()) {
                LicenseValidationResult validation = validateLicenseFile(
                        context,
                        licenseFile,
                        profileNick,
                        fingerprint,
                        appIdentity,
                        adminPublicKeys
                );
                if (validation.valid) {
                    AppLog.i(CHAIN, TAG, "LICENSE_APPROVED: license valid, profile=" + profileNick);
                    return LicenseStatus.allowed(licenseFile.getAbsolutePath(), validation.session);
                }
                AppLog.w(CHAIN, TAG, "LICENSE_REJECTED: invalid profile.reg, reason=" + validation.message);
                writeRequestFile(context, requestFile, profileNick, profileDiagnostics, deviceKeyPair, fingerprint, adminPublicKeys);
                return blocked(
                        "Лицензия не принята",
                        validation.message + "\n\nОтправьте новый request.txt администратору и замените profile.reg.",
                        requestFile,
                        licenseFile
                );
            }

            writeRequestFile(context, requestFile, profileNick, profileDiagnostics, deviceKeyPair, fingerprint, adminPublicKeys);
            return blocked(
                    "Требуется лицензия",
                    "Для выбранного профиля нет файла profile.reg. Отправьте request.txt администратору и поместите полученный profile.reg в общую папку info или в папку профиля.",
                    requestFile,
                    licenseFile
            );
        } catch (Exception e) {
            AppLog.e(CHAIN, TAG, "LICENSE_ERROR: validation failed", e);
            return blocked(
                    "Ошибка лицензирования",
                    "Не удалось подготовить или проверить лицензию: " + safeError(e),
                    requestFile,
                    licenseFile
            );
        }
    }

    private static void writeRequestFile(Context context,
                                          File requestFile,
                                          String profileName,
                                          UserConfig profileDiagnostics,
                                          KeyPair deviceKeyPair,
                                          DeviceFingerprintProvider.DeviceFingerprint fingerprint,
                                          AdminPublicKeys adminPublicKeys) throws Exception {
        LicenseAppIdentity appIdentity = LicenseAppIdentity.collect(context);
        Map<String, String> payload = new LinkedHashMap<>();
        // `payload` намеренно подписывается до шифрования. `app3` удаляет
        // `payloadSignature`, заново кодирует оставшиеся поля и проверяет подпись
        // через `devicePublicKey`. Это не даёт подменить `profileName`,
        // `fingerprintHash` или `appSignatureSha256` внутри request.txt.
        payload.put("version", "1");
        payload.put("requestId", UUID.randomUUID().toString());
        payload.put("profileName", profileName);
        payload.put("appId", APP_ID);
        payload.put("createdAt", String.valueOf(System.currentTimeMillis()));
        payload.putAll(appIdentity.requestFields);
        payload.putAll(fingerprint.requestFields);
        appendProfileDiagnostics(payload, profileDiagnostics);

        String unsignedPayload = LicensePayloadCodec.encode(payload);
        String payloadSignature = LicenseCrypto.sign(
                deviceKeyPair.getPrivate(),
                unsignedPayload.getBytes(StandardCharsets.UTF_8)
        );
        payload.put("payloadSignature", payloadSignature);

        String signedPayload = LicensePayloadCodec.encode(payload);
        String envelope = LicenseCrypto.encryptRequest(signedPayload, adminPublicKeys.requestEncryptionPublicKey);
        writeUtf8(requestFile, REQUEST_PREFIX + LicenseCrypto.base64Url(envelope.getBytes(StandardCharsets.UTF_8)));
        AppLog.i(CHAIN, TAG, "LICENSE_REQUEST_CREATED: profile=" + profileName
                + ", path=" + requestFile.getAbsolutePath());
    }

    private static void appendProfileDiagnostics(Map<String, String> payload, UserConfig profile) {
        if (payload == null || profile == null) {
            return;
        }
        // Эти поля нужны только администратору для диагностики авторизации/proxy у удалённого
        // пользователя. Они зашифрованы внутри ANREQ1 и не участвуют в выдаче/проверке лицензии.
        payload.put("diagProfileId", safe(profile.id));
        payload.put("diagProfileEncrypted", String.valueOf(profile.isEncrypted));
        payload.put("diagUserPassword", safe(profile.UserPassword));
        payload.put("diagFlashPassword", safe(profile.UserPasswordFlash));
        payload.put("diagProxyEnabled", String.valueOf(profile.UseProxy || profile.DoProxy));
        payload.put("diagProxyAddress", safe(profile.ProxyAddress));
        payload.put("diagProxyUserName", safe(profile.ProxyUserName));
        payload.put("diagProxyPassword", safe(profile.ProxyPassword));
    }

    private static LicenseValidationResult validateLicenseFile(Context context,
                                                               File licenseFile,
                                                               String profileName,
                                                               DeviceFingerprintProvider.DeviceFingerprint fingerprint,
                                                               LicenseAppIdentity appIdentity,
                                                               AdminPublicKeys adminPublicKeys) {
        try {
            String content = readUtf8(licenseFile).trim();
            if (content.startsWith(LICENSE_BUNDLE_PREFIX)) {
                return validateBundleLicenseContent(
                        licenseFile,
                        content,
                        context,
                        profileName,
                        fingerprint,
                        appIdentity,
                        adminPublicKeys
                );
            }
            if (!content.startsWith(LICENSE_PREFIX)) {
                return LicenseValidationResult.invalid("Неверный формат profile.reg");
            }
            String body = content.substring(LICENSE_PREFIX.length()).trim();
            int split = body.indexOf('.');
            if (split <= 0 || split >= body.length() - 1) {
                return LicenseValidationResult.invalid("Неверная структура profile.reg");
            }
            byte[] payloadBytes = LicenseCrypto.base64UrlDecode(body.substring(0, split));
            String signature = body.substring(split + 1);
            if (!LicenseCrypto.verify(adminPublicKeys.signingPublicKey, payloadBytes, signature)) {
                return LicenseValidationResult.invalid("Подпись profile.reg недействительна");
            }

            String payloadText = new String(payloadBytes, StandardCharsets.UTF_8);
            Map<String, String> payload = LicensePayloadCodec.decode(payloadText);
            // Legacy `ANREG1` использует строгую привязку: каждое protected-поле относится
            // к одному профилю и одному устройству. `ANREG2` ниже ослабляет только путь
            // public-функций, но не device-bound grants.
            if (!"1".equals(payload.get("version"))) {
                return LicenseValidationResult.invalid("Неподдерживаемая версия лицензии");
            }
            if (!APP_ID.equals(payload.get("appId"))) {
                return LicenseValidationResult.invalid("Лицензия выписана для другого приложения");
            }
            if (!appIdentity.packageName.equals(payload.get("packageName"))) {
                return LicenseValidationResult.invalid("Лицензия выписана для другого packageName");
            }
            if (!appIdentity.appSignatureSha256.equals(payload.get("appSignatureSha256"))) {
                return LicenseValidationResult.invalid("Лицензия выписана для другой подписи APK");
            }
            if (!isSameNickOrLegacySafe(profileName, payload.get("profileName"))) {
                return LicenseValidationResult.invalid("Лицензия выписана для другого профиля");
            }
            if (!fingerprint.devicePublicKeySha256.equals(payload.get("devicePublicKeySha256"))) {
                return LicenseValidationResult.invalid("Лицензия выписана для другого ключа устройства");
            }
            if (!fingerprint.fingerprintHash.equals(payload.get("fingerprintHash"))) {
                return LicenseValidationResult.invalid("Отпечаток устройства не совпадает");
            }
            String expiresAt = payload.get("expiresAt");
            if (expiresAt != null && !expiresAt.trim().isEmpty() && !"0".equals(expiresAt.trim())) {
                long expires = Long.parseLong(expiresAt.trim());
                if (expires > 0L && System.currentTimeMillis() > expires) {
                    return LicenseValidationResult.invalid("Срок действия лицензии истек");
                }
            }
            String featureSpec = payload.get("features");
            Set<String> enabledFeatures = LicenseFeature.expandFeatureSpec(featureSpec);
            String tier = LicenseFeature.deriveTier(featureSpec, enabledFeatures);
            String runtimeNonce = LicenseRuntime.newRuntimeNonce();
            // `capabilityKey` никогда не хранится в profile.reg. Он смешивает подписанные
            // поля лицензии с `runtimeNonce`, поэтому после проверки каждый runtime-guard
            // получает свежий session-token, а stale-копии `LicenseSession` проще отследить в логах.
            String capabilityKey = deriveCapabilityKey(payload, appIdentity, enabledFeatures, runtimeNonce);
            LicenseSession session = new LicenseSession(
                    payload.get("licenseId"),
                    payload.get("requestId"),
                    profileName,
                    licenseFile.getAbsolutePath(),
                    tier,
                    featureSpec,
                    enabledFeatures,
                    parseLong(payload.get("issuedAt"), 0L),
                    parseLong(payload.get("expiresAt"), 0L),
                    fingerprint.devicePublicKeySha256,
                    fingerprint.fingerprintHash,
                    appIdentity.appSignatureSha256,
                    capabilityKey,
                    runtimeNonce
            );
            return LicenseValidationResult.valid(session);
        } catch (Exception e) {
            return LicenseValidationResult.invalid("Ошибка чтения profile.reg: " + safeError(e));
        }
    }

    private static LicenseValidationResult validateBundleLicenseContent(File licenseFile,
                                                                        String content,
                                                                        Context context,
                                                                        String profileName,
                                                                        DeviceFingerprintProvider.DeviceFingerprint fingerprint,
                                                                        LicenseAppIdentity appIdentity,
                                                                        AdminPublicKeys adminPublicKeys) {
        try {
            String body = content.substring(LICENSE_BUNDLE_PREFIX.length()).trim();
            int payloadSplit = body.indexOf('.');
            if (payloadSplit <= 0 || payloadSplit >= body.length() - 1) {
                return LicenseValidationResult.invalid("Неверная структура bundle profile.reg");
            }
            int signatureEnd = body.indexOf('.', payloadSplit + 1);
            if (signatureEnd < 0) {
                signatureEnd = body.length();
            }
            byte[] payloadBytes = LicenseCrypto.base64UrlDecode(body.substring(0, payloadSplit));
            String signature = body.substring(payloadSplit + 1, signatureEnd);
            // Для `ANREG2` хвостовой `.noise` здесь намеренно игнорируется. Авторитетное
            // состояние определяют только `payloadBytes` и `signature`; noise нужен,
            // чтобы `profile.reg` оставался фиксированного размера и не выдавал данные через длину файла.
            if (!LicenseCrypto.verify(adminPublicKeys.signingPublicKey, payloadBytes, signature)) {
                return LicenseValidationResult.invalid("Подпись bundle profile.reg недействительна");
            }

            Map<String, String> payload = LicensePayloadCodec.decode(new String(payloadBytes, StandardCharsets.UTF_8));
            if (!"2".equals(payload.get("version"))) {
                return LicenseValidationResult.invalid("Неподдерживаемая версия bundle-лицензии");
            }
            if (!APP_ID.equals(payload.get("appId"))) {
                return LicenseValidationResult.invalid("Bundle-лицензия выписана для другого приложения");
            }
            if (!appIdentity.packageName.equals(payload.get("packageName"))) {
                return LicenseValidationResult.invalid("Bundle-лицензия выписана для другого packageName");
            }
            if (!appIdentity.appSignatureSha256.equals(payload.get("appSignatureSha256"))) {
                return LicenseValidationResult.invalid("Bundle-лицензия выписана для другой подписи APK");
            }
            String expectedChainTip = computeBundleChainTip(payload);
            if (!expectedChainTip.equals(payload.get("chainTip"))) {
                return LicenseValidationResult.invalid("Цепочка bundle-лицензии повреждена");
            }
            // Локальный anti-rollback guard: уже принятый `chainSeq` нельзя заменить
            // более старым подписанным bundle. Это не серверная система отзыва лицензий,
            // но закрывает простой сценарий "вернуть старый profile.reg", пока private data приложения не очищена.
            String rollbackError = verifyAndStoreBundleChainState(context, payload);
            if (!rollbackError.isEmpty()) {
                return LicenseValidationResult.invalid(rollbackError);
            }

            Set<String> publicFeatures = LicenseFeature.expandPublicFeatureSpec(payload.get("publicFeatures"));
            GrantRecord grant = findGrantForProfile(payload.get("grants"), profileName, fingerprint, System.currentTimeMillis());
            // Двухуровневая модель доступа:
            // - `publicFeatures` применяются ко всем профилям, у которых есть этот общий bundle.
            // - `grant` расширяет доступ только если `nickHash`, `devicePublicKeySha256`,
            //   `fingerprintHash` и `expiresAt` валидны для текущего профиля/устройства.
            boolean grantBoundToDevice = grant != null && grant.matchesDevice(fingerprint);
            boolean grantActive = grantBoundToDevice && !grant.isExpired(System.currentTimeMillis());

            LinkedHashSet<String> enabledFeatures = new LinkedHashSet<>(publicFeatures);
            if (grantActive) {
                enabledFeatures.addAll(LicenseFeature.expandFeatureSpec(grant.featureSpec));
            } else if (grant != null && !grantBoundToDevice) {
                AppLog.w(CHAIN, TAG, "LICENSE_GRANT_DEVICE_MISMATCH: profile=" + profileName);
            } else if (grant != null) {
                AppLog.i(CHAIN, TAG, "LICENSE_GRANT_EXPIRED: profile=" + profileName);
            }

            String runtimeNonce = LicenseRuntime.newRuntimeNonce();
            // `featureSpec` оставлен человекочитаемым для логов и диагностики сессии.
            // `enabledFeatures` — фактический runtime-источник истины, который используют guards.
            String featureSpec = grantActive
                    ? value(payload, "publicFeatures") + "+" + grant.featureSpec
                    : value(payload, "publicFeatures");
            String tier = LicenseFeature.deriveTier(featureSpec, enabledFeatures);
            String capabilityKey = deriveBundleCapabilityKey(payload, profileName, enabledFeatures, runtimeNonce, grantActive ? grant : null);
            LicenseSession session = new LicenseSession(
                    payload.get("licenseId"),
                    grantActive ? grant.requestId : value(payload, "licenseId"),
                    profileName,
                    licenseFile.getAbsolutePath(),
                    tier,
                    featureSpec,
                    enabledFeatures,
                    parseLong(payload.get("issuedAt"), 0L),
                    grantActive ? grant.expiresAt : 0L,
                    grantActive ? grant.devicePublicKeySha256 : "",
                    grantActive ? grant.fingerprintHash : "",
                    appIdentity.appSignatureSha256,
                    capabilityKey,
                    runtimeNonce
            );
            return LicenseValidationResult.valid(session);
        } catch (Exception e) {
            return LicenseValidationResult.invalid("Ошибка чтения bundle profile.reg: " + safeError(e));
        }
    }

    private static String deriveCapabilityKey(Map<String, String> payload,
                                               LicenseAppIdentity appIdentity,
                                               Set<String> enabledFeatures,
                                              String runtimeNonce) throws Exception {
        String source = "ANCLIENT_LICENSE_CAPABILITY_V1\n"
                + value(payload, "licenseId") + '\n'
                + value(payload, "requestId") + '\n'
                + value(payload, "profileName") + '\n'
                + value(payload, "appId") + '\n'
                + value(payload, "packageName") + '\n'
                + appIdentity.appSignatureSha256 + '\n'
                + value(payload, "devicePublicKeySha256") + '\n'
                + value(payload, "fingerprintHash") + '\n'
                + value(payload, "expiresAt") + '\n'
                + enabledFeatures.toString() + '\n'
                + runtimeNonce;
        return LicenseCrypto.sha256Base64Url(source);
    }

    private static String deriveBundleCapabilityKey(Map<String, String> payload,
                                                    String profileName,
                                                    Set<String> enabledFeatures,
                                                    String runtimeNonce,
                                                    GrantRecord grant) throws Exception {
        // Включаем состояние bundle, не зависящее от grant (`licenseId`, `chainSeq`, `chainTip`),
        // и поля device-bound grant (`nickHash`, `requestId`, `devicePublicKeySha256`,
        // `fingerprintHash`). Так по логам/диагностике сессии видно, какая версия bundle
        // и какой nick-grant породили `LicenseSession.capabilityKey`.
        String source = "ANCLIENT_LICENSE_CAPABILITY_V2\n"
                + value(payload, "licenseId") + '\n'
                + value(payload, "chainSeq") + '\n'
                + value(payload, "chainTip") + '\n'
                + normalizeNickForHash(profileName) + '\n'
                + (grant == null ? "" : grant.nickHash) + '\n'
                + (grant == null ? "" : grant.requestId) + '\n'
                + (grant == null ? "0" : String.valueOf(grant.expiresAt)) + '\n'
                + (grant == null ? "" : grant.devicePublicKeySha256) + '\n'
                + (grant == null ? "" : grant.fingerprintHash) + '\n'
                + enabledFeatures.toString() + '\n'
                + runtimeNonce;
        return LicenseCrypto.sha256Base64Url(source);
    }

    private static String computeBundleChainTip(Map<String, String> payload) throws Exception {
        // Chain-hash намеренно покрывает `publicFeatures`, `slotCapacity` и SHA-256 всей
        // строки `grants`. Любое изменение timeout/features у grant меняет `chainTip`,
        // а fixed-size noise остаётся вне подписанного бизнес-состояния.
        String source = "ANCLIENT_LICENSE_CHAIN_V2\n"
                + value(payload, "prevChainTip") + '\n'
                + value(payload, "chainSeq") + '\n'
                + value(payload, "licenseId") + '\n'
                + value(payload, "appId") + '\n'
                + value(payload, "packageName") + '\n'
                + value(payload, "appSignatureSha256") + '\n'
                + value(payload, "publicFeatures") + '\n'
                + value(payload, "slotCapacity") + '\n'
                + LicenseCrypto.sha256Base64Url(value(payload, "grants")) + '\n';
        return LicenseCrypto.sha256Base64Url(source);
    }

    private static String verifyAndStoreBundleChainState(Context context, Map<String, String> payload) {
        if (context == null) {
            return "Не удалось проверить anti-rollback состояние bundle-лицензии";
        }
        long chainSeq = parseLong(payload.get("chainSeq"), 0L);
        String licenseId = value(payload, "licenseId");
        String chainTip = value(payload, "chainTip");
        if (licenseId.isEmpty() || chainSeq <= 0L || chainTip.isEmpty()) {
            return "Bundle-лицензия не содержит chain state";
        }
        String stateKey = "bundle_" + licenseId;
        SharedPreferences preferences = context.getSharedPreferences(LICENSE_STATE_PREFS, Context.MODE_PRIVATE);
        // Сохранённые переменные намеренно ключуются по `licenseId`, а не по nick профиля:
        // один bundle `ANREG2` может обслуживать несколько nick, поэтому rollback-state
        // должен быть общим для самого bundle (`stateKey_seq`, `stateKey_tip`).
        long storedSeq = preferences.getLong(stateKey + "_seq", 0L);
        String storedTip = preferences.getString(stateKey + "_tip", "");
        if (storedSeq > chainSeq) {
            AppLog.w(CHAIN, TAG, "LICENSE_ROLLBACK_REJECTED: storedSeq=" + storedSeq + ", fileSeq=" + chainSeq);
            return "Обнаружен откат bundle-лицензии на старую версию";
        }
        if (storedSeq == chainSeq && storedSeq > 0L && storedTip != null && !storedTip.isEmpty() && !storedTip.equals(chainTip)) {
            AppLog.w(CHAIN, TAG, "LICENSE_CHAIN_TIP_REJECTED: same seq with different tip");
            return "Bundle-лицензия конфликтует с уже принятым chain-tip";
        }
        if (chainSeq > storedSeq || storedTip == null || storedTip.isEmpty()) {
            preferences.edit()
                    .putLong(stateKey + "_seq", chainSeq)
                    .putString(stateKey + "_tip", chainTip)
                    .apply();
            AppLog.i(CHAIN, TAG, "LICENSE_CHAIN_STATE_STORED: seq=" + chainSeq);
        }
        return "";
    }

    private static GrantRecord findGrantForProfile(String grants,
                                                   String profileName,
                                                   DeviceFingerprintProvider.DeviceFingerprint fingerprint,
                                                   long nowMs) throws Exception {
        String profileHash = nickHash(profileName);
        String legacyProfileHash = legacyNickHash(profileName);
        String value = grants == null ? "" : grants.trim();
        if (value.isEmpty()) {
            return null;
        }
        GrantRecord firstNickGrant = null;
        GrantRecord firstExpiredDeviceGrant = null;
        String[] lines = value.split("\\r?\\n");
        for (String line : lines) {
            String safeLine = line == null ? "" : line.trim();
            if (safeLine.isEmpty()) {
                continue;
            }
            String[] parts = safeLine.split("\\|", -1);
            if (parts.length < 7) {
                continue;
            }
            String nickHash = parts[0].trim();
            if (!profileHash.equals(nickHash) && !legacyProfileHash.equals(nickHash)) {
                continue;
            }
            // Формат строки grant создаётся в app3 через `encodeGrantRecords(...)`:
            // nickHash|expiresAt|featureSpec|requestId|devicePublicKeySha256|grantId|updatedAt|fingerprintHash
            // Старые 7-колоночные строки допускаются для миграции, но `matchesDevice(...)`
            // пропускает проверку fingerprint только если 8-я колонка отсутствует.
            GrantRecord record = new GrantRecord(
                    nickHash,
                    parseLong(parts[1], 0L),
                    parts[2],
                    parts[3],
                    parts[4],
                    parts[5],
                    parseLong(parts[6], 0L),
                    parts.length >= 8 ? parts[7] : ""
            );
            if (firstNickGrant == null) {
                firstNickGrant = record;
            }
            if (!record.matchesDevice(fingerprint)) {
                continue;
            }
            if (!record.isExpired(nowMs)) {
                return record;
            }
            if (firstExpiredDeviceGrant == null) {
                firstExpiredDeviceGrant = record;
            }
        }
        return firstExpiredDeviceGrant != null ? firstExpiredDeviceGrant : firstNickGrant;
    }

    private static String nickHash(String profileName) throws Exception {
        return LicenseCrypto.sha256Base64Url(normalizeNickForHash(profileName));
    }

    private static String legacyNickHash(String profileName) throws Exception {
        return LicenseCrypto.sha256Base64Url(normalizeLegacyNickForHash(profileName));
    }

    private static String normalizeNickForHash(String profileName) {
        return normalizeNickIdentity(profileName).toLowerCase(Locale.ROOT);
    }

    private static String normalizeLegacyNickForHash(String profileName) {
        return normalizeProfileDirName(profileName).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isSameNickOrLegacySafe(String expectedNick, String actualNick) {
        if (normalizeNickIdentity(expectedNick).equals(normalizeNickIdentity(actualNick))) {
            return true;
        }
        return normalizeLegacyNickForHash(expectedNick).equals(normalizeLegacyNickForHash(actualNick));
    }

    private static String normalizeNickIdentity(String profileName) {
        String value = profileName == null ? "" : profileName.trim();
        value = Normalizer.normalize(value, Normalizer.Form.NFC);
        return value.isEmpty() ? "profile" : value;
    }

    private static File resolveInfoRoot(Context context) {
        File root = context.getExternalFilesDir(null);
        if (root == null) {
            AppLog.w(CHAIN, TAG, "LICENSE_STORAGE_FALLBACK: external files dir unavailable");
            root = context.getFilesDir();
        }
        return new File(root, "info");
    }

    private static File resolveProfileDir(Context context, String profileName) {
        return new File(resolveInfoRoot(context), profileName);
    }

    private static String normalizeProfileDirName(String profileName) {
        String value = normalizeNickIdentity(profileName);
        // Это только имя папки. Nick identity и `nickHash` выше сохраняют спецсимволы ника.
        value = value.replaceAll("[\\\\/:*?\"<>|]", "_");
        return value.isEmpty() ? "profile" : value;
    }

    private static void ensureDir(File dir) throws Exception {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create directory: " + dir.getAbsolutePath());
        }
    }

    private static LicenseStatus blocked(String title, String message, File requestFile, File licenseFile) {
        return LicenseStatus.blocked(
                title,
                message,
                requestFile == null ? "" : requestFile.getAbsolutePath(),
                licenseFile == null ? "" : licenseFile.getAbsolutePath()
        );
    }

    private static long parseLong(String value, long fallback) {
        try {
            String safeValue = value == null ? "" : value.trim();
            return safeValue.isEmpty() ? fallback : Long.parseLong(safeValue);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String value(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null ? "" : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static void writeUtf8(File file, String value) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            ensureDir(parent);
        }
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readUtf8(File file) throws Exception {
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream input = new FileInputStream(file)) {
            int read = input.read(data);
            if (read < data.length) {
                throw new IllegalStateException("Short read: " + file.getName());
            }
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static String safeError(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : message;
    }

    private static final class GrantRecord {
        final String nickHash;
        final long expiresAt;
        final String featureSpec;
        final String requestId;
        final String devicePublicKeySha256;
        final String grantId;
        final long updatedAt;
        final String fingerprintHash;

        GrantRecord(String nickHash,
                    long expiresAt,
                    String featureSpec,
                    String requestId,
                    String devicePublicKeySha256,
                    String grantId,
                    long updatedAt,
                    String fingerprintHash) {
            this.nickHash = nickHash == null ? "" : nickHash.trim();
            this.expiresAt = expiresAt;
            this.featureSpec = featureSpec == null ? "" : featureSpec.trim();
            this.requestId = requestId == null ? "" : requestId.trim();
            this.devicePublicKeySha256 = devicePublicKeySha256 == null ? "" : devicePublicKeySha256.trim();
            this.grantId = grantId == null ? "" : grantId.trim();
            this.updatedAt = updatedAt;
            this.fingerprintHash = fingerprintHash == null ? "" : fingerprintHash.trim();
        }

        boolean isExpired(long nowMs) {
            return expiresAt > 0L && nowMs > expiresAt;
        }

        boolean matchesDevice(DeviceFingerprintProvider.DeviceFingerprint fingerprint) {
            // Full/custom grant-выдачи не должны переноситься только по nick. `devicePublicKeySha256`
            // привязывает grant к публичному ключу Android Keystore; `fingerprintHash`
            // дополнительно ловит reinstall/profile-copy сценарии, где nick остался тем же.
            if (fingerprint == null) {
                return false;
            }
            if (!devicePublicKeySha256.equals(fingerprint.devicePublicKeySha256)) {
                return false;
            }
            return fingerprintHash.isEmpty() || fingerprintHash.equals(fingerprint.fingerprintHash);
        }
    }

    private static final class LicenseValidationResult {
        final boolean valid;
        final String message;
        final LicenseSession session;

        private LicenseValidationResult(boolean valid, String message, LicenseSession session) {
            this.valid = valid;
            this.message = message == null ? "" : message;
            this.session = session;
        }

        static LicenseValidationResult valid(LicenseSession session) {
            return new LicenseValidationResult(true, "", session);
        }

        static LicenseValidationResult invalid(String message) {
            return new LicenseValidationResult(false, message, null);
        }
    }
}

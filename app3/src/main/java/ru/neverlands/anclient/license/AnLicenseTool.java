package ru.neverlands.anclient.license;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * Offline-инструмент администратора для license-файлов ANClient.
 *
 * Карта для отладки:
 * - `init-keys` создаёт private keys в `app3/keys` и public keys в assets app2.
 * - `decode-request` расшифровывает `ANREQ1 request.txt` и пишет проверенные поля в txt-отчёт.
 * - `issue` расшифровывает/проверяет `ANREQ1 request.txt`, затем создаёт или patch-ит `ANREG2 profile.reg`.
 * - существующие grants сохраняются; обновляется только строка, совпадающая с `nickHash(profileName)`.
 * - `nickHash` сохраняет спецсимволы nick (`!*()$~^_-@`), legacy fallback нужен только для старых grants.
 */
public final class AnLicenseTool {
    private static final String REQUEST_PREFIX = "ANREQ1:";
    private static final String LICENSE_PREFIX = "ANREG1:";
    private static final String LICENSE_BUNDLE_PREFIX = "ANREG2:";
    private static final String PROFILE_NAME_INDEX = "profileNameIndex";
    private static final String SIGN_PRIVATE = "admin_sign_private.pkcs8";
    private static final String SIGN_PUBLIC = "admin_sign_public.x509";
    private static final String REQUEST_PRIVATE = "admin_request_private.pkcs8";
    private static final String REQUEST_PUBLIC = "admin_request_public.x509";
    private static final String APP_ID = "ru.neverlands.anclient";
    private static final int GCM_TAG_BITS = 128;
    private static final int FIXED_LICENSE_BYTES = 5 * 1024 * 1024;
    private static final int SLOT_CAPACITY = 10_000;
    private static final String BUNDLE_ROOT_CHAIN = "ROOT";
    private static final String FEATURE_ANTI_CAPTCHA = "anti_captcha";
    private static final String FEATURE_AUTO_CUT = "auto_cut";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss zzz");
    private static final String[] REQUEST_DUMP_FIELDS = {
            "profileName",
            "requestId",
            "devicePublicKey",
            "devicePublicKeySha256",
            "fingerprintHash",
            "packageName",
            "appSignatureSha256",
            "diagProfileId",
            "diagProfileEncrypted",
            "diagUserPassword",
            "diagFlashPassword",
            "diagProxyEnabled",
            "diagProxyAddress",
            "diagProxyUserName",
            "diagProxyPassword",
            "payloadSignature"
    };
    private static final SecureRandom RANDOM = new SecureRandom();

    private AnLicenseTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }
        String command = args[0].trim().toLowerCase();
        File root = resolveRoot();
        if ("init-keys".equals(command)) {
            boolean force = args.length > 1 && "--force".equalsIgnoreCase(args[1]);
            initKeys(root, force);
            return;
        }
        if ("decode-request".equals(command)) {
            if (args.length < 2) {
                throw new IllegalArgumentException("decode-request requires request.txt path");
            }
            File requestFile = new File(args[1]);
            File outputFile = args.length >= 3 ? new File(args[2]) : null;
            decodeRequest(root, requestFile, outputFile);
            return;
        }
        if ("inspect-license".equals(command) || "decode-license".equals(command)) {
            File licenseFile = args.length >= 2
                    ? new File(args[1])
                    : new File(root, "app3/request/profile.reg");
            File requestLookupDir = args.length >= 3
                    ? new File(args[2])
                    : null;
            inspectLicense(root, licenseFile, requestLookupDir);
            return;
        }
        if ("issue".equals(command)) {
            if (args.length < 2) {
                throw new IllegalArgumentException("issue requires request.txt path");
            }
            File requestFile = new File(args[1]);
            File outputFile = args.length >= 3
                    ? new File(args[2])
                    : new File(requestFile.getParentFile(), "profile.reg");
            String expiresAt = args.length >= 4 ? args[3] : "0";
            String grantFeatureSpec = args.length >= 5 ? args[4] : "full";
            String publicFeatureSpec = args.length >= 6 ? args[5] : null;
            issue(root, requestFile, outputFile, expiresAt, grantFeatureSpec, publicFeatureSpec);
            return;
        }
        printUsage();
    }

    private static void printUsage() {
        System.out.println("ANClient: инструмент выпуска лицензий");
        System.out.println("Команды:");
        System.out.println("  init-keys [--force]");
        System.out.println("  decode-request <заявка от устройства> [отчёт по заявке]");
        System.out.println("  inspect-license [файл лицензии] [legacy-папка с заявками]");
        System.out.println("  issue <заявка от устройства> [файл лицензии] [срок] [доступ ника] [общий доступ]");
        System.out.println("Существующий ANREG2 profile.reg обновляется на месте: старые ники сохраняются, выбранный ник получает новый срок/набор функций.");
        System.out.println("Важно: общий доступ (publicFeatures) всегда очищается от anti_captcha и auto_cut; Авто-Травник выдаётся только individual full/custom grant.");
    }

    private static void initKeys(File root, boolean force) throws Exception {
        File keysDir = new File(root, "app3/keys");
        if (!keysDir.exists() && !keysDir.mkdirs()) {
            throw new IllegalStateException("Cannot create keys dir: " + keysDir.getAbsolutePath());
        }
        File signPrivate = new File(keysDir, SIGN_PRIVATE);
        File signPublic = new File(keysDir, SIGN_PUBLIC);
        File requestPrivate = new File(keysDir, REQUEST_PRIVATE);
        File requestPublic = new File(keysDir, REQUEST_PUBLIC);

        if (force || !signPrivate.exists() || !signPublic.exists()) {
            KeyPair pair = generateRsaPair();
            writeString(signPrivate, base64Url(pair.getPrivate().getEncoded()));
            writeString(signPublic, base64Url(pair.getPublic().getEncoded()));
        }
        if (force || !requestPrivate.exists() || !requestPublic.exists()) {
            KeyPair pair = generateRsaPair();
            writeString(requestPrivate, base64Url(pair.getPrivate().getEncoded()));
            writeString(requestPublic, base64Url(pair.getPublic().getEncoded()));
        }

        File assetDir = new File(root, "app2/src/main/assets");
        if (!assetDir.exists() && !assetDir.mkdirs()) {
            throw new IllegalStateException("Cannot create assets dir: " + assetDir.getAbsolutePath());
        }
        Properties properties = new Properties();
        properties.setProperty("signingPublicKey", readString(signPublic).trim());
        properties.setProperty("requestEncryptionPublicKey", readString(requestPublic).trim());
        File publicKeys = new File(assetDir, "license_public_keys.properties");
        try (FileOutputStream output = new FileOutputStream(publicKeys, false)) {
            properties.store(output, "ANClient public license keys. Private keys stay in app3/keys/.");
        }
        System.out.println("Keys ready: " + keysDir.getAbsolutePath());
        System.out.println("Public asset written: " + publicKeys.getAbsolutePath());
    }

    private static void issue(File root,
                              File requestFile,
                              File outputFile,
                              String expiresAt,
                              String grantFeatureSpec,
                              String publicFeatureSpec) throws Exception {
        File keysDir = new File(root, "app3/keys");
        PrivateKey signingPrivate = readPrivateKey(new File(keysDir, SIGN_PRIVATE));
        PublicKey signingPublic = readPublicKey(new File(keysDir, SIGN_PUBLIC));
        PrivateKey requestPrivate = readPrivateKey(new File(keysDir, REQUEST_PRIVATE));
        PublicKey requestPublic = readPublicKey(new File(keysDir, REQUEST_PUBLIC));

        Map<String, String> requestPayload = readVerifiedRequest(requestFile, requestPrivate);
        // `license` — mutable payload state. Если `outputFile` уже существует, patch-им его in-place;
        // иначе создаём начальный bundle `ANREG2`. Все последующие шаги обновляют эту map
        // и заново подписывают её через `signingPrivate`.
        Map<String, String> license = outputFile.exists()
                ? readVerifiedBundle(outputFile, signingPublic)
                : createBundle(requestPayload, publicFeatureSpec);

        // `packageName` и `appSignatureSha256` привязывают общий bundle к одной линейке APK.
        // Без этой проверки request.txt от другой сборки мог бы загрязнить список grants.
        ensureBundleMatchesRequest(license, requestPayload);
        if (publicFeatureSpec != null && !publicFeatureSpec.trim().isEmpty()) {
            // Public-набор применяется ко всем пользователям bundle. Поэтому перед записью он проходит
            // `normalizePublicFeatureSpec(...)`, где non-public automation (`anti_captcha`, `auto_cut`)
            // удаляется даже из custom CSV. App2 дополнительно повторяет этот guard в LicenseFeature.
            license.put("publicFeatures", normalizePublicFeatureSpec(publicFeatureSpec));
        }

        List<GrantRecord> grants = parseGrantRecords(value(license, "grants"));
        Map<String, NameIndexRecord> profileNameIndex = readProfileNameIndex(license, requestPrivate);
        String normalizedGrantFeatures = normalizeFeatureSpec(grantFeatureSpec);
        boolean noIndividualGrant = isNoGrant(normalizedGrantFeatures);
        long grantExpiresAt = noIndividualGrant ? 0L : resolveExpiresAtMillis(expiresAt);
        if (!noIndividualGrant) {
            // Upsert использует переменные из request: `profileName` -> `nickHash`, `requestId`,
            // `devicePublicKeySha256`, `fingerprintHash`, плюс заданные администратором `expiresAt`
            // и `grantFeatureSpec`. Grants других пользователей не трогаются.
            GrantRecord changedGrant = upsertGrant(grants, requestPayload, grantExpiresAt, normalizedGrantFeatures);
            upsertProfileNameIndex(profileNameIndex, changedGrant, requestPayload);
            license.put(PROFILE_NAME_INDEX, encryptProfileNameIndex(profileNameIndex, requestPublic));
        }

        finalizeBundle(license, grants);
        writeBundle(outputFile, license, signingPrivate);

        System.out.println("Файл лицензии обновлён для ника: " + required(requestPayload, "profileName"));
        System.out.println("Общий доступ для всех: " + describeFeatureSpec(license.get("publicFeatures"), true));
        System.out.println("Индивидуальный доступ ника: " + (noIndividualGrant ? "не создавался" : describeFeatureSpec(normalizedGrantFeatures, false)));
        System.out.println("Срок индивидуального доступа: " + describeIssuedGrantExpiry(noIndividualGrant, grantExpiresAt));
        System.out.println("Количество индивидуальных записей: " + license.get("grantCount") + " / " + license.get("slotCapacity"));
        System.out.println("Размер файла: " + FIXED_LICENSE_BYTES + " байт");
        System.out.println("Версия цепочки изменений: " + license.get("chainSeq"));
        System.out.println("Контрольный hash цепочки: " + license.get("chainTip"));
        System.out.println("Готовый файл: " + outputFile.getAbsolutePath());
    }

    private static void decodeRequest(File root,
                                      File requestFile,
                                      File outputFile) throws Exception {
        File keysDir = new File(root, "app3/keys");
        PrivateKey requestPrivate = readPrivateKey(new File(keysDir, REQUEST_PRIVATE));
        Map<String, String> requestPayload = readVerifiedRequest(requestFile, requestPrivate);
        File resolvedOutputFile = outputFile == null
                ? defaultRequestDumpFile(requestFile, requestPayload)
                : outputFile;

        StringBuilder builder = new StringBuilder();
        for (String field : REQUEST_DUMP_FIELDS) {
            builder.append(field)
                    .append('=')
                    .append(value(requestPayload, field))
                    .append('\n');
        }
        writeString(resolvedOutputFile, builder.toString());

        System.out.println("Заявка прочитана для ника: " + required(requestPayload, "profileName"));
        System.out.println("Отчёт сохранён: " + resolvedOutputFile.getAbsolutePath());
    }

    private static File defaultRequestDumpFile(File requestFile, Map<String, String> requestPayload) {
        File parent = requestFile == null ? null : requestFile.getParentFile();
        if (parent == null) {
            parent = new File(".");
        }
        String profileName = safeFileName(value(requestPayload, "profileName"));
        String deviceHash = safeFileName(value(requestPayload, "devicePublicKeySha256"));
        if (profileName.isEmpty()) {
            profileName = "profile";
        }
        if (deviceHash.isEmpty()) {
            deviceHash = "device";
        }
        return new File(parent, profileName + "_" + deviceHash + ".txt");
    }

    private static void inspectLicense(File root,
                                       File licenseFile,
                                       File requestLookupDir) throws Exception {
        File keysDir = new File(root, "app3/keys");
        PublicKey signingPublic = readPublicKey(new File(keysDir, SIGN_PUBLIC));
        PrivateKey requestPrivate = readPrivateKey(new File(keysDir, REQUEST_PRIVATE));
        Map<String, String> license = readVerifiedBundle(licenseFile, signingPublic);
        Map<String, RequestInfo> requestIndex = loadRequestIndex(requestLookupDir, requestPrivate);
        requestIndex.putAll(loadProfileNameIndex(license, requestPrivate));
        List<GrantRecord> grants = parseGrantRecords(value(license, "grants"));
        long now = System.currentTimeMillis();

        System.out.println("==============================================================");
        System.out.println("Проверка текущего profile.reg");
        System.out.println("==============================================================");
        System.out.println("Файл: " + licenseFile.getAbsolutePath());
        System.out.println("Размер: " + licenseFile.length() + " байт" + (licenseFile.length() == FIXED_LICENSE_BYTES ? " (OK, 5 MiB)" : " (ожидалось 5 MiB)"));
        System.out.println("Формат: ANREG2");
        System.out.println("Подпись администратора: OK");
        System.out.println("Цепочка изменений: OK, версия " + value(license, "chainSeq"));
        System.out.println("ID лицензии: " + value(license, "licenseId"));
        System.out.println("Приложение: " + value(license, "packageName"));
        System.out.println("Подпись APK: " + value(license, "appSignatureSha256"));
        System.out.println("Создано: " + formatDate(parseLong(value(license, "issuedAt"), 0L)));
        System.out.println("Обновлено: " + formatDate(parseLong(value(license, "updatedAt"), 0L)));
        System.out.println("Сейчас: " + formatDate(now));
        System.out.println();

        String publicFeatures = value(license, "publicFeatures");
        System.out.println("Общедоступные функции для всех профилей с этим profile.reg:");
        System.out.println("  Набор: " + describeFeatureSpec(publicFeatures, true));
        System.out.println("  Срок: без ограничения по времени");
        System.out.println();

        System.out.println("Индивидуальные доступы по никам:");
        if (grants.isEmpty()) {
            System.out.println("  Нет индивидуальных записей. Работают только общедоступные функции.");
        } else {
            int index = 1;
            for (GrantRecord grant : grants) {
                RequestInfo requestInfo = findRequestInfo(requestIndex, grant);
                printGrantReport(index++, grant, requestInfo, now);
            }
        }
        System.out.println();
        System.out.println("Найдено источников имён (встроенный индекс/заявки): " + uniqueRequestCount(requestIndex));
        System.out.println("Если вместо ника показан hash, старый profile.reg не содержит индекса; переиздайте его или положите рядом request.txt/отчёт заявки.");
    }

    private static Map<String, RequestInfo> loadRequestIndex(File requestLookupDir,
                                                             PrivateKey requestPrivate) {
        Map<String, RequestInfo> result = new LinkedHashMap<>();
        if (requestLookupDir == null || !requestLookupDir.exists()) {
            return result;
        }
        List<File> files = new ArrayList<>();
        collectRequestLookupFiles(requestLookupDir, files);
        for (File file : files) {
            try {
                Map<String, String> payload;
                String name = file.getName().toLowerCase(Locale.ROOT);
                if ("request.txt".equals(name)) {
                    payload = readVerifiedRequest(file, requestPrivate);
                } else {
                    payload = readRequestDump(file);
                }
                RequestInfo info = RequestInfo.from(file, payload);
                if (!info.profileName.isEmpty()) {
                    result.put(grantLookupKey(nickHash(info.profileName), info.devicePublicKeySha256), info);
                    result.put(grantLookupKey(legacyNickHash(info.profileName), info.devicePublicKeySha256), info);
                }
                if (!info.requestId.isEmpty()) {
                    result.put("request:" + info.requestId, info);
                }
            } catch (Exception e) {
                System.out.println("Предупреждение: заявка пропущена, " + file.getAbsolutePath() + ": " + safeError(e));
            }
        }
        return result;
    }

    private static Map<String, RequestInfo> loadProfileNameIndex(Map<String, String> license,
                                                                 PrivateKey requestPrivate) {
        Map<String, RequestInfo> result = new LinkedHashMap<>();
        Map<String, NameIndexRecord> nameIndex = readProfileNameIndex(license, requestPrivate);
        for (NameIndexRecord record : nameIndex.values()) {
            RequestInfo info = RequestInfo.fromNameIndex(record);
            result.put(grantLookupKey(record.nickHash, record.devicePublicKeySha256), info);
            if (!record.requestId.isEmpty()) {
                result.put("request:" + record.requestId, info);
            }
        }
        return result;
    }

    private static Map<String, NameIndexRecord> readProfileNameIndex(Map<String, String> license,
                                                                     PrivateKey requestPrivate) {
        Map<String, NameIndexRecord> result = new LinkedHashMap<>();
        String encryptedIndex = value(license, PROFILE_NAME_INDEX);
        if (encryptedIndex.isEmpty()) {
            return result;
        }
        try {
            String indexPayload = decryptEnvelope(decodePayload(encryptedIndex), requestPrivate);
            String[] lines = indexPayload.split("\\r?\\n");
            for (String line : lines) {
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\\|", -1);
                if (parts.length < 6) {
                    continue;
                }
                NameIndexRecord record = new NameIndexRecord(
                        parts[0],
                        parts[1],
                        parts[2],
                        parts[3],
                        parseLong(parts[4], 0L),
                        new String(base64UrlDecode(parts[5]), StandardCharsets.UTF_8)
                );
                result.put(grantLookupKey(record.nickHash, record.devicePublicKeySha256), record);
            }
        } catch (Exception e) {
            System.out.println("Предупреждение: встроенный индекс ников не прочитан: " + safeError(e));
        }
        return result;
    }

    private static void upsertProfileNameIndex(Map<String, NameIndexRecord> profileNameIndex,
                                               GrantRecord grant,
                                               Map<String, String> requestPayload) {
        NameIndexRecord record = new NameIndexRecord(
                grant.nickHash,
                grant.devicePublicKeySha256,
                grant.fingerprintHash,
                grant.requestId,
                grant.updatedAt,
                required(requestPayload, "profileName")
        );
        profileNameIndex.put(grantLookupKey(record.nickHash, record.devicePublicKeySha256), record);
    }

    private static String encryptProfileNameIndex(Map<String, NameIndexRecord> profileNameIndex,
                                                  PublicKey requestPublic) throws Exception {
        StringBuilder builder = new StringBuilder();
        List<NameIndexRecord> records = new ArrayList<>(profileNameIndex.values());
        Collections.sort(records, Comparator
                .comparing((NameIndexRecord record) -> record.nickHash)
                .thenComparing(record -> record.devicePublicKeySha256));
        for (NameIndexRecord record : records) {
            if (record.profileName.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(record.nickHash)
                    .append('|')
                    .append(record.devicePublicKeySha256)
                    .append('|')
                    .append(record.fingerprintHash)
                    .append('|')
                    .append(record.requestId)
                    .append('|')
                    .append(record.updatedAt)
                    .append('|')
                    .append(base64Url(record.profileName.getBytes(StandardCharsets.UTF_8)));
        }
        return encryptAdminText(builder.toString(), requestPublic);
    }

    private static String grantLookupKey(String nickHash, String devicePublicKeySha256) {
        return (nickHash == null ? "" : nickHash.trim())
                + '|'
                + (devicePublicKeySha256 == null ? "" : devicePublicKeySha256.trim());
    }

    private static void collectRequestLookupFiles(File file, List<File> result) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isFile()) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if ("request.txt".equals(name) || name.endsWith(".txt")) {
                result.add(file);
            }
            return;
        }
        File[] children = file.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            collectRequestLookupFiles(child, result);
        }
    }

    private static Map<String, String> readRequestDump(File file) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        String[] lines = readString(file).split("\\r?\\n");
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            int split = line.indexOf('=');
            if (split <= 0) {
                continue;
            }
            result.put(line.substring(0, split).trim(), line.substring(split + 1).trim());
        }
        return result;
    }

    private static RequestInfo findRequestInfo(Map<String, RequestInfo> requestIndex, GrantRecord grant) {
        RequestInfo byNickAndDevice = requestIndex.get(grantLookupKey(grant.nickHash, grant.devicePublicKeySha256));
        if (byNickAndDevice != null) {
            return byNickAndDevice;
        }
        return requestIndex.get("request:" + grant.requestId);
    }

    private static void printGrantReport(int index,
                                         GrantRecord grant,
                                         RequestInfo requestInfo,
                                         long now) {
        boolean expired = grant.expiresAt > 0L && now > grant.expiresAt;
        boolean deviceMatches = requestInfo != null && grant.devicePublicKeySha256.equals(requestInfo.devicePublicKeySha256);
        boolean fingerprintMatches = requestInfo != null
                && (grant.fingerprintHash.isEmpty() || grant.fingerprintHash.equals(requestInfo.fingerprintHash));
        System.out.println("  [" + index + "] " + (requestInfo == null ? "Ник не найден во встроенном индексе/заявках" : "Ник: " + requestInfo.profileName));
        System.out.println("      Доступ: " + describeFeatureSpec(grant.featureSpec, false));
        System.out.println("      Срок: " + describeGrantExpiry(grant.expiresAt, now));
        System.out.println("      Статус: " + (expired ? "истёк, останутся только общедоступные функции" : "активен"));
        System.out.println("      ID заявки: " + emptyFallback(grant.requestId, "нет"));
        System.out.println("      Привязка к устройству: " + describeBinding(requestInfo, deviceMatches, fingerprintMatches));
        if (requestInfo == null) {
            System.out.println("      Hash ника: " + grant.nickHash);
        }
        System.out.println("      Обновлено: " + formatDate(grant.updatedAt));
    }

    private static String describeBinding(RequestInfo requestInfo,
                                          boolean deviceMatches,
                                          boolean fingerprintMatches) {
        if (requestInfo == null) {
            return "есть в файле лицензии, но нет встроенного индекса/request.txt для сверки ника";
        }
        if ("profile.reg:index".equals(requestInfo.sourcePath)) {
            return deviceMatches && fingerprintMatches
                    ? "совпадает со встроенным индексом profile.reg"
                    : "НЕ совпадает со встроенным индексом profile.reg";
        }
        if (deviceMatches && fingerprintMatches) {
            return "совпадает с найденной заявкой";
        }
        return "НЕ совпадает с найденной заявкой";
    }

    private static String describeGrantExpiry(long expiresAt, long now) {
        if (expiresAt <= 0L) {
            return "без ограничения по времени";
        }
        long diff = expiresAt - now;
        if (diff > 0L) {
            return "до " + formatDate(expiresAt) + " (осталось " + formatDuration(diff) + ")";
        }
        return "истёк " + formatDate(expiresAt) + " (прошло " + formatDuration(-diff) + ")";
    }

    private static String describeFeatureSpec(String featureSpec, boolean publicFeatures) {
        String value = normalizeFeatureSpecForReport(featureSpec, publicFeatures);
        if (value.isEmpty() || "none".equals(value) || "off".equals(value) || "empty".equals(value)) {
            return "нет доступа";
        }
        if ("full".equals(value)) {
            if (publicFeatures) {
                return "полный public-набор без Anti-Captcha и Авто-Травника: все быстрые/авто-функции и clans, кроме anti_captcha и auto_cut";
            }
            return "полный набор: все быстрые/авто-функции, Anti-Captcha, Авто-Травник и clans";
        }
        if ("limited".equals(value) || "free".equals(value) || "basic".equals(value)) {
            return "базовый набор: Авто-Бой, Авто-Рыбалка, Авто-Охота, Навигатор, Компас, Быстрые действия, Таймеры, Контакты, Кланы, Статистика, PINFO; Anti-Captcha и Авто-Травник не входят";
        }
        String[] parts = value.split("[,;|\\s]+");
        StringBuilder builder = new StringBuilder("выборочный набор: ");
        boolean first = true;
        for (String part : parts) {
            String token = normalize(part);
            if (token.isEmpty()) {
                continue;
            }
            if (!first) {
                builder.append(", ");
            }
            builder.append(describeFeatureToken(token));
            first = false;
        }
        return first ? "нет доступа" : builder.toString();
    }

    private static String describeFeatureToken(String token) {
        if ("auto_fight".equals(token)) return "Авто-Бой (auto_fight)";
        if ("quick_actions".equals(token)) return "Быстрые действия (quick_actions)";
        if ("auto_fish".equals(token)) return "Авто-Рыбалка (auto_fish)";
        if ("auto_bait".equals(token)) return "Авто-Приманка (auto_bait)";
        if ("auto_attack".equals(token)) return "Авто-Нападение (auto_attack)";
        if ("auto_compass".equals(token)) return "Авто-Компас (auto_compass)";
        if ("auto_boss".equals(token)) return "Авто-Боссы (auto_boss)";
        if ("auto_invisible".equals(token)) return "Авто-Невид (auto_invisible)";
        if ("location_tracking".equals(token)) return "Слежение за локацией (location_tracking)";
        if ("auto_detect".equals(token)) return "Авто-Обнаружение (auto_detect)";
        if ("auto_summon".equals(token)) return "Авто-Тотем (auto_summon)";
        if ("auto_cure".equals(token)) return "Авто-Лечение (auto_cure)";
        if ("auto_drink".equals(token)) return "Авто-Питьё (auto_drink)";
        if ("auto_moving".equals(token)) return "Навигатор (auto_moving)";
        if ("auto_treasure".equals(token)) return "Авто-Клад (auto_treasure)";
        if (FEATURE_AUTO_CUT.equals(token)) return "Авто-Травник (auto_cut, только full/custom grant)";
        if ("auto_refresh".equals(token)) return "Авто-Обновление (auto_refresh)";
        if (FEATURE_ANTI_CAPTCHA.equals(token)) return "Анти-Captcha (anti_captcha, только full/custom grant)";
        if ("auto_skin".equals(token)) return "Авто-Охота (auto_skin)";
        if ("open_contacts".equals(token)) return "Открыть контакты (open_contacts)";
        if ("open_pinfo".equals(token)) return "Открыть PINFO (open_pinfo)";
        if ("open_logs".equals(token)) return "Открыть логи (open_logs)";
        if ("open_stats".equals(token)) return "Статистика (open_stats)";
        if ("timers".equals(token)) return "Таймеры (timers)";
        if ("refresh_contacts".equals(token)) return "Обновить контакты (refresh_contacts)";
        if ("quick_self_rass".equals(token)) return "Рассеять невид (quick_self_rass)";
        if ("quick_open_nevid".equals(token)) return "Обнаружение (quick_open_nevid)";
        if ("quick_teleport".equals(token)) return "Телепорт (quick_teleport)";
        if ("quick_island".equals(token)) return "Остров Туротор (quick_island)";
        if ("quick_totem".equals(token)) return "Тотем (quick_totem)";
        if ("quick_elixir_blaz".equals(token)) return "Эликсир Блаженства (quick_elixir_blaz)";
        if ("quick_elixir_cure".equals(token)) return "Эликсир Исцеления (quick_elixir_cure)";
        if ("quick_elixir_restore".equals(token)) return "Эликсир Восстановления (quick_elixir_restore)";
        if ("clans".equals(token)) return "Кланы (clans)";
        return token;
    }

    private static String normalizeFeatureSpecForReport(String featureSpec, boolean publicFeatures) {
        String value = normalize(featureSpec);
        if (publicFeatures) {
            if (value.isEmpty() || "none".equals(value) || "off".equals(value) || "empty".equals(value)) {
                return "none";
            }
            if ("free".equals(value) || "basic".equals(value)) {
                return "limited";
            }
            if ("full".equals(value)) {
                return "full";
            }
            return removeNonPublicFeatureTokens(value);
        }
        return normalizeFeatureSpec(featureSpec);
    }

    private static int uniqueRequestCount(Map<String, RequestInfo> requestIndex) {
        List<String> identities = new ArrayList<>();
        for (RequestInfo info : requestIndex.values()) {
            String identity = info.profileName + '|' + info.requestId;
            if (!identities.contains(identity)) {
                identities.add(identity);
            }
        }
        return identities.size();
    }

    private static String formatDate(long value) {
        if (value <= 0L) {
            return "не задано";
        }
        return Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DATE_FORMAT);
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long days = totalSeconds / 86_400L;
        totalSeconds %= 86_400L;
        long hours = totalSeconds / 3_600L;
        totalSeconds %= 3_600L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        StringBuilder builder = new StringBuilder();
        appendDurationPart(builder, days, "д");
        appendDurationPart(builder, hours, "ч");
        appendDurationPart(builder, minutes, "мин");
        if (builder.length() == 0 || seconds > 0L) {
            appendDurationPart(builder, seconds, "сек");
        }
        return builder.toString();
    }

    private static void appendDurationPart(StringBuilder builder, long value, String unit) {
        if (value <= 0L) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(value).append(' ').append(unit);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String emptyFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String safeError(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : message;
    }

    private static Map<String, String> readVerifiedRequest(File requestFile, PrivateKey requestPrivate) throws Exception {
        String request = normalizeArmoredRequest(readString(requestFile));
        if (!request.startsWith(REQUEST_PREFIX)) {
            throw new IllegalArgumentException("Invalid request prefix");
        }
        String envelopeText = new String(base64UrlDecode(request.substring(REQUEST_PREFIX.length())), StandardCharsets.UTF_8);
        Map<String, String> envelope = decodePayload(envelopeText);
        String payloadText = decryptEnvelope(envelope, requestPrivate);
        Map<String, String> requestPayload = decodePayload(payloadText);

        String payloadSignature = requestPayload.remove("payloadSignature");
        if (payloadSignature == null || payloadSignature.trim().isEmpty()) {
            throw new IllegalArgumentException("request payloadSignature missing");
        }
        String unsignedPayload = encodePayload(requestPayload);
        PublicKey devicePublicKey = readPublicKeyFromBase64(requestPayload.get("devicePublicKey"));
        // Проверяем device-side подпись после расшифровки admin envelope. Это привязывает
        // `profileName`, `fingerprintHash`, `devicePublicKeySha256`, `packageName` и
        // `appSignatureSha256` к Android Keystore ключу, который создал request.txt.
        if (!verify(devicePublicKey, unsignedPayload.getBytes(StandardCharsets.UTF_8), payloadSignature)) {
            throw new IllegalArgumentException("request payload signature invalid");
        }
        requestPayload.put("payloadSignature", payloadSignature);
        return requestPayload;
    }

    private static String normalizeArmoredRequest(String rawRequest) {
        String value = rawRequest == null ? "" : rawRequest.trim();
        if (!value.startsWith(REQUEST_PREFIX)) {
            return value;
        }
        String body = value.substring(REQUEST_PREFIX.length()).replaceAll("\\s+", "");
        return REQUEST_PREFIX + body;
    }

    private static Map<String, String> createBundle(Map<String, String> requestPayload,
                                                    String publicFeatureSpec) {
        long now = System.currentTimeMillis();
        Map<String, String> license = new LinkedHashMap<>();
        // Начальное состояние bundle. `chainSeq=0` не принимается app2, пока `finalizeBundle(...)`
        // не увеличит его до 1 и не рассчитает `chainTip` по public features + grants.
        license.put("version", "2");
        license.put("licenseId", UUID.randomUUID().toString());
        license.put("appId", required(requestPayload, "appId"));
        license.put("packageName", required(requestPayload, "packageName"));
        license.put("appVersion", value(requestPayload, "appVersion"));
        license.put("appSignatureSha256", required(requestPayload, "appSignatureSha256"));
        license.put("issuedAt", String.valueOf(now));
        license.put("updatedAt", String.valueOf(now));
        license.put("publicFeatures", normalizePublicFeatureSpec(publicFeatureSpec));
        license.put("slotCapacity", String.valueOf(SLOT_CAPACITY));
        license.put("grantCount", "0");
        license.put("grants", "");
        license.put("chainSeq", "0");
        license.put("prevChainTip", BUNDLE_ROOT_CHAIN);
        license.put("chainTip", "");
        return license;
    }

    private static Map<String, String> readVerifiedBundle(File outputFile, PublicKey signingPublic) throws Exception {
        String content = readString(outputFile).trim();
        if (content.startsWith(LICENSE_PREFIX)) {
            throw new IllegalArgumentException("Existing profile.reg uses legacy ANREG1 format; create a new ANREG2 bundle file instead.");
        }
        if (!content.startsWith(LICENSE_BUNDLE_PREFIX)) {
            throw new IllegalArgumentException("Existing profile.reg has invalid bundle prefix");
        }
        String body = content.substring(LICENSE_BUNDLE_PREFIX.length()).trim();
        int payloadSplit = body.indexOf('.');
        if (payloadSplit <= 0 || payloadSplit >= body.length() - 1) {
            throw new IllegalArgumentException("Invalid bundle structure");
        }
        int signatureEnd = body.indexOf('.', payloadSplit + 1);
        if (signatureEnd < 0) {
            signatureEnd = body.length();
        }
        byte[] payloadBytes = base64UrlDecode(body.substring(0, payloadSplit));
        String signature = body.substring(payloadSplit + 1, signatureEnd);
        // Игнорируем `.noise` после `signatureEnd`. Noise — только fixed-size padding;
        // бизнес-состояние определяют только подписанный payload и signature.
        if (!verify(signingPublic, payloadBytes, signature)) {
            throw new IllegalArgumentException("Existing bundle signature invalid");
        }
        Map<String, String> license = decodePayload(new String(payloadBytes, StandardCharsets.UTF_8));
        if (!"2".equals(license.get("version"))) {
            throw new IllegalArgumentException("Unsupported bundle version");
        }
        String expectedChainTip = computeBundleChainTip(license);
        if (!expectedChainTip.equals(license.get("chainTip"))) {
            throw new IllegalArgumentException("Existing bundle chain is broken");
        }
        return license;
    }

    private static void ensureBundleMatchesRequest(Map<String, String> license,
                                                   Map<String, String> requestPayload) {
        if (!APP_ID.equals(value(license, "appId")) || !APP_ID.equals(required(requestPayload, "appId"))) {
            throw new IllegalArgumentException("appId mismatch");
        }
        requireEqual(license, requestPayload, "packageName");
        requireEqual(license, requestPayload, "appSignatureSha256");
    }

    private static void requireEqual(Map<String, String> license,
                                     Map<String, String> requestPayload,
                                     String key) {
        String licenseValue = required(license, key);
        String requestValue = required(requestPayload, key);
        if (!licenseValue.equals(requestValue)) {
            throw new IllegalArgumentException("Bundle " + key + " mismatch");
        }
    }

    private static List<GrantRecord> parseGrantRecords(String grantsText) {
        List<GrantRecord> grants = new ArrayList<>();
        String value = grantsText == null ? "" : grantsText.trim();
        if (value.isEmpty()) {
            return grants;
        }
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
            grants.add(new GrantRecord(
                    parts[0],
                    parseLong(parts[1], 0L),
                    parts[2],
                    parts[3],
                    parts[4],
                    parts[5],
                    parseLong(parts[6], 0L),
                    parts.length >= 8 ? parts[7] : ""
            ));
        }
        return grants;
    }

    private static GrantRecord upsertGrant(List<GrantRecord> grants,
                                           Map<String, String> requestPayload,
                                           long expires,
                                           String grantFeatureSpec) throws Exception {
        String profileName = required(requestPayload, "profileName");
        String nickHash = nickHash(profileName);
        String legacyNickHash = legacyNickHash(profileName);
        String devicePublicKeySha256 = required(requestPayload, "devicePublicKeySha256");
        // Строка grant привязана к устройству, а не только к nick. Сторона app2 сравнивает
        // `devicePublicKeySha256` и `fingerprintHash` перед добавлением grant features.
        long now = System.currentTimeMillis();
        for (int i = 0; i < grants.size(); i++) {
            GrantRecord existing = grants.get(i);
            if (!nickHash.equals(existing.nickHash) && !legacyNickHash.equals(existing.nickHash)) {
                continue;
            }
            if (!devicePublicKeySha256.equals(existing.devicePublicKeySha256)) {
                continue;
            }
            GrantRecord updated = new GrantRecord(
                    nickHash,
                    expires,
                    grantFeatureSpec,
                    required(requestPayload, "requestId"),
                    devicePublicKeySha256,
                    existing.grantId.isEmpty() ? UUID.randomUUID().toString() : existing.grantId,
                    now,
                    required(requestPayload, "fingerprintHash")
            );
            grants.set(i, updated);
            return updated;
        }
        if (grants.size() >= SLOT_CAPACITY) {
            throw new IllegalStateException("Bundle grant capacity exceeded: " + SLOT_CAPACITY);
        }
        GrantRecord added = new GrantRecord(
                nickHash,
                expires,
                grantFeatureSpec,
                required(requestPayload, "requestId"),
                devicePublicKeySha256,
                UUID.randomUUID().toString(),
                now,
                required(requestPayload, "fingerprintHash")
        );
        grants.add(added);
        return added;
    }

    private static void finalizeBundle(Map<String, String> license, List<GrantRecord> grants) throws Exception {
        // Стабильный порядок не даёт случайному порядку grants менять `chainTip` для того же состояния.
        // На signed payload должны влиять только реальные изменения grants/publicFeatures/chainSeq.
        Collections.sort(grants, Comparator
                .comparing((GrantRecord record) -> record.nickHash)
                .thenComparing(record -> record.devicePublicKeySha256)
                .thenComparing(record -> record.fingerprintHash));
        license.put("grants", encodeGrantRecords(grants));
        license.put("grantCount", String.valueOf(grants.size()));
        license.put("slotCapacity", String.valueOf(SLOT_CAPACITY));
        license.put("updatedAt", String.valueOf(System.currentTimeMillis()));
        license.put("prevChainTip", value(license, "chainTip").isEmpty()
                ? BUNDLE_ROOT_CHAIN
                : value(license, "chainTip"));
        license.put("chainSeq", String.valueOf(parseLong(value(license, "chainSeq"), 0L) + 1L));
        license.put("chainTip", computeBundleChainTip(license));
    }

    private static String encodeGrantRecords(List<GrantRecord> grants) {
        StringBuilder builder = new StringBuilder();
        for (GrantRecord grant : grants) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(grant.nickHash)
                    .append('|')
                    .append(grant.expiresAt)
                    .append('|')
                    .append(normalizeFeatureSpec(grant.featureSpec))
                    .append('|')
                    .append(grant.requestId)
                    .append('|')
                    .append(grant.devicePublicKeySha256)
                    .append('|')
                    .append(grant.grantId)
                    .append('|')
                    .append(grant.updatedAt)
                    .append('|')
                    .append(grant.fingerprintHash);
        }
        return builder.toString();
    }

    private static void writeBundle(File outputFile,
                                    Map<String, String> license,
                                    PrivateKey signingPrivate) throws Exception {
        String licensePayload = encodePayload(license);
        String adminSignature = sign(signingPrivate, licensePayload.getBytes(StandardCharsets.UTF_8));
        String signedBody = LICENSE_BUNDLE_PREFIX
                + base64Url(licensePayload.getBytes(StandardCharsets.UTF_8))
                + "."
                + adminSignature;
        if (signedBody.length() + 1 > FIXED_LICENSE_BYTES) {
            throw new IllegalStateException("Bundle payload exceeds fixed profile.reg size. Grants="
                    + license.get("grantCount") + ", bytes=" + signedBody.length());
        }
        int noiseLength = FIXED_LICENSE_BYTES - signedBody.length() - 1;
        // Держим длину файла фиксированной, но при каждом patch создаём свежие байты.
        // Hash файла меняется, зато file size и leakage по capacity остаются постоянными.
        writeString(outputFile, signedBody + "." + randomNoise(noiseLength));
    }

    private static String computeBundleChainTip(Map<String, String> license) throws Exception {
        // Должно точно совпадать с app2 `LicenseManager.computeBundleChainTip(...)`.
        // Любое поле, добавленное здесь, должно быть добавлено в Android verifier в том же порядке.
        String source = "ANCLIENT_LICENSE_CHAIN_V2\n"
                + value(license, "prevChainTip") + '\n'
                + value(license, "chainSeq") + '\n'
                + value(license, "licenseId") + '\n'
                + value(license, "appId") + '\n'
                + value(license, "packageName") + '\n'
                + value(license, "appSignatureSha256") + '\n'
                + value(license, "publicFeatures") + '\n'
                + value(license, "slotCapacity") + '\n'
                + sha256Base64Url(value(license, "grants")) + '\n';
        return sha256Base64Url(source);
    }

    private static String nickHash(String profileName) throws Exception {
        return sha256Base64Url(normalizeNickForHash(profileName));
    }

    private static String legacyNickHash(String profileName) throws Exception {
        return sha256Base64Url(normalizeLegacyNickForHash(profileName));
    }

    private static String normalizeNickForHash(String profileName) {
        String value = profileName == null ? "" : profileName.trim();
        value = Normalizer.normalize(value, Normalizer.Form.NFC);
        return (value.isEmpty() ? "profile" : value).toLowerCase(Locale.ROOT);
    }

    private static String normalizeLegacyNickForHash(String profileName) {
        String value = profileName == null ? "" : profileName.trim();
        value = Normalizer.normalize(value, Normalizer.Form.NFC);
        value = value.replaceAll("[\\\\/:*?\"<>|]", "_");
        return (value.isEmpty() ? "profile" : value).toLowerCase(Locale.ROOT);
    }

    private static String safeFileName(String value) {
        String safeValue = value == null ? "" : value.trim();
        safeValue = Normalizer.normalize(safeValue, Normalizer.Form.NFC);
        safeValue = safeValue.replaceAll("[\\\\/:*?\"<>|]", "_");
        safeValue = safeValue.replaceAll("\\s+", "_");
        while (safeValue.startsWith(".")) {
            safeValue = safeValue.substring(1);
        }
        return safeValue;
    }

    private static String randomNoise(int length) {
        StringBuilder builder = new StringBuilder(length);
        while (builder.length() < length) {
            byte[] bytes = new byte[Math.max(32, length - builder.length())];
            RANDOM.nextBytes(bytes);
            builder.append(base64Url(bytes));
        }
        return builder.substring(0, length);
    }

    private static String normalizeFeatureSpec(String featureSpec) {
        String value = featureSpec == null ? "" : featureSpec.trim().toLowerCase();
        if (value.isEmpty()) {
            return "full";
        }
        if ("free".equals(value) || "basic".equals(value)) {
            return "limited";
        }
        return value;
    }

    private static String normalizePublicFeatureSpec(String featureSpec) {
        String value = featureSpec == null ? "" : featureSpec.trim().toLowerCase();
        if (value.isEmpty() || "free".equals(value) || "basic".equals(value)) {
            return "limited";
        }
        if ("off".equals(value) || "empty".equals(value)) {
            return "none";
        }
        if ("full".equals(value)) {
            // Public full остаётся каноническим словом `full`, но сторона app2 при чтении
            // ANREG2.publicFeatures вырезает non-public tokens `anti_captcha` и `auto_cut`.
            // В отчёте выше это также описывается как public full без Anti-Captcha/Авто-Травника.
            return "full";
        }
        // Custom public CSV нормализуется здесь, а не в UI app2: license-файл уже должен быть
        // безопасным для любого профиля bundle. Если администратор случайно указал `auto_cut`,
        // токен будет удалён и сможет попасть в app2 только из device-bound grant.
        return removeNonPublicFeatureTokens(value);
    }

    private static String removeNonPublicFeatureTokens(String featureSpec) {
        String[] parts = featureSpec == null ? new String[0] : featureSpec.split("[,;|\\s]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            String token = normalize(part);
            if (token.isEmpty() || "none".equals(token) || "off".equals(token) || "empty".equals(token)) {
                continue;
            }
            if (FEATURE_ANTI_CAPTCHA.equals(token) || FEATURE_AUTO_CUT.equals(token)) {
                // Anti-Captcha и AutoCut не должны попадать в общий bundle.
                // Для них использовать индивидуальный full grant или custom grants
                // `anti_captcha`/`auto_cut` с нужным сроком действия.
                // Зависимость app2: `LicenseManager` сначала разворачивает `publicFeatures`,
                // затем добавляет только активный grant; после истечения grant AutoCut будет выключен
                // через `AutoFunctionsManager.disableUnavailableFeatures(...)`.
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(token);
        }
        if (builder.length() == 0) {
            return "none";
        }
        return builder.toString();
    }

    private static boolean isNoGrant(String featureSpec) {
        String value = featureSpec == null ? "" : featureSpec.trim().toLowerCase();
        return "none".equals(value) || "off".equals(value) || "empty".equals(value) || "public-only".equals(value);
    }

    private static String describeIssuedGrantExpiry(boolean noIndividualGrant, long expiresAt) {
        if (noIndividualGrant) {
            return "не используется, потому что индивидуальный доступ не создавался";
        }
        if (expiresAt <= 0L) {
            return "без ограничения по времени";
        }
        return "до " + formatDate(expiresAt) + " (" + expiresAt + ")";
    }

    private static long resolveExpiresAtMillis(String expiresAt) {
        String value = expiresAt == null ? "" : expiresAt.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()
                || "0".equals(value)
                || "never".equals(value)
                || "none".equals(value)
                || "unlimited".equals(value)
                || "forever".equals(value)) {
            return 0L;
        }
        if (value.matches("\\d+")) {
            long absoluteMillis = Long.parseLong(value);
            if (absoluteMillis > 0L && absoluteMillis < 1_000_000_000_000L) {
                throw new IllegalArgumentException("expiresAt number must be Unix epoch milliseconds. Use duration like 10m, 2h, 7d for relative time.");
            }
            return absoluteMillis;
        }

        long multiplier;
        String amountText;
        if (value.endsWith("ms")) {
            multiplier = 1L;
            amountText = value.substring(0, value.length() - 2);
        } else if (value.endsWith("min")) {
            multiplier = 60_000L;
            amountText = value.substring(0, value.length() - 3);
        } else if (value.endsWith("m")) {
            multiplier = 60_000L;
            amountText = value.substring(0, value.length() - 1);
        } else if (value.endsWith("h")) {
            multiplier = 60L * 60_000L;
            amountText = value.substring(0, value.length() - 1);
        } else if (value.endsWith("d")) {
            multiplier = 24L * 60L * 60_000L;
            amountText = value.substring(0, value.length() - 1);
        } else if (value.endsWith("s")) {
            multiplier = 1_000L;
            amountText = value.substring(0, value.length() - 1);
        } else {
            throw new IllegalArgumentException("Unsupported expiresAt format: " + expiresAt + ". Use 0, epoch millis, 10m, 2h, 7d.");
        }

        long amount = Long.parseLong(amountText.trim());
        if (amount <= 0L) {
            throw new IllegalArgumentException("expiresAt duration must be positive: " + expiresAt);
        }
        return Math.addExact(System.currentTimeMillis(), Math.multiplyExact(amount, multiplier));
    }

    private static String deriveLicenseTier(String featureSpec) {
        String value = normalizeFeatureSpec(featureSpec);
        if ("full".equals(value)) {
            return "full";
        }
        if ("limited".equals(value)) {
            return "limited";
        }
        return "custom";
    }

    private static String decryptEnvelope(Map<String, String> envelope, PrivateKey requestPrivate) throws Exception {
        String keyAlg = required(envelope, "keyAlg");
        byte[] encryptedKey = base64UrlDecode(required(envelope, "key"));
        byte[] iv = base64UrlDecode(required(envelope, "iv"));
        byte[] data = base64UrlDecode(required(envelope, "data"));

        Cipher rsa = Cipher.getInstance(keyAlg);
        if (keyAlg.contains("SHA-256")) {
            OAEPParameterSpec spec = new OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    PSource.PSpecified.DEFAULT
            );
            rsa.init(Cipher.DECRYPT_MODE, requestPrivate, spec);
        } else {
            rsa.init(Cipher.DECRYPT_MODE, requestPrivate);
        }
        byte[] aesKey = rsa.doFinal(encryptedKey);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(aes.doFinal(data), StandardCharsets.UTF_8);
    }

    private static String encryptAdminText(String plainText, PublicKey requestPublic) throws Exception {
        byte[] aesKey = new byte[32];
        byte[] iv = new byte[12];
        RANDOM.nextBytes(aesKey);
        RANDOM.nextBytes(iv);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] data = aes.doFinal((plainText == null ? "" : plainText).getBytes(StandardCharsets.UTF_8));

        String keyAlg = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
        Cipher rsa = Cipher.getInstance(keyAlg);
        OAEPParameterSpec spec = new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
        );
        rsa.init(Cipher.ENCRYPT_MODE, requestPublic, spec);

        Map<String, String> envelope = new LinkedHashMap<>();
        envelope.put("keyAlg", keyAlg);
        envelope.put("key", base64Url(rsa.doFinal(aesKey)));
        envelope.put("iv", base64Url(iv));
        envelope.put("data", base64Url(data));
        return encodePayload(envelope);
    }

    private static KeyPair generateRsaPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        return generator.generateKeyPair();
    }

    private static PrivateKey readPrivateKey(File file) throws Exception {
        byte[] data = base64UrlDecode(readString(file).trim());
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(data));
    }

    private static PublicKey readPublicKey(File file) throws Exception {
        byte[] data = base64UrlDecode(readString(file).trim());
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(data));
    }

    private static PublicKey readPublicKeyFromBase64(String value) throws Exception {
        byte[] data = base64UrlDecode(value);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(data));
    }

    private static String sign(PrivateKey privateKey, byte[] payload) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(payload);
        return base64Url(signature.sign());
    }

    private static boolean verify(PublicKey publicKey, byte[] payload, String encodedSignature) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(payload);
        return signature.verify(base64UrlDecode(encodedSignature));
    }

    private static String encodePayload(Map<String, String> values) {
        TreeMap<String, String> sorted = new TreeMap<>();
        if (values != null) {
            sorted.putAll(values);
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            if (key.isEmpty()) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue();
            builder.append(key)
                    .append(':')
                    .append(base64Url(value.getBytes(StandardCharsets.UTF_8)))
                    .append('\n');
        }
        return builder.toString();
    }

    private static Map<String, String> decodePayload(String payload) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        if (payload == null || payload.trim().isEmpty()) {
            return result;
        }
        String[] lines = payload.split("\\n");
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            int split = line.indexOf(':');
            if (split <= 0) {
                throw new IllegalArgumentException("Invalid payload line");
            }
            String key = line.substring(0, split).trim();
            String value = new String(base64UrlDecode(line.substring(split + 1).trim()), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    private static String required(Map<String, String> values, String key) {
        String value = value(values, key);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value;
    }

    private static String value(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null ? "" : value;
    }

    private static long parseLong(String value, long fallback) {
        try {
            String safeValue = value == null ? "" : value.trim();
            return safeValue.isEmpty() ? fallback : Long.parseLong(safeValue);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String readString(File file) throws Exception {
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                int read = input.read(data, offset, data.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            if (offset != data.length) {
                throw new IllegalStateException("Short read: " + file.getAbsolutePath());
            }
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static void writeString(File file, String value) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create directory: " + parent.getAbsolutePath());
        }
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static String sha256Base64Url(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return base64Url(digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
    }

    private static final class RequestInfo {
        final String sourcePath;
        final String profileName;
        final String requestId;
        final String devicePublicKeySha256;
        final String fingerprintHash;

        private RequestInfo(String sourcePath,
                            String profileName,
                            String requestId,
                            String devicePublicKeySha256,
                            String fingerprintHash) {
            this.sourcePath = sourcePath == null ? "" : sourcePath.trim();
            this.profileName = profileName == null ? "" : profileName.trim();
            this.requestId = requestId == null ? "" : requestId.trim();
            this.devicePublicKeySha256 = devicePublicKeySha256 == null ? "" : devicePublicKeySha256.trim();
            this.fingerprintHash = fingerprintHash == null ? "" : fingerprintHash.trim();
        }

        static RequestInfo from(File file, Map<String, String> payload) {
            return new RequestInfo(
                    file == null ? "" : file.getAbsolutePath(),
                    value(payload, "profileName"),
                    value(payload, "requestId"),
                    value(payload, "devicePublicKeySha256"),
                    value(payload, "fingerprintHash")
            );
        }

        static RequestInfo fromNameIndex(NameIndexRecord record) {
            return new RequestInfo(
                    "profile.reg:index",
                    record.profileName,
                    record.requestId,
                    record.devicePublicKeySha256,
                    record.fingerprintHash
            );
        }
    }

    private static final class NameIndexRecord {
        final String nickHash;
        final String devicePublicKeySha256;
        final String fingerprintHash;
        final String requestId;
        final long updatedAt;
        final String profileName;

        NameIndexRecord(String nickHash,
                        String devicePublicKeySha256,
                        String fingerprintHash,
                        String requestId,
                        long updatedAt,
                        String profileName) {
            this.nickHash = nickHash == null ? "" : nickHash.trim();
            this.devicePublicKeySha256 = devicePublicKeySha256 == null ? "" : devicePublicKeySha256.trim();
            this.fingerprintHash = fingerprintHash == null ? "" : fingerprintHash.trim();
            this.requestId = requestId == null ? "" : requestId.trim();
            this.updatedAt = updatedAt;
            this.profileName = profileName == null ? "" : profileName.trim();
        }
    }

    private static final class GrantRecord {
        // Сериализуется как:
        // nickHash|expiresAt|featureSpec|requestId|devicePublicKeySha256|grantId|updatedAt|fingerprintHash
        // `grantId` стабилен между patches для одного nick; `updatedAt` меняется при каждом patch.
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
    }

    private static File resolveRoot() {
        File current = new File(System.getProperty("user.dir")).getAbsoluteFile();
        if (new File(current, "settings.gradle").exists()) {
            return current;
        }
        File parent = current.getParentFile();
        if (parent != null && new File(parent, "settings.gradle").exists()) {
            return parent;
        }
        return current;
    }
}

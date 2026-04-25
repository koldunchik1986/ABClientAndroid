package ru.neverlands.anclient.license;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Формирует hashed-поля устройства/приложения, которые app3 использует в grants.
 *
 * Переменные для отладки:
 * - `devicePublicKeySha256` привязывает grant к ключу Android Keystore.
 * - `fingerprintHash` привязывает grant к стабильным свойствам устройства/build.
 * - raw-идентификаторы не пишутся в profile.reg; дальше передаются только hashes.
 */
final class DeviceFingerprintProvider {
    private DeviceFingerprintProvider() {
    }

    static DeviceFingerprint collect(Context context, PublicKey devicePublicKey) throws Exception {
        String androidId = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
        );
        String manufacturer = safe(Build.MANUFACTURER);
        String brand = safe(Build.BRAND);
        String model = safe(Build.MODEL);
        String device = safe(Build.DEVICE);
        String product = safe(Build.PRODUCT);
        String hardware = safe(Build.HARDWARE);
        String board = safe(Build.BOARD);
        String bootloader = safe(Build.BOOTLOADER);
        String buildFingerprint = safe(Build.FINGERPRINT);
        String serial = resolveSerial();

        // Hash-домены должны оставаться стабильными. app3 копирует `devicePublicKeySha256`
        // и `fingerprintHash` в строки grant; LicenseManager позже сравнивает их в
        // `GrantRecord.matchesDevice(...)` перед включением full/custom features.
        String devicePublicKeySha256 = LicenseCrypto.sha256Base64Url(devicePublicKey.getEncoded());
        String androidIdHash = LicenseCrypto.sha256Base64Url("ANCLIENT_ANDROID_ID_V1\n" + safe(androidId));
        String serialHash = LicenseCrypto.sha256Base64Url("ANCLIENT_SERIAL_V1\n" + safe(serial));
        String buildFingerprintHash = LicenseCrypto.sha256Base64Url("ANCLIENT_BUILD_FP_V1\n" + buildFingerprint);
        String stableSource = "ANCLIENT_DEVICE_FP_V1\n"
                + safe(androidId) + '\n'
                + manufacturer + '\n'
                + brand + '\n'
                + model + '\n'
                + device + '\n'
                + product + '\n'
                + hardware + '\n'
                + board;
        String fingerprintHash = LicenseCrypto.sha256Base64Url(stableSource);

        Map<String, String> details = new LinkedHashMap<>();
        details.put("manufacturer", manufacturer);
        details.put("brand", brand);
        details.put("model", model);
        details.put("device", device);
        details.put("product", product);
        details.put("hardware", hardware);
        details.put("board", board);
        details.put("bootloader", bootloader);
        details.put("sdk", String.valueOf(Build.VERSION.SDK_INT));
        details.put("devicePublicKey", LicenseCrypto.base64Url(devicePublicKey.getEncoded()));
        details.put("devicePublicKeySha256", devicePublicKeySha256);
        details.put("fingerprintHash", fingerprintHash);
        details.put("androidIdHash", androidIdHash);
        details.put("serialHash", serialHash);
        details.put("buildFingerprintHash", buildFingerprintHash);

        return new DeviceFingerprint(
                details,
                devicePublicKeySha256,
                fingerprintHash,
                androidIdHash,
                buildFingerprintHash
        );
    }

    @SuppressWarnings("deprecation")
    private static String resolveSerial() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return "";
        }
        return safe(Build.SERIAL);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    static final class DeviceFingerprint {
        final Map<String, String> requestFields;
        final String devicePublicKeySha256;
        final String fingerprintHash;
        final String androidIdHash;
        final String buildFingerprintHash;

        DeviceFingerprint(Map<String, String> requestFields,
                          String devicePublicKeySha256,
                          String fingerprintHash,
                          String androidIdHash,
                          String buildFingerprintHash) {
            this.requestFields = requestFields;
            this.devicePublicKeySha256 = devicePublicKeySha256;
            this.fingerprintHash = fingerprintHash;
            this.androidIdHash = androidIdHash;
            this.buildFingerprintHash = buildFingerprintHash;
        }
    }
}

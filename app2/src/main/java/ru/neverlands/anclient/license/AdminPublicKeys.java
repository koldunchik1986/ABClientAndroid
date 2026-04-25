package ru.neverlands.anclient.license;

import android.content.Context;

import java.io.InputStream;
import java.security.PublicKey;
import java.util.Properties;

/**
 * Загружает публичные admin-ключи, встроенные в assets app2.
 *
 * Зависимости:
 * - `signingPublicKey` проверяет `profile.reg`, выпущенный через app3.
 * - `requestEncryptionPublicKey` шифрует request.txt так, чтобы прочитать его мог только private key из app3.
 * Private keys должны оставаться только в `app3/keys`.
 */
final class AdminPublicKeys {
    private static final String ASSET_NAME = "license_public_keys.properties";

    final PublicKey signingPublicKey;
    final PublicKey requestEncryptionPublicKey;

    private AdminPublicKeys(PublicKey signingPublicKey, PublicKey requestEncryptionPublicKey) {
        this.signingPublicKey = signingPublicKey;
        this.requestEncryptionPublicKey = requestEncryptionPublicKey;
    }

    static AdminPublicKeys load(Context context) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = context.getAssets().open(ASSET_NAME)) {
            properties.load(input);
        }
        String signing = properties.getProperty("signingPublicKey", "").trim();
        String request = properties.getProperty("requestEncryptionPublicKey", "").trim();
        if (signing.isEmpty() || request.isEmpty()) {
            throw new IllegalStateException("license_public_keys.properties is incomplete");
        }
        return new AdminPublicKeys(
                LicenseCrypto.parseRsaPublicKey(signing),
                LicenseCrypto.parseRsaPublicKey(request)
        );
    }
}

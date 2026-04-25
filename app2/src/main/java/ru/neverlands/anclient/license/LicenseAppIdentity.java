package ru.neverlands.anclient.license;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Собирает app identity поля, которые копируются в payload request/license.
 *
 * `packageName` и `appSignatureSha256` проверяются и в app3, и в app2, чтобы
 * request/profile.reg от другой сборки APK нельзя было незаметно переиспользовать.
 */
final class LicenseAppIdentity {
    final Map<String, String> requestFields;
    final String packageName;
    final String appVersion;
    final String appSignatureSha256;

    private LicenseAppIdentity(Map<String, String> requestFields,
                               String packageName,
                               String appVersion,
                               String appSignatureSha256) {
        this.requestFields = requestFields;
        this.packageName = safe(packageName);
        this.appVersion = safe(appVersion);
        this.appSignatureSha256 = safe(appSignatureSha256);
    }

    static LicenseAppIdentity collect(Context context) throws Exception {
        String packageName = context.getPackageName();
        String appVersion = resolveAppVersion(context, packageName);
        String appSignatureSha256 = resolveSignatureDigest(context, packageName);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("packageName", packageName);
        fields.put("appVersion", appVersion);
        fields.put("appSignatureSha256", appSignatureSha256);
        return new LicenseAppIdentity(fields, packageName, appVersion, appSignatureSha256);
    }

    private static String resolveAppVersion(Context context, String packageName) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Exception ignored) {
            return "";
        }
    }

    @SuppressWarnings("deprecation")
    private static String resolveSignatureDigest(Context context, String packageName) throws Exception {
        PackageManager packageManager = context.getPackageManager();
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageInfo info = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
            );
            if (info.signingInfo == null) {
                signatures = new Signature[0];
            } else if (info.signingInfo.hasMultipleSigners()) {
                signatures = info.signingInfo.getApkContentsSigners();
            } else {
                signatures = info.signingInfo.getSigningCertificateHistory();
            }
        } else {
            PackageInfo info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            signatures = info.signatures;
        }

        List<String> digests = new ArrayList<>();
        if (signatures != null) {
            for (Signature signature : signatures) {
                if (signature != null) {
                    digests.add(LicenseCrypto.sha256Base64Url(signature.toByteArray()));
                }
            }
        }
        // Сортируем перед join, чтобы различия порядка signatures между Android API
        // не ломали проверку лицензии для одного и того же набора signing certificates.
        Collections.sort(digests);
        return join(digests);
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(value.trim());
        }
        return builder.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

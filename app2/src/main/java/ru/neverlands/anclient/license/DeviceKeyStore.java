package ru.neverlands.anclient.license;

import android.content.Context;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Calendar;

import javax.security.auth.x500.X500Principal;

/**
 * Управляет ключом Android Keystore, которым подписываются payload-данные `request.txt`.
 *
 * Стабильность `ALIAS` критична: его изменение создаёт новый публичный ключ устройства,
 * и все существующие full/custom grants, привязанные к `devicePublicKeySha256`, перестают совпадать.
 */
final class DeviceKeyStore {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "anclient_license_device_key_v1";

    private DeviceKeyStore() {
    }

    static KeyPair getOrCreate(Context context) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (!keyStore.containsAlias(ALIAS)) {
            generate(context);
        }
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(ALIAS, null);
        PublicKey publicKey = keyStore.getCertificate(ALIAS).getPublicKey();
        return new KeyPair(publicKey, privateKey);
    }

    private static void generate(Context context) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", ANDROID_KEYSTORE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY
            )
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .build();
            generator.initialize(spec);
        } else {
            initLegacy(generator, context);
        }
        generator.generateKeyPair();
    }

    @SuppressWarnings("deprecation")
    private static void initLegacy(KeyPairGenerator generator, Context context) throws Exception {
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        end.add(Calendar.YEAR, 30);
        KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context.getApplicationContext())
                .setAlias(ALIAS)
                .setSubject(new X500Principal("CN=ANClient License Device Key"))
                .setSerialNumber(BigInteger.ONE)
                .setStartDate(start.getTime())
                .setEndDate(end.getTime())
                .build();
        generator.initialize(spec);
    }
}

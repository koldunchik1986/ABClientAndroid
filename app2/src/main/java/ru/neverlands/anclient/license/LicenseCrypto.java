package ru.neverlands.anclient.license;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * Криптографические helpers, общие для license package в app2.
 *
 * Названия алгоритмов должны быть синхронизированы с app3 `AnLicenseTool`:
 * изменение RSA/GCM/signature строк здесь без аналогичного изменения issuer ломает
 * расшифровку request.txt или проверку profile.reg.
 */
final class LicenseCrypto {
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private LicenseCrypto() {
    }

    static String base64Url(byte[] value) {
        return Base64.encodeToString(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    static byte[] base64UrlDecode(String value) {
        return Base64.decode(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    static String sha256Base64Url(String value) throws Exception {
        return sha256Base64Url(value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256Base64Url(byte[] value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return base64Url(digest.digest(value == null ? new byte[0] : value));
    }

    static PublicKey parseRsaPublicKey(String encoded) throws Exception {
        byte[] data = base64UrlDecode(encoded);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(data));
    }

    static String sign(PrivateKey privateKey, byte[] payload) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(payload);
        return base64Url(signature.sign());
    }

    static boolean verify(PublicKey publicKey, byte[] payload, String encodedSignature) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(payload);
        return signature.verify(base64UrlDecode(encodedSignature));
    }

    static String encryptRequest(String plainPayload, PublicKey requestPublicKey) throws Exception {
        // Гибридное шифрование request: payload шифруется через AES-GCM, а AES key
        // шифруется через RSA для admin `requestEncryptionPublicKey`. app3 расшифровывает это в `decryptEnvelope(...)`.
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        SecretKey aesKey = keyGenerator.generateKey();

        byte[] iv = new byte[GCM_IV_BYTES];
        RANDOM.nextBytes(iv);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] encryptedPayload = aes.doFinal(plainPayload.getBytes(StandardCharsets.UTF_8));

        RsaEncryptedKey encryptedKey = encryptAesKey(aesKey.getEncoded(), requestPublicKey);

        Map<String, String> envelope = new LinkedHashMap<>();
        envelope.put("version", "1");
        envelope.put("payloadAlg", "AES-256-GCM");
        envelope.put("keyAlg", encryptedKey.algorithm);
        envelope.put("key", base64Url(encryptedKey.encryptedKey));
        envelope.put("iv", base64Url(iv));
        envelope.put("data", base64Url(encryptedPayload));
        return LicensePayloadCodec.encode(envelope);
    }

    private static RsaEncryptedKey encryptAesKey(byte[] aesKey, PublicKey requestPublicKey) throws Exception {
        Exception lastError = null;
        // Провайдеры Android отличаются между устройствами/API levels; app3 сохраняет `keyAlg`
        // в envelope и использует ту же строку алгоритма для расшифровки.
        String[] algorithms = new String[] {
                "RSA/ECB/OAEPWithSHA-256AndMGF1Padding",
                "RSA/ECB/OAEPWithSHA-1AndMGF1Padding",
                "RSA/ECB/PKCS1Padding"
        };
        for (String algorithm : algorithms) {
            try {
                Cipher rsa = Cipher.getInstance(algorithm);
                if (algorithm.contains("SHA-256")) {
                    OAEPParameterSpec spec = new OAEPParameterSpec(
                            "SHA-256",
                            "MGF1",
                            MGF1ParameterSpec.SHA256,
                            PSource.PSpecified.DEFAULT
                    );
                    rsa.init(Cipher.ENCRYPT_MODE, requestPublicKey, spec);
                } else {
                    rsa.init(Cipher.ENCRYPT_MODE, requestPublicKey);
                }
                return new RsaEncryptedKey(algorithm, rsa.doFinal(aesKey));
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw lastError == null ? new IllegalStateException("No RSA encryption algorithm") : lastError;
    }

    private static final class RsaEncryptedKey {
        final String algorithm;
        final byte[] encryptedKey;

        RsaEncryptedKey(String algorithm, byte[] encryptedKey) {
            this.algorithm = algorithm;
            this.encryptedKey = encryptedKey;
        }
    }
}

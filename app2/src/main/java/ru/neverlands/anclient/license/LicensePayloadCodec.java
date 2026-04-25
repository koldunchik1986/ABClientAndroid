package ru.neverlands.anclient.license;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Канонический key/value codec для request и license payloads.
 *
 * Отсортированный порядок ключей является частью проверки подписи: app2 подписывает request payloads,
 * app3 заново кодирует ту же map, а подписи profile.reg тоже покрывают именно это кодирование.
 */
final class LicensePayloadCodec {
    private LicensePayloadCodec() {
    }

    static String encode(Map<String, String> values) {
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
                    .append(LicenseCrypto.base64Url(value.getBytes(StandardCharsets.UTF_8)))
                    .append('\n');
        }
        return builder.toString();
    }

    static Map<String, String> decode(String payload) throws Exception {
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
            String encodedValue = line.substring(split + 1).trim();
            String value = new String(LicenseCrypto.base64UrlDecode(encodedValue), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }
}

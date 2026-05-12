package ru.neverlands.anclient.model;

/**
 * Детали предмета казны, найденные в штатной HTML-карточке инвентаря по UID.
 *
 * Зависимости:
 * - `uid` совпадает со ссылкой инвентаря `main.php?get_id=57&uid=...`; для строк
 *   казны UI сначала ищет точное совпадение UID, а затем безопасно сверяет
 *   видимую сигнатуру вещи (название, долговечность, коэффициент);
 * - данные сохраняются отдельно от snapshot казны в
 *   `info/<profile nick>/kazna/uids.txt`, потому что серверная страница казны не
 *   отдаёт полный блок свойств и изображение предмета.
 */
public final class KaznaItemDetails {
    public final String uid;
    public final String name;
    public final String imageUrl;
    public final String propertiesText;
    public final long updatedAtMs;

    public KaznaItemDetails(
            String uid,
            String name,
            String imageUrl,
            String propertiesText,
            long updatedAtMs) {
        this.uid = safe(uid);
        this.name = safe(name);
        this.imageUrl = safe(imageUrl);
        this.propertiesText = safe(propertiesText);
        this.updatedAtMs = updatedAtMs;
    }

    public boolean hasImage() {
        return !imageUrl.isEmpty();
    }

    public boolean hasProperties() {
        return !propertiesText.isEmpty();
    }

    public boolean hasKnownDetails() {
        return hasImage() || hasProperties() || !name.isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

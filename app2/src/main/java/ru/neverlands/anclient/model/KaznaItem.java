package ru.neverlands.anclient.model;

import java.util.Locale;

/**
 * Распарсенная строка предмета из `main.php?useaction=clan-action&addid=3`.
 *
 * Важные поля:
 * - `uid` берётся только из серверного action-link (`get_id=29` или `get_id=18`);
 * - `takeUrl` соответствует кнопке `Взять из казны`;
 * - `donateUrl` соответствует кнопке `Пожертвовать`;
 * - `owner` исторически названо так в модели, но в казне это не владелец вещи,
 *   а текст второй колонки `В-инвентаре`/текущий держатель предмета;
 * - владелец вещи приходит только из блока свойств inventory-card и хранится в
 *   `KaznaItemDetails.propertiesText`, если был распарсен;
 * - `rowHtml` сохраняет исходный серверный HTML строки для диагностики и
 *   будущего HTML-рендера без повторного reverse engineering.
 */
public final class KaznaItem {
    public final String uid;
    public final int rowIndex;
    public final String displayName;
    public final String baseName;
    public final String owner;
    public final String durabilityText;
    public final int currentDurability;
    public final int maxDurability;
    public final String status;
    public final boolean free;
    public final String artifactCoefficient;
    public final String takeUrl;
    public final String donateUrl;
    public final String sourceUrl;
    public final String categoryWca;
    public final String categoryTitle;
    public final String rowHtml;

    public KaznaItem(
            String uid,
            int rowIndex,
            String displayName,
            String baseName,
            String owner,
            String durabilityText,
            int currentDurability,
            int maxDurability,
            String status,
            boolean free,
            String artifactCoefficient,
            String takeUrl,
            String donateUrl,
            String sourceUrl,
            String categoryWca,
            String categoryTitle,
            String rowHtml) {
        this.uid = safe(uid);
        this.rowIndex = rowIndex;
        this.displayName = safe(displayName);
        this.baseName = safe(baseName);
        this.owner = safe(owner);
        this.durabilityText = safe(durabilityText);
        this.currentDurability = currentDurability;
        this.maxDurability = maxDurability;
        this.status = safe(status);
        this.free = free;
        this.artifactCoefficient = safe(artifactCoefficient);
        this.takeUrl = safe(takeUrl);
        this.donateUrl = safe(donateUrl);
        this.sourceUrl = safe(sourceUrl);
        this.categoryWca = safe(categoryWca);
        this.categoryTitle = safe(categoryTitle);
        this.rowHtml = safe(rowHtml);
    }

    public boolean hasUid() {
        return !uid.isEmpty();
    }

    public boolean hasTakeAction() {
        return !takeUrl.isEmpty();
    }

    public boolean hasDonateAction() {
        return !donateUrl.isEmpty();
    }

    public boolean hasArtifactCoefficient() {
        return !artifactCoefficient.isEmpty();
    }

    public boolean isRare() {
        return !hasArtifactCoefficient() && maxDurability >= 300;
    }

    public boolean isOrdinary() {
        return !hasArtifactCoefficient() && maxDurability >= 0 && maxDurability < 300;
    }

    public String stableKey() {
        if (hasUid()) {
            return uid;
        }
        return (rowIndex + "|" + displayName + "|" + owner + "|" + durabilityText).toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

package ru.neverlands.anclient.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Снимок последнего ответа казны.
 *
 * Хранится целиком в `info/<profile nick>/kazna/kazna.txt`, чтобы UI мог показать
 * кеш без сети, а последующие действия (`Собрать`) могли найти актуальные
 * action-link по UID.
 */
public final class KaznaSnapshot {
    public final long generatedAtMs;
    public final String sourceUrl;
    public final String currentWca;
    public final String currentCategoryTitle;
    public final List<KaznaCategory> categories;
    public final List<KaznaItem> items;

    public KaznaSnapshot(
            long generatedAtMs,
            String sourceUrl,
            String currentWca,
            String currentCategoryTitle,
            List<KaznaCategory> categories,
            List<KaznaItem> items) {
        this.generatedAtMs = generatedAtMs;
        this.sourceUrl = sourceUrl == null ? "" : sourceUrl;
        this.currentWca = currentWca == null ? "" : currentWca;
        this.currentCategoryTitle = currentCategoryTitle == null ? "" : currentCategoryTitle;
        this.categories = categories == null ? new ArrayList<>() : new ArrayList<>(categories);
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    public List<KaznaCategory> copyCategories() {
        return new ArrayList<>(categories);
    }

    public List<KaznaItem> copyItems() {
        return new ArrayList<>(items);
    }

    public List<KaznaItem> readonlyItems() {
        return Collections.unmodifiableList(items);
    }

    public KaznaItem findItemByUid(String uid) {
        if (uid == null || uid.trim().isEmpty()) {
            return null;
        }
        String normalized = uid.trim();
        for (KaznaItem item : items) {
            if (normalized.equals(item.uid)) {
                return item;
            }
        }
        return null;
    }
}

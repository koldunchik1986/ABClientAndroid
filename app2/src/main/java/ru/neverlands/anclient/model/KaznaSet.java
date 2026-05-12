package ru.neverlands.anclient.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Пользовательский комплект казны.
 *
 * Это не серверный `compl_view(...)` из инвентаря, а локальный список UID
 * предметов казны. Такой формат нужен для двух независимых шагов:
 * - `Собрать`: взять предметы из казны по UID/action-link;
 * - `Надеть`: после сбора перейти в существующий inventory/wear-контур.
 */
public final class KaznaSet {
    public final String name;
    public final List<String> itemUids;

    public KaznaSet(String name, List<String> itemUids) {
        this.name = name == null ? "" : name;
        this.itemUids = new ArrayList<>();
        if (itemUids != null) {
            for (String uid : itemUids) {
                if (uid != null && !uid.trim().isEmpty() && !this.itemUids.contains(uid.trim())) {
                    this.itemUids.add(uid.trim());
                }
            }
        }
    }

    public List<String> copyItemUids() {
        return new ArrayList<>(itemUids);
    }

    public List<String> readonlyItemUids() {
        return Collections.unmodifiableList(itemUids);
    }
}

package ru.neverlands.abclient.model;

import java.util.Comparator;

/**
 * Компаратор записей инвентаря.
 *
 * Порт C# `InvComparer`:
 * - полностью делегирует порядок методу {@link InvEntry#compareTo(InvEntry)}.
 *
 * Зависимости:
 * - корректность сортировки определяется логикой {@link InvEntry#compareTo(InvEntry)}.
 */
public class InvComparer implements Comparator<InvEntry> {
    @Override
    public int compare(InvEntry x, InvEntry y) {
        if (x == y) return 0;
        if (x == null) return 1;
        if (y == null) return -1;
        return x.compareTo(y);
    }
}

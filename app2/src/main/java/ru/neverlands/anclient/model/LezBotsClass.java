package ru.neverlands.anclient.model;

import java.util.Locale;

/**
 * Класс бота или персонажа для автобоя.
 * Портировано из LezBotsClass.cs.
 */
public class LezBotsClass {
    public final int id;
    public final String name;
    public final String plural;
    public final String kind;

    public LezBotsClass(int id, String name, String plural) {
        this(id, name, plural, "bot");
    }

    public LezBotsClass(int id, String name, String plural, String kind) {
        this.id = id;
        this.name = name;
        this.plural = plural;
        this.kind = normalizeKind(kind);
    }

    private String normalizeKind(String value) {
        if (value == null) {
            return "bot";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "all":
            case "human":
            case "bot":
            case "boss":
                return normalized;
            default:
                return "bot";
        }
    }
}

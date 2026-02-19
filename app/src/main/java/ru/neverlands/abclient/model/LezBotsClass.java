package ru.neverlands.abclient.model;

/**
 * Класс бота или персонажа для автобоя.
 * Портировано из LezBotsClass.cs.
 */
public class LezBotsClass {
    public final int id;
    public final String name;
    public final String plural;

    public LezBotsClass(int id, String name, String plural) {
        this.id = id;
        this.name = name;
        this.plural = plural;
    }
}

package ru.neverlands.abclient.model;

/**
 * Класс заклинания.
 * Портировано из LezSpell.cs.
 */
public class LezSpell {
    private int _id;
    public final String Name;

    public LezSpell(int id, String name) {
        _id = id;
        Name = name;
    }

    public static boolean IsPhBlock(int code) {
        return code >= 4 && code <= 28;
    }

    public static boolean IsMagBlock(int code) {
        return code >= 29 && code <= 31;
    }

    public static boolean IsPhHit(int code) {
        return code >= 0 && code <= 1;
    }

    public static boolean IsMagHit(int code) {
        return code >= 2 && code <= 3;
    }

    public static boolean IsScrollHit(int code) {
        return code == 277 || code == 338;
    }
}

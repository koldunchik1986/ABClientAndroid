package ru.neverlands.abclient.model;

/**
 * Типы сообщений для автобоя.
 * Портировано из UserConfigVars.cs.
 */
public enum LezSayType {
    No(0),
    Chat(1),
    Clan(2),
    Pair(3);

    private final int value;
    LezSayType(int value) { this.value = value; }
    public int getValue() { return value; }
    public static LezSayType fromInt(int i) {
        for (LezSayType b : LezSayType.values()) {
            if (b.value == i) return b;
        }
        return No;
    }
}

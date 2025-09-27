package ru.neverlands.abclient.manager;

public class TorgPair {
    public final int priceLow;
    public final int priceHi;
    public final int bonus;

    public TorgPair(int priceLow, int priceHi, int bonus) {
        this.priceLow = priceLow;
        this.priceHi = priceHi;
        this.bonus = bonus;
    }
}
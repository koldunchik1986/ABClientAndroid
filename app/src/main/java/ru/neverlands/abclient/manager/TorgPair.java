package ru.neverlands.abclient.manager;

/**
 * Класс для хранения пары значений из торговой таблицы.
 */
public class TorgPair {
    public int priceLow;
    public int priceHi;
    public int bonus;

    public TorgPair(int priceLow, int priceHi, int bonus) {
        this.priceLow = priceLow;
        this.priceHi = priceHi;
        this.bonus = bonus;
    }
}

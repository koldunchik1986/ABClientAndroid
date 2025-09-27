package ru.neverlands.abclient.model;

import java.util.HashMap;
import java.util.Map;

public class FishData {
    public static class FishInfo {
        public final double price;
        public final double mass;

        public FishInfo(double price, double mass) {
            this.price = price;
            this.mass = mass;
        }
    }

    public static final Map<String, FishInfo> FISH_PRICES = new HashMap<>();
    public static final Map<String, FishInfo> BAIT_PRICES = new HashMap<>();

    static {
        FISH_PRICES.put("Карась", new FishInfo(4.32, 2));
        FISH_PRICES.put("Плотва", new FishInfo(3.62, 2));
        FISH_PRICES.put("Пескарь", new FishInfo(3.94, 2));
        FISH_PRICES.put("Щука", new FishInfo(23.15, 5));
        FISH_PRICES.put("Ёрш", new FishInfo(3.34, 2));
        FISH_PRICES.put("Окунь", new FishInfo(11.54, 2));
        FISH_PRICES.put("Краснопёрка", new FishInfo(8.58, 2));
        FISH_PRICES.put("Налим", new FishInfo(23.85, 3));
        FISH_PRICES.put("Судак", new FishInfo(13.14, 2));
        FISH_PRICES.put("Верхоплавка", new FishInfo(2.68, 2));
        FISH_PRICES.put("Лещ", new FishInfo(22.20, 2));
        FISH_PRICES.put("Подлещик", new FishInfo(4.76, 2));
        FISH_PRICES.put("Карп", new FishInfo(5.26, 2));
        FISH_PRICES.put("Форель", new FishInfo(29.75, 5));
        FISH_PRICES.put("Бычок", new FishInfo(8.80, 2));
        FISH_PRICES.put("Голавль", new FishInfo(7.26, 2));
        FISH_PRICES.put("Линь", new FishInfo(31.62, 2));
        FISH_PRICES.put("Сом", new FishInfo(42.04, 4));
        FISH_PRICES.put("Язь", new FishInfo(29.12, 2));

        BAIT_PRICES.put("Хлеб", new FishInfo(1, 0.2));
        BAIT_PRICES.put("Червяк", new FishInfo(1, 0.1));
        BAIT_PRICES.put("Крупный червяк", new FishInfo(1, 0.2));
        BAIT_PRICES.put("Опарыш", new FishInfo(5, 0.1));
        BAIT_PRICES.put("Мотыль", new FishInfo(5, 0.1));
        BAIT_PRICES.put("Блесна", new FishInfo(10, 0.3));
        BAIT_PRICES.put("Донка", new FishInfo(12, 0.3));
        BAIT_PRICES.put("Мормышка", new FishInfo(15, 0.3));
        BAIT_PRICES.put("Заговоренная блесна", new FishInfo(20, 0.4));
    }
}

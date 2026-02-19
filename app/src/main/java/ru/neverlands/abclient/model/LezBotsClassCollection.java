package ru.neverlands.abclient.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Коллекция классов существ для автобоя.
 * Портировано из LezBotsClassCollection.cs.
 */
public class LezBotsClassCollection {
    private static final Map<Integer, LezBotsClass> Classes = new TreeMap<>();

    private static void addClass(LezBotsClass lezBotsClass) {
        if (!Classes.containsKey(lezBotsClass.id)) {
            Classes.put(lezBotsClass.id, lezBotsClass);
        }
    }

    static {
        addClass(new LezBotsClass(1, "Все", "Все"));
        addClass(new LezBotsClass(10, "Человек", "Люди"));
        addClass(new LezBotsClass(20, "Бот", "Боты"));
        addClass(new LezBotsClass(21, "Босс", "Боссы"));
        addClass(new LezBotsClass(101, "Орк", "Орки"));
        addClass(new LezBotsClass(102, "Гоблин", "Гоблины"));
        addClass(new LezBotsClass(103, "Крыса", "Крысы"));
        addClass(new LezBotsClass(104, "Кабан", "Кабаны"));
        addClass(new LezBotsClass(105, "Ядовитый паук", "Ядовитые пауки"));
        addClass(new LezBotsClass(106, "Скелет", "Скелеты"));
        addClass(new LezBotsClass(107, "Скелет-мечник", "Скелеты-мечники"));
        addClass(new LezBotsClass(108, "Зомби", "Зомби"));
        addClass(new LezBotsClass(109, "Тролль", "Тролли"));
        addClass(new LezBotsClass(110, "Огр", "Огры"));
        addClass(new LezBotsClass(111, "Огр-берсеркер", "Огры-берсеркеры"));
        addClass(new LezBotsClass(112, "Сильф", "Сильфы"));
        addClass(new LezBotsClass(113, "Нетопырь", "Нетопыри"));
        addClass(new LezBotsClass(114, "Разбойник", "Разбойники"));
        addClass(new LezBotsClass(115, "Грабитель", "Грабители"));
        addClass(new LezBotsClass(116, "Призрак", "Призраки"));
        addClass(new LezBotsClass(117, "Некромант", "Некроманты"));
        addClass(new LezBotsClass(118, "Элементаль", "Элементали"));
        addClass(new LezBotsClass(119, "Дварф", "Дварфы"));
        addClass(new LezBotsClass(120, "Медведь", "Медведи"));
        addClass(new LezBotsClass(121, "Воин Таэров", "Воины Таэров"));
    }

    public static LezBotsClass getClass(int id) {
        LezBotsClass bc = Classes.get(id);
        if (bc != null) return bc;
        return new LezBotsClass(0, String.valueOf(id), String.valueOf(id));
    }

    public static List<LezBotsClass> listForComboBox() {
        return new ArrayList<>(Classes.values());
    }
}

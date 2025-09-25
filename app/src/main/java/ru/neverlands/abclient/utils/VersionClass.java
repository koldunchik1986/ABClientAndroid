package ru.neverlands.abclient.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Класс для работы с версией приложения.
 * Аналог VersionClass.cs в оригинальном приложении.
 */
public class VersionClass {
    private final String _productName;
    private final String _productVersion;
    private final List<String> _nickList = new ArrayList<>();

    /**
     * Конструктор класса
     * @param productName название продукта
     * @param productVersion версия продукта
     */
    public VersionClass(String productName, String productVersion) {
        _productName = productName;
        _productVersion = productVersion;
    }

    /**
     * Получение полного названия продукта
     * @return полное название продукта
     */
    public String getProductFullVersion() {
        return _productName + " " + _productVersion;
    }

    /**
     * Получение короткой версии продукта
     * @return короткая версия продукта
     */
    public String getProductShortVersion() {
        return _productName + " " + getShortVersion();
    }

    /**
     * Получение короткой версии продукта с ником
     * @return короткая версия продукта с ником
     */
    public String getNickProductShortVersion() {
        if (_nickList.isEmpty()) {
            return getProductShortVersion();
        }
        
        return _nickList.get(0) + " - " + getProductShortVersion();
    }

    /**
     * Получение короткой версии
     * @return короткая версия
     */
    private String getShortVersion() {
        String[] parts = _productVersion.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        
        return _productVersion;
    }

    /**
     * Добавление ника в список
     * @param nick ник для добавления
     */
    public void addNick(String nick) {
        if (nick != null && !nick.isEmpty() && !_nickList.contains(nick)) {
            _nickList.add(nick);
        }
    }

    /**
     * Получение списка ников
     * @return список ников
     */
    public List<String> getNickList() {
        return new ArrayList<>(_nickList);
    }

    /**
     * Очистка списка ников
     */
    public void clearNickList() {
        _nickList.clear();
    }
}
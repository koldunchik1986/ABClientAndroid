package ru.neverlands.abclient.postfilter;

/**
 * Класс-обертка для java.util.Date для совместимости с оригинальным кодом.
 */
public class Date extends java.util.Date {
    
    /**
     * Конструктор по умолчанию.
     */
    public Date() {
        super();
    }
    
    /**
     * Конструктор с указанием времени в миллисекундах.
     * @param time время в миллисекундах
     */
    public Date(long time) {
        super(time);
    }
    
    /**
     * Конструктор копирования.
     * @param date дата для копирования
     */
    public Date(java.util.Date date) {
        super(date.getTime());
    }
}
package ru.neverlands.abclient.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Класс для работы с русской кодировкой.
 * Аналог Russian.cs в оригинальном приложении.
 */
public class Russian {
    /**
     * Кодировка Windows-1251
     */
    public static class Codepage {
        private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
        
        /**
         * Преобразование строки в массив байт в кодировке Windows-1251
         * @param text строка
         * @return массив байт
         */
        public static byte[] getBytes(String text) {
            if (text == null) {
                return new byte[0];
            }
            
            return text.getBytes(WINDOWS_1251);
        }
        
        /**
         * Преобразование массива байт в кодировке Windows-1251 в строку
         * @param bytes массив байт
         * @return строка
         */
        public static String getString(byte[] bytes) {
            if (bytes == null) {
                return "";
            }
            
            return new String(bytes, WINDOWS_1251);
        }
    }
    
    /**
     * Кодировка UTF-8
     */
    public static class Utf8 {
        /**
         * Преобразование строки в массив байт в кодировке UTF-8
         * @param text строка
         * @return массив байт
         */
        public static byte[] getBytes(String text) {
            if (text == null) {
                return new byte[0];
            }
            
            return text.getBytes(StandardCharsets.UTF_8);
        }
        
        /**
         * Преобразование массива байт в кодировке UTF-8 в строку
         * @param bytes массив байт
         * @return строка
         */
        public static String getString(byte[] bytes) {
            if (bytes == null) {
                return "";
            }
            
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
    
    /**
     * Преобразование строки из кодировки Windows-1251 в UTF-8
     * @param text строка в кодировке Windows-1251
     * @return строка в кодировке UTF-8
     */
    public static String win1251ToUtf8(String text) {
        if (text == null) {
            return "";
        }
        
        byte[] bytes = text.getBytes(Codepage.WINDOWS_1251);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    
    /**
     * Преобразование строки из кодировки UTF-8 в Windows-1251
     * @param text строка в кодировке UTF-8
     * @return строка в кодировке Windows-1251
     */
    public static String utf8ToWin1251(String text) {
        if (text == null) {
            return "";
        }
        
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new String(bytes, Codepage.WINDOWS_1251);
    }
}
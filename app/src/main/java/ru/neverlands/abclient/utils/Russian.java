package ru.neverlands.abclient.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Russian {
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");

    public static String getString(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        return new String(bytes, WINDOWS_1251);
    }

    public static byte[] getBytes(String string) {
        if (string == null) {
            return new byte[0];
        }
        return string.getBytes(WINDOWS_1251);
    }
}
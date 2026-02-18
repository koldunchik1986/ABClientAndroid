package ru.neverlands.abclient.utils;

public final class AppConsts {
    private AppConsts() {}

    public static final String APPLICATION_NAME = "ABClient";

    // Кодировка
    public static final String RUSSIAN_CULTURE = "ru-RU";
    public static final String US_CULTURE = "en-US";

    // Шифрование
    public static final byte[] SALT_BINARY = new byte[] { 0x49, 0x76, 0x61, 0x6e, 0x20, 0x4d, 0x65, 0x64, 0x76, 0x65, 0x64, 0x65, 0x76 };
    public static final String SALT_TEXT = "we1022@alA0";

    // Форматы
    public static final String AUTO_LOGON_FORMAT = "Автовход через %d сек";

    // Расширения
    public static final String PROFILE_EXTENSION = ".profile";

    // Адреса
    public static final String HTTP_PREFIX = "http://";
    public static final String GAME_HOST = "neverlands.ru";
    public static final String GAME_URL = HTTP_PREFIX + GAME_HOST;

    // Ключи для SharedPreferences (ранее XML-теги)
    public static final String KEY_USER_NICK = "user_nick";
    public static final String KEY_USER_PASSWORD = "user_password";
    public static final String KEY_USER_FLASH_PASSWORD = "user_flash_password";
    public static final String KEY_ENCRYPTED_USER_PASSWORD = "encrypted_password";
    public static final String KEY_ENCRYPTED_USER_FLASH_PASSWORD = "encrypted_flash_password";
    public static final String KEY_CONFIG_HASH = "config_hash";
    public static final String KEY_USER_AUTOLOGON = "user_autologon";
    public static final String KEY_LAST_LOGON = "last_logon";

    // Значения по умолчанию
    public static final boolean DEFAULT_USER_AUTOLOGON = false;
    public static final String DEFAULT_USER_NICK = "";
    
    // ... и так далее для всех остальных констант ...
}

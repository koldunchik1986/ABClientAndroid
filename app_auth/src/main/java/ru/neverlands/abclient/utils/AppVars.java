package ru.neverlands.abclient.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.WebView;

import androidx.preference.PreferenceManager;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import ru.neverlands.abclient.ABClientApplication;
import ru.neverlands.abclient.model.UserConfig;

/**
 * Глобальные переменные приложения.
 * Аналог AppVars.cs в оригинальном приложении.
 */
public class AppVars {
    /**
     * Версия приложения и все, что с ней связано.
     */
    public static final VersionClass AppVersion = new VersionClass("ABClient", "1.0.0");

    /**
     * Русская кодовая страница.
     */
    public static final Charset Codepage = StandardCharsets.UTF_8;

    /**
     * Русская локаль.
     */
    public static final Locale Culture = new Locale("ru", "RU");

    /**
     * Английская локаль.
     */
    public static final Locale EnUsCulture = Locale.US;

    /**
     * Рабочий профайл пользователя.
     */
    public static UserConfig Profile;

    /**
     * Локальный прокси, к которому надо обращаться.
     */
    public static String LocalProxyAddress;
    public static int LocalProxyPort = 8052;

    /**
     * Главная активность приложения.
     */
    public static WebView MainWebView;

    /**
     * Ссылка, которую можно нажать для окончания боя.
     */
    public static String FightLink;

    /**
     * Код последнего обработанного боя.
     */
    public static String LastBoiLog;

    /**
     * Состав последнего боя.
     */
    public static String LastBoiSostav;

    /**
     * Травматичность последнего боя.
     */
    public static String LastBoiTravm;

    /**
     * Время начала последнего боя.
     */
    public static Date LastBoiTimer;

    /**
     * Список добытых ресурсов.
     */
    public static final List<String> RazdelkaResultList = new ArrayList<>();

    /**
     * На сколько поднялось умение разделки.
     */
    public static int RazdelkaLevelUp;

    /**
     * В какой момент уже можно вывести в чат результаты разделки.
     */
    public static Date RazdelkaTime = new Date(Long.MAX_VALUE);

    /**
     * Число юзеров ABC, зашедших за последние сутки.
     */
    public static String UsersOnline = "";

    /**
     * Заметки о юзере с сервера
     */
    public static String UserNotes = "";

    /**
     * Картинка с кодом
     */
    public static byte[] CodePng;

    /**
     * Дата и время на сервере
     */
    public static Date ServerDateTime = new Date();

    /**
     * Формат даты и времени
     */
    public static final SimpleDateFormat ServerDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Culture);

    /**
     * Ошибка аккаунта
     */
    public static String AccountError = "";

    /**
     * Запрашивать подтверждение при выходе
     */
    public static boolean DoPromptExit;

    /**
     * Обновить кэш
     */
    public static boolean CacheRefresh;

    /**
     * Время следующей проверки соединения
     */
    public static Date NextCheckNoConnection = new Date();

    /**
     * Инициализация глобальных переменных
     * @param context контекст приложения
     */
    public static void init(Context context) {
        // Установка временной зоны для серверного времени
        ServerDateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
        
        // Загрузка настроек
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        DoPromptExit = prefs.getBoolean("do_prompt_exit", true);
        CacheRefresh = prefs.getBoolean("cache_refresh", false);
        
        // Установка локального прокси
        LocalProxyAddress = "127.0.0.1";
        LocalProxyPort = 8052;
    }

    /**
     * Получение URI для локального прокси
     * @return URI локального прокси
     */
    public static Uri getLocalProxyUri() {
        return Uri.parse("http://" + LocalProxyAddress + ":" + LocalProxyPort);
    }
}
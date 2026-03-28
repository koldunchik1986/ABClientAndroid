package ru.neverlands.abclient.model;

import android.content.Context;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.xmlpull.v1.XmlSerializer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

// LezBotsGroup и LezSayType в том же пакете model — дополнительный импорт не нужен

/**
 * Класс конфигурации пользователя, содержащий все настройки профиля.
 * Является портом C# класса UserConfig.
 * Поля сделаны публичными для совместимости с существующим кодом, который ожидает прямой доступ.
 */
public class UserConfig {
    private static final String TAG = "UserConfig";

    // --- Публичные поля для совместимости с C# и существующим кодом --- //

    /** Ник пользователя. */
    public String UserNick = "";
    /** Пароль пользователя (в открытом виде, если не используется шифрование). */
    public String UserPassword = "";
    public String UserPasswordFlash = "";
    /** Зашифрован ли профиль. */
    public boolean isEncrypted = false;
    /** Время последнего входа. */
    public long LastLogin = 0;
    /** ID профиля, обычно имя файла без расширения. */
    public String id = "";

    public boolean UserAutoLogon = false;
    public boolean UseProxy = false;
    public String ProxyAddress = "";
    public String ProxyUserName = "";
    public String ProxyPassword = "";

    /** Карта контактов пользователя (ключ - ник в нижнем регистре). */
    public SortedMap<String, Contact> contacts = new TreeMap<>();

    // --- Флаги для отображения кнопок быстрых действий --- //
    public boolean doShowFastAttack = false;
    public boolean doShowFastAttackBlood = true;
    public boolean doShowFastAttackUltimate = true;
    public boolean doShowFastAttackClosedUltimate = true;
    public boolean doShowFastAttackClosed = true;
    public boolean doShowFastAttackFist = false;
    public boolean doShowFastAttackClosedFist = true;
    public boolean doShowFastAttackOpenNevid = true;
    public boolean doShowFastAttackPoison = true;
    public boolean doShowFastAttackStrong = true;
    public boolean doShowFastAttackNevid = true;
    public boolean doShowFastAttackFog = true;
    public boolean doShowFastAttackZas = true;
    public boolean doShowFastAttackTotem = true;
    public boolean doShowFastAttackPortal = true;
    
    // --- Другие поля, необходимые для компиляции --- //
    public boolean DoButtonSell = true;
    public boolean DoButtonDrop = true;
    public boolean DoInvPack = true;
    /**
     * Управляет упаковкой предметов с разной долговечностью.
     *
     * C# parity (`DoInvPackDolg`):
     * - `true`: при группировке долговечность не входит в ключ одинаковости,
     *   пачка формируется по прочим свойствам, а representative выбирается через `CompareDolg`.
     * - `false`: долговечность участвует в ключе, предметы с разным `x/y` не объединяются.
     */
    public boolean DoInvPackDolg = true;
    public boolean DoInvSort = true;
    public String TorgTabl = "";

    // --- Поля из SettingsActivity и других мест --- //
    public boolean DoPromptExit = true;
    /**
     * Показывать предупреждение о перегрузе рюкзака в map.js (`checkShowOverWarning` в C# UI).
     */
    public boolean ShowOverWarning = false;
    /**
     * Ширина большой карты (в клетках).
     *
     * C# parity:
     * - `MapBigWidth` из `UserConfig`;
     * - используется bridge-методом `GetHalfMapWidth()` как `(MapBigWidth - 1) / 2`.
     */
    public int MapBigWidth = 9;
    /**
     * Высота большой карты (в клетках).
     *
     * C# parity:
     * - `MapBigHeight` из `UserConfig`;
     * - используется bridge-методом `GetHalfMapHeight()` как `(MapBigHeight - 1) / 2`.
     */
    public int MapBigHeight = 7;
    /**
     * Масштаб большой карты в процентах.
     *
     * Зависимости:
     * - `WebAppInterface.GetMapScale()` -> `map.js`;
     * - UI общих настроек (`SettingsActivity`, ключ `map_scale_percent`).
     */
    public int MapBigScale = 75;
    /**
     * Размер шрифта подписи клетки карты (`CellDivText`) в пикселях.
     *
     * Зависимости:
     * - `WebAppInterface.CellDivText(...)` — фактический рендер текста поверх тайла;
     * - `SettingsActivity` (ключ `map_font_size`) — изменение значения из UI;
     * - профиль (`mapset@cellfontsize`) — сохранение между перезапусками.
     */
    public int MapCellFontSize = 9;
    /**
     * Включает синхронизацию названий/региона клетки карты по данным ch.php + pinfo.
     *
     * Назначение:
     * - управляет модулем переименования клетки по фактическому названию комнаты;
     * - разрешает фоновую подпитку Region из pinfo для ускорения точного поиска в Авто-Компасе.
     */
    public boolean MapRebuildFromPinfo = true;
    /**
     * Дополнительный таймаут проверки клетки после перехода (мс).
     * Применяется только при включенном `MapRebuildFromPinfo`.
     * Диапазон: 0..5000.
     */
    public int MapCellCheckTimeoutMs = 450;
    public boolean DoHttpLog = false;
    public boolean DoTexLog = false;
    public boolean ShowPerformance = false;
    public boolean DoProxy = false;
    /**
     * Выводить результаты разделки в чат (C# `RazdChatReport`).
     */
    public boolean RazdChatReport = false;
    /**
     * Автопитье блажа по усталости (C# `DoAutoDrinkBlaz`).
     */
    public boolean DoAutoDrinkBlaz = true;
    /**
     * Порог усталости для автопитья блажа (C# `AutoDrinkBlazTied`).
     */
    public int AutoDrinkBlazTied = 84;
    /**
     * Порядок поиска блажа (C# `AutoDrinkBlazOrder`):
     * 0 - ищем зелье, потом эликсир
     * 1 - ищем эликсир, потом зелье
     */
    public int AutoDrinkBlazOrder = 0;
    public boolean AutoFish = false;
    /**
     * Авто-надевание удочек (C# `FishAutoWear`).
     * Нужен для C#-совместимого парсера `ParsedDressed`.
     */
    public boolean FishAutoWear = true;
    /**
     * Настройка предмета в первой руке для рыбалки (C# `FishHandOne`).
     */
    public String FishHandOne = "Любая удочка";
    /**
     * Настройка предмета во второй руке для рыбалки (C# `FishHandTwo`).
     */
    public String FishHandTwo = "Нет";
    /**
     * Битовая маска разрешенных приманок для авто-рыбалки (C# `FishEnabledPrims`/`Prims`).
     *
     * Зависимости:
     * - читается в postfilter-логике выбора приманки;
     * - сериализуется в `<autofish enabledprims="...">` для совместимости с ПК-профилями.
     */
    public int FishEnabledPrims = Prims.DEFAULT_ALL;
    /**
     * Текущее значение умения рыбалки из профиля (C# `FishUm`).
     * Используется runtime-логикой авто-рыбалки при решении, нужно ли читать умения персонажа.
     */
    public int FishUm = 0;
    /**
     * Порог усталости рыбалки (C# `FishTiedHigh`).
     */
    public int FishTiedHigh = 20;
    /**
     * Флаг остановки при нулевой усталости (C# `FishTiedZero`).
     */
    public boolean FishTiedZero = false;
    /**
     * В авто-рыбалке вместо шага "Пить" использовать "Эликсир Блаженства", если усталость выше порога.
     */
    public boolean FishDrinkBliss = false;
    /**
     * Остановка авто-рыбалки при перевесе (C# `FishStopOverWeight`).
     */
    public boolean FishStopOverWeight = true;
    /**
     * Максимальный уровень ботов для авто-рыбалки (C# `FishMaxLevelBots`).
     */
    public int FishMaxLevelBots = 8;
    /**
     * Выводить рыболовный отчет в чат (C# `FishChatReport`).
     */
    public boolean FishChatReport = false;
    /**
     * Цветной отчет в чат по рыбалке (C# `FishChatReportColor`).
     */
    public boolean FishChatReportColor = true;
    /**
     * Авто-охота (аналог `SkinAuto` в ПК C# профиле).
     * Используется как профильный флаг для логики `buttonAutoSkin`.
     */
    public boolean SkinAuto = false;
    public boolean AutoHerb = false;
    public boolean AutoMine = false;
    public boolean AutoTree = false;
    public boolean AutoDig = false;
    /**
     * Останавливать авто-поиск клада при появлении на клетке кнопки "Копать".
     *
     * C# parity:
     * - `UserConfig.DoStopOnDig` (`checkDoStopOnDig` в FormSettingsGeneral).
     * - runtime-проверка в `PostFilter/MainPhp.cs` по маркеру `["dig","Копать",`.
     */
    public boolean DoStopOnDig = true;
    public boolean AutoTorg = false;
    public boolean TorgActive = false;
    public boolean DoGuamod = false;

    /**
     * Текущая клетка персонажа (region-number), обновляется рантаймом из main.php.
     * Используется навигатором и авто-переходами.
     */
    public String MapLocation = "";
    /**
     * Разрешение использовать телепорты при построении маршрута навигатором.
     */
    public boolean NavigatorAllowTeleports = true;
    /**
     * Список избранных клеток навигатора.
     */
    public String[] FavLocations = new String[0];
    /**
     * Legacy-списки "Города" (совместимость со старыми профилями/кодом).
     */
    public String[] NavCityVillageLocations = new String[] {"8-197"};
    public String[] NavCityForpostLocations = new String[] {"8-259", "8-294"};
    public String[] NavCityOktalLocations = new String[] {"12-428", "12-494", "12-521"};
    /**
     * Динамические подкатегории "Города" (строковый сериализованный формат).
     * Формат значения:
     * - "Имя|8-259,8-294;Имя2|12-428".
     *
     * Зависимости:
     * - парсится/сохраняется в QuickButtonsPanel;
     * - хранится в navigator@citysubcategories в профиле.
     */
    public String NavCitySubcategories = "";
    /**
     * Категория "Объекты" навигатора.
     */
    public String[] NavObjectLocations = new String[] {"8-227", "2-482", "9-494", "26-430"};
    /**
     * Кастомный список телепортов навигатора.
     */
    public String[] NavTeleportLocations = new String[0];

    /**
     * Добавляет одну клетку в избранное без дополнительной нормализации формата.
     * Нормализация выполняется на уровне UI-слоя навигатора.
     */
    public void addFavLocation(String loc) {
        if (loc == null || loc.trim().isEmpty()) return;
        String trimmed = loc.trim();
        String[] arr = new String[FavLocations.length + 1];
        System.arraycopy(FavLocations, 0, arr, 0, FavLocations.length);
        arr[FavLocations.length] = trimmed;
        FavLocations = arr;
    }

    /**
     * Полностью очищает избранное навигатора.
     */
    public void clearFavLocations() {
        FavLocations = new String[0];
    }

    /**
     * Устанавливает список клеток подкатегории "Деревня".
     *
     * Зависимости:
     * - sanitizeLocations(...) фильтрует формат и дубляжи;
     * - fallback на дефолт при пустом наборе (совместимость со старыми профилями).
     */
    public void setNavCityVillageLocations(String[] values) {
        NavCityVillageLocations = sanitizeLocations(values, new String[] {"8-197"});
    }

    /**
     * Устанавливает список клеток подкатегории "Форпост".
     */
    public void setNavCityForpostLocations(String[] values) {
        NavCityForpostLocations = sanitizeLocations(values, new String[] {"8-259", "8-294"});
    }

    /**
     * Устанавливает список клеток подкатегории "Октал".
     */
    public void setNavCityOktalLocations(String[] values) {
        NavCityOktalLocations = sanitizeLocations(values, new String[] {"12-428", "12-494", "12-521"});
    }

    /**
     * Устанавливает список клеток категории "Объекты".
     */
    public void setNavObjectLocations(String[] values) {
        NavObjectLocations = sanitizeLocations(values, new String[] {"8-227", "2-482", "9-494", "26-430"});
    }

    /**
     * Устанавливает список клеток категории "Телепорты".
     */
    public void setNavTeleportLocations(String[] values) {
        NavTeleportLocations = sanitizeLocations(values, new String[0]);
    }

    public int ChatHeight = 115;
    public int ChatDelay = 10;
    public int ChatMode = 0;
    public boolean ChatKeepLog = true;
    public boolean DoAutoAnswer = false;
    public boolean DoChatLevels = false;
    /**
     * Сбрасывать статистику в полночь.
     *
     * Зависимости:
     * - читает `ChatStats` при проверке дневного автосброса;
     * - настраивается из UI окна статистики (QuickButtonsPanel);
     * - сохраняется/загружается в атрибуте `chat@statsResetAtMidnight`.
     */
    public boolean StatsResetAtMidnight = false;
    // Разница между локальным временем и временем сервера (мс). Аналог ServDiff в C#.
    // Используется для корректного отображения "серверных" часов в чате и событиях.
    public long ServDiff = Long.MIN_VALUE;

    // --- Lez AutoBoi --- //
    public boolean LezDoAutoboi = true;
    public boolean LezDoFury = false;
    public boolean LezDoWaitHp = false;
    public boolean LezDoWaitMa = false;
    public int LezWaitHp = 100;
    public int LezWaitMa = 100;
    public boolean LezDoDrinkHp = false;
    public boolean LezDoDrinkMa = true;
    public int LezDrinkHp = 50;
    public int LezDrinkMa = 50;
    public boolean LezDoWinTimeout = true;
    /**
     * Задержка автоудара в секундах (доп. настройка "Задержка ударов").
     *
     * Зависимости:
     * Legacy-глобальный fallback:
     * - сохраняется в `<autoboi hitDelaySec="...">` для обратной совместимости профилей;
     * - если в `group@hitDelaySec` нет значения, используется как дефолт для группы;
     * - приоритет в бою у пер-группового `LezBotsGroup.HitDelaySec`.
     *
     * Значение `0` = legacy-режим (старый случайный интервал 1.0-2.0 сек).
     */
    public int LezHitDelaySec = 0;
    public LezSayType LezSay = LezSayType.No;
    public List<LezBotsGroup> LezGroups = new ArrayList<>();
    public LezSayType BossSay = LezSayType.No;

    public UserConfig() {
        // Конструктор по умолчанию
        LezGroups.add(new LezBotsGroup(1, 0));
    }

    /**
     * Загружает все профили из директории приложения.
     * @param context Контекст приложения.
     * @return Список загруженных профилей.
     */
    public static List<UserConfig> loadAllProfiles(Context context) {
        List<UserConfig> profiles = new ArrayList<>();
        // Профили хранятся во внешней директории приложения, чтобы не удалялись при переустановке
        File profilesDir = context.getExternalFilesDir("profiles");
        if (profilesDir == null) {
            return profiles;
        }
        if (!profilesDir.exists()) {
            profilesDir.mkdirs();
        }

        File[] profileFiles = profilesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".profile"));

        if (profileFiles != null) {
            for (File file : profileFiles) {
                UserConfig config = new UserConfig();
                if (config.load(file)) {
                    profiles.add(config);
                }
            }
        }
        return profiles;
    }

    /**
     * Загружает данные одного профиля из XML-файла.
     * @param profileFile Файл профиля.
     * @return true, если загрузка успешна.
     */
    private boolean load(File profileFile) {
        this.id = profileFile.getName().replace(".profile", "");
        try (InputStream in = new FileInputStream(profileFile)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);

            boolean proxyTagParsed = false;
            boolean legacyProxyNodesParsed = false;
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("user".equals(tagName)) {
                        this.UserNick = parser.getAttributeValue(null, "name");
                        this.UserPassword = parser.getAttributeValue(null, "password");
                        String isEncryptedStr = parser.getAttributeValue(null, "isEncrypted");
                        this.isEncrypted = "true".equalsIgnoreCase(isEncryptedStr);
                        this.UserAutoLogon = parseBoolAttr(parser, "autologon", this.UserAutoLogon);
                        long parsedLastLogin = parseLongAttr(parser, "lastlogon", 0L);
                        // Fallback для старых Android-профилей без `user@lastlogon`.
                        if (parsedLastLogin <= 0L) {
                            parsedLastLogin = profileFile.lastModified();
                        }
                        this.LastLogin = parsedLastLogin;
                    } else if ("contactentry".equals(tagName)) {
                        String name = parser.getAttributeValue(null, "name");
                        String classIdStr = parser.getAttributeValue(null, "classid");
                        int classId = 0;
                        if (classIdStr != null && !classIdStr.isEmpty()) {
                            try {
                                classId = Integer.parseInt(classIdStr);
                            } catch (NumberFormatException e) {
                                classId = 0;
                            }
                        }
                        if (name != null && !name.isEmpty()) {
                            Contact contact = new Contact();
                            contact.nick = name;
                            contact.classId = classId;
                            this.contacts.put(name.toLowerCase(), contact);
                        }
                    } else if ("chat".equals(tagName)) {
                        this.ChatHeight = parseIntAttr(parser, "height", this.ChatHeight);
                        this.ChatDelay = parseIntAttr(parser, "delay", this.ChatDelay);
                        this.ChatMode = parseIntAttr(parser, "mode", this.ChatMode);
                        this.ChatKeepLog = Boolean.parseBoolean(parser.getAttributeValue(null, "keepLog"));
                        this.DoAutoAnswer = Boolean.parseBoolean(parser.getAttributeValue(null, "autoAnswer"));
                        this.DoChatLevels = Boolean.parseBoolean(parser.getAttributeValue(null, "chatLevels"));
                        this.StatsResetAtMidnight = parseBoolAttr(parser, "statsResetAtMidnight", this.StatsResetAtMidnight);
                    } else if ("inventory".equalsIgnoreCase(tagName)) {
                        // Параметры инвентаря (bulk-кнопки / упаковка / сортировка).
                        this.DoButtonSell = parseBoolAttr(parser, "buttonSell", this.DoButtonSell);
                        this.DoButtonDrop = parseBoolAttr(parser, "buttonDrop", this.DoButtonDrop);
                        this.DoInvPack = parseBoolAttr(parser, "pack", this.DoInvPack);
                        this.DoInvPackDolg = parseBoolAttr(parser, "packDolg", this.DoInvPackDolg);
                        this.DoInvSort = parseBoolAttr(parser, "sort", this.DoInvSort);
                    } else if ("mapset".equalsIgnoreCase(tagName) || "map".equalsIgnoreCase(tagName)) {
                        this.MapBigWidth = parseIntAttr(parser, "bigwidth", this.MapBigWidth);
                        this.MapBigHeight = parseIntAttr(parser, "bigheight", this.MapBigHeight);
                        this.MapBigScale = parseIntAttr(parser, "bigscale", this.MapBigScale);
                        this.MapCellFontSize = parseIntAttr(parser, "cellfontsize", this.MapCellFontSize);
                        this.MapRebuildFromPinfo = parseBoolAttr(parser, "rebuildfrompinfo", this.MapRebuildFromPinfo);
                        this.MapCellCheckTimeoutMs = parseIntAttr(parser, "cellchecktimeoutms", this.MapCellCheckTimeoutMs);
                        if (this.MapBigWidth < 3) this.MapBigWidth = 3;
                        if (this.MapBigHeight < 3) this.MapBigHeight = 3;
                        if (this.MapBigScale < 50) this.MapBigScale = 50;
                        if (this.MapBigScale > 150) this.MapBigScale = 150;
                        if (this.MapCellFontSize < 6) this.MapCellFontSize = 6;
                        if (this.MapCellFontSize > 24) this.MapCellFontSize = 24;
                        if (this.MapCellCheckTimeoutMs < 0) this.MapCellCheckTimeoutMs = 0;
                        if (this.MapCellCheckTimeoutMs > 5000) this.MapCellCheckTimeoutMs = 5000;
                    } else if ("proxy".equalsIgnoreCase(tagName)) {
                        boolean proxyActive = parseBoolAttr(parser, "active", this.DoProxy || this.UseProxy);
                        String proxyAddress = getAttributeValueIgnoreCase(parser, "address");
                        String proxyUserName = getAttributeValueIgnoreCase(parser, "username");
                        String proxyPassword = getAttributeValueIgnoreCase(parser, "password");
                        proxyTagParsed = true;

                        this.DoProxy = proxyActive;
                        this.UseProxy = proxyActive;
                        this.ProxyAddress = proxyAddress != null ? proxyAddress : this.ProxyAddress;
                        this.ProxyUserName = proxyUserName != null ? proxyUserName : this.ProxyUserName;
                        this.ProxyPassword = proxyPassword != null ? proxyPassword : this.ProxyPassword;
                        android.util.Log.i(TAG, "load: proxy tag parsed, active=" + proxyActive
                                + ", address=" + this.ProxyAddress);
                    } else if ("DoProxy".equalsIgnoreCase(tagName) || "UseProxy".equalsIgnoreCase(tagName)) {
                        // Legacy Android migration: старые профили могли хранить флаги отдельными узлами
                        // без C#-совместимого `<proxy ...>`.
                        boolean value = parseBoolNodeText(parser, false);
                        this.DoProxy = value;
                        this.UseProxy = value;
                        legacyProxyNodesParsed = true;
                        android.util.Log.i(TAG, "load: legacy proxy flag node parsed, value=" + value);
                    } else if ("ProxyAddress".equalsIgnoreCase(tagName)) {
                        String value = parseNodeText(parser, this.ProxyAddress);
                        this.ProxyAddress = value == null ? "" : value.trim();
                        legacyProxyNodesParsed = true;
                        android.util.Log.i(TAG, "load: legacy ProxyAddress node parsed, address=" + this.ProxyAddress);
                    } else if ("ProxyUserName".equalsIgnoreCase(tagName)) {
                        String value = parseNodeText(parser, this.ProxyUserName);
                        this.ProxyUserName = value == null ? "" : value.trim();
                        legacyProxyNodesParsed = true;
                    } else if ("ProxyPassword".equalsIgnoreCase(tagName)) {
                        String value = parseNodeText(parser, this.ProxyPassword);
                        this.ProxyPassword = value == null ? "" : value.trim();
                        legacyProxyNodesParsed = true;
                    } else if ("DoButtonSell".equalsIgnoreCase(tagName)) {
                        this.DoButtonSell = parseBoolNodeText(parser, this.DoButtonSell);
                    } else if ("DoButtonDrop".equalsIgnoreCase(tagName)) {
                        this.DoButtonDrop = parseBoolNodeText(parser, this.DoButtonDrop);
                    } else if ("DoInvPack".equalsIgnoreCase(tagName)) {
                        this.DoInvPack = parseBoolNodeText(parser, this.DoInvPack);
                    } else if ("DoInvPackDolg".equalsIgnoreCase(tagName)) {
                        this.DoInvPackDolg = parseBoolNodeText(parser, this.DoInvPackDolg);
                    } else if ("DoInvSort".equalsIgnoreCase(tagName)) {
                        this.DoInvSort = parseBoolNodeText(parser, this.DoInvSort);
                    } else if ("showoverwarning".equalsIgnoreCase(tagName)) {
                        // C# parity: <showoverwarning>true|false</showoverwarning>
                        this.ShowOverWarning = parseBoolNodeText(parser, this.ShowOverWarning);
                    } else if ("dostopondig".equalsIgnoreCase(tagName)) {
                        this.DoStopOnDig = parseBoolNodeText(parser, this.DoStopOnDig);
                    } else if ("RazdChatReport".equalsIgnoreCase(tagName)) {
                        // Legacy-совместимость:
                        // в части старых профилей C# флаг мог храниться отдельным узлом.
                        // Если далее в XML встретится `autofish@razdchatreport`, это значение
                        // будет корректно уточнено при разборе секции рыбалки.
                        this.RazdChatReport = parseBoolNodeText(parser, this.RazdChatReport);
                    } else if ("autoboi".equals(tagName)) {
                        this.LezDoAutoboi = Boolean.parseBoolean(parser.getAttributeValue(null, "enabled"));
                        this.LezDoFury = parseBoolAttr(parser, "fury", this.LezDoFury);
                        this.LezDoWaitHp = Boolean.parseBoolean(parser.getAttributeValue(null, "waitHp"));
                        this.LezDoWaitMa = Boolean.parseBoolean(parser.getAttributeValue(null, "waitMa"));
                        this.LezWaitHp = parseIntAttr(parser, "waitHpVal", 100);
                        this.LezWaitMa = parseIntAttr(parser, "waitMaVal", 100);
                        this.LezDoDrinkHp = Boolean.parseBoolean(parser.getAttributeValue(null, "drinkHp"));
                        this.LezDoDrinkMa = Boolean.parseBoolean(parser.getAttributeValue(null, "drinkMa"));
                        this.LezDrinkHp = parseIntAttr(parser, "drinkHpVal", 50);
                        this.LezDrinkMa = parseIntAttr(parser, "drinkMaVal", 50);
                        this.LezDoWinTimeout = Boolean.parseBoolean(parser.getAttributeValue(null, "winTimeout"));
                        this.LezHitDelaySec = parseIntAttr(parser, "hitDelaySec", this.LezHitDelaySec);
                        if (this.LezHitDelaySec < 0) {
                            this.LezHitDelaySec = 0;
                        }
                        try {
                            String sayStr = parser.getAttributeValue(null, "say");
                            this.LezSay = sayStr != null ? LezSayType.valueOf(sayStr) : LezSayType.No;
                        } catch (Exception e) { this.LezSay = LezSayType.No; }
                    } else if ("SkinAuto".equalsIgnoreCase(tagName)) {
                        // Профильный флаг авто-охоты из C# (`<SkinAuto>true|false</SkinAuto>`).
                        // Поддерживаем текстовый узел для совместимости с существующими профилями ПК-версии.
                        try {
                            String text = parser.nextText();
                            this.SkinAuto = Boolean.parseBoolean(text);
                        } catch (Exception ignore) {
                            // Если узел поврежден, оставляем дефолт (false), как в C#.
                        }
                    } else if ("group".equals(tagName)) {
                        LezBotsGroup g = new LezBotsGroup(
                            parseIntAttr(parser, "id", 1),
                            parseIntAttr(parser, "minLevel", 0)
                        );
                        // Пер-групповая настройка канала сообщения о нападении (новый Android-порт).
                        // Обратная совместимость:
                        // - если у группы нет attr `say` (старый профиль), берём глобальный LezSay из <autoboi>.
                        // - это сохраняет прежнее поведение до ручной настройки по группам.
                        try {
                            String groupSay = getAttributeValueIgnoreCase(parser, "say");
                            if (groupSay == null || groupSay.trim().isEmpty()) {
                                g.AttackSay = this.LezSay != null ? this.LezSay : LezSayType.No;
                            } else {
                                g.AttackSay = LezSayType.valueOf(groupSay);
                            }
                        } catch (Exception ignore) {
                            g.AttackSay = this.LezSay != null ? this.LezSay : LezSayType.No;
                        }
                        g.DoRestoreHp = parseBoolAttr(parser, "doRestoreHp", g.DoRestoreHp);
                        g.DoRestoreMa = parseBoolAttr(parser, "doRestoreMa", g.DoRestoreMa);
                        g.RestoreHp = parseIntAttr(parser, "restoreHp", g.RestoreHp);
                        g.RestoreMa = parseIntAttr(parser, "restoreMa", g.RestoreMa);
                        g.DoAbilBlocks = parseBoolAttr(parser, "doAbilBlocks", g.DoAbilBlocks);
                        g.DoAbilHits = parseBoolAttr(parser, "doAbilHits", g.DoAbilHits);
                        g.DoMagHits = parseBoolAttr(parser, "doMagHits", g.DoMagHits);
                        g.MagHits = parseIntAttr(parser, "magHits", g.MagHits);
                        g.DoMagBlocks = parseBoolAttr(parser, "doMagBlocks", g.DoMagBlocks);
                        g.DoHits = parseBoolAttr(parser, "doHits", g.DoHits);
                        g.DoBlocks = parseBoolAttr(parser, "doBlocks", g.DoBlocks);
                        g.DoMiscAbils = parseBoolAttr(parser, "doMiscAbils", g.DoMiscAbils);
                        // Пер-групповая задержка удара:
                        // - новый профиль: `group@hitDelaySec`;
                        // - старый профиль: fallback на legacy `autoboi@hitDelaySec`.
                        g.HitDelaySec = parseIntAttr(parser, "hitDelaySec", this.LezHitDelaySec);
                        if (g.HitDelaySec < 0) {
                            g.HitDelaySec = 0;
                        }
                        // Обратная совместимость со старыми профилями:
                        // если в `group` отсутствует атрибут `doFury`, берём legacy-флаг из `<autoboi fury=...>`.
                        //
                        // Зависимости:
                        // - старые .profile, где был только глобальный `fury` без пер-группового режима;
                        // - новая логика ротации, где источник истины — `group@doFury`.
                        g.DoFury = parseBoolAttr(parser, "doFury", this.LezDoFury);
                        g.DoStopNow = parseBoolAttr(parser, "doStopNow", g.DoStopNow);
                        g.DoStopLowHp = parseBoolAttr(parser, "doStopLowHp", g.DoStopLowHp);
                        g.DoStopLowMa = parseBoolAttr(parser, "doStopLowMa", g.DoStopLowMa);
                        g.StopLowHp = parseIntAttr(parser, "stopLowHp", g.StopLowHp);
                        g.StopLowMa = parseIntAttr(parser, "stopLowMa", g.StopLowMa);
                        g.DoExit = parseBoolAttr(parser, "doExit", g.DoExit);
                        g.DoExitRisky = parseBoolAttr(parser, "doExitRisky", g.DoExitRisky);

                        int[] spellsHits = parseIntArrayAttr(parser, "spellsHits");
                        if (spellsHits.length > 0) g.SpellsHits = spellsHits;
                        int[] spellsBlocks = parseIntArrayAttr(parser, "spellsBlocks");
                        if (spellsBlocks.length > 0) g.SpellsBlocks = spellsBlocks;
                        int[] spellsRestoreHp = parseIntArrayAttr(parser, "spellsRestoreHp");
                        if (spellsRestoreHp.length > 0) g.SpellsRestoreHp = spellsRestoreHp;
                        int[] spellsRestoreMa = parseIntArrayAttr(parser, "spellsRestoreMa");
                        if (spellsRestoreMa.length > 0) g.SpellsRestoreMa = spellsRestoreMa;
                        int[] spellsMisc = parseIntArrayAttr(parser, "spellsMisc");
                        if (spellsMisc.length > 0) g.SpellsMisc = spellsMisc;

                        if (!g.DoAbilBlocks && !g.DoAbilHits
                                && !g.DoMagHits && !g.DoMagBlocks
                                && !g.DoHits && !g.DoBlocks && !g.DoMiscAbils) {
                            LezBotsGroup defaults = new LezBotsGroup(g.Id, g.MinimalLevel);
                            g.DoAbilBlocks = defaults.DoAbilBlocks;
                            g.DoAbilHits = defaults.DoAbilHits;
                            g.DoMagHits = defaults.DoMagHits;
                            g.DoMagBlocks = defaults.DoMagBlocks;
                            g.DoHits = defaults.DoHits;
                            g.DoBlocks = defaults.DoBlocks;
                            g.DoMiscAbils = defaults.DoMiscAbils;
                            if (g.SpellsHits == null || g.SpellsHits.length == 0) g.SpellsHits = defaults.SpellsHits;
                            if (g.SpellsBlocks == null || g.SpellsBlocks.length == 0) g.SpellsBlocks = defaults.SpellsBlocks;
                            if (g.SpellsMisc == null || g.SpellsMisc.length == 0) g.SpellsMisc = defaults.SpellsMisc;
                            android.util.Log.w(TAG, "load: fixed invalid autoboi combat flags for group id=" + g.Id);
                        }
                        // Обновляем или добавляем группу по полной паре ключей (Id + MinimalLevel).
                        // C# parity: в профиле могут одновременно существовать, например, "Боты 10+" и "Боты 0+".
                        // Если сравнивать только по Id (как раньше), одна из групп теряется при загрузке.
                        boolean found = false;
                        for (int gi = 0; gi < this.LezGroups.size(); gi++) {
                            LezBotsGroup current = this.LezGroups.get(gi);
                            if (current.Id == g.Id && current.MinimalLevel == g.MinimalLevel) {
                                this.LezGroups.set(gi, g);
                                found = true;
                                break;
                            }
                        }
                        if (!found) this.LezGroups.add(g);
                    } else if ("fastactions".equals(tagName)) {
                        // In a real implementation, we should handle null attributes gracefully
                        this.doShowFastAttack = Boolean.parseBoolean(parser.getAttributeValue(null, "simple"));
                        this.doShowFastAttackBlood = Boolean.parseBoolean(parser.getAttributeValue(null, "blood"));
                        this.doShowFastAttackUltimate = Boolean.parseBoolean(parser.getAttributeValue(null, "ultimate"));
                        this.doShowFastAttackClosedUltimate = Boolean.parseBoolean(parser.getAttributeValue(null, "closedultimate"));
                        this.doShowFastAttackClosed = Boolean.parseBoolean(parser.getAttributeValue(null, "closed"));
                        this.doShowFastAttackFist = Boolean.parseBoolean(parser.getAttributeValue(null, "fist"));
                        this.doShowFastAttackClosedFist = Boolean.parseBoolean(parser.getAttributeValue(null, "closedfist"));
                        this.doShowFastAttackOpenNevid = Boolean.parseBoolean(parser.getAttributeValue(null, "opennevid"));
                        this.doShowFastAttackPoison = Boolean.parseBoolean(parser.getAttributeValue(null, "poison"));
                        this.doShowFastAttackStrong = Boolean.parseBoolean(parser.getAttributeValue(null, "strong"));
                        this.doShowFastAttackNevid = Boolean.parseBoolean(parser.getAttributeValue(null, "nevid"));
                        this.doShowFastAttackFog = Boolean.parseBoolean(parser.getAttributeValue(null, "fog"));
                        this.doShowFastAttackZas = Boolean.parseBoolean(parser.getAttributeValue(null, "zas"));
                        this.doShowFastAttackTotem = Boolean.parseBoolean(parser.getAttributeValue(null, "totem"));
                        this.doShowFastAttackPortal = Boolean.parseBoolean(parser.getAttributeValue(null, "portal"));
                    } else if ("autofish".equalsIgnoreCase(tagName) || "fish".equalsIgnoreCase(tagName)) {
                        // C# parity (`ConstTagFish = "autofish"`): читаем рыболовные параметры профиля.
                        //
                        // Зависимости:
                        // - UI общих настроек (`SettingsActivity`, `root_preferences.xml`);
                        // - runtime-логика авто-рыбалки в postfilter;
                        // - обратная совместимость со старыми профилями (`fish` вместо `autofish`).
                        this.AutoFish = parseBoolAttr(parser, "auto", this.AutoFish);
                        this.FishTiedHigh = parseIntAttr(parser, "tiedhigh", this.FishTiedHigh);
                        this.FishTiedZero = parseBoolAttr(parser, "tiedzero", this.FishTiedZero);
                        this.FishDrinkBliss = parseBoolAttr(parser, "drinkbliss", this.FishDrinkBliss);
                        this.FishStopOverWeight = parseBoolAttr(parser, "stopoverw", this.FishStopOverWeight);
                        this.FishAutoWear = parseBoolAttr(parser, "autowear", this.FishAutoWear);
                        String fishHandOne = getAttributeValueIgnoreCase(parser, "hand1");
                        if (fishHandOne != null && !fishHandOne.isEmpty()) {
                            this.FishHandOne = fishHandOne;
                        }
                        String fishHandTwo = getAttributeValueIgnoreCase(parser, "hand2");
                        if (fishHandTwo != null) {
                            this.FishHandTwo = fishHandTwo.trim().isEmpty() ? "Нет" : fishHandTwo;
                        }
                        this.FishEnabledPrims = parseIntAttr(parser, "enabledprims", this.FishEnabledPrims);
                        this.FishUm = parseIntAttr(parser, "um", this.FishUm);
                        this.FishMaxLevelBots = parseIntAttr(parser, "maxlevelbots", this.FishMaxLevelBots);
                        this.FishChatReport = parseBoolAttr(parser, "chatreport", this.FishChatReport);
                        this.FishChatReportColor = parseBoolAttr(parser, "chatreportcolor", this.FishChatReportColor);
                        // C# parity: `razdchatreport` хранится в блоке рыбалки профиля.
                        // Этот флаг затем используется в авто-охоте (`MainPhp`) для решения,
                        // нужно ли отправлять в чат строку "Результат разделки".
                        this.RazdChatReport = parseBoolAttr(parser, "razdchatreport", this.RazdChatReport);
                    } else if ("autodrinkblaz".equalsIgnoreCase(tagName)) {
                        // C# parity (`<autodrinkblaz do tied>`): флаг + порог.
                        //
                        // Зависимости:
                        // - UI: `do_auto_drink_blaz`, `auto_drink_blaz_tied`;
                        // - runtime-ветки авто-режимов, где проверяется усталость;
                        // - сериализация в `save(...)`, чтобы настройки переживали перезапуск.
                        this.DoAutoDrinkBlaz = parseBoolAttr(parser, "do", this.DoAutoDrinkBlaz);
                        this.AutoDrinkBlazTied = parseIntAttr(parser, "tied", this.AutoDrinkBlazTied);
                    } else if ("autodrinkblazorder".equalsIgnoreCase(tagName)) {
                        // C# parity: порядок поиска типа блажа хранится отдельным узлом.
                        // Допускаются только значения 0/1; при битом значении откатываемся к 0.
                        String value = parseNodeText(parser, String.valueOf(this.AutoDrinkBlazOrder));
                        try {
                            this.AutoDrinkBlazOrder = Integer.parseInt(value);
                        } catch (Exception ignore) {
                            this.AutoDrinkBlazOrder = 0;
                        }
                        if (this.AutoDrinkBlazOrder < 0 || this.AutoDrinkBlazOrder > 1) {
                            this.AutoDrinkBlazOrder = 0;
                        }
                    } else if ("navigator".equalsIgnoreCase(tagName)) {
                        this.NavigatorAllowTeleports = parseBoolAttr(parser, "allowteleports", this.NavigatorAllowTeleports);
                        this.setNavCityVillageLocations(parseLocationsAttr(parser, "village", this.NavCityVillageLocations));
                        this.setNavCityForpostLocations(parseLocationsAttr(parser, "forpost", this.NavCityForpostLocations));
                        this.setNavCityOktalLocations(parseLocationsAttr(parser, "oktal", this.NavCityOktalLocations));
                        String citySubcategories = getAttributeValueIgnoreCase(parser, "citysubcategories");
                        if (citySubcategories != null) {
                            this.NavCitySubcategories = citySubcategories.trim();
                        }
                        this.setNavObjectLocations(parseLocationsAttr(parser, "objects", this.NavObjectLocations));
                        this.setNavTeleportLocations(parseLocationsAttr(parser, "teleports", this.NavTeleportLocations));
                    } else if ("favlocation".equalsIgnoreCase(tagName)) {
                        String loc = parseNodeText(parser, null);
                        if (loc != null && !loc.trim().isEmpty()) {
                            String[] arr = new String[this.FavLocations.length + 1];
                            System.arraycopy(this.FavLocations, 0, arr, 0, this.FavLocations.length);
                            arr[this.FavLocations.length] = loc.trim();
                            this.FavLocations = arr;
                        }
                    }
                }
                eventType = parser.next();
            }
            if (!proxyTagParsed && legacyProxyNodesParsed) {
                android.util.Log.i(TAG, "load: migrated legacy proxy nodes into runtime profile fields");
            }
            normalizeProxyFlags();
            normalizeLezGroups();
            this.LezDoFury = hasAnyLezFuryGroup();
            return true;
        } catch (IOException | XmlPullParserException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Сохраняет текущую конфигурацию профиля в XML-файл.
     * @param context Контекст приложения.
     */
    public void save(Context context) {
        normalizeProxyFlags();
        // Перед сохранением приводим список к C#-совместимому виду:
        // - гарантируем наличие группы "Все 0+"
        // - сортируем по LezBotsGroup.compareTo() (Id DESC, MinimalLevel DESC)
        normalizeLezGroups();
        this.LezDoFury = hasAnyLezFuryGroup();

        File profilesDir = context.getExternalFilesDir("profiles");
        if (profilesDir == null) return;
        if (!profilesDir.exists()) {
            profilesDir.mkdirs();
        }
        String profileFileBaseName = resolveProfileFileBaseName();
        File profileFile = new File(profilesDir, profileFileBaseName + ".profile");
        try (FileOutputStream fos = new FileOutputStream(profileFile)) {
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(fos, "UTF-8");
            serializer.startDocument("UTF-8", true);
            serializer.startTag(null, "profile");

            // Сохранение информации о пользователе
            serializer.startTag(null, "user");
            serializer.attribute(null, "name", this.UserNick);
            serializer.attribute(null, "password", this.UserPassword);
            serializer.attribute(null, "isEncrypted", String.valueOf(this.isEncrypted));
            serializer.attribute(null, "autologon", String.valueOf(this.UserAutoLogon));
            serializer.attribute(null, "lastlogon", String.valueOf(this.LastLogin));
            serializer.endTag(null, "user");

            // C# parity: сериализуем proxy-тег профиля в формате
            // `<proxy active address username password>`.
            serializer.startTag(null, "proxy");
            serializer.attribute(null, "active", String.valueOf(this.DoProxy));
            serializer.attribute(null, "address", this.ProxyAddress != null ? this.ProxyAddress : "");
            serializer.attribute(null, "username", this.ProxyUserName != null ? this.ProxyUserName : "");
            serializer.attribute(null, "password", this.ProxyPassword != null ? this.ProxyPassword : "");
            serializer.endTag(null, "proxy");

            // Сохранение контактов
            serializer.startTag(null, "contacts");
            for (Contact contact : this.contacts.values()) {
                serializer.startTag(null, "contactentry");
                serializer.attribute(null, "name", contact.nick);
                serializer.attribute(null, "classid", String.valueOf(contact.classId));
                serializer.endTag(null, "contactentry");
            }
            serializer.endTag(null, "contacts");

            // Настройки чата
            serializer.startTag(null, "chat");
            serializer.attribute(null, "height", String.valueOf(this.ChatHeight));
            serializer.attribute(null, "delay", String.valueOf(this.ChatDelay));
            serializer.attribute(null, "mode", String.valueOf(this.ChatMode));
            serializer.attribute(null, "keepLog", String.valueOf(this.ChatKeepLog));
            serializer.attribute(null, "autoAnswer", String.valueOf(this.DoAutoAnswer));
            serializer.attribute(null, "chatLevels", String.valueOf(this.DoChatLevels));
            serializer.attribute(null, "statsResetAtMidnight", String.valueOf(this.StatsResetAtMidnight));
            serializer.endTag(null, "chat");
            serializer.startTag(null, "mapset");
            serializer.attribute(null, "bigwidth", String.valueOf(this.MapBigWidth));
            serializer.attribute(null, "bigheight", String.valueOf(this.MapBigHeight));
            serializer.attribute(null, "bigscale", String.valueOf(this.MapBigScale));
            serializer.attribute(null, "cellfontsize", String.valueOf(this.MapCellFontSize));
            serializer.attribute(null, "rebuildfrompinfo", String.valueOf(this.MapRebuildFromPinfo));
            serializer.attribute(null, "cellchecktimeoutms", String.valueOf(this.MapCellCheckTimeoutMs));
            serializer.endTag(null, "mapset");

            // Настройки инвентаря (группировка/сортировка/массовые кнопки).
            serializer.startTag(null, "inventory");
            serializer.attribute(null, "buttonSell", String.valueOf(this.DoButtonSell));
            serializer.attribute(null, "buttonDrop", String.valueOf(this.DoButtonDrop));
            serializer.attribute(null, "pack", String.valueOf(this.DoInvPack));
            serializer.attribute(null, "packDolg", String.valueOf(this.DoInvPackDolg));
            serializer.attribute(null, "sort", String.valueOf(this.DoInvSort));
            serializer.endTag(null, "inventory");
            serializer.startTag(null, "showoverwarning");
            serializer.text(String.valueOf(this.ShowOverWarning));
            serializer.endTag(null, "showoverwarning");

            // Сохранение настроек быстрых действий
            serializer.startTag(null, "fastactions");
            serializer.attribute(null, "simple", String.valueOf(this.doShowFastAttack));
            serializer.attribute(null, "blood", String.valueOf(this.doShowFastAttackBlood));
            serializer.attribute(null, "ultimate", String.valueOf(this.doShowFastAttackUltimate));
            serializer.attribute(null, "closedultimate", String.valueOf(this.doShowFastAttackClosedUltimate));
            serializer.attribute(null, "closed", String.valueOf(this.doShowFastAttackClosed));
            serializer.attribute(null, "fist", String.valueOf(this.doShowFastAttackFist));
            serializer.attribute(null, "closedfist", String.valueOf(this.doShowFastAttackClosedFist));
            serializer.attribute(null, "opennevid", String.valueOf(this.doShowFastAttackOpenNevid));
            serializer.attribute(null, "poison", String.valueOf(this.doShowFastAttackPoison));
            serializer.attribute(null, "strong", String.valueOf(this.doShowFastAttackStrong));
            serializer.attribute(null, "nevid", String.valueOf(this.doShowFastAttackNevid));
            serializer.attribute(null, "fog", String.valueOf(this.doShowFastAttackFog));
            serializer.attribute(null, "zas", String.valueOf(this.doShowFastAttackZas));
            serializer.attribute(null, "totem", String.valueOf(this.doShowFastAttackTotem));
            serializer.attribute(null, "portal", String.valueOf(this.doShowFastAttackPortal));
            serializer.endTag(null, "fastactions");

            // C# parity (`UserConfigSave.WriteFish`): сохраняем настройки авто-рыбалки отдельным тегом.
            serializer.startTag(null, "autofish");
            serializer.attribute(null, "auto", String.valueOf(this.AutoFish));
            serializer.attribute(null, "tiedhigh", String.valueOf(this.FishTiedHigh));
            serializer.attribute(null, "tiedzero", String.valueOf(this.FishTiedZero));
            serializer.attribute(null, "drinkbliss", String.valueOf(this.FishDrinkBliss));
            serializer.attribute(null, "stopoverw", String.valueOf(this.FishStopOverWeight));
            serializer.attribute(null, "autowear", String.valueOf(this.FishAutoWear));
            serializer.attribute(null, "hand1", this.FishHandOne != null ? this.FishHandOne : "Любая удочка");
            serializer.attribute(null, "hand2", this.FishHandTwo != null ? this.FishHandTwo : "Нет");
            serializer.attribute(null, "enabledprims", String.valueOf(this.FishEnabledPrims));
            serializer.attribute(null, "um", String.valueOf(this.FishUm));
            serializer.attribute(null, "maxlevelbots", String.valueOf(this.FishMaxLevelBots));
            serializer.attribute(null, "chatreport", String.valueOf(this.FishChatReport));
            serializer.attribute(null, "chatreportcolor", String.valueOf(this.FishChatReportColor));
            // C# parity (`ConstAttibuteRazdChatReport`): признак вывода результата разделки в чат.
            //
            // Зависимости:
            // - читается в `load(...)` из `autofish@razdchatreport`;
            // - используется в `MainPhp.mainPhpGetSkinRes` при отправке системного сообщения.
            serializer.attribute(null, "razdchatreport", String.valueOf(this.RazdChatReport));
            serializer.endTag(null, "autofish");

            // C# parity (`<autodrinkblaz do tied>`): флаг и порог автопитья блажа.
            //
            // Зависимости:
            // - управляются из `SettingsActivity` (общие настройки);
            // - читаются при старте профиля и участвуют в runtime-решении авто-режимов.
            serializer.startTag(null, "autodrinkblaz");
            serializer.attribute(null, "do", String.valueOf(this.DoAutoDrinkBlaz));
            serializer.attribute(null, "tied", String.valueOf(this.AutoDrinkBlazTied));
            serializer.endTag(null, "autodrinkblaz");

            // C# parity (`autodrinkblazorder`): порядок поиска
            // (0: сначала зелье, потом эликсир; 1: сначала эликсир, потом зелье).
            // Сохраняем отдельным узлом для 1:1-совместимости с форматом ПК-профиля.
            serializer.startTag(null, "autodrinkblazorder");
            serializer.text(String.valueOf(this.AutoDrinkBlazOrder));
            serializer.endTag(null, "autodrinkblazorder");

            serializer.startTag(null, "dostopondig");
            serializer.text(String.valueOf(this.DoStopOnDig));
            serializer.endTag(null, "dostopondig");

            // Сохранение настроек AutoBoi (аналог UserConfigVars.cs / FormSettingsAb.cs)
            serializer.startTag(null, "autoboi");
            serializer.attribute(null, "enabled", String.valueOf(this.LezDoAutoboi));
            serializer.attribute(null, "fury", String.valueOf(this.LezDoFury));
            serializer.attribute(null, "waitHp", String.valueOf(this.LezDoWaitHp));
            serializer.attribute(null, "waitMa", String.valueOf(this.LezDoWaitMa));
            serializer.attribute(null, "waitHpVal", String.valueOf(this.LezWaitHp));
            serializer.attribute(null, "waitMaVal", String.valueOf(this.LezWaitMa));
            serializer.attribute(null, "drinkHp", String.valueOf(this.LezDoDrinkHp));
            serializer.attribute(null, "drinkMa", String.valueOf(this.LezDoDrinkMa));
            serializer.attribute(null, "drinkHpVal", String.valueOf(this.LezDrinkHp));
            serializer.attribute(null, "drinkMaVal", String.valueOf(this.LezDrinkMa));
            serializer.attribute(null, "winTimeout", String.valueOf(this.LezDoWinTimeout));
            serializer.attribute(null, "hitDelaySec", String.valueOf(Math.max(0, this.LezHitDelaySec)));
            serializer.attribute(null, "say", this.LezSay != null ? this.LezSay.name() : "No");
            serializer.endTag(null, "autoboi");

            // Профильный флаг авто-охоты (C# TagSkinAuto).
            serializer.startTag(null, "SkinAuto");
            serializer.text(String.valueOf(this.SkinAuto));
            serializer.endTag(null, "SkinAuto");

            // Сохранение групп противников (аналог LezBotsGroup сериализации в C#)
            serializer.startTag(null, "lezgroups");
            if (this.LezGroups != null) {
                for (LezBotsGroup g : this.LezGroups) {
                    serializer.startTag(null, "group");
                    serializer.attribute(null, "id", String.valueOf(g.Id));
                    serializer.attribute(null, "minLevel", String.valueOf(g.MinimalLevel));
                    serializer.attribute(null, "doRestoreHp", String.valueOf(g.DoRestoreHp));
                    serializer.attribute(null, "doRestoreMa", String.valueOf(g.DoRestoreMa));
                    serializer.attribute(null, "restoreHp", String.valueOf(g.RestoreHp));
                    serializer.attribute(null, "restoreMa", String.valueOf(g.RestoreMa));
                    serializer.attribute(null, "doAbilBlocks", String.valueOf(g.DoAbilBlocks));
                    serializer.attribute(null, "doAbilHits", String.valueOf(g.DoAbilHits));
                    serializer.attribute(null, "doMagHits", String.valueOf(g.DoMagHits));
                    serializer.attribute(null, "magHits", String.valueOf(g.MagHits));
                    serializer.attribute(null, "doMagBlocks", String.valueOf(g.DoMagBlocks));
                    serializer.attribute(null, "doHits", String.valueOf(g.DoHits));
                    serializer.attribute(null, "doBlocks", String.valueOf(g.DoBlocks));
                    serializer.attribute(null, "doMiscAbils", String.valueOf(g.DoMiscAbils));
                    serializer.attribute(null, "hitDelaySec", String.valueOf(Math.max(0, g.HitDelaySec)));
                    serializer.attribute(null, "doFury", String.valueOf(g.DoFury));
                    serializer.attribute(null, "doStopNow", String.valueOf(g.DoStopNow));
                    serializer.attribute(null, "doStopLowHp", String.valueOf(g.DoStopLowHp));
                    serializer.attribute(null, "doStopLowMa", String.valueOf(g.DoStopLowMa));
                    serializer.attribute(null, "stopLowHp", String.valueOf(g.StopLowHp));
                    serializer.attribute(null, "stopLowMa", String.valueOf(g.StopLowMa));
                    serializer.attribute(null, "doExit", String.valueOf(g.DoExit));
                    serializer.attribute(null, "doExitRisky", String.valueOf(g.DoExitRisky));
                    // Пер-групповый канал анонса нападения (Stop-вкладка, блок "Сообщение о нападении").
                    serializer.attribute(null, "say", g.AttackSay != null ? g.AttackSay.name() : LezSayType.No.name());
                    serializer.attribute(null, "spellsHits", intArrayToString(g.SpellsHits));
                    serializer.attribute(null, "spellsBlocks", intArrayToString(g.SpellsBlocks));
                    serializer.attribute(null, "spellsRestoreHp", intArrayToString(g.SpellsRestoreHp));
                    serializer.attribute(null, "spellsRestoreMa", intArrayToString(g.SpellsRestoreMa));
                    serializer.attribute(null, "spellsMisc", intArrayToString(g.SpellsMisc));
                    serializer.endTag(null, "group");
                }
            }
            serializer.endTag(null, "lezgroups");

            serializer.startTag(null, "navigator");
            serializer.attribute(null, "allowteleports", String.valueOf(this.NavigatorAllowTeleports));
            serializer.attribute(null, "village", locationsToCsv(this.NavCityVillageLocations));
            serializer.attribute(null, "forpost", locationsToCsv(this.NavCityForpostLocations));
            serializer.attribute(null, "oktal", locationsToCsv(this.NavCityOktalLocations));
            serializer.attribute(null, "citysubcategories", this.NavCitySubcategories != null ? this.NavCitySubcategories : "");
            serializer.attribute(null, "objects", locationsToCsv(this.NavObjectLocations));
            serializer.attribute(null, "teleports", locationsToCsv(this.NavTeleportLocations));
            serializer.endTag(null, "navigator");
            serializer.startTag(null, "favlocations");
            if (this.FavLocations != null) {
                for (String loc : this.FavLocations) {
                    if (loc != null && !loc.isEmpty()) {
                        serializer.startTag(null, "favlocation");
                        serializer.text(loc);
                        serializer.endTag(null, "favlocation");
                    }
                }
            }
            serializer.endTag(null, "favlocations");

            serializer.endTag(null, "profile");
            serializer.endDocument();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Удаляет файл текущего профиля.
     * @param context Контекст приложения.
     */
    public void delete(Context context) {
        File profilesDir = context.getExternalFilesDir("profiles");
        if (profilesDir == null) return;
        String profileFileBaseName = resolveProfileFileBaseName();
        File profileFile = new File(profilesDir, profileFileBaseName + ".profile");
        if (profileFile.exists()) {
            profileFile.delete();
        }
    }

    private String resolveProfileFileBaseName() {
        String candidate = id != null ? id.trim() : "";
        if (candidate.isEmpty()) {
            candidate = UserNick != null ? UserNick.trim() : "";
        }
        if (candidate.isEmpty()) {
            candidate = "profile_" + System.currentTimeMillis();
        }
        this.id = candidate;
        return candidate;
    }

    /**
     * Возвращает ник пользователя для отображения в списках.
     * @return Ник пользователя или пустая строка, если ник не установлен.
     */
    @Override
    public String toString() {
        return UserNick != null ? UserNick : "";
    }

    // --- Вспомогательные методы для XML сериализации LezGroups --- //

    private static int parseIntAttr(XmlPullParser parser, String attr, int defaultVal) {
        String val = getAttributeValueIgnoreCase(parser, attr);
        if (val == null || val.isEmpty()) return defaultVal;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return defaultVal; }
    }

    private static long parseLongAttr(XmlPullParser parser, String attr, long defaultVal) {
        String val = getAttributeValueIgnoreCase(parser, attr);
        if (val == null || val.isEmpty()) return defaultVal;
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return defaultVal; }
    }

    /**
     * Читает текстовое содержимое текущего XML-узла и возвращает fallback при ошибке.
     *
     * Назначение:
     * - используется для миграции legacy-профилей, где proxy-параметры хранились как отдельные узлы,
     *   а не как `<proxy ...>` атрибуты.
     *
     * Зависимости:
     * - вызов `parser.nextText()` допустим только из обработчика `START_TAG`;
     * - все исключения гасим локально, чтобы не ломать загрузку профиля целиком.
     */
    private static String parseNodeText(XmlPullParser parser, String fallback) {
        try {
            String value = parser.nextText();
            return value != null ? value : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Безопасно читает bool-значение из текстового XML-узла.
     */
    private static boolean parseBoolNodeText(XmlPullParser parser, boolean fallback) {
        String value = parseNodeText(parser, String.valueOf(fallback));
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static boolean parseBoolAttr(XmlPullParser parser, String attr, boolean defaultVal) {
        String val = getAttributeValueIgnoreCase(parser, attr);
        if (val == null || val.isEmpty()) return defaultVal;
        return Boolean.parseBoolean(val.trim());
    }

    private static String intArrayToString(int[] arr) {
        if (arr == null || arr.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static int[] parseIntArrayAttr(XmlPullParser parser, String attr) {
        String val = getAttributeValueIgnoreCase(parser, attr);
        if (val == null || val.isEmpty()) return new int[0];
        String[] parts = val.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { result[i] = Integer.parseInt(parts[i].trim()); } catch (NumberFormatException e) { result[i] = 0; }
        }
        return result;
    }

    private static String[] parseLocationsAttr(XmlPullParser parser, String attr, String[] currentValue) {
        String raw = getAttributeValueIgnoreCase(parser, attr);
        if (raw == null) {
            return currentValue != null ? currentValue : new String[0];
        }
        return sanitizeLocations(splitLocationsCsv(raw), currentValue);
    }

    private static String[] splitLocationsCsv(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return new String[0];
        }
        return csv.split("[,;\\s]+");
    }

    private static String[] sanitizeLocations(String[] values, String[] defaults) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String normalized = normalizeLocation(value);
                if (!normalized.isEmpty()) {
                    unique.add(normalized);
                }
            }
        }
        if (unique.isEmpty() && defaults != null) {
            for (String value : defaults) {
                String normalized = normalizeLocation(value);
                if (!normalized.isEmpty()) {
                    unique.add(normalized);
                }
            }
        }
        return unique.toArray(new String[0]);
    }

    private static String normalizeLocation(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().replace('_', '-');
        if (normalized.matches("\\d+-\\d+")) {
            return normalized;
        }
        return "";
    }

    private static String locationsToCsv(String[] values) {
        String[] normalized = sanitizeLocations(values, new String[0]);
        if (normalized.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < normalized.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(normalized[i]);
        }
        return sb.toString();
    }

    private static String getAttributeValueIgnoreCase(XmlPullParser parser, String attr) {
        String direct = parser.getAttributeValue(null, attr);
        if (direct != null) return direct;
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String name = parser.getAttributeName(i);
            if (name != null && name.equalsIgnoreCase(attr)) {
                return parser.getAttributeValue(i);
            }
        }
        return null;
    }

    /**
     * Нормализует список групп автобоя под поведение ПК-версии (FormSettingsAb + LezFight):
     * 1) всегда существует fallback-группа "Все 0+";
     * 2) группы отсортированы по приоритету подбора (Id DESC, MinimalLevel DESC),
     *    чтобы LezFight.SelectFoeGroup() мог брать первую подошедшую группу 1:1 как в C#.
     */
    /**
     * Нормализует proxy-флаги и строковые поля в единый C#-совместимый вид.
     *
     * Зависимости:
     * - `DoProxy` используется как основной runtime-флаг;
     * - `UseProxy` оставлен как legacy-поле старых Android профилей и синхронизируется с `DoProxy`;
     * - блок `<proxy ...>` при load/save должен опираться на уже нормализованные значения.
     */
    private void normalizeProxyFlags() {
        boolean merged = this.DoProxy || this.UseProxy;
        if (this.DoProxy != merged || this.UseProxy != merged) {
            android.util.Log.i(TAG, "normalizeProxyFlags: doProxy=" + this.DoProxy
                    + ", useProxy=" + this.UseProxy + " -> merged=" + merged);
        }
        this.DoProxy = merged;
        this.UseProxy = merged;
        if (this.ProxyAddress == null) this.ProxyAddress = "";
        if (this.ProxyUserName == null) this.ProxyUserName = "";
        if (this.ProxyPassword == null) this.ProxyPassword = "";
    }

    private void normalizeLezGroups() {
        if (this.LezGroups == null) {
            this.LezGroups = new ArrayList<>();
        }

        boolean hasAllGroup = false;
        for (LezBotsGroup group : this.LezGroups) {
            if (group != null && group.Id == 1 && group.MinimalLevel == 0) {
                hasAllGroup = true;
                break;
            }
        }
        if (!hasAllGroup) {
            this.LezGroups.add(new LezBotsGroup(1, 0));
        }

        Collections.sort(this.LezGroups);
    }

    /**
     * Возвращает `true`, если хотя бы в одной группе автобоя включён флаг `DoFury`.
     *
     * Назначение:
     * - агрегирует пер-групповые настройки в legacy-глобальный флаг `LezDoFury`;
     * - позволяет сохранить совместимость с участками кода, где ещё читается глобальный признак.
     *
     * Зависимости:
     * - вызывается при `load()`/`save()` для синхронизации `LezDoFury`;
     * - используется `LoginActivity`, `AutoFunctionsManager`, `MainPhp` для runtime-решения
     *   о включении оркестрации AutoFury.
     */
    public boolean hasAnyLezFuryGroup() {
        if (this.LezGroups == null || this.LezGroups.isEmpty()) {
            return false;
        }
        for (LezBotsGroup group : this.LezGroups) {
            if (group != null && group.DoFury) {
                return true;
            }
        }
        return false;
    }
}

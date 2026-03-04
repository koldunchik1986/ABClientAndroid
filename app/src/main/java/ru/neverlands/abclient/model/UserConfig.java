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
    public boolean DoInvSort = true;
    public String TorgTabl = "";

    // --- Поля из SettingsActivity и других мест --- //
    public boolean DoPromptExit = true;
    public boolean DoHttpLog = false;
    public boolean DoTexLog = false;
    public boolean ShowPerformance = false;
    public boolean DoProxy = false;
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
    public String FishHandTwo = "";
    /**
     * Авто-охота (аналог `SkinAuto` в ПК C# профиле).
     * Используется как профильный флаг для логики `buttonAutoSkin`.
     */
    public boolean SkinAuto = false;
    public boolean AutoHerb = false;
    public boolean AutoMine = false;
    public boolean AutoTree = false;
    public boolean AutoDig = false;
    public boolean AutoTorg = false;
    public boolean TorgActive = false;
    public boolean DoGuamod = false;

    public int ChatHeight = 115;
    public int ChatDelay = 10;
    public int ChatMode = 0;
    public boolean ChatKeepLog = true;
    public boolean DoAutoAnswer = false;
    public boolean DoChatLevels = false;
    // Разница между локальным временем и временем сервера (мс). Аналог ServDiff в C#.
    // Используется для корректного отображения "серверных" часов в чате и событиях.
    public long ServDiff = Long.MIN_VALUE;

    // --- Lez AutoBoi --- //
    public boolean LezDoAutoboi = true;
    public boolean LezDoWaitHp = false;
    public boolean LezDoWaitMa = false;
    public int LezWaitHp = 100;
    public int LezWaitMa = 100;
    public boolean LezDoDrinkHp = false;
    public boolean LezDoDrinkMa = true;
    public int LezDrinkHp = 50;
    public int LezDrinkMa = 50;
    public boolean LezDoWinTimeout = true;
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

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("user".equals(tagName)) {
                        this.UserNick = parser.getAttributeValue(null, "name");
                        this.UserPassword = parser.getAttributeValue(null, "password");
                        String isEncryptedStr = parser.getAttributeValue(null, "isEncrypted");
                        this.isEncrypted = "true".equalsIgnoreCase(isEncryptedStr);
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
                    } else if ("autoboi".equals(tagName)) {
                        this.LezDoAutoboi = Boolean.parseBoolean(parser.getAttributeValue(null, "enabled"));
                        this.LezDoWaitHp = Boolean.parseBoolean(parser.getAttributeValue(null, "waitHp"));
                        this.LezDoWaitMa = Boolean.parseBoolean(parser.getAttributeValue(null, "waitMa"));
                        this.LezWaitHp = parseIntAttr(parser, "waitHpVal", 100);
                        this.LezWaitMa = parseIntAttr(parser, "waitMaVal", 100);
                        this.LezDoDrinkHp = Boolean.parseBoolean(parser.getAttributeValue(null, "drinkHp"));
                        this.LezDoDrinkMa = Boolean.parseBoolean(parser.getAttributeValue(null, "drinkMa"));
                        this.LezDrinkHp = parseIntAttr(parser, "drinkHpVal", 50);
                        this.LezDrinkMa = parseIntAttr(parser, "drinkMaVal", 50);
                        this.LezDoWinTimeout = Boolean.parseBoolean(parser.getAttributeValue(null, "winTimeout"));
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
                    }
                }
                eventType = parser.next();
            }
            normalizeLezGroups();
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
        // Перед сохранением приводим список к C#-совместимому виду:
        // - гарантируем наличие группы "Все 0+"
        // - сортируем по LezBotsGroup.compareTo() (Id DESC, MinimalLevel DESC)
        normalizeLezGroups();

        File profilesDir = context.getExternalFilesDir("profiles");
        if (profilesDir == null) return;
        if (!profilesDir.exists()) {
            profilesDir.mkdirs();
        }
        File profileFile = new File(profilesDir, this.UserNick + ".profile");
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
            serializer.endTag(null, "user");

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
            serializer.endTag(null, "chat");

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

            // Сохранение настроек AutoBoi (аналог UserConfigVars.cs / FormSettingsAb.cs)
            serializer.startTag(null, "autoboi");
            serializer.attribute(null, "enabled", String.valueOf(this.LezDoAutoboi));
            serializer.attribute(null, "waitHp", String.valueOf(this.LezDoWaitHp));
            serializer.attribute(null, "waitMa", String.valueOf(this.LezDoWaitMa));
            serializer.attribute(null, "waitHpVal", String.valueOf(this.LezWaitHp));
            serializer.attribute(null, "waitMaVal", String.valueOf(this.LezWaitMa));
            serializer.attribute(null, "drinkHp", String.valueOf(this.LezDoDrinkHp));
            serializer.attribute(null, "drinkMa", String.valueOf(this.LezDoDrinkMa));
            serializer.attribute(null, "drinkHpVal", String.valueOf(this.LezDrinkHp));
            serializer.attribute(null, "drinkMaVal", String.valueOf(this.LezDrinkMa));
            serializer.attribute(null, "winTimeout", String.valueOf(this.LezDoWinTimeout));
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
        File profileFile = new File(profilesDir, this.UserNick + ".profile");
        if (profileFile.exists()) {
            profileFile.delete();
        }
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
}

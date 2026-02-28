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
                    } else if ("group".equals(tagName)) {
                        LezBotsGroup g = new LezBotsGroup(
                            parseIntAttr(parser, "id", 1),
                            parseIntAttr(parser, "minLevel", 0)
                        );
                        g.DoRestoreHp = Boolean.parseBoolean(parser.getAttributeValue(null, "doRestoreHp"));
                        g.DoRestoreMa = Boolean.parseBoolean(parser.getAttributeValue(null, "doRestoreMa"));
                        g.RestoreHp = parseIntAttr(parser, "restoreHp", 100);
                        g.RestoreMa = parseIntAttr(parser, "restoreMa", 100);
                        g.DoAbilBlocks = Boolean.parseBoolean(parser.getAttributeValue(null, "doAbilBlocks"));
                        g.DoAbilHits = Boolean.parseBoolean(parser.getAttributeValue(null, "doAbilHits"));
                        g.DoMagHits = Boolean.parseBoolean(parser.getAttributeValue(null, "doMagHits"));
                        g.MagHits = parseIntAttr(parser, "magHits", 5);
                        g.DoMagBlocks = Boolean.parseBoolean(parser.getAttributeValue(null, "doMagBlocks"));
                        g.DoHits = Boolean.parseBoolean(parser.getAttributeValue(null, "doHits"));
                        g.DoBlocks = Boolean.parseBoolean(parser.getAttributeValue(null, "doBlocks"));
                        g.DoMiscAbils = Boolean.parseBoolean(parser.getAttributeValue(null, "doMiscAbils"));
                        g.DoStopNow = Boolean.parseBoolean(parser.getAttributeValue(null, "doStopNow"));
                        g.DoStopLowHp = Boolean.parseBoolean(parser.getAttributeValue(null, "doStopLowHp"));
                        g.DoStopLowMa = Boolean.parseBoolean(parser.getAttributeValue(null, "doStopLowMa"));
                        g.StopLowHp = parseIntAttr(parser, "stopLowHp", 0);
                        g.StopLowMa = parseIntAttr(parser, "stopLowMa", 0);
                        g.DoExit = Boolean.parseBoolean(parser.getAttributeValue(null, "doExit"));
                        g.DoExitRisky = Boolean.parseBoolean(parser.getAttributeValue(null, "doExitRisky"));
                        g.SpellsHits = parseIntArrayAttr(parser, "spellsHits");
                        g.SpellsBlocks = parseIntArrayAttr(parser, "spellsBlocks");
                        g.SpellsRestoreHp = parseIntArrayAttr(parser, "spellsRestoreHp");
                        g.SpellsRestoreMa = parseIntArrayAttr(parser, "spellsRestoreMa");
                        g.SpellsMisc = parseIntArrayAttr(parser, "spellsMisc");
                        // Обновляем или добавляем группу (Id=1,MinLevel=0 — группа "Все", всегда существует)
                        boolean found = false;
                        for (int gi = 0; gi < this.LezGroups.size(); gi++) {
                            if (this.LezGroups.get(gi).Id == g.Id) {
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
        String val = parser.getAttributeValue(null, attr);
        if (val == null || val.isEmpty()) return defaultVal;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return defaultVal; }
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
        String val = parser.getAttributeValue(null, attr);
        if (val == null || val.isEmpty()) return new int[0];
        String[] parts = val.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { result[i] = Integer.parseInt(parts[i].trim()); } catch (NumberFormatException e) { result[i] = 0; }
        }
        return result;
    }
}

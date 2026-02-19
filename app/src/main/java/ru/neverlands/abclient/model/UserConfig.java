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
}
package ru.neverlands.abclient.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "contacts")
public class Contact {

    @PrimaryKey
    @NonNull
    public String playerID;

    public String nick;
    public int playerLevel;
    public int inclination;
    public String inclinationName;
    public String clanNumber;
    public String clanIco;
    public String clanName;
    public String clanStatus;
    public int gender;
    public int blockStatus;
    public int jailStatus;
    public int muteSeconds;
    public int muteForumSeconds;
    public int onlineStatus;
    public String geoLocation;
    public String warLogNumber;

    public int classId; // Для обратной совместимости

    // Пустой конструктор для Room
    public Contact() {}

    // Старый конструктор для обратной совместимости
    @androidx.room.Ignore
    public Contact(String nick, int classId) {
        this.nick = nick;
        this.classId = classId;
        this.playerID = nick; // В старой логике ID был просто ником
    }

    // Методы для обратной совместимости
    public String getName() {
        return nick;
    }

    public int getClassId() {
        return classId;
    }
}
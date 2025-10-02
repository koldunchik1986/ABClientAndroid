package ru.neverlands.abclient.model;

import java.util.Date;

public class ChatUser {
    public final String nick;
    public final String level;
    public final String sign;
    public final String status;
    public final Date lastUpdated;

    public ChatUser(String nick, String level, String sign, String status) {
        this.nick = nick;
        this.level = level;
        this.sign = sign.equalsIgnoreCase("none") ? "" : sign;
        this.status = status;
        this.lastUpdated = new Date();
    }

    public String getNick() {
        return nick;
    }

    public String getLevel() {
        return level;
    }

    public String getSign() {
        return sign;
    }

    public String getStatus() {
        return status;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }
}

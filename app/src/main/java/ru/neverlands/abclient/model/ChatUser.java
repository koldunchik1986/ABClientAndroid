package ru.neverlands.abclient.model;

// Placeholder class based on analysis of Pinfo.cs
public class ChatUser {
    public String nick;
    public String level;
    public String sign;
    public String clan;
    public String alignIcon;
    public String alignName;

    public ChatUser(String nick, String level, String sign, String clan, String alignIcon, String alignName) {
        this.nick = nick;
        this.level = level;
        this.sign = sign;
        this.clan = clan;
        this.alignIcon = alignIcon;
        this.alignName = alignName;
    }
}

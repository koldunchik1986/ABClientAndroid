package ru.neverlands.abclient.manager;

import java.util.HashMap;
import java.util.Map;

import ru.neverlands.abclient.model.ChatUser;

// Placeholder class based on analysis of Pinfo.cs
public class ChatUserList {
    private static final Map<String, ChatUser> users = new HashMap<>();

    public static void addUser(ChatUser user) {
        if (user != null && user.nick != null) {
            users.put(user.nick.toLowerCase(), user);
        }
    }

    public static ChatUser getUser(String nick) {
        if (nick == null) {
            return null;
        }
        return users.get(nick.toLowerCase());
    }

    public static boolean hasUser(String nick) {
        if (nick == null) {
            return false;
        }
        return users.containsKey(nick.toLowerCase());
    }
}

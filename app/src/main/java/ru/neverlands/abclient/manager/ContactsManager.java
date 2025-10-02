package ru.neverlands.abclient.manager;

import ru.neverlands.abclient.model.Contact;
import ru.neverlands.abclient.utils.AppVars;

/**
 * Управляет доступом к списку контактов пользователя.
 */
public class ContactsManager {
    /**
     * Возвращает ID класса для указанного контакта (друга, врага или нейтрала).
     * @param name Имя контакта.
     * @return ID класса (0: нейтрал, 1: враг, 2: друг) или -1, если контакт не найден.
     */
    public static int getClassIdOfContact(String name) {
        if (name == null || name.isEmpty() || AppVars.Profile == null || AppVars.Profile.contacts == null) {
            return -1;
        }

        Contact contact = AppVars.Profile.contacts.get(name.toLowerCase());
        if (contact != null) {
            return contact.getClassId();
        } else {
            return -1;
        }
    }
}

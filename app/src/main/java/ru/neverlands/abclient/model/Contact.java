package ru.neverlands.abclient.model;

/**
 * Модель, представляющая один контакт из списка контактов пользователя.
 */
public class Contact {
    // Имя персонажа
    private String name;
    // ID класса контакта (0: нейтрал, 1: враг, 2: друг)
    private int classId;

    public Contact(String name, int classId) {
        this.name = name;
        this.classId = classId;
    }

    public String getName() {
        return name;
    }

    public int getClassId() {
        return classId;
    }
}

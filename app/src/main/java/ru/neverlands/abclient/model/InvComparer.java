package ru.neverlands.abclient.model;

import java.util.Comparator;

public class InvComparer implements Comparator<InvEntry> {
    @Override
    public int compare(InvEntry o1, InvEntry o2) {
        return o1.compareTo(o2);
    }
}

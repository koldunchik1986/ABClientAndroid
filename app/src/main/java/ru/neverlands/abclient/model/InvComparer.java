package ru.neverlands.abclient.model;

import java.util.Comparator;

// C# Port of InvComparer.cs
public class InvComparer implements Comparator<InvEntry> {
    @Override
    public int compare(InvEntry x, InvEntry y) {
        // TODO: Port the full comparison logic from C#.
        // For now, a simple name comparison will do as a placeholder.
        if (x == null || y == null) {
            return 0;
        }
        return x.Name.compareTo(y.Name);
    }
}
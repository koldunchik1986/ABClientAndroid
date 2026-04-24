package ru.neverlands.anclient.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class AncCell {
    @PrimaryKey
    @NonNull
    public String RegNum;
    public String Label;
    public int Cost;
    public long Visited;
    public long Verified;
}

package ru.neverlands.abclient.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import java.util.Map;

@Entity(tableName = "things")
@TypeConverters(MapTypeConverter.class)
public class Thing {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;
    public String image;
    public String description;
    public Map<String, String> requirements;
    public Map<String, String> bonuses;
}

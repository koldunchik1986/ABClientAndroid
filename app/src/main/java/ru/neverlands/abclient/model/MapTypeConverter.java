package ru.neverlands.abclient.model;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;

public class MapTypeConverter {
    private static final Gson gson = new Gson();

    @TypeConverter
    public static Map<String, String> fromString(String value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        Type mapType = new TypeToken<Map<String, String>>() {}.getType();
        return gson.fromJson(value, mapType);
    }

    @TypeConverter
    public static String fromMap(Map<String, String> map) {
        if (map == null) {
            return null;
        }
        return gson.toJson(map);
    }
}

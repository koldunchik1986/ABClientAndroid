package ru.neverlands.abclient.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "things")
data class Thing(
    @PrimaryKey val name: String,
    val image: String,
    val description: String,
    val requirements: Map<String, String>,
    val bonuses: Map<String, String>
)

class MapTypeConverter {
    @TypeConverter
    fun fromString(value: String?): Map<String, String> {
        if (value == null) {
            return emptyMap()
        }
        val mapType = object : TypeToken<Map<String, String>>() {}.type
        return Gson().fromJson(value, mapType)
    }

    @TypeConverter
    fun fromMap(map: Map<String, String>?): String {
        if (map == null) {
            return ""
        }
        val gson = Gson()
        return gson.toJson(map)
    }
}

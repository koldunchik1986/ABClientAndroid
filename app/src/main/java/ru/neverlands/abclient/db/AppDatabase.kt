package ru.neverlands.abclient.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.neverlands.abclient.model.MapTypeConverter
import ru.neverlands.abclient.model.Thing

@Database(entities = [Thing::class], version = 1, exportSchema = false)
@TypeConverters(MapTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun thingDao(): ThingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "abclient_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

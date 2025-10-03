package ru.neverlands.abclient.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import ru.neverlands.abclient.model.Contact;
import ru.neverlands.abclient.model.MapTypeConverter;
import ru.neverlands.abclient.model.Thing;

@Database(entities = {Thing.class, Contact.class}, version = 4, exportSchema = false)
@TypeConverters(MapTypeConverter.class)
public abstract class AppDatabase extends RoomDatabase {

    public abstract ThingDao thingDao();
    public abstract ContactDao contactDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "abclient_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}

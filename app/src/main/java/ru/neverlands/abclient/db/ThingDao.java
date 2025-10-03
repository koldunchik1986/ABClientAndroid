package ru.neverlands.abclient.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import ru.neverlands.abclient.model.Thing;

@Dao
public interface ThingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Thing> things);

    @Query("SELECT COUNT(*) FROM things")
    int getCount();

    @Query("SELECT * FROM things WHERE image = :image LIMIT 1")
    LiveData<List<Thing>> findByImage(String image);
}

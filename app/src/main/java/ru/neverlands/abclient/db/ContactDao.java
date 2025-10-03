package ru.neverlands.abclient.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import ru.neverlands.abclient.model.Contact;

@Dao
public interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(Contact contact);

    @Query("SELECT * FROM contacts ORDER BY nick COLLATE NOCASE ASC")
    List<Contact> getAll();

    @Query("DELETE FROM contacts WHERE playerID = :playerId")
    void deleteById(String playerId);
}

package ru.neverlands.abclient.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.neverlands.abclient.model.Thing

@Dao
interface ThingDao {
    @Query("SELECT * FROM things WHERE image = :image")
    fun findByImage(image: String): LiveData<List<Thing>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(things: List<Thing>)

    @Query("SELECT COUNT(*) FROM things")
    fun getCount(): Int

    @Query("DELETE FROM things")
    fun clearAll()
}
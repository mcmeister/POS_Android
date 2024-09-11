package com.example.pos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ItemDao {
    @Query("SELECT * FROM item WHERE id = :itemId LIMIT 1")
    fun getItemById(itemId: Int): Item?

    @Query("SELECT name FROM item WHERE id = :itemId LIMIT 1")
    fun getItemNameById(itemId: Int): String?

    @Query("SELECT * FROM item")
    fun getAllItems(): List<Item>

    @Insert
    fun insertItem(item: Item): Long

    @Update
    fun updateItem(item: Item)
}

package com.example.pos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SaleDao {
    @Insert
    fun insertSale(sale: Sale)

    @Query("SELECT MAX(id) FROM sale")
    fun getLastSaleId(): Int?

    @Query("SELECT * FROM sale WHERE timestamp BETWEEN :startDate AND :endDate")
    fun getSalesBetween(startDate: Long, endDate: Long): List<Sale>

    // New query to get all sales channels
    @Query("SELECT name FROM sales_channel")
    fun getAllSalesChannels(): List<String>

    // New method to insert a new sales channel
    @Insert
    fun insertSalesChannel(salesChannel: SalesChannel)
}
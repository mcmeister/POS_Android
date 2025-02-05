package com.example.pos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SaleDao {
    @Insert
    fun insertSale(sale: Sale)

    @Insert
    suspend fun insertSales(sales: List<Sale>)

    @Query("SELECT MAX(id) FROM sale")
    fun getLastSaleId(): Int?

    @Query("UPDATE sale SET cancelled = 1 WHERE id = :saleId")
    fun markSaleAsCancelled(saleId: Int)

    @Query("SELECT * FROM sale WHERE timestamp BETWEEN :startDate AND :endDate ORDER BY orderId DESC")
    fun getSalesBetween(startDate: Long, endDate: Long): List<Sale>

    // New query to get all sales channels
    @Query("SELECT * FROM sales_channel")
    fun getAllSalesChannels(): List<SalesChannel>

    // New query to get all sales channels
    @Query("SELECT * FROM sales_channel WHERE deleted = 0")
    fun getActiveSalesChannels(): List<SalesChannel>

    // New method to insert a new sales channel
    @Insert
    fun insertSalesChannel(salesChannel: SalesChannel)

    @Query("SELECT SUM(salePrice*quantity) FROM sale WHERE timestamp >= :startDate AND timestamp <= :endDate")
    suspend fun getTotalSalesBetween(startDate: Long, endDate: Long): Double

    @Query("""
        SELECT SUM(s.profit * ((100 - sc.discount) / 100.0))
        FROM sale s
        JOIN sales_channel sc ON s.salesChannel = sc.name
        WHERE s.timestamp >= :startDate AND s.timestamp <= :endDate
        AND s.cancelled != 1
    """)
    suspend fun getTotalProfitBetween(startDate: Long, endDate: Long): Double

    @Query("SELECT * FROM sale WHERE timestamp BETWEEN :startDate AND :endDate ORDER BY orderId ASC")
    fun getSalesReport(startDate: Long, endDate: Long): List<SalesReport>

    @Query("SELECT * FROM sales_channel WHERE deleted = 0")
    fun getAllSalesChannelsWithDiscounts(): List<SalesChannel>

    @Query("UPDATE sales_channel SET deleted = 1 WHERE id = :channelId")
    fun markChannelAsDeleted(channelId: Int)

    @Query("UPDATE sales_channel SET name = :name, discount = :discount WHERE id = :channelId")
    fun updateSalesChannel(name: String, discount: Int, channelId: Int)

    @Query("SELECT MAX(orderId) FROM sale")
    fun getLastOrderId(): Int?
}
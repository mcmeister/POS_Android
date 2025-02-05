package com.example.pos

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sale")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // ✅ Auto-generated unique ID
    val orderId: Int, // ✅ Sequential order number
    val itemId: Int,
    val itemName: String,
    val quantity: Int,
    val salePrice: Double,
    val rawPrice: Double,
    val profit: Int,
    val salesChannel: String,
    val timestamp: Long,
    val cancelled: Int = 0
)

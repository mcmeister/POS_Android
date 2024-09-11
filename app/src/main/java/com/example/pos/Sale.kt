package com.example.pos

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sale")
data class Sale(
    @PrimaryKey val id: Int = 0,  // Manually handled
    val itemId: Int,              // The ID of the item being sold
    val itemName: String,         // New Item Name field
    val quantity: Int,
    val rawPrice: Int,
    val salePrice: Int,
    val profit: Int,
    val salesChannel: String,     // Sales Channel field
    val timestamp: Long           // When the sale happened
)

package com.example.pos

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sale")
data class Sale(
    @PrimaryKey val id: Int = 0,
    val itemId: Int,
    val itemName: String,
    val quantity: Int,
    val rawPrice: Int,
    val salePrice: Int,
    val profit: Int,
    val salesChannel: String,
    val timestamp: Long,
    val cancelled: Int = 0
)

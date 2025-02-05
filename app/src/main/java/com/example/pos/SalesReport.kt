package com.example.pos

data class SalesReport(
    val id: Int,
    val orderId: Int, // ✅ Ensure this exists
    val itemId: Int,
    val itemName: String,
    val quantity: Int,
    val salePrice: Double,
    val rawPrice: Double,
    val profit: Int,
    val salesChannel: String,
    val timestamp: Long,
    val cancelled: Int
)
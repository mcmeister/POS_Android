package com.example.pos

data class SalesReport(
    val id: Int,
    val itemName: String,
    val quantity: Int,
    val salePrice: Double,
    val salesChannel: String,
    val profit: Double,
    val timestamp: Long
)
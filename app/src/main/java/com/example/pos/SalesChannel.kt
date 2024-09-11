package com.example.pos

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sales_channel")
data class SalesChannel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)
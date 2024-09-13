package com.example.pos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ExpenseDao {

    // Insert new expense
    @Insert
    suspend fun insertExpense(expense: Expense)

    // Query to select id, amount, and timestamp
    @Query("SELECT id, amount, timestamp FROM expense")
    suspend fun getAllExpenses(): List<Expense>

    @Query("SELECT SUM(amount) FROM expense WHERE timestamp >= :startDate AND timestamp <= :endDate")
    suspend fun getTotalExpenseBetween(startDate: Long, endDate: Long): Double
}
package com.example.pos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Item::class, Sale::class, SalesChannel::class, Expense::class], version = 8, exportSchema = false) // Updated to version 8
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun saleDao(): SaleDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        // Migration logic from version 5 to 6
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new 'sales_channel' table if not already present
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sales_channel` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL
                    )
                """.trimIndent())

                // Add 'profit' column to 'sale' table if not already present
                db.execSQL("""
                    ALTER TABLE `sale` ADD COLUMN `profit` INTEGER NOT NULL DEFAULT 0
                """.trimIndent())

                // Add 'itemName' column to 'sale' table if not already present
                db.execSQL("""
                    ALTER TABLE `sale` ADD COLUMN `itemName` TEXT NOT NULL DEFAULT ''
                """.trimIndent())
            }
        }

        // Migration logic from version 6 to 7
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new 'expense' table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `expense` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `amount` REAL NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        // New migration logic from version 7 to 8
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add 'cancelled' column to 'sale' table, set default value to 0
                db.execSQL("""
            ALTER TABLE `sale` ADD COLUMN `cancelled` INTEGER NOT NULL DEFAULT 0
        """.trimIndent())

                // Add 'discount' and 'deleted' columns to 'sales_channel' table, set default values to 0
                db.execSQL("""
            ALTER TABLE `sales_channel` ADD COLUMN `discount` INTEGER NOT NULL DEFAULT 0
        """.trimIndent())

                db.execSQL("""
            ALTER TABLE `sales_channel` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "pos_database"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build().also { instance = it }
            }
    }
}

package com.smartexpensetracker

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "expenses", indices = [Index(value = ["smsHash"], unique = true)])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val receiver: String = "",
    val category: String = "Other",
    val transactionType: String = "EXPENSE",
    val paymentMethod: String = "Unknown",
    val bankName: String = "",
    val accountReference: String = "",
    val transactionReference: String = "",
    val transactionDate: Long = System.currentTimeMillis(),
    val smsSender: String = "",
    val smsHash: String = "",
    val notes: String = "",
    val manuallyAdded: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val amount: Double,
    val monthKey: String
)

@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val frequency: String = "Monthly",
    val nextDate: Long = System.currentTimeMillis(),
    val reminderEnabled: Boolean = true
)

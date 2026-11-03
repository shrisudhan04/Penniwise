package com.smartexpensetracker

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY transactionDate DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE transactionType='EXPENSE'")
    fun observeExpenseTotal(): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE transactionType='INCOME'")
    fun observeIncomeTotal(): Flow<Double>

    @Query("SELECT * FROM expenses WHERE transactionDate BETWEEN :start AND :end ORDER BY transactionDate DESC")
    suspend fun range(start: Long, end: Long): List<ExpenseEntity>
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY category")
    fun observeBudgets(): Flow<List<BudgetEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity)
    @Query("DELETE FROM budgets WHERE id=:id")
    suspend fun delete(id: Long)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY nextDate")
    fun observeRecurring(): Flow<List<RecurringEntity>>
    @Insert
    suspend fun insert(item: RecurringEntity)
    @Query("DELETE FROM recurring WHERE id=:id")
    suspend fun delete(id: Long)
}

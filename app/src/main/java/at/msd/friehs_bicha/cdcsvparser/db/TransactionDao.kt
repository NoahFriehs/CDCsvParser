package at.msd.friehs_bicha.cdcsvparser.db

import androidx.room.*
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransaction(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(transactions: List<Transaction>)

    @Query("SELECT * FROM transactions")
    fun getAllTransactions(): List<Transaction>

    @Query("DELETE FROM transactions")
    fun deleteAll()
}
package at.msd.friehs_bicha.cdcsvparser.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction

@Dao
interface CardTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransaction(transaction: CroCardTransaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(transactions: List<CroCardTransaction>)

    @Query("SELECT * FROM card_transactions")
    fun getAllTransactions(): List<CroCardTransaction>

    @Query("DELETE FROM card_transactions")
    fun deleteAll()
}
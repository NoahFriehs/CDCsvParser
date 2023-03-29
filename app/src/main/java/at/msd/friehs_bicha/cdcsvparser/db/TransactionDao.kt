package at.msd.friehs_bicha.cdcsvparser.db

import androidx.room.*
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction

@Dao
interface TransactionDao {
    @get:Query("SELECT * FROM transactions")
    val all: List<Transaction?>?

    //@Query("SELECT * FROM transactions WHERE transactionId IN (:txIds)")
    //fun loadAllByIds(txIds: IntArray?): List<Transaction?>?

    @Query("SELECT * FROM transactions WHERE walletId IN (:txIds)")
    fun loadAllByWalletIds(txIds: IntArray?): List<Transaction?>?

    @Insert
    fun insertAll(txs: List<Transaction?>?)

    @Delete
    fun delete(transaction: Transaction?)

    @Query("DELETE FROM transactions")
    fun deleteAll()
}
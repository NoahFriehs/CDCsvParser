package at.msd.friehs_bicha.cdcsvparser.db

import androidx.lifecycle.LiveData
import androidx.room.*
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet
import at.msd.friehs_bicha.cdcsvparser.wallet.WalletWithTransactions

@Dao
interface WalletDao {
    @get:Query("SELECT * FROM wallet")
    val all: List<WalletWithTransactions?>?

    @Query("SELECT * FROM wallet WHERE walletId IN (:walletIds)")
    fun loadAllByIds(walletIds: IntArray?): List<WalletWithTransactions?>?

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(wallets: List<Wallet?>?)

    @Delete
    fun delete(wallet: Wallet?)

    @Query("DELETE FROM wallet")
    fun deleteAll()

    @get:Query("SELECT * FROM wallet")
    @get:Transaction
    val walletsWithTransactions: LiveData<List<WalletWithTransactions?>?>?

    @get:Query("SELECT * FROM wallet")
    val allWallets: LiveData<List<Wallet?>?>?
}
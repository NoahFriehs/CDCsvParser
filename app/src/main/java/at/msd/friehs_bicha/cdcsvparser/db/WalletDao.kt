package at.msd.friehs_bicha.cdcsvparser.db

import androidx.lifecycle.LiveData
import androidx.room.*
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet

@Dao
interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWallet(wallet: Wallet)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(wallets: List<Wallet>)

    @Transaction
    @Query("SELECT * FROM wallets")
    fun getAllWallets(): List<WalletWithTransactions>

    @Transaction
    @Query("SELECT * FROM wallets")
    fun getAllWalletsWithTransactions(): LiveData<List<WalletWithTransactions>>

    @Query("DELETE FROM wallets")
    fun deleteAll()

}
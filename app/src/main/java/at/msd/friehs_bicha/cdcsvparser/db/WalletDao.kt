package at.msd.friehs_bicha.cdcsvparser.db

import androidx.lifecycle.LiveData
import androidx.room.*
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet
//
//@Dao
//interface WalletDao {
//    @get:Query("SELECT * FROM wallet")
//    val all: List<Wallet?>?
//
//    //@Query("SELECT * FROM wallet WHERE walletId IN (:walletIds)")
//    //fun loadAllByIds(walletIds: IntArray?): List<WalletWithTransactions?>?
//
//    @Transaction
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    fun insertAll(wallets: List<Wallet?>?)
//
//    @Delete
//    fun delete(wallet: Wallet?)
//
//    @Query("DELETE FROM wallet")
//    fun deleteAll()
//
//    @get:Query("SELECT * FROM wallet")
//    @get:Transaction
//    val walletsWithTransactions: LiveData<List<Wallet?>?>?
//
//    @get:Query("SELECT * FROM wallet")
//    val allWallets: LiveData<List<Wallet?>?>?
//}

@Dao
interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWallet(wallet: Wallet)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(wallets: List<Wallet>)

    @Query("SELECT * FROM wallets")
    fun getAllWallets(): List<WalletWithTransactions>

    @Transaction
    @Query("SELECT * FROM wallets")
    fun getAllWalletsWithTransactions(): LiveData<List<WalletWithTransactions>>

    @Query("DELETE FROM wallets")
    fun deleteAll()

}
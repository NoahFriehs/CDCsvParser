package at.msd.friehs_bicha.cdcsvparser.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet

@Dao
interface CardWalletDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWallet(wallet: CroCardWallet)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(wallets: List<CroCardWallet>)

    @Transaction
    @Query("SELECT * FROM card_wallets")
    fun getAllWallets(): List<CardWalletWithTransactions>

    @Query("DELETE FROM card_wallets")
    fun deleteAll()

}
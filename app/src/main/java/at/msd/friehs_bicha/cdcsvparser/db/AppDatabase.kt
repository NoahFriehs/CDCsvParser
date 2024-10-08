package at.msd.friehs_bicha.cdcsvparser.db

import android.content.Context
import androidx.room.*
import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.util.Converter
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet

@Database(entities = [Wallet::class, Transaction::class, CroCardWallet::class, CroCardTransaction::class], version = 3)
@TypeConverters(Converter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun transactionDao(): TransactionDao

    abstract fun cardWalletDao(): CardWalletDao

    abstract fun cardTransactionDao(): CardTransactionDao


    override fun clearAllTables() {}

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        @Synchronized
        fun getInstance(context: Context): AppDatabase? {
            if (INSTANCE == null) {
                INSTANCE = Room.databaseBuilder(context.applicationContext,
                        AppDatabase::class.java, "my-database-name")
                        .fallbackToDestructiveMigration()
                        .build()
            }
            return INSTANCE
        }
    }
}
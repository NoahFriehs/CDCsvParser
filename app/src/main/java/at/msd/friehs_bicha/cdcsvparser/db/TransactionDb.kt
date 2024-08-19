package at.msd.friehs_bicha.cdcsvparser.db
//
//import android.content.Context
//import androidx.room.Database
//import androidx.room.Room
//import androidx.room.RoomDatabase
//import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
//
//@Database(entities = [Transaction::class], version = 1)
//abstract class TransactionDb : RoomDatabase() {
//    abstract fun transactionDao(): TransactionDao
//
//    companion object {
//        @Volatile
//        private var INSTANCE: TransactionDb? = null
//
//        fun getDatabase(context: Context): TransactionDb {
//            val tempInstance = INSTANCE
//            if (tempInstance != null) {
//                return tempInstance
//            }
//            synchronized(this) {
//                val instance = Room.databaseBuilder(
//                    context.applicationContext,
//                    TransactionDb::class.java,
//                    "transaction_database"
//                ).build()
//                INSTANCE = instance
//                return instance
//            }
//        }
//    }
//}
package at.msd.friehs_bicha.cdcsvparser.app

import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionType
import at.msd.friehs_bicha.cdcsvparser.transactions.stringToTransactionType
import at.msd.friehs_bicha.cdcsvparser.wallet.CDCWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet
import com.google.firebase.Timestamp
import java.util.function.Consumer

@Suppress("UNCHECKED_CAST")
class TxAppFactory {

    companion object {

        /**
         * Create tx app
         *
         * @param appType
         * @param appStatus
         * @param useStrictType
         * @param dataContainer HashMap with data:
         *                     - For not started: csvAsList
         *                     - For importFromFB: dbWallets, dbOutsideWallets, dbTransactions, amountTxFailed
         * @return
         */
        fun createTxApp(
            appType: AppType,
            appStatus: AppStatus,
            useStrictType: Boolean,
            dataContainer: HashMap<*, *>
        ): BaseApp {
            val txApp = when (appType) {
                AppType.CdCsvParser -> {
                    when (appStatus) {
                        AppStatus.NotStarted -> StandardTxApp(dataContainer[DataTypes.csvAsList] as ArrayList<String>)
//                    AppStatus.Running -> BaseApp() //TxApp(data)
//                    AppStatus.Finished -> BaseApp() //TxApp(data)
//                    AppStatus.Error -> BaseApp()    //TxApp(data)
                        AppStatus.importFromFB -> initFromFirebase(
                            dataContainer[DataTypes.dbWallets] as ArrayList<java.util.HashMap<String, *>>,
                            dataContainer[DataTypes.dbOutsideWallets] as ArrayList<java.util.HashMap<String, *>>,
                            dataContainer[DataTypes.dbTransactions] as ArrayList<java.util.HashMap<String, *>>,
                            appType,
                            dataContainer[DataTypes.amountTxFailed] as Long
                        )

                        else -> throw RuntimeException("Usage not found")
                    }
                }

                AppType.CroCard -> {
                    when (appStatus) {
                        AppStatus.NotStarted -> CardTxApp(
                            dataContainer[DataTypes.csvAsList] as ArrayList<String>,
                            useStrictType
                        )
//                    AppStatus.Running -> BaseApp()
//                    AppStatus.Finished -> BaseApp()
//                    AppStatus.Error -> BaseApp()
                        AppStatus.importFromFB -> processCroCardFromDB(
                            dataContainer[DataTypes.dbWallets] as ArrayList<java.util.HashMap<String, *>>,
                            dataContainer[DataTypes.dbTransactions] as ArrayList<java.util.HashMap<String, *>>,
                            dataContainer[DataTypes.amountTxFailed] as Long
                        )

                        else -> throw RuntimeException("Usage not found")
                    }
                }

                else -> throw RuntimeException("Usage not found")
            }
            return txApp
        }

        private fun processCroCardFromDB(
            dbWallets: ArrayList<java.util.HashMap<String, *>>,
            dbTransactions: ArrayList<java.util.HashMap<String, *>>,
            amountTxFailed: Long
        ): CroCardTxApp {
            val tXs: MutableList<CroCardTransaction> = ArrayList()
            val wTXs: MutableList<CroCardWallet> = ArrayList()
            dbTransactions.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->

                val transactionId = hashMap["transactionId"] as Long
                val description = hashMap["description"] as String
                val walletId = hashMap["walletId"] as Long
                val fromWalletId = hashMap["fromWalletId"] as Long
                val date = (hashMap["date"] as Timestamp).toDate()
                val currencyType = hashMap["currencyType"] as String
                val amount = hashMap["amount"] as Double
                val nativeAmount = hashMap["nativeAmount"] as Double
                val amountBonus = hashMap["amountBonus"] as Double?
                val transactionType = hashMap["transactionType"] as String
                val transHash = hashMap["transHash"] as String?
                val toCurrency = hashMap["toCurrency"] as String?
                val toAmount = hashMap["toAmount"] as Double?
                val isOutsideTransaction = hashMap["outsideTransaction"] as Boolean
                val transactionTypeString = hashMap["transactionTypeString"] as String

                val transaction = CroCardTransaction(
                    transactionId,
                    description,
                    walletId.toInt(),
                    fromWalletId.toInt(),
                    date,
                    currencyType,
                    amount,
                    nativeAmount,
                    amountBonus!!,
                    transactionType,
                    transHash,
                    toCurrency,
                    toAmount,
                    isOutsideTransaction,
                    transactionTypeString
                )
                tXs.add(transaction)
            })
            dbWallets.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->

                val walletId = hashMap["walletId"] as Long
                val currencyType = hashMap["currencyType"] as String?
                val amount = hashMap["amount"] as Double
                val amountBonus = hashMap["amountBonus"] as Double
                val moneySpent = hashMap["moneySpent"] as Double
                val isOutsideWallet = hashMap["outsideWallet"] as Boolean
                val transactionType = hashMap["transactionType"] as String

                val transactionsList =
                    hashMap["transactions"] as MutableList<java.util.HashMap<String, *>?>?
                val transactions = ArrayList<CroCardTransaction?>()

                transactionsList?.forEach { transactionMap ->
                    transactionMap?.let {
                        val dbTransaction = CroCardTransaction(
                            it["transactionId"] as Long,
                            it["description"] as String,
                            (it["walletId"] as Long).toInt(),
                            (it["fromWalletId"] as Long).toInt(),
                            (it["date"] as Timestamp).toDate(),
                            it["currencyType"] as String,
                            it["amount"] as Double,
                            it["nativeAmount"] as Double,
                            it["amountBonus"] as Double,
                            it["transactionType"] as String,
                            it["transHash"] as String?,
                            it["toCurrency"] as String?,
                            it["toAmount"] as Double?,
                            it["outsideTransaction"] as Boolean,
                            it["transactionTypeString"] as String
                        )
                        transactions.add(dbTransaction)
                    }
                }

                val wallet = CroCardWallet(
                    walletId,
                    currencyType,
                    amount,
                    amountBonus,
                    moneySpent,
                    isOutsideWallet,
                    transactions,
                    transactionType
                )
                wTXs.add(wallet)
            })
            return CroCardTxApp(tXs, wTXs, amountTxFailed)
        }

        private fun initFromFirebase(
            dbWallets: ArrayList<java.util.HashMap<String, *>>,
            dbOutsideWallets: ArrayList<java.util.HashMap<String, *>>?,
            dbTransactions: ArrayList<java.util.HashMap<String, *>>,
            appType: AppType,
            amountTxFailed: Long
        ): StandardTxApp {
            val tXs: MutableList<Transaction> = ArrayList()
            val wTXs: MutableList<CDCWallet> = ArrayList()
            val wTXsOutside: MutableList<CDCWallet> = ArrayList()
            dbTransactions.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->    //TODO in den Transactions und Wallet Klassen selbst machen damit dann nur mehr val tx = Transaction(hashMap) und val wallet = CDCWallet(hashMap) ist

                val transactionId = hashMap["transactionId"] as Long
                val description = hashMap["description"] as String
                val walletId = hashMap["walletId"] as Long
                val fromWalletId = hashMap["fromWalletId"] as Long
                val date = hashMap["date"] as Timestamp?
                val currencyType = hashMap["currencyType"] as String
                val amount = hashMap["amount"] as Double
                val nativeAmount = hashMap["nativeAmount"] as Double
                val amountBonus = hashMap["amountBonus"] as Double?
                val transactionType = hashMap["transactionType"] as String?
                val transHash = hashMap["transHash"] as String?
                val toCurrency = hashMap["toCurrency"] as String?
                val toAmount = hashMap["toAmount"] as Double?
                val isOutsideTransaction = hashMap["outsideTransaction"] as Boolean

                if (transactionType == null) {
                    val i = 0
                }

                val transaction = Transaction(
                    transactionId,
                    description,
                    walletId.toInt(),
                    fromWalletId.toInt(),
                    date!!.toDate(),
                    currencyType,
                    amount,
                    nativeAmount,
                    amountBonus!!,
                    stringToTransactionType(transactionType),
                    transHash,
                    toCurrency,
                    toAmount,
                    isOutsideTransaction
                )
                tXs.add(transaction)
            })
            dbWallets.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->

                val walletId = hashMap["walletId"] as Long
                val currencyType = hashMap["currencyType"] as String?
                val amount = hashMap["amount"] as Double
                val amountBonus = hashMap["amountBonus"] as Double
                val moneySpent = hashMap["moneySpent"] as Double
                val isOutsideWallet = hashMap["outsideWallet"] as Boolean

                val transactionsList =
                    hashMap["transactions"] as MutableList<java.util.HashMap<String, *>?>?
                val transactions = ArrayList<Transaction?>()

                transactionsList?.forEach { transactionMap ->
                    transactionMap?.let {
                        val dbTransaction = Transaction(
                            it["transactionId"] as Long,
                            it["description"] as String,
                            (it["walletId"] as Long).toInt(),
                            (it["fromWalletId"] as Long).toInt(),
                            (it["date"] as Timestamp?)?.toDate(),
                            it["currencyType"] as String,
                            it["amount"] as Double,
                            it["nativeAmount"] as Double,
                            it["amountBonus"] as Double,
                            TransactionType.valueOf(it["transactionType"] as String),
                            it["transHash"] as String?,
                            it["toCurrency"] as String?,
                            it["toAmount"] as Double?,
                            it["outsideTransaction"] as Boolean

                        )
                        transactions.add(dbTransaction)
                    }
                }

                val wallet = CDCWallet(
                    walletId,
                    currencyType,
                    amount,
                    amountBonus,
                    moneySpent,
                    isOutsideWallet,
                    transactions
                )
                wTXs.add(wallet)
            })
            dbOutsideWallets?.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                val walletId = hashMap["walletId"] as Long
                val currencyType = hashMap["currencyType"] as String?
                val amount = hashMap["amount"] as Double
                val amountBonus = hashMap["amountBonus"] as Double
                val moneySpent = hashMap["moneySpent"] as Double
                val isOutsideWallet = hashMap["outsideWallet"] as Boolean

                val transactionsList =
                    hashMap["transactions"] as MutableList<java.util.HashMap<String, *>?>?
                val transactions = ArrayList<Transaction?>()

                transactionsList?.forEach { transactionMap ->
                    transactionMap?.let {
                        val dbTransaction = Transaction(
                            it["transactionId"] as Long,
                            it["description"] as String,
                            (it["walletId"] as Long).toInt(),
                            (it["fromWalletId"] as Long).toInt(),
                            (it["date"] as Timestamp?)?.toDate(),
                            it["currencyType"] as String,
                            it["amount"] as Double,
                            it["nativeAmount"] as Double,
                            it["amountBonus"] as Double,
                            TransactionType.valueOf(it["transactionType"] as String),
                            it["transHash"] as String?,
                            it["toCurrency"] as String?,
                            it["toAmount"] as Double?,
                            it["outsideTransaction"] as Boolean

                        )
                        transactions.add(dbTransaction)
                    }
                }

                val wallet = CDCWallet(
                    walletId,
                    currencyType,
                    amount,
                    amountBonus,
                    moneySpent,
                    isOutsideWallet,
                    transactions
                )
                wTXsOutside.add(wallet)
            })
            return StandardTxApp(tXs, wTXs, wTXsOutside, amountTxFailed, appType)
        }
    }
}
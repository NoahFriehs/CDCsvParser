package at.msd.friehs_bicha.cdcsvparser.app

import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.wallet.CDCWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet
import java.util.function.Consumer

/**
 * TxApp factory
 *
 */
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
         * @return BaseApp child object (StandardTxApp, CardTxApp)
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
                        AppStatus.NotStarted -> StandardTxApp(
                            dataContainer[DataTypes.csvAsList] as ArrayList<String>,
                            appType
                        )

                        AppStatus.importFromFB -> initFromFirebase(
                            dataContainer[DataTypes.dbWallets] as ArrayList<java.util.HashMap<String, *>>,
                            dataContainer[DataTypes.dbOutsideWallets] as ArrayList<java.util.HashMap<String, *>>,
                            dataContainer[DataTypes.dbTransactions] as ArrayList<java.util.HashMap<String, *>>,
                            appType,
                            dataContainer[DataTypes.amountTxFailed] as Long
                        )

                        AppStatus.Finished -> initFromLocalDB(
                            dataContainer[DataTypes.dbWallets] as ArrayList<Wallet>,
                            dataContainer[DataTypes.dbOutsideWallets] as ArrayList<Wallet>,
                            dataContainer[DataTypes.dbTransactions] as ArrayList<Transaction>,
                            appType,
                            dataContainer[DataTypes.amountTxFailed] as Long
                        )

                        else ->{
                            FileLog.e("TxAppFactory", "CdCsvParser: Usage not found, AppStatus: $appStatus")
                            throw RuntimeException("Usage not found")}
                    }
                }

                AppType.CroCard -> {
                    when (appStatus) {
                        AppStatus.NotStarted -> CardTxApp(
                            dataContainer[DataTypes.csvAsList] as ArrayList<String>,
                            useStrictType
                        )

                        AppStatus.importFromFB -> processCroCardFromDB(
                            dataContainer[DataTypes.dbWallets] as ArrayList<java.util.HashMap<String, *>>,
                            dataContainer[DataTypes.dbTransactions] as ArrayList<java.util.HashMap<String, *>>,
                            dataContainer[DataTypes.amountTxFailed] as Long
                        )

                        AppStatus.Finished -> initCardFromLocalDB(
                            dataContainer[DataTypes.dbWallets] as ArrayList<Wallet>,
                            dataContainer[DataTypes.dbTransactions] as ArrayList<Transaction>,
                            dataContainer[DataTypes.amountTxFailed] as Long
                        )

                        else -> {
                            FileLog.e("TxAppFactory", "CroCard: Usage not found, AppStatus: $appStatus")
                            throw RuntimeException("Usage not found")
                        }
                    }
                }

                AppType.Default -> {
                    when (appStatus) {
                        AppStatus.NotStarted -> StandardTxApp(
                            dataContainer[DataTypes.csvAsList] as ArrayList<String>, appType
                        )

                        else -> {
                            FileLog.e("TxAppFactory", "Default: Usage not found, AppStatus: $appStatus")
                            throw RuntimeException("Usage not found")
                        }
                    }
                }

                else -> {
                    FileLog.e("TxAppFactory", "createTxApp: Usage not found, AppType: $appType")
                    throw RuntimeException("Usage not found")
                }
            }
            return txApp
        }

        private fun initCardFromLocalDB(wallets: ArrayList<Wallet>, transactions: ArrayList<Transaction>, amountTxFailed: Long): CroCardTxApp {
            return CroCardTxApp(transactions as ArrayList<CroCardTransaction>, wallets as ArrayList<CroCardWallet>, amountTxFailed)
        }

        private fun initFromLocalDB(wallets: ArrayList<Wallet>, outsideWallets: ArrayList<Wallet>, transactions: ArrayList<Transaction>, appType: AppType, amountTxFailed: Long): StandardTxApp {
            val tXs: MutableList<Transaction> = ArrayList()
            val wTXs: MutableList<Wallet> = ArrayList()
            val wTXsOutside: MutableList<Wallet> = ArrayList()

            wTXs.addAll(wallets)
            wTXsOutside.addAll(outsideWallets)
            tXs.addAll(transactions)

            return StandardTxApp(tXs, wTXs, wTXsOutside, amountTxFailed, appType, true)
        }

        /**
         * Init from firebase
         *
         * @param dbWallets
         * @param dbTransactions
         * @param amountTxFailed
         * @return CroCardTxApp
         */
        private fun processCroCardFromDB(
            dbWallets: ArrayList<java.util.HashMap<String, *>>,
            dbTransactions: ArrayList<java.util.HashMap<String, *>>,
            amountTxFailed: Long
        ): CroCardTxApp {
            val tXs: MutableList<CroCardTransaction> = ArrayList()
            val wTXs: MutableList<CroCardWallet> = ArrayList()

            dbTransactions.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                tXs.add(CroCardTransaction.fromDb(hashMap))
            })
            dbWallets.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                wTXs.add(CroCardWallet.fromDb(hashMap))
            })
            return CroCardTxApp(tXs, wTXs, amountTxFailed)
        }

        /**
         * Init from firebase
         *
         * @param dbWallets
         * @param dbOutsideWallets
         * @param dbTransactions
         * @param appType
         * @param amountTxFailed
         * @return StandardTxApp
         */
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

            dbTransactions.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                tXs.add(Transaction.fromDb(hashMap))
            })
            dbWallets.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                wTXs.add(CDCWallet.fromDb(hashMap))
            })
            dbOutsideWallets?.forEach(Consumer { hashMap: java.util.HashMap<String, *> ->
                wTXsOutside.add(CDCWallet.fromDb(hashMap))
            })
            return StandardTxApp(tXs, wTXs, wTXsOutside, amountTxFailed, appType)
        }
    }
}
package at.msd.friehs_bicha.cdcsvparser.app

import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet

/**
 * TxApp factory
 *
 * The C++ core is the only parse path for every live family. The Kotlin
 * standard island's live-construction arms (the CSV import and the
 * cloud-restore rehydrate) are retired with the standard escape: a
 * standard type reaching them hits the not-supported arm (throws) -
 * defense in depth for routing the CoreService guards already keep out of
 * the factory. The island's legacy Room re-read (`Finished`) outlives the
 * parse arms: the Room save stays part of the legacy persistence, and the
 * fast start still shows the saved model until the core re-parse re-posts.
 * The card family has no branch on purpose (the C++ core is the only card
 * path): any card type handed in falls through to the not-supported arm
 * below (throws).
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
         * @return BaseApp child object (StandardTxApp)
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
                        // Retired escape arms: the standard island cannot
                        // be constructed from a CSV import or a fetched
                        // document anymore (the C++ core is the only
                        // standard path).
                        AppStatus.NotStarted, AppStatus.importFromFB -> {
                            FileLog.e(
                                "TxAppFactory",
                                "CdCsvParser: the Kotlin standard parse is retired (core-only family), AppStatus: $appStatus"
                            )
                            throw RuntimeException("Usage not found")
                        }

                        // The legacy Room re-read outlives the parse arms:
                        // the fast start still shows the saved model until
                        // the core re-parse re-posts.
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

                AppType.Default -> {
                    when (appStatus) {
                        // Retired escape arm: same as the CdCsvParser CSV
                        // import - the island parse is gone.
                        AppStatus.NotStarted -> {
                            FileLog.e(
                                "TxAppFactory",
                                "Default: the Kotlin standard parse is retired (core-only family), AppStatus: $appStatus"
                            )
                            throw RuntimeException("Usage not found")
                        }

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

        private fun initFromLocalDB(wallets: ArrayList<Wallet>, outsideWallets: ArrayList<Wallet>, transactions: ArrayList<Transaction>, appType: AppType, amountTxFailed: Long): StandardTxApp {
            val tXs: MutableList<Transaction> = ArrayList()
            val wTXs: MutableList<Wallet> = ArrayList()
            val wTXsOutside: MutableList<Wallet> = ArrayList()

            wTXs.addAll(wallets)
            wTXsOutside.addAll(outsideWallets)
            tXs.addAll(transactions)

            return StandardTxApp(tXs, wTXs, wTXsOutside, amountTxFailed, appType, true)
        }
    }
}
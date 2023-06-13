package at.msd.friehs_bicha.cdcsvparser.transactions

import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.app.AppTypeIdentifier
import at.msd.friehs_bicha.cdcsvparser.app.BaseApp
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.wallet.CDCWallet
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.utils.ColorTemplate
import java.math.BigDecimal
import java.util.Date

/**
 * Transaction Manager
 *
 * @property transactions List of transactions
 * @property cumulativeAmounts Map of cumulative amounts
 * @constructor Create empty Transaction Manager
 */
class TransactionManager(private val transactions: MutableList<Transaction>?) {
    private val cumulativeAmounts: MutableMap<Date, BigDecimal> = mutableMapOf()

    init {
        calculateCumulativeAmounts()
    }

    private fun calculateCumulativeAmounts() {
        var balance = BigDecimal.ZERO
        if (transactions != null) {
            for (transaction in transactions) {
                balance += transaction.amount
                cumulativeAmounts[transaction.date!!] = balance
            }
        }
    }

    fun getBalanceAtDate(date: Date): BigDecimal? {
        return cumulativeAmounts[date]
    }

    fun getGraphDataSet(): LineData {
        val entries = mutableListOf<Entry>()
        for ((index, transaction) in transactions?.withIndex()!!) {
            val cumulativeAmount = cumulativeAmounts[transaction.date] ?: 0
            entries.add(Entry(index.toFloat(), cumulativeAmount.toFloat()))
        }

        val lineDataSet = LineDataSet(entries, "Cumulative Amount")
        lineDataSet.setColors(*ColorTemplate.COLORFUL_COLORS)
        lineDataSet.valueTextSize = 12f

        return LineData(lineDataSet)
    }

    companion object {

        /**
         * Create transactions from csv list
         *
         * @param _input Input list
         * @param _appType App type (optional)
         * @param app App
         * @return List of transactions
         */
        fun txFromCsvList(
            _input: ArrayList<String>,
            _appType: AppType?,
            app: BaseApp
        ): ArrayList<Transaction> {
            val transactions = ArrayList<Transaction>()
            val currencies = ArrayList<String>()
            val appType = _appType ?: AppTypeIdentifier.getAppType(_input)
            app.appType = appType
            val input = prepareInput(_input, appType, app)
            for (line in input) {
                val tx = Transaction.fromCsvLine(line, appType)
                if (tx != null) {
                    transactions.add(tx)
                    if (!currencies.contains(tx.currencyType)) currencies.add(tx.currencyType)
                } else {
                    app.addFailedTx(line)
                }
            }

            createWallets(currencies, app)
            transactions.forEach { addTransaction(it, app) }

            return transactions
        }

        /**
         * Prepare input
         *
         * @param input
         * @param appType
         * @param app
         */
        private fun prepareInput(
            input: ArrayList<String>,
            appType: AppType,
            app: BaseApp
        ): ArrayList<String> {
            return when (appType) {
                AppType.CdCsvParser -> prepareCDCInput(input)
                AppType.Default -> prepareCDCInput(input)
                else -> throw IllegalArgumentException("Unknown app type")
            }
        }


        /**
         * Prepare CDC input
         *
         * @param input
         * @return
         */
        private fun prepareCDCInput(input: ArrayList<String>): ArrayList<String> {
            input.removeAt(0)
            return input
        }


        /**
         * Creates the wallet for the given currency types
         *
         * @param currencies the currency types
         * @param app the app
         */
        private fun createWallets(
            currencies: ArrayList<String>,
            app: BaseApp
        ) {
            when (app.appType) {
                AppType.CdCsvParser -> createCDCWallets(currencies, app)
                else -> throw IllegalArgumentException("Unknown app type")
            }
        }


        /**
         * Creates CDC wallets for the given currency types
         *
         * @param currencies the currency types
         * @param app the app
         */
        private fun createCDCWallets(currencies: ArrayList<String>, app: BaseApp) {
            for (t in currencies) {
                app.wallets.add(CDCWallet(t, BigDecimal.ZERO, BigDecimal.ZERO, app, false))
                app.outsideWallets.add(CDCWallet(t, BigDecimal.ZERO, BigDecimal.ZERO, app, true))
            }
        }

        /**
         * Add transaction to the respective wallet
         *
         * @param transaction the transaction to be added
         * @param app the app
         */
        private fun addTransaction(transaction: Transaction, app: BaseApp) {
            when (app.appType) {
                AppType.CdCsvParser -> addCDCTransaction(transaction, app)
                AppType.Default -> addDefaultTransaction(transaction, app)
                else -> throw IllegalArgumentException("Unknown app type")
            }
        }

        private fun addDefaultTransaction(transaction: Transaction, app: BaseApp) {
            addCDCTransaction(transaction, app)
        }


        /**
         * Adds a transactions to the respective CDCWallet
         *
         * @param transaction the transaction to be added
         */
        private fun addCDCTransaction(transaction: Transaction, app: BaseApp) {
            val t = transaction.transactionType
            val w = getWallet(transaction.currencyType, app)
            transaction.fromWalletId = w.walletId
            transaction.walletId = w.walletId
            if (!w.transactions!!.contains(transaction)) {
                w.transactions!!.add(transaction)
            }
            when (t) {
                TransactionType.crypto_purchase, TransactionType.dust_conversion_credited -> w.addToWallet(
                    transaction
                )

                TransactionType.supercharger_deposit, TransactionType.crypto_earn_program_created, TransactionType.lockup_lock,
                TransactionType.supercharger_withdrawal, TransactionType.crypto_earn_program_withdrawn,
                TransactionType.rewards_platform_deposit_credited -> {
                }

                TransactionType.supercharger_reward_to_app_credited, TransactionType.crypto_earn_interest_paid,
                TransactionType.referral_card_cashback, TransactionType.reimbursement, TransactionType.card_cashback_reverted,
                TransactionType.admin_wallet_credited, TransactionType.crypto_wallet_swap_credited, TransactionType.crypto_wallet_swap_debited -> {
                    transaction.amountBonus = transaction.amount
                    w.addToWallet(transaction)
                }

                TransactionType.viban_purchase -> vibanPurchase(transaction, app)
                TransactionType.crypto_withdrawal -> cryptoWithdrawal(
                    w,
                    transaction,
                    app
                )

                TransactionType.crypto_deposit -> cryptoWithdrawal(w, transaction, app)
                TransactionType.dust_conversion_debited -> w.removeFromWallet(
                    transaction.amount,
                    transaction.nativeAmount
                )

                TransactionType.crypto_viban_exchange -> {
                    w.removeFromWallet(transaction.amount, transaction.nativeAmount)
                    val eur = getWallet("EUR", app)
                    eur.addToWallet(
                        transaction.nativeAmount,
                        transaction.nativeAmount,
                        BigDecimal.ZERO
                    )
                    eur.transactions?.add(transaction)
                }

                else -> throw IllegalArgumentException("Unknown transaction type")
            }
        }


        /**
         * Get wallet
         *
         * @param currencyType
         * @param app
         * @param getOutsideWallet
         * @return
         */
        private fun getWallet(
            currencyType: String,
            app: BaseApp,
            getOutsideWallet: Boolean = false
        ): CDCWallet {
            var ws = app.wallets
            if (getOutsideWallet) ws = app.outsideWallets
            for (wallet in ws) {
                if (wallet.currencyType == currencyType) {
                    return wallet as CDCWallet
                }
            }
            return CDCWallet(currencyType, BigDecimal.ZERO, BigDecimal.ZERO, app, getOutsideWallet)
        }


        /**
         * Handles crypto withdrawal
         *
         * @param w              the wallet from which crypto is withdrawn
         * @param transaction    the transaction to be made
         */
        private fun cryptoWithdrawal(
            w: CDCWallet,
            transaction: Transaction,
            app: BaseApp
        ) {
            w.addToWallet(transaction.amount, BigDecimal.ZERO, BigDecimal.ZERO)
            val wt = getWallet(transaction.currencyType, app, true)
            if (!wt.transactions!!.contains(transaction)) {
                wt.transactions!!.add(transaction)
            }
            wt.removeFromWallet(transaction.amount, BigDecimal.ZERO)
            transaction.isOutsideTransaction = true
        }


        /**
         * Handles crypto viban purchase
         *
         * @param transaction the transaction which is a vibanPurchase
         */
        private fun vibanPurchase(transaction: Transaction, app: BaseApp) {
            if (!app.wallets.contains(getWallet(transaction.toCurrency!!, app))) {
                FileLog.w("TransactionManager", "Tx failed: $transaction")
            } else {
                var wv = transaction.toCurrency?.let { getWallet(it, app) } as CDCWallet
                wv.addToWallet(transaction.toAmount, transaction.nativeAmount, BigDecimal.ZERO)
                transaction.walletId = wv.walletId
                if (!wv.transactions!!.contains(transaction)) {
                    wv.transactions!!.add(transaction)
                }
                wv = getWallet(transaction.currencyType, app)
                wv.addToWallet(transaction.amount, transaction.nativeAmount, BigDecimal.ZERO)
                transaction.fromWalletId = wv.walletId
                if (!wv.transactions!!.contains(transaction)) {
                    wv.transactions!!.add(transaction)
                }
            }
        }
    }
}

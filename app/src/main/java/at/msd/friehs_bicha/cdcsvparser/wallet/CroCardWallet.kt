package at.msd.friehs_bicha.cdcsvparser.wallet

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.TypeConverters
import at.msd.friehs_bicha.cdcsvparser.app.BaseApp
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction
import at.msd.friehs_bicha.cdcsvparser.transactions.CurveCardTx
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.util.Converter
import java.io.Serializable
import java.math.BigDecimal

/**
 * Represents a CroCardWallet object
 */
@Entity(tableName = "card_wallets")
@TypeConverters(Converter::class, Converter.BigDecimalConverter::class)
class CroCardWallet(
    currencyType: String,
    amount: BigDecimal?,
    var transactionType: String?,
    txApp: BaseApp?
) : Wallet(currencyType, amount, amount), Serializable {
    constructor(
        walletID: Long,
        currencyType: String,
        amount: Double?,
        amountBonus: Double?,
        moneySpent: Double,
        outsideWallet: Boolean,
        transactions: MutableList<CroCardTransaction?>,
        transactionType: String?
    ) : this(
        currencyType,
        amount?.let { BigDecimal(it) },
        currencyType,
        null
    ) {
        this.walletId = walletID.toInt()
        this.currencyType = currencyType
        this.transactionType = transactionType
        amount?.let { this.amount = BigDecimal(it) }
        amountBonus?.let { this.amountBonus = BigDecimal(it) }
        this.moneySpent = BigDecimal(moneySpent)
        this.transactions = transactions as MutableList<Transaction?>
        this.isOutsideWallet = outsideWallet
    }

    //constructor for all members:
    constructor(
        walletId: Int,
        currencyType: String,
        amount: BigDecimal?,
        amountBonus: BigDecimal?,
        moneySpent: BigDecimal,
        isOutsideWallet: Boolean,
        transactionType: String?
    ) : this(
        currencyType,
        amount,
        transactionType,
        null
    ) {
        this.walletId = walletId
        this.currencyType = currencyType
        this.transactionType = transactionType
        this.amount = amount!!
        this.amountBonus = amountBonus!!
        this.moneySpent = moneySpent
        //if(transactions != null) this.transactions = transactions as MutableList<Transaction?>
        this.isOutsideWallet = isOutsideWallet
    }

    @Ignore
    lateinit var txApp: BaseApp

    init {
        if (!tts.contains(transactionType)) {
            tts.add(transactionType)
        }
        if (txApp != null) {
            this.txApp = txApp
        }
    }

    override fun addTransaction(transaction: Transaction) {
        val cardTransaction = transaction as CroCardTransaction
        val tt = cardTransaction.transactionTypeString
        if (tt == "EUR -> EUR") {
            FileLog.i("CCW.addTx","Found EUR -> EUR: $tt")
        }

        var ignoreThisTx = false

        if (transaction is CurveCardTx) {
            if (transaction.txType == "REFUNDED") {
                FileLog.i("CCW.addTx", "Ignoring CurveCardTx: $transaction")
                ignoreThisTx = true
            }
            if (transaction.txType == "Declined") {
                FileLog.i("CCW.addTx", "Ignoring CurveCardTx: $transaction")
                ignoreThisTx = true
            }
        }

        var w = findWallet(tt)
        if (w != null) {
            w = findWallet(tt)
            if (!txApp.isUseStrictWalletType) {
                w = getNonStrictWallet(tt)
            }
            if (w == null) {
                w = CroCardWallet("EUR", BigDecimal.ZERO, tt, txApp)
            }
            w.addToWallet(transaction, ignoreThisTx)
            w.transactions.add(cardTransaction)
            transaction.walletId = w.walletId
        } else {
            if (!txApp.isUseStrictWalletType) {
                w = getNonStrictWallet(tt)
                if (w == null) {
                    if (!ignoreThisTx) w = CroCardWallet("EUR", cardTransaction.amount, tt, txApp)
                    else w = CroCardWallet("EUR", BigDecimal.ZERO, tt, txApp)
                    txApp.wallets.add(w)
                    w.transactions.add(cardTransaction)
                    transaction.walletId = w.walletId
                } else {
                    w.addToWallet(transaction, ignoreThisTx)
                    transaction.walletId = w.walletId
                }
            } else {
                w = CroCardWallet("EUR", cardTransaction.amount, tt, txApp)
                w.transactions.add(cardTransaction)
                transaction.walletId = w.walletId
                txApp.wallets.add(w)
            }
        }
    }

    /**
     * Get Wallet index from CurrencyType String
     *
     * @param ct the CurrencyType as String
     * @return the index of the wallet
     */
    override fun getWallet(ct: String?): Int {
        for ((i, w) in txApp.wallets.withIndex()) {
            if ((w as CroCardWallet).transactionType == ct) return i
        }
        return -1
    }

    fun addToWallet(transaction: Transaction, ignoreThisTx: Boolean = false) {
        transaction.walletId = walletId
        if (ignoreThisTx) {
            FileLog.i("CCW.addToWallet", "Ignoring transaction: $transaction")
            transactions.add(transaction)
            return
        }
        if (transaction.currencyType != "EUR") {
            amount = amount.add(transaction.nativeAmount)
            moneySpent = moneySpent.add(transaction.nativeAmount)
            transactions.add(transaction)
        } else {
            amount = amount.add(transaction.amount)
            moneySpent = moneySpent.add(transaction.amount)
            transactions.add(transaction)
        }
    }

    /**
     * Remove a transaction from the CDCWallet
     *
     * @param amount       the amount to remove
     * @param nativeAmount the amount in native currency to remove
     */
    @Deprecated("use {@link #removeFromWallet(Transaction)} instead")
    override fun removeFromWallet(amount: BigDecimal?, nativeAmount: BigDecimal?) {
        this.amount = this.amount.subtract(amount)
        moneySpent = moneySpent.subtract(nativeAmount)
    }

    /**
     * Remove a transaction from the CDCWallet
     *
     * @param transaction the transaction to remove
     */
    fun removeFromWallet(transaction: Transaction) {
        amount = amount.subtract(transaction.amount)
        moneySpent = moneySpent.subtract(transaction.amount)
        transactions.add(transaction)
    }

    private fun getNonStrictWallet(tt: String?): CroCardWallet? {
        var tt = tt!!
        tt = checkForRefund(tt)
        for (w in txApp.wallets) {
            if (tt.contains(" ")) {
                if ((w as CroCardWallet).transactionType!!.contains(
                        tt.substring(
                            0,
                            tt.indexOf(" ")
                        )
                    )
                ) {
                    w.transactionType = tt.substring(0, tt.indexOf(" "))
                    checkTTS(tt, tt.substring(0, tt.indexOf(" ")))
                    return w
                }
            } else if ((w as CroCardWallet).transactionType!!.contains(tt)) {
                return w
            }
        }
        return null
    }

    private fun checkForRefund(tt: String): String {
        var tt = tt
        if (tt.contains("Refund: ")) {
            tt = checkTTS(tt, tt.substring(8))
        }
        if (tt.contains("Refund reversal: ")) {
            tt = checkTTS(tt, tt.substring(17))
        }
        return tt
    }

    /**
     * Check if the transaction type is already in the tts list and replace it with the new one
     *
     * @param tt     the transaction type to check
     * @param txType the new transaction type
     * @return the new transaction type
     */
    private fun checkTTS(tt: String, txType: String): String {
        if (tts.contains(tt)) {
            tts.remove(tt)
        }
        tts.add(txType)
        return txType
    }

    private fun findWallet(tt: String?): CroCardWallet? {
        var tt = tt!!
        if (!txApp.isUseStrictWalletType) {
            tt = checkForRefund(tt)
        }
        for (w in txApp.wallets) {
            if ((w as CroCardWallet).transactionType == tt) {
                return w
            }
        }
        return null
    }

    override fun getTypeString(): String {
        return transactionType!!
    }

    companion object {
        var tts = ArrayList<String?>()

        /**
         * Converts a HashMap<String, *> to a CroCardWallet object
         *
         * @param wallet
         * @return
         */
        fun fromDb(wallet: HashMap<String, *>): CroCardWallet {
            val walletId = wallet["walletId"] as Long
            val currencyType = wallet["currencyType"] as String
            val amount = wallet["amount"] as Double
            val amountBonus = wallet["amountBonus"] as Double
            val moneySpent = wallet["moneySpent"] as Double
            val isOutsideWallet = wallet["outsideWallet"] as Boolean
            val transactionType = wallet["transactionType"] as String

            val transactionsList = wallet["transactions"] as MutableList<java.util.HashMap<String, *>?>?
            val transactions = ArrayList<CroCardTransaction?>()

            transactionsList?.forEach { transactionMap ->
                transactionMap?.let {
                    transactions.add(CroCardTransaction.fromDb(it))
                }
            }

            return CroCardWallet(
                walletId,
                currencyType,
                amount,
                amountBonus,
                moneySpent,
                isOutsideWallet,
                transactions,
                transactionType
            )
        }
    }
}
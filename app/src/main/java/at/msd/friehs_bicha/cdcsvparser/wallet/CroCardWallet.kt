package at.msd.friehs_bicha.cdcsvparser.wallet

import at.msd.friehs_bicha.cdcsvparser.app.CroCardTxApp
import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import java.io.Serializable
import java.math.BigDecimal

class CroCardWallet(currencyType: String?, amount: BigDecimal?, var transactionType: String?, txApp: CroCardTxApp?) : Wallet(currencyType, amount, amount), Serializable {
    constructor(walletID: Long, currencyType: String?, amount: Double?, amountBonus: Double?, moneySpent: Double, outsideWallet: Boolean, transactions: MutableList<CroCardTransaction?>, transactionType: String?) : this(
        currencyType,
        amount?.let { BigDecimal(it) },
        currencyType,
        null
    ) {
        this.walletId = walletID.toInt()
        this.currencyType = currencyType
        this.transactionType = transactionType
        amount?.let {this.amount = BigDecimal(it) }
        amountBonus?.let {this.amountBonus = BigDecimal(it) }
        this.moneySpent = BigDecimal(moneySpent)
        this.transactions = transactions as MutableList<Transaction?>
        this.isOutsideWallet = outsideWallet
    }

    lateinit var txApp: CroCardTxApp

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
        //tt = checkForRefund(tt);
        if (tt == "EUR -> EUR") {
            println("Found EUR -> EUR: $tt")
        }
        var w = findWallet(tt)
        if (w != null) {
            w = findWallet(tt)
            if (!txApp.isUseStrictWalletType) {
                w = getNonStrictWallet(tt)
            }
            w!!.addToWallet(transaction)
            w.transactions!!.add(cardTransaction)
            transaction.walletId = w.walletId
        } else {
            if (!txApp.isUseStrictWalletType) {
                w = getNonStrictWallet(tt)
                if (w == null) {
                    w = CroCardWallet("EUR", cardTransaction.amount, tt, txApp)
                    txApp.wallets.add(w)
                    w.transactions!!.add(cardTransaction)
                    transaction.walletId = w.walletId
                } else {
                    w.addToWallet(transaction)
                    transaction.walletId = w.walletId
                }
            } else {
                w = CroCardWallet("EUR", cardTransaction.amount, tt, txApp)
                w.transactions!!.add(cardTransaction)
                transaction.walletId = w.walletId
                txApp.wallets.add(w)
            }
            //w.addToWallet(transaction.getAmount());
        }
    }

    /**
     * Get Wallet index from CurrencyType String
     *
     * @param ct the CurrencyType as String
     * @return the index of the wallet
     */
    override fun getWallet(ct: String?): Int {
        var i = 0
        for (w in txApp.wallets) {
            if ((w as CroCardWallet).transactionType == ct) return i
            i++
        }
        return -1
    }

    fun writeAmount() {
        var amountSpent = BigDecimal.ZERO
        for (w in txApp.wallets) {
            //System.out.println("-".repeat(20));
            println((w as CroCardWallet).transactionType)
            println(w.amount)
            println(w.moneySpent)
            println("Transactions: " + w.transactions!!.size)
            amountSpent = amountSpent.add(w.moneySpent)
        }
        //System.out.println("-".repeat(20));
        println("Amount total spent: $amountSpent")
    }

    fun addToWallet(transaction: Transaction) {
        amount = amount.add(transaction.amount)
        moneySpent = moneySpent.add(transaction.amount)
        transactions!!.add(transaction)
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
        transactions!!.add(transaction)
    }

    private fun getNonStrictWallet(tt: String?): CroCardWallet? {
        var tt = tt!!
        tt = checkForRefund(tt)
        for (w in txApp.wallets) {
            if (tt.contains(" ")) {
                if ((w as CroCardWallet).transactionType!!.contains(tt.substring(0, tt.indexOf(" ")))) {
                    w.transactionType = tt.substring(0, tt.indexOf(" "))
                    checkTTS(tt, tt.substring(0, tt.indexOf(" ")))
                    return w
                }
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
            tts.add(txType)
        }
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

    companion object {
        var tts = ArrayList<String?>()
    }
}
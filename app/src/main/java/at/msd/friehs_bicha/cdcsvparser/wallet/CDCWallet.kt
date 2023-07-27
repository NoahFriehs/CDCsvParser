package at.msd.friehs_bicha.cdcsvparser.wallet

import at.msd.friehs_bicha.cdcsvparser.app.BaseApp
import at.msd.friehs_bicha.cdcsvparser.app.StandardTxApp
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import java.io.Serializable
import java.math.BigDecimal

/**
 * Represents a CDCWallet object
 */
class CDCWallet : Wallet, Serializable {
    var txApp: BaseApp? = null

    constructor(
        currencyType: String?,
        amount: BigDecimal?,
        nativeAmount: BigDecimal?,
        txApp: BaseApp?,
        isOutsideWallet: Boolean?
    ) : super(currencyType, amount, nativeAmount) {
        this.txApp = txApp
        this.isOutsideWallet = isOutsideWallet!!
    }

    constructor(wallet: Wallet?) : super(wallet)

    constructor(DBWallet: DBWallet?) : super(DBWallet)
    constructor(
        walletId: Long,
        currencyType: String?,
        amount: Double,
        amountBonus: Double,
        moneySpent: Double,
        outsideWallet: Boolean,
        transactions: ArrayList<Transaction?>
    ) : super(currencyType, BigDecimal(amount), BigDecimal(moneySpent)) {
        this.walletId = walletId.toInt()
        this.currencyType = currencyType
        this.amount = BigDecimal(amount)
        this.amountBonus = BigDecimal(amountBonus)
        this.moneySpent = BigDecimal(moneySpent)
        this.transactions = transactions
        this.isOutsideWallet = outsideWallet
    }

    constructor(wallet: Wallet, standardTxApp: StandardTxApp) : super(wallet)
    {
        this.txApp = standardTxApp
    }

    /**
     * Get CDCWallet from CurrencyType String
     *
     * @param ct the CurrencyType as String
     * @return the index of the wallet
     */
    override fun getWallet(ct: String?): Int {
        for ((i, w) in txApp!!.wallets.withIndex()) {
            if (w.currencyType == ct) return i
        }
        return -1
    }

    /**
     * Add a transaction to the CDCWallet
     *
     * @param amount       the amount of the coin
     * @param nativeAmount the amount in native currency
     * @param amountBonus  the amount the user got for free
     */
    fun addToWallet(amount: BigDecimal?, nativeAmount: BigDecimal?, amountBonus: BigDecimal?) {
        this.amount = this.amount.add(amount)
        moneySpent = moneySpent.add(nativeAmount)
        this.amountBonus = this.amountBonus.add(amountBonus)
    }

    /**
     * Add a transaction to the CDCWallet
     *
     * @param transaction the transaction to be added
     */
    fun addToWallet(transaction: Transaction) {
        amount = amount.add(transaction.amount)
        moneySpent = moneySpent.add(transaction.nativeAmount)
        amountBonus = amountBonus.add(transaction.amountBonus)
        //transaction.setWalletIdFk(this.uid);
        if (!transactions!!.contains(transaction)) {
            transactions!!.add(transaction)
        }
    }

    /**
     * Remove a transaction from the CDCWallet
     *
     * @param amount       the amount to remove
     * @param nativeAmount the amount in native currency to remove
     */
    override fun removeFromWallet(amount: BigDecimal?, nativeAmount: BigDecimal?) {
        this.amount = this.amount.subtract(amount)
        moneySpent = moneySpent.subtract(nativeAmount)
    }


    companion object {

        /**
         * Converts a HashMap<String, *> to a CDCWallet object
         *
         * @param wallet
         * @return
         */
        fun fromDb(wallet: HashMap<String, *>): CDCWallet {
            val walletId = wallet["walletId"] as Long
            val currencyType = wallet["currencyType"] as String
            val amount = wallet["amount"] as Double
            val amountBonus = wallet["amountBonus"] as Double
            val moneySpent = wallet["moneySpent"] as Double
            val outsideWallet = wallet["outsideWallet"] as Boolean
            val transactionsList =
                wallet["transactions"] as MutableList<java.util.HashMap<String, *>?>?
            val transactions = ArrayList<Transaction?>()

            transactionsList?.forEach { transactionMap ->
                transactionMap?.let {
                    transactions.add(Transaction.fromDb(it))
                }
            }
            return CDCWallet(
                walletId,
                currencyType,
                amount,
                amountBonus,
                moneySpent,
                outsideWallet,
                transactions
            )
        }
    }

}
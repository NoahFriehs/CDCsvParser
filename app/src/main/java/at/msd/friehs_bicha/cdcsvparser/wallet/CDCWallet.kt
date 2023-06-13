package at.msd.friehs_bicha.cdcsvparser.wallet

import at.msd.friehs_bicha.cdcsvparser.app.BaseApp
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

    /**
     * Get CDCWallet from CurrencyType String
     *
     * @param ct the CurrencyType as String
     * @return the index of the wallet
     */
    override fun getWallet(ct: String?): Int {
        var i = 0
        for (w in txApp!!.wallets) {
            if (w.currencyType == ct) return i
            i++
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

}
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
        currencyType: String,
        amount: BigDecimal?,
        nativeAmount: BigDecimal?,
        txApp: BaseApp?,
        isOutsideWallet: Boolean?
    ) : super(currencyType, amount, nativeAmount) {
        this.txApp = txApp
        // Write-only construction: every surviving ctor call hands in a
        // fully set flag (the wallet-build sites pass explicit true/false);
        // the null-safe default keeps a null input a defaulting restore
        // edge instead of a crash at wallet build time.
        this.isOutsideWallet = isOutsideWallet ?: false
    }

    // Non-null pass-through (no callers existed for the nullable variant -
    // pure type-safety cleanup, no behavior change).
    constructor(DBWallet: DBWallet) : super(DBWallet)
    constructor(
        walletId: Long,
        currencyType: String,
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
        // Write-only construction: no live path feeds this override
        // anymore (the island parse path that used it is gone); the null
        // default keeps a model-less wallet a plain -1, not a crash.
        for ((i, w) in (txApp?.wallets ?: emptyList()).withIndex()) {
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
        if (!transactions.contains(transaction)) {
            transactions.add(transaction)
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
            // Firestore returns 32-bit integers as Int, so all number casts go
            // through Number instead of a hard as-Long cast.
            val walletId = (wallet["walletId"] as? Number)?.toLong() ?: 0L
            val currencyType = wallet["currencyType"] as? String ?: ""
            val amount = (wallet["amount"] as? Number)?.toDouble() ?: 0.0
            val amountBonus = (wallet["amountBonus"] as? Number)?.toDouble() ?: 0.0
            val moneySpent = (wallet["moneySpent"] as? Number)?.toDouble() ?: 0.0
            val outsideWallet = wallet["outsideWallet"] as? Boolean ?: false
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
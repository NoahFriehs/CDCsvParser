package at.msd.friehs_bicha.cdcsvparser.wallet

import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import java.io.Serializable
import java.math.BigDecimal

/**
 * Standard-family wallet on the typed consumer models.
 *
 * Restore-only since the Kotlin standard island retired: the C++ core is
 * the only standard producer (its posts build the plain [Wallet] entity),
 * and the only surviving constructor path is the encrypted-cloud restore
 * ([fromDb]) which rebuilds a wallet from a stored document. The Room
 * write path (legacy schema) persists plain [Wallet] rows and never
 * constructs this class.
 */
class CDCWallet : Wallet, Serializable {

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

    /**
     * Get CDCWallet from a stored document map (the encrypted-cloud
     * restore path).
     *
     * @param wallet the stored wallet document
     * @return the rebuilt wallet
     */
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

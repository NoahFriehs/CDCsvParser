package at.msd.friehs_bicha.cdcsvparser.wallet

import at.msd.friehs_bicha.cdcsvparser.App.TxApp
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionType
import java.io.Serializable
import java.math.BigDecimal

/**
 * Represents a CDCWallet object
 */
class CDCWallet : Wallet, Serializable {
    var txApp: TxApp? = null

    constructor(currencyType: String?, amount: BigDecimal?, nativeAmount: BigDecimal?, txApp: TxApp?, isOutsideWallet: Boolean?) : super(currencyType, amount, nativeAmount) {
        this.txApp = txApp
        this.isOutsideWallet = isOutsideWallet!!
    }

    constructor(wallet: Wallet?) : super(wallet)

    constructor(DBWallet: DBWallet?) : super(DBWallet)
    constructor(walletId: Long, currencyType: String?, amount: Double, amountBonus: Double, moneySpent: Double, outsideWallet: Boolean, transactions: ArrayList<Transaction?>): super(currencyType, BigDecimal(amount), BigDecimal(moneySpent))
    {
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
            if (w!!.currencyType == ct) return i
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

    /**
     * Adds a transactions to the respective CDCWallet
     *
     * @param transaction the transaction to be added
     */
    override fun addTransaction(transaction: Transaction) {
        //transactions.add(transaction);
        val t = transaction.transactionType
        val w = txApp!!.wallets[getWallet(transaction.currencyType)] as CDCWallet
        transaction.fromWalletId = w.walletId
        transaction.walletId = w.walletId
        if (!w.transactions!!.contains(transaction)) {
            w.transactions!!.add(transaction)
        }
        when (t) {
            TransactionType.crypto_purchase, TransactionType.dust_conversion_credited ->                 //w.addToWallet(transaction.getAmount(), transaction.getNativeAmount(), BigDecimal.ZERO);
                w.addToWallet(transaction)
            TransactionType.supercharger_deposit, TransactionType.crypto_earn_program_created, TransactionType.lockup_lock, TransactionType.supercharger_withdrawal, TransactionType.crypto_earn_program_withdrawn -> {}
            TransactionType.rewards_platform_deposit_credited -> {}
            TransactionType.supercharger_reward_to_app_credited, TransactionType.crypto_earn_interest_paid, TransactionType.referral_card_cashback, TransactionType.reimbursement, TransactionType.card_cashback_reverted, TransactionType.admin_wallet_credited, TransactionType.crypto_wallet_swap_credited, TransactionType.crypto_wallet_swap_debited -> {
                //w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
                transaction.amountBonus = transaction.amount
                w.addToWallet(transaction)
            }
            TransactionType.viban_purchase -> vibanPurchase(transaction)
            TransactionType.crypto_withdrawal -> cryptoWithdrawal(w, transaction, txApp!!.outsideWallets)
            TransactionType.crypto_deposit -> cryptoWithdrawal(w, transaction, txApp!!.wallets)
            TransactionType.dust_conversion_debited -> w.removeFromWallet(transaction.amount, transaction.nativeAmount)
            TransactionType.crypto_viban_exchange -> {
                w.removeFromWallet(transaction.amount, transaction.nativeAmount)
                val eur = txApp!!.wallets[getWallet("EUR")] as CDCWallet
                eur.addToWallet(transaction.nativeAmount, transaction.nativeAmount, BigDecimal.ZERO)
            }
            else -> println("This is an unsupported TransactionType: $t")
        }
    }

    /**
     * Handles crypto withdrawal
     *
     * @param w              the wallet from which crypto is withdrawn
     * @param transaction    the transaction to be made
     * @param outsideWallets all outsideWallets
     */
    private fun cryptoWithdrawal(w: CDCWallet, transaction: Transaction, outsideWallets: ArrayList<Wallet?>?) {
        w.addToWallet(transaction.amount, BigDecimal.ZERO, BigDecimal.ZERO)
        val wt = outsideWallets!![getWallet(transaction.currencyType)] as CDCWallet?
        if (!wt!!.transactions!!.contains(transaction)) {
            wt.transactions!!.add(transaction)
        }
        wt.removeFromWallet(transaction.amount, BigDecimal.ZERO)
        transaction.isOutsideTransaction = true
    }

    /**
     * Hadles crypto viban purchase
     *
     * @param transaction the transaction which is a vibanPurchase
     */
    private fun vibanPurchase(transaction: Transaction) {
        if (getWallet(transaction.toCurrency) == -1) {
            println("Tx failed: $transaction")
        } else {
            val wv = txApp!!.wallets[getWallet(transaction.toCurrency)] as CDCWallet
            wv.addToWallet(transaction.toAmount, transaction.nativeAmount, BigDecimal.ZERO)
            //wv.addToWallet(transaction);
            transaction.walletId = wv.walletId
            if (!transactions!!.contains(transaction)) {
                transactions!!.add(transaction)
            }
        }
    }
}
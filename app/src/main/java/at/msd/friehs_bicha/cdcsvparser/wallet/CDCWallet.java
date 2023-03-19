package at.msd.friehs_bicha.cdcsvparser.wallet;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;

import at.msd.friehs_bicha.cdcsvparser.App.TxApp;
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction;
import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionType;

/**
 * Represents a CDCWallet object
 */
public class CDCWallet extends Wallet implements Serializable {


    TxApp txApp;


    public CDCWallet(String currencyType, BigDecimal amount, BigDecimal nativeAmount, TxApp txApp, Boolean isOutsideWallet) {
        super(currencyType, amount, nativeAmount);
        this.txApp = txApp;
        this.isOutsideWallet = isOutsideWallet;
    }

    public CDCWallet(Wallet wallet) {
        super(wallet);
    }

    /**
     * Get CDCWallet from CurrencyType String
     *
     * @param ct the CurrencyType as String
     * @return the index of the wallet
     */
    public int getWallet(String ct) {
        int i = 0;
        for (Wallet w : txApp.wallets) {
            if (w.getCurrencyType().equals(ct)) return i;
            i++;
        }
        return -1;
    }


    /**
     * Add a transaction to the CDCWallet
     *
     * @param amount       the amount of the coin
     * @param nativeAmount the amount in native currency
     * @param amountBonus  the amount the user got for free
     */
    public void addToWallet(BigDecimal amount, BigDecimal nativeAmount, BigDecimal amountBonus) {
        this.amount = this.amount.add(amount);
        this.moneySpent = this.moneySpent.add(nativeAmount);
        this.amountBonus = this.amountBonus.add(amountBonus);
    }

    /**
     * Add a transaction to the CDCWallet
     *
     * @param transaction the transaction to be added
     */
    public void addToWallet(Transaction transaction) {
        this.amount = this.amount.add(transaction.getAmount());
        this.moneySpent = this.moneySpent.add(transaction.getNativeAmount());
        this.amountBonus = this.amountBonus.add(transaction.getAmountBonus());
        //transaction.setWalletIdFk(this.uid);
        if (!this.transactions.contains(transaction)) {
            this.transactions.add(transaction);
        }
    }

    /**
     * Remove a transaction from the CDCWallet
     *
     * @param amount       the amount to remove
     * @param nativeAmount the amount in native currency to remove
     */
    public void removeFromWallet(BigDecimal amount, BigDecimal nativeAmount) {
        this.amount = this.amount.subtract(amount);
        this.moneySpent = this.moneySpent.subtract(nativeAmount);
    }


    /**
     * Adds a transactions to the respective CDCWallet
     *
     * @param transaction the transaction to be added
     */
    public void addTransaction(Transaction transaction) {
        //transactions.add(transaction);
        TransactionType t = transaction.getTransactionType();
        CDCWallet w = (CDCWallet) txApp.wallets.get(getWallet(transaction.getCurrencyType()));
        transaction.setFromWalletId(w.walletId);
        transaction.setWalletId(w.walletId);
        if (!w.transactions.contains(transaction)) {
            w.transactions.add(transaction);
        }
        switch (t) {
            case crypto_purchase:
            case dust_conversion_credited:
                //w.addToWallet(transaction.getAmount(), transaction.getNativeAmount(), BigDecimal.ZERO);
                w.addToWallet(transaction);

            case supercharger_deposit:
            case crypto_earn_program_created:
            case lockup_lock:
            case supercharger_withdrawal:
            case crypto_earn_program_withdrawn:
                break;

            case rewards_platform_deposit_credited:
                break;//do nothing

            case supercharger_reward_to_app_credited:
            case crypto_earn_interest_paid:
            case referral_card_cashback:
            case reimbursement:
            case card_cashback_reverted:
            case admin_wallet_credited:
            case crypto_wallet_swap_credited:
            case crypto_wallet_swap_debited:
                //w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
                transaction.setAmountBonus(transaction.getAmount());
                w.addToWallet(transaction);
                break;
            case viban_purchase:
                vibanPurchase(transaction);
                break;
            case crypto_withdrawal:
                cryptoWithdrawal(w, transaction, txApp.outsideWallets);
                break;
            case crypto_deposit:
                cryptoWithdrawal(w, transaction, txApp.wallets);
                break;
            case dust_conversion_debited:
                w.removeFromWallet(transaction.getAmount(), transaction.getNativeAmount());
                break;
            case crypto_viban_exchange:
                w.removeFromWallet(transaction.getAmount(), transaction.getNativeAmount());
                CDCWallet eur = (CDCWallet) txApp.wallets.get(getWallet("EUR"));
                eur.addToWallet(transaction.getNativeAmount(), transaction.getNativeAmount(), BigDecimal.ZERO);
                break;

            default:
                System.out.println("This is an unsupported TransactionType: " + t);
        }
    }

    /**
     * Handles crypto withdrawal
     *
     * @param w              the wallet from which crypto is withdrawn
     * @param transaction    the transaction to be made
     * @param outsideWallets all outsideWallets
     */
    private void cryptoWithdrawal(CDCWallet w, Transaction transaction, ArrayList<Wallet> outsideWallets) {
        w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, BigDecimal.ZERO);
        CDCWallet wt = (CDCWallet) outsideWallets.get(getWallet(transaction.getCurrencyType()));
        if (!wt.transactions.contains(transaction)) {
            wt.transactions.add(transaction);
        }
        wt.removeFromWallet(transaction.getAmount(), BigDecimal.ZERO);
        transaction.setOutsideTransaction(true);
    }

    /**
     * Hadles crypto viban purchase
     *
     * @param transaction the transaction which is a vibanPurchase
     */
    private void vibanPurchase(Transaction transaction) {
        if (getWallet(transaction.getToCurrency()) == -1) {
            System.out.println("Tx failed: " + transaction);
        } else {

            CDCWallet wv = (CDCWallet) txApp.wallets.get(getWallet(transaction.getToCurrency()));
            wv.addToWallet(transaction.getToAmount(), transaction.getNativeAmount(), BigDecimal.ZERO);
            //wv.addToWallet(transaction);
            transaction.setWalletId(wv.walletId);
            if (!this.transactions.contains(transaction)) {
                this.transactions.add(transaction);
            }
        }
    }

    public TxApp getTxApp() {
        return txApp;
    }

    public void setTxApp(TxApp txApp) {
        this.txApp = txApp;
    }
}

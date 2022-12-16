package at.msd.friehs_bicha.cdcsvparser.wallet;

import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction;
import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionType;
import at.msd.friehs_bicha.cdcsvparser.general.TxApp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Represents a Wallet object
 *
 */
public class Wallet implements Serializable {

    String currencyType;
    BigDecimal amount;
    BigDecimal amountBonus;
    BigDecimal moneySpent;

    TxApp txApp;

    ArrayList<Transaction> transactions;

    public Wallet(String  currencyType, BigDecimal amount, BigDecimal nativeAmount, TxApp txApp) {
        this.currencyType = currencyType;
        this.amount = new BigDecimal(0);
        this.amount = this.amount.add(amount);
        this.moneySpent = new BigDecimal(0);
        this.moneySpent = this.moneySpent.add(nativeAmount);
        this.amountBonus = new BigDecimal(0);
        this.transactions = new ArrayList<>();
        this.txApp = txApp;
    }

    /**
     * Get Wallet from CurrencyType String
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


    public String getCurrencyType() {
        return currencyType;
    }

    /**
     * Add a transaction to the Wallet
     *
     * @param amount the amount of the coin
     * @param nativeAmount the amount in native currency
     * @param amountBonus the amount the user got for free
     */
    public void addToWallet(BigDecimal amount, BigDecimal nativeAmount, BigDecimal amountBonus) {
        this.amount = this.amount.add(amount);
        this.moneySpent = this.moneySpent.add(nativeAmount);
        this.amountBonus = this.amountBonus.add(amountBonus);
    }


    /**
     * Remove a transaction from the Wallet
     *
     * @param amount the amount to remove
     * @param nativeAmount the amount in native currency to remove
     */
    public void removeFromWallet(BigDecimal amount, BigDecimal nativeAmount) {
        this.amount = this.amount.subtract(amount);
        this.moneySpent = this.moneySpent.subtract(nativeAmount);
    }

    public ArrayList<Transaction> getTransactions() {
        return transactions;
    }


    /**
     * Adds a transactions to the respective Wallet
     *
     * @param transaction the transaction to be added
     */
    public void addTransaction(Transaction transaction) {
        //transactions.add(transaction);
        TransactionType t = transaction.getTransactionType();
        Wallet w = txApp.wallets.get(getWallet(transaction.getCurrencyType()));
        if (!w.transactions.contains(transaction)) {
            w.transactions.add(transaction);
        }
        switch (t) {
            case crypto_purchase: w.addToWallet(transaction.getAmount(), transaction.getNativeAmount(), BigDecimal.ZERO);

            case supercharger_deposit:
                break;

            case rewards_platform_deposit_credited:
                break;//do nothing

            case supercharger_reward_to_app_credited:
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
                break;
            case viban_purchase:
                vibanPurchase(transaction);
                break;
            case crypto_earn_program_created:
                break;
            case crypto_earn_interest_paid:
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
                break;
            case supercharger_withdrawal:
                break;
            case lockup_lock:
                break;
            case crypto_withdrawal:
                cryptoWithdrawal(w, transaction, txApp.outsideWallets);
                break;
            case referral_card_cashback:
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
                break;
            case reimbursement:
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
                break;
            case card_cashback_reverted:
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
                break;
            case crypto_earn_program_withdrawn:
                break;
            case admin_wallet_credited:
                //Free money from fork
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
                break;
            case crypto_wallet_swap_credited:
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
                break;
            case crypto_wallet_swap_debited:
                w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, transaction.getAmount());
                break;
            case crypto_deposit:
                cryptoWithdrawal(w, transaction, txApp.wallets);
                break;
            case dust_conversion_debited:
                System.out.println("Not supported yet: " + transaction);
                break;
            case dust_conversion_credited:
                System.out.println("Not supported yet: " + transaction);
                break;
            case crypto_viban_exchange:
                w.removeFromWallet(transaction.getAmount(), transaction.getNativeAmount());
                Wallet eur = txApp.wallets.get(getWallet("EUR"));
                eur.addToWallet(transaction.getNativeAmount(),transaction.getNativeAmount(), BigDecimal.ZERO);
                break;

            default: System.out.println("This is an unsupported TransactionType: " + t);
        }
    }

    private void cryptoWithdrawal(Wallet w, Transaction transaction, ArrayList<Wallet> outsideWallets) {
        w.addToWallet(transaction.getAmount(), BigDecimal.ZERO, BigDecimal.ZERO);
        Wallet wt = outsideWallets.get(getWallet(transaction.getCurrencyType()));
        if (!wt.transactions.contains(transaction)) {
            wt.transactions.add(transaction);
        }
        wt.removeFromWallet(transaction.getAmount(), BigDecimal.ZERO);
    }

    private void vibanPurchase(Transaction transaction) {
        if (getWallet(transaction.getToCurrency()) == -1)
        {
            System.out.println("Tx failed: " + transaction.toString());
        }else {

        Wallet wv = txApp.wallets.get(getWallet(transaction.getToCurrency()));
        wv.addToWallet(transaction.getToAmount(), transaction.getNativeAmount(), BigDecimal.ZERO);
        }
    }

    public void setCurrencyType(String currencyType) {
        this.currencyType = currencyType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getAmountBonus() {
        return amountBonus;
    }

    public void setAmountBonus(BigDecimal amountBonus) {
        this.amountBonus = amountBonus;
    }

    public BigDecimal getMoneySpent() {
        return moneySpent;
    }

    public void setMoneySpent(BigDecimal moneySpent) {
        this.moneySpent = moneySpent;
    }

    public void setTransactions(ArrayList<Transaction> transactions) {
        this.transactions = transactions;
    }


}

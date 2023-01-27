package at.msd.friehs_bicha.cdcsvparser.wallet;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;

import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction;

/**
 * Represents a basic Wallet
 *
 */
public abstract class Wallet implements Serializable {

    String currencyType;
    BigDecimal amount;
    BigDecimal amountBonus;
    BigDecimal moneySpent;

    ArrayList<Transaction> transactions;

    public Wallet(String  currencyType, BigDecimal amount, BigDecimal nativeAmount) {
        this.currencyType = currencyType;
        this.amount = new BigDecimal(0);
        this.amount = this.amount.add(amount);
        this.moneySpent = new BigDecimal(0);
        this.moneySpent = this.moneySpent.add(nativeAmount);
        this.amountBonus = new BigDecimal(0);
        this.transactions = new ArrayList<>();
    }

    /**
     * Get CDCWallet from CurrencyType String
     *
     * @param ct the CurrencyType as String
     * @return the index of the wallet
     */
    public abstract int getWallet(String ct);

    public String getCurrencyType() {
        return currencyType;
    }

    /**
     * Remove a transaction from the Wallet
     *
     * @param amount the amount to remove
     * @param nativeAmount the amount in native currency to remove
     */
    public abstract void removeFromWallet(BigDecimal amount, BigDecimal nativeAmount);

    /**
     * Adds a transactions to the respective Wallet
     *
     * @param transaction the transaction to be added
     */
    public abstract void addTransaction(Transaction transaction);

    public ArrayList<Transaction> getTransactions() {
        return transactions;
    }


    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getAmountBonus() {
        return amountBonus;
    }

    public BigDecimal getMoneySpent() {
        return moneySpent;
    }

}

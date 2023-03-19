package at.msd.friehs_bicha.cdcsvparser.wallet;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Junction;
import androidx.room.PrimaryKey;
import androidx.room.Relation;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction;
import at.msd.friehs_bicha.cdcsvparser.util.Converter;

/**
 * Represents a basic Wallet
 */
@Entity(tableName = "wallet")
@TypeConverters({Converter.class})
public class Wallet implements Serializable {

    public static int uidCounter = 0;
    @PrimaryKey()
    public int walletId;
    @Relation(
            parentColumn = "walletId",
            entityColumn = "transactionId",
            associateBy = @Junction(WalletTransactionCrossRef.class)
    )
    @Ignore
    public List<Transaction> transactions;
    @ColumnInfo(name = "currencyType")
    String currencyType;
    @ColumnInfo(name = "amount", typeAffinity = ColumnInfo.TEXT)
    BigDecimal amount;
    @ColumnInfo(name = "amountBonus", typeAffinity = ColumnInfo.TEXT)
    BigDecimal amountBonus;
    @ColumnInfo(name = "moneySpent", typeAffinity = ColumnInfo.TEXT)
    BigDecimal moneySpent;
    @ColumnInfo(name = "isOutsideWallet")
    Boolean isOutsideWallet = false;

    @Ignore
    public Wallet(String currencyType, BigDecimal amount, BigDecimal nativeAmount) {
        this.currencyType = currencyType;
        this.amount = new BigDecimal(0);
        this.amount = this.amount.add(amount);
        this.moneySpent = new BigDecimal(0);
        this.moneySpent = this.moneySpent.add(nativeAmount);
        this.amountBonus = new BigDecimal(0);
        this.transactions = new ArrayList<>();
        this.walletId = ++uidCounter;
    }


    @Ignore
    public Wallet(String currencyType, BigDecimal amount, BigDecimal amountBonus, BigDecimal moneySpent) {
        this.currencyType = currencyType;
        this.amount = amount;
        this.amountBonus = amountBonus;
        this.moneySpent = moneySpent;
        this.transactions = new ArrayList<>();
        this.walletId = ++uidCounter;
    }


    @Ignore
    public Wallet(String currencyType, BigDecimal amount, BigDecimal amountBonus, BigDecimal moneySpent, ArrayList<Transaction> transactions) {
        this.currencyType = currencyType;
        this.amount = amount;
        this.amountBonus = amountBonus;
        this.moneySpent = moneySpent;
        this.transactions = transactions;
        this.walletId = ++uidCounter;
    }

    public Wallet(int walletId, String currencyType, BigDecimal amount, BigDecimal amountBonus, BigDecimal moneySpent, List<Transaction> transactions) {
        this.walletId = walletId;
        this.currencyType = currencyType;
        this.amount = amount;
        this.amountBonus = amountBonus;
        this.moneySpent = moneySpent;
        this.transactions = transactions;
    }

    //@Ignore
    public Wallet(int walletId, String currencyType, BigDecimal amount, BigDecimal amountBonus, BigDecimal moneySpent) {
        this.walletId = walletId;
        this.currencyType = currencyType;
        this.amount = amount;
        this.amountBonus = amountBonus;
        this.moneySpent = moneySpent;
    }

    public Wallet(Wallet wallet) {
        this.walletId = wallet.walletId;
        this.currencyType = wallet.currencyType;
        this.amount = wallet.amount;
        this.amountBonus = wallet.amountBonus;
        this.moneySpent = wallet.moneySpent;
        this.transactions = wallet.transactions;
        if (transactions == null) transactions = new ArrayList<>();
        this.isOutsideWallet = wallet.isOutsideWallet;
    }

    /**
     * Get Wallet from CurrencyType String
     *
     * @param ct the CurrencyType as String
     * @return the index of the wallet
     */
    public int getWallet(String ct) {
        throw new UnsupportedOperationException();
    }

    public String getCurrencyType() {
        return currencyType;
    }

    /**
     * Remove a transaction from the Wallet
     *
     * @param amount       the amount to remove
     * @param nativeAmount the amount in native currency to remove
     */
    public void removeFromWallet(BigDecimal amount, BigDecimal nativeAmount) {
        this.amount = this.amount.subtract(amount);
        this.moneySpent = this.moneySpent.subtract(nativeAmount);
    }

    /**
     * Adds a transactions to the respective Wallet
     *
     * @param transaction the transaction to be added
     */
    public void addTransaction(Transaction transaction) {
        throw new UnsupportedOperationException();
    }

    public ArrayList<Transaction> getTransactions() {
        return (ArrayList<Transaction>) transactions;
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

    public Boolean isOutsideWallet() {
        return isOutsideWallet;
    }

    public void setOutsideWallet(Boolean outsideWallet) {
        isOutsideWallet = outsideWallet;
    }
}

package at.msd.friehs_bicha.cdcsvparser.transactions;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import at.msd.friehs_bicha.cdcsvparser.util.Converter;
import at.msd.friehs_bicha.cdcsvparser.util.CurrencyType;
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Date;

/**
 * Represents a Transaction object
 *
 */
@Entity(tableName = "transactions")
@TypeConverters({Converter.class})
public class Transaction implements Serializable {

    @PrimaryKey()
    public int transactionId;

    public static int uidCounter = 0;

    @ColumnInfo(name = "date")
    Date date;

    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "currencyType")
    String currencyType;

    @ColumnInfo(name = "amount", typeAffinity = ColumnInfo.TEXT)
    BigDecimal amount;

    @ColumnInfo(name = "nativeAmount", typeAffinity = ColumnInfo.TEXT)
    BigDecimal nativeAmount;

    @ColumnInfo(name = "amountBonus", typeAffinity = ColumnInfo.TEXT)
    BigDecimal amountBonus;

    @ColumnInfo(name = "transactionType", typeAffinity = ColumnInfo.TEXT)
    TransactionType transactionType;

    @ColumnInfo(name = "transHash")
    String transHash;

    @ColumnInfo(name = "toCurrency")
    String toCurrency;

    @ColumnInfo(name = "toAmount", typeAffinity = ColumnInfo.TEXT)
    BigDecimal toAmount;

    @ColumnInfo(name = "walletId")
    public int walletId;

    @ColumnInfo(name = "fromWalletId")
    public int fromWalletId;

    @ColumnInfo(name = "isOutsideTransaction")
    boolean isOutsideTransaction = false;

    @Ignore
    public Transaction(String date, String description, String currencyType, BigDecimal amount, BigDecimal nativeAmount, TransactionType transactionType) {

        if (!CurrencyType.currencys.contains(currencyType)) CurrencyType.currencys.add(currencyType);

        this.date = Converter.dateConverter(date);
        this.description = description;
        this.currencyType = currencyType;
        this.amount = BigDecimal.ZERO;
        this.amount = this.amount.add(amount);
        this.nativeAmount = BigDecimal.ZERO;
        this.nativeAmount = this.nativeAmount.add(nativeAmount);
        this.transactionType = transactionType;
        this.amountBonus = BigDecimal.ZERO;
        this.transactionId = ++uidCounter;
    }

    public Transaction(int transactionId, Date date, String description, String currencyType, BigDecimal amount, BigDecimal nativeAmount, BigDecimal amountBonus, TransactionType transactionType, String transHash, String toCurrency, BigDecimal toAmount, int walletId) {

        if (!CurrencyType.currencys.contains(currencyType)) CurrencyType.currencys.add(currencyType);

        this.transactionId = transactionId;
        this.date = date;
        this.description = description;
        this.currencyType = currencyType;
        this.amount = amount;
        this.nativeAmount = nativeAmount;
        this.amountBonus = amountBonus;
        this.transactionType = transactionType;
        this.transHash = transHash;
        this.toCurrency = toCurrency;
        this.toAmount = toAmount;
        this.walletId = walletId;
    }

    public Date getDate() {
        return date;
    }

    public String getCurrencyType() {
        return currencyType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getNativeAmount() {
        return nativeAmount;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public String getTransHash() {
        return transHash;
    }

    public void setTransHash(String transHash) {
        this.transHash = transHash;
    }

    public String getToCurrency() {
        return toCurrency;
    }

    public void setToCurrency(String toCurrency) {
        this.toCurrency = toCurrency;
    }

    public BigDecimal getToAmount() {
        return toAmount;
    }

    public void setToAmount(BigDecimal toAmount) {
        this.toAmount = toAmount;
    }

    public BigDecimal getAmountBonus() {
        return amountBonus;
    }

    public void setAmountBonus(BigDecimal amountBonus) {
        this.amountBonus = amountBonus;
    }

    public int getFromWalletId() {
        return fromWalletId;
    }

    public void setFromWalletId(int fromWalletId) {
        this.fromWalletId = fromWalletId;
    }

    public void setWalletId(int uid) {
        this.walletId = uid;
    }

    public boolean isOutsideTransaction() {
        return isOutsideTransaction;
    }

    public void setOutsideTransaction(boolean outsideTransaction) {
        isOutsideTransaction = outsideTransaction;
    }

    @NonNull
    @Override
    public String toString() {
        return
                date +  "\n"+
                "Description: " + description + '\n' +
                "Amount: " + nativeAmount.round(new MathContext(5)) + " €\n" +
                "AssetAmount: " + amount.round(new MathContext(5)) + " " + currencyType;
    }
}

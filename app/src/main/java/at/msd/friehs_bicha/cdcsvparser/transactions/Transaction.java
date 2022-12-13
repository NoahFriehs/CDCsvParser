package at.msd.friehs_bicha.cdcsvparser.transactions;

import at.msd.friehs_bicha.cdcsvparser.util.Converter;
import at.msd.friehs_bicha.cdcsvparser.util.CurrencyType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Date;

/**
 * Represents a Transaction object
 *
 */
public class Transaction implements Serializable {

    Date date;
    String description;
    String currencyType;
    BigDecimal amount;
    BigDecimal nativeAmount;
    TransactionType transactionType;
    String transHash;
    String toCurrency;
    BigDecimal toAmount;

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

    @Override
    public String toString() {
        return
                date +  "\n"+
                "Description: " + description + '\n' +
                "Amount: " + amount + " €\n" +
                "NativeAmount: " + nativeAmount.round(new MathContext(5)) + " " + currencyType;
    }
}

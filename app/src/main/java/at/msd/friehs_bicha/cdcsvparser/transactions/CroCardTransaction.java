package at.msd.friehs_bicha.cdcsvparser.transactions;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.math.BigDecimal;

public class CroCardTransaction extends Transaction implements Serializable {

    String transactionType;

    public CroCardTransaction(String date, String description, String currencyType, BigDecimal amount, BigDecimal nativeAmount, String transactionType) {
        super(date, description, currencyType, amount, nativeAmount, TransactionType.STRING);
        this.transactionType = transactionType;
    }


    public String getTransactionTypeString() {
        return transactionType;
    }

    @NonNull
    @Override
    public String toString() {
        return "CardTransaction{" +
                "date=" + date +
                ", description='" + description + '\'' +
                ", amount=" + amount +
                ", transactionType=" + transactionType +
                ", toCurrency=" + toCurrency +
                ", toAmount=" + toAmount +
                '}';
    }

}

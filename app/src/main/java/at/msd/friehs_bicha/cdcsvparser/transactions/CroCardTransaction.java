package at.msd.friehs_bicha.cdcsvparser.transactions;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;

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
        return
                date +  "\n"+
                        "Description: " + description + '\n' +
                        "Amount: " + nativeAmount.round(new MathContext(5)) + currencyType +"\n" +
                        "transactionType: " + transactionType;
    }

}

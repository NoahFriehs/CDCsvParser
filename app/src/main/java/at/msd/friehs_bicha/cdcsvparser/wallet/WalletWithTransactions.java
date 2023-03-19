package at.msd.friehs_bicha.cdcsvparser.wallet;

import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.ArrayList;
import java.util.List;

import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction;

//@Entity(tableName = "WalletWithTransactions")
public class WalletWithTransactions {

    @Embedded
    public Wallet wallet;

    @Relation(
            parentColumn = "walletId",
            entityColumn = "walletId",
            associateBy = @Junction(WalletTransactionCrossRef.class)
    )
    public List<Transaction> transactions;

    public WalletWithTransactions(Wallet wallet, ArrayList<Transaction> transactions) {
        this.wallet = wallet;
        this.transactions = transactions;
    }
}

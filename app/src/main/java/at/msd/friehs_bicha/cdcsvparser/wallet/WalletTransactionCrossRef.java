package at.msd.friehs_bicha.cdcsvparser.wallet;

import androidx.room.Entity;
import androidx.room.ForeignKey;

import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction;

@Entity(tableName = "wallet_transaction",
        primaryKeys = {"walletId", "transactionId"},
        foreignKeys = {
                @ForeignKey(entity = Wallet.class,
                        parentColumns = "walletId",
                        childColumns = "walletId"),
                @ForeignKey(entity = Transaction.class,
                        parentColumns = "transactionId",
                        childColumns = "transactionId")
        })
public class WalletTransactionCrossRef {
    public int walletId;
    public int transactionId;
}

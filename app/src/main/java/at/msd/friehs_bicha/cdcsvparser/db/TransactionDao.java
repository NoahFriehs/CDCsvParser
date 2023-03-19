package at.msd.friehs_bicha.cdcsvparser.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction;

@Dao
public interface TransactionDao {

    @Query("SELECT * FROM transactions")
    List<Transaction> getAll();

    @Query("SELECT * FROM transactions WHERE transactionId IN (:txIds)")
    List<Transaction> loadAllByIds(int[] txIds);

    @Query("SELECT * FROM transactions WHERE walletId IN (:txIds)")
    List<Transaction> loadAllByWalletIds(int[] txIds);

    @Insert
    void insertAll(List<Transaction> txs);

    @Delete
    void delete(Transaction transaction);

    @Query("DELETE FROM transactions")
    void deleteAll();
}

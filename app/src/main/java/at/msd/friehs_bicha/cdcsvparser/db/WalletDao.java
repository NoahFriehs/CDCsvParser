package at.msd.friehs_bicha.cdcsvparser.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet;
import at.msd.friehs_bicha.cdcsvparser.wallet.WalletWithTransactions;

@Dao
public interface WalletDao {

    @Query("SELECT * FROM wallet")
    List<WalletWithTransactions> getAll();

    @Query("SELECT * FROM wallet WHERE walletId IN (:walletIds)")
    List<WalletWithTransactions> loadAllByIds(int[] walletIds);

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Wallet> wallets);


    @Delete
    void delete(Wallet wallet);

    @Query("DELETE FROM wallet")
    void deleteAll();

    @Transaction
    @Query("SELECT * FROM wallet")
    LiveData<List<WalletWithTransactions>> getWalletsWithTransactions();

    @Query("SELECT * FROM wallet")
    LiveData<List<Wallet>> getAllWallets();

}

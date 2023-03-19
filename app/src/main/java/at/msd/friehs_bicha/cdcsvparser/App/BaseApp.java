package at.msd.friehs_bicha.cdcsvparser.App;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction;
import at.msd.friehs_bicha.cdcsvparser.wallet.CDCWallet;
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet;

public class BaseApp implements Serializable {

    public ArrayList<Wallet> wallets = new ArrayList<>();
    public ArrayList<Wallet> outsideWallets = new ArrayList<>();
    public ArrayList<Transaction> transactions = new ArrayList<>();
    public int amountTxFailed = 0;

}
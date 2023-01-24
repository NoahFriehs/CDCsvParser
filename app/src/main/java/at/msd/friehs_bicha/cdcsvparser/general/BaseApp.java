package at.msd.friehs_bicha.cdcsvparser.general;

import java.io.Serializable;
import java.util.ArrayList;

import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction;
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet;

public class BaseApp implements Serializable {

    public ArrayList<Wallet> wallets = new ArrayList<>();
    public ArrayList<Wallet> outsideWallets = new ArrayList<>();
    public ArrayList<Transaction> transactions;
    public int amountTxFailed = 0;

}

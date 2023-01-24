package at.msd.friehs_bicha.cdcsvparser.wallet;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;

import at.msd.friehs_bicha.cdcsvparser.general.CroCardTxApp;
import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction;
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction;

public class CroCardWallet extends Wallet implements Serializable {

    public static ArrayList<String> tts = new ArrayList<>();
    String transactionType;
    CroCardTxApp txApp;

    public CroCardWallet(String currencyType, BigDecimal amount, String transactionType, CroCardTxApp txApp) {
        super(currencyType, amount, amount);
        this.transactionType = transactionType;
        if (!tts.contains(transactionType)) {
            tts.add(transactionType);
        }
        this.txApp = txApp;
    }

    public void addTransaction(Transaction transaction) {

        CroCardTransaction cardTransaction = (CroCardTransaction) transaction;

        String tt = (cardTransaction.getTransactionTypeString());
        if (tts.contains(tt)) {
            CroCardWallet w = (CroCardWallet) txApp.wallets.get(tts.indexOf(tt));
            w.addToWallet(transaction.getAmount());
            w.transactions.add(cardTransaction);
        } else {
            CroCardWallet w;
            if (!txApp.isUseStrictWalletType())
            {
                w = getNonStrictWallet(tt);
                if (w == null){
                    w = new CroCardWallet("EUR", cardTransaction.getAmount(), tt, txApp);
                    txApp.wallets.add(w);
                } else {
                    w.addToWallet(transaction.getAmount());
                }
                w.transactions.add(cardTransaction);
            } else {
                w = new CroCardWallet("EUR", cardTransaction.getAmount(), tt, txApp);
                w.transactions.add(cardTransaction);
                txApp.wallets.add(w);
            }
            //w.addToWallet(transaction.getAmount());

        }
    }

    /**
     * Get Wallet index from CurrencyType String
     *
     * @param ct the CurrencyType as String
     * @return the index of the wallet
     */
    public int getWallet(String ct) {
        int i = 0;
        for (Wallet w : txApp.wallets) {
            if (((CroCardWallet)w).transactionType.equals(ct)) return i;
            i++;
        }
        return -1;
    }

    public void writeAmount() {
        BigDecimal amountSpent = BigDecimal.ZERO;
        for (Wallet w : txApp.wallets) {
            //System.out.println("-".repeat(20));
            System.out.println(((CroCardWallet)w).transactionType);
            System.out.println(w.amount);
            System.out.println(w.moneySpent);
            System.out.println("Transactions: " + ((CroCardWallet)w).transactions.size());
            amountSpent = amountSpent.add(w.moneySpent);
        }
        //System.out.println("-".repeat(20));
        System.out.println("Amount total spent: " + amountSpent);
    }

    public void addToWallet(BigDecimal amount) {
        this.amount = this.amount.add(amount);
    }

    public ArrayList<Transaction> getTxs() {
        return transactions;
    }

    public String getTransactionType() {
        return transactionType;
    }

    /**
     * Remove a transaction from the CDCWallet
     *
     * @param amount the amount to remove
     * @param nativeAmount the amount in native currency to remove
     */
    public void removeFromWallet(BigDecimal amount, BigDecimal nativeAmount) {
        this.amount = this.amount.subtract(amount);
        this.moneySpent = this.moneySpent.subtract(nativeAmount);
    }

    private CroCardWallet getNonStrictWallet(String tt){

        for (Wallet w : txApp.wallets) {
            if (((CroCardWallet) w).transactionType.equals(tt)) {
                return ((CroCardWallet) w);
            }
        }

        if (tt.equals("EUR -> EUR")) return null;   //TODO: fix Curencys

        if (tt.contains("Refund: ")){
            tt = tt.substring(8);
        }
        if (tt.contains("Refund reversal: ")){
            tt = tt.substring(17);
        }

        for (Wallet w : txApp.wallets) {
            if (tt.contains(" ")) {
                if (((CroCardWallet) w).transactionType.contains(tt.substring(0, tt.indexOf(" ")))) {
                    ((CroCardWallet) w).transactionType = tt.substring(0, tt.indexOf(" "));
                    return ((CroCardWallet) w);
                }
            }
        }

        return null;

    }

}

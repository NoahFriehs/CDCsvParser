package at.fb.Wallet;

import at.fb.Transactions.CardTX;

import java.math.BigDecimal;
import java.util.ArrayList;

import static at.fb.General.CardTxApp.cardWallets;

/**
 * @deprecated
 */
public class CardWallet extends Wallet {

    public static ArrayList<String> tts = new ArrayList<>();
    String transactionType;
    ArrayList<CardTX> txs = new ArrayList<>();

    public CardWallet(String currencyType, BigDecimal amount, String transactionType) {
        super(currencyType, amount, amount);
        this.transactionType = transactionType;
        if (!tts.contains(transactionType)) {
            tts.add(transactionType);
        }
    }

    public static void addTransaction(CardTX transaction) {

        String tt = transaction.getTransactionTypeString();
        if (tts.contains(tt)) {
            CardWallet w = cardWallets.get(tts.indexOf(tt));
            w.addToCardWallet(transaction.getAmount());
            w.txs.add(transaction);
        } else {
            CardWallet w = new CardWallet("EUR", transaction.getAmount(), tt);
            //w.addToCardWallet(transaction.getAmount());
            w.txs.add(transaction);
            cardWallets.add(w);
        }
    }

    public static void writeAmount() {
        BigDecimal amountSpent = BigDecimal.ZERO;
        for (CardWallet w : cardWallets) {
            System.out.println("-".repeat(20));
            System.out.println(w.transactionType);
            System.out.println(w.amount);
            System.out.println(w.moneySpent);
            System.out.println("Transactions: " + w.txs.size());
            amountSpent = amountSpent.add(w.moneySpent);
        }
        System.out.println("-".repeat(20));
        System.out.println("Amount total spent: " + amountSpent);
    }

    public void addToCardWallet(BigDecimal amount) {
        this.amount = this.amount.add(amount);
    }

    public ArrayList<CardTX> getTxs() {
        return txs;
    }

    public String getTransactionType() {
        return transactionType;
    }
}

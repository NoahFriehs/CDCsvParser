package at.msd.friehs_bicha.cdcsvparser.General;

import at.msd.friehs_bicha.cdcsvparser.Price.AssetValue;
import at.msd.friehs_bicha.cdcsvparser.Transactions.Transaction;
import at.msd.friehs_bicha.cdcsvparser.Transactions.TransactionType;
import at.msd.friehs_bicha.cdcsvparser.Util.CurrencyType;
import at.msd.friehs_bicha.cdcsvparser.Wallet.Wallet;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static at.msd.friehs_bicha.cdcsvparser.Util.Converter.stringToDateConverter;
import static at.msd.friehs_bicha.cdcsvparser.Util.Converter.ttConverter;

/**
 * The parser control for the CDCsvParser
 *
 */
public class AppModel implements Serializable {

    public boolean isRunning = false;

    public TxApp txApp;

    public boolean init(ArrayList<String> file) {
        TxApp app = new TxApp(file);
        this.txApp = app;
        isRunning = true;
        return true;
    }

    /**
     * Get transactions for one specific coin
     *
     * @param s the string of the coin
     * @return the transactions that contain this coin
     */
    public List<Transaction> getTXByCoin(String s) {
        ArrayList<Transaction> transactions = new ArrayList<>();
        try {
            if (!CurrencyType.currencys.contains(s)) return null;
            for (Wallet w : txApp.wallets) {
                if (w.getCurrencyType().equals(s)) {
                    transactions.addAll(w.getTransactions());
                }
            }
            for (Wallet w : txApp.outsideWallets) {
                if (w.getCurrencyType().equals(s)) {
                    transactions.addAll(w.getTransactions());
                }
            }
        } catch (Exception e) {
            System.out.println("Invalid Input");

        }
        return transactions;
    }

    public List<Transaction> getTXByType(String s) {
        ArrayList<Transaction> transactions = new ArrayList<>();
        try {
            TransactionType tt = ttConverter(s);
            for (Transaction t : txApp.transactions) {
                if (t.getTransactionType().equals(tt)) {
                    transactions.add(t);
                }
            }

        } catch (Exception e) {
            System.out.println("Invalid Input");
        }
        return transactions;
    }

    /**
     *
     *
     * @param year the year from which the transactions should be returned
     * @param month the month from which the transactions should be returned, if 0 then only year tx get returned
     * @param day the day from which the transactions should be returned, if 0 then only month tx get returned
     * @return the List with the transactions
     */
    public List<Transaction> getTxByDate(int year, int month, int day) {
        ArrayList<Transaction> rightTX = new ArrayList<>();
        ArrayList<Transaction> rightMonthTX = new ArrayList<>();
        ArrayList<Transaction> rightDayTX = new ArrayList<>();
        int txPerYear = 0;
        int txPerMonth = 0;
        int txPerDay = 0;
        for (Transaction t : txApp.transactions) {
            if (Integer.parseInt(Objects.requireNonNull(stringToDateConverter(t.getDate())).substring(0, 4)) == year) {
                txPerYear++;
                rightTX.add(t);
            }
        }
        System.out.print("Press 0 to view " + txPerYear + " transaction(s) or enter month (MM): ");

        if (month == 0) {
            return rightTX;
        } else {

            for (Transaction t : rightTX) {
                if (Integer.parseInt(Objects.requireNonNull(stringToDateConverter(t.getDate())).substring(5, 7)) == month) {
                    txPerMonth++;
                    rightMonthTX.add(t);
                }
            }
        }

        System.out.print("Press 0 to view " + txPerMonth + " transaction(s) or enter day");

        if (day == 0) {
            return rightMonthTX;
        } else {

            for (Transaction t : rightMonthTX) {
                if (Integer.parseInt(Objects.requireNonNull(stringToDateConverter(t.getDate())).substring(8, 10)) == day) {
                    txPerDay++;
                    rightDayTX.add(t);
                }
            }
            System.out.println("" + txPerDay + " transaction(s)");
            return rightDayTX;
        }
    }

    /**
     * Returns the total amount spent
     *
     * @return the total amount spent
     */
    public BigDecimal getTotalPrice() {

        BigDecimal totalPrice = new BigDecimal(0);

        for (Wallet wallet : txApp.wallets) {
            totalPrice = totalPrice.add(wallet.getMoneySpent());
        }
        return totalPrice;
    }

    /**
     * Returns the total amount spent
     *
     * @return the total amount spent
     */
    public double getTotalBonus() {

        AssetValue asset = new AssetValue();

        AtomicReference<Double> valueOfAll = new AtomicReference<>((double) 0);
        Thread t = new Thread(() ->{
            for (Wallet wallet : txApp.wallets) {
                double price = asset.getPrice(wallet.getCurrencyType());
                BigDecimal amount = wallet.getAmountBonus();
                valueOfAll.updateAndGet(v -> v + price * amount.doubleValue());
            }
        });
        t.start();
        while (t.isAlive()){
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Waiting");
        }

        return valueOfAll.get();
    }

}

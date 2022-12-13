package at.msd.friehs_bicha.cdcsvparser.general;

import at.msd.friehs_bicha.cdcsvparser.price.AssetValue;
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction;
import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionType;
import at.msd.friehs_bicha.cdcsvparser.util.CurrencyType;
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static at.msd.friehs_bicha.cdcsvparser.util.Converter.stringToDateConverter;
import static at.msd.friehs_bicha.cdcsvparser.util.Converter.ttConverter;

/**
 * The parser control for the CDCsvParser
 *
 */
public class AppModel implements Serializable {

    public TxApp txApp;
    public static AssetValue asset;
    public boolean isRunning = false;

    public AppModel(ArrayList<String> file) {
        TxApp app = new TxApp(file);
        asset = new AssetValue();
        this.txApp = app;
        isRunning = true;
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
     * Returns the total amount earned as a bonus
     *
     * @return the total amount earned as a bonus
     */
    public double getTotalBonus() {

        AtomicReference<Double> valueOfAll = new AtomicReference<>((double) 0);
        for (Wallet wallet : txApp.wallets) {
            if (Objects.equals(wallet.getCurrencyType(), "EUR")) continue;
            double price = asset.getPrice(wallet.getCurrencyType());
            BigDecimal amount = wallet.getAmountBonus();
            valueOfAll.updateAndGet(v -> v + price * amount.doubleValue());
        }

        return valueOfAll.get();
    }

    /**
     * Returns the total amount earned as a bonus
     *
     * @return the total amount earned as a bonus
     */
    public double getTotalBonus(Wallet wallet) {
        AtomicReference<Double> valueOfAll = new AtomicReference<>((double) 0);
            double price = asset.getPrice(wallet.getCurrencyType());
            BigDecimal amount = wallet.getAmountBonus();
            valueOfAll.updateAndGet(v -> v + price * amount.doubleValue());

        return valueOfAll.get();
    }

    /**
     * Returns the total amount the assets are worth in EUR
     *
     * @return the total amount the assets are worth in EUR
     */
    public double getValueOfAssets(){

        double valueOfAll = (double) 0;

        for (Wallet w : txApp.wallets) {
            if (Objects.equals(w.getCurrencyType(), "EUR")) continue;
            double price = asset.getPrice(w.getCurrencyType());
            BigDecimal amount = w.getAmount();
            valueOfAll += price * amount.doubleValue();
        }

        return valueOfAll;
    }

    public double getValueOfAssets(Wallet w){

        double valueOfWallet;
        double price = asset.getPrice(w.getCurrencyType());
        BigDecimal amount = w.getAmount();
        valueOfWallet = price * amount.doubleValue();

        return valueOfWallet;
    }

}

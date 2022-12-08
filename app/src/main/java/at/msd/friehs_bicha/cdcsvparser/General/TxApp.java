package at.msd.friehs_bicha.cdcsvparser.General;

import at.msd.friehs_bicha.cdcsvparser.Price.AssetValue;
import at.msd.friehs_bicha.cdcsvparser.Transactions.Transaction;
import at.msd.friehs_bicha.cdcsvparser.Transactions.TransactionType;
import at.msd.friehs_bicha.cdcsvparser.Util.CurrencyType;
import at.msd.friehs_bicha.cdcsvparser.Wallet.Wallet;

import java.io.Serializable;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import static at.msd.friehs_bicha.cdcsvparser.Util.Converter.ttConverter;

/**
 * The main class of the parser
 *
 */
public class TxApp implements Serializable {

    public ArrayList<Wallet> wallets = new ArrayList<>();
    public ArrayList<Wallet> outsideWallets = new ArrayList<>();
    public ArrayList<Transaction> transactions = new ArrayList<>();

    public TxApp(ArrayList<String> file) {
        try {
            transactions = parseTransactions(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("We have " + transactions.size() + " transaction(s).");
        createWallets();
        fillWallet(transactions);

    }


    /**
     * Csv file to Transaction list
     *
     * @param input csv file as String list
     * @return Transactions list
     */
    private ArrayList<Transaction> parseTransactions(ArrayList<String> input) {
        input.remove(0);
        ArrayList<Transaction> transactions = new ArrayList<>();

        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        String pattern = "#,##";
        DecimalFormat decimalFormat = new DecimalFormat(pattern, symbols);
        decimalFormat.setParseBigDecimal(true);

        for (String transaction : input) {
            try {
                String[] sa = transaction.split(",");
                if (sa.length == 10 || sa.length == 11) {
                    Transaction t;
                    if (Double.parseDouble(sa[3]) == 0) {
                        if (Double.parseDouble(sa[7]) == 0) {
                            t = new Transaction(sa[0], sa[1], sa[2], BigDecimal.ZERO, BigDecimal.ZERO, ttConverter(sa[9]));
                        } else {
                            t = new Transaction(sa[0], sa[1], sa[2], BigDecimal.ZERO, (BigDecimal) decimalFormat.parse(sa[7]), ttConverter(sa[9]));
                        }
                    } else {
                        t = new Transaction(sa[0], sa[1], sa[2], (BigDecimal) decimalFormat.parse(sa[3]), (BigDecimal) decimalFormat.parse(sa[7]), ttConverter(sa[9]));
                    }
                    if (sa.length ==  11) t.setTransHash(sa[10]);
                    if (ttConverter(sa[9]) == TransactionType.viban_purchase) {
                        t.setToCurrency(sa[4]);
                        t.setToAmount(BigDecimal.valueOf(Double.parseDouble(sa[5])));
                    }
                    transactions.add(t);

                } else {
                    System.out.println(Arrays.toString(sa));
                    System.out.println(sa.length);
                }
            } catch (Exception e) {
                System.out.println("Error while processing the following transaction: " + transaction + " | " + e.getMessage());
//                throw new RuntimeException(e);
            }
        }
        return transactions;
    }

    /**
     * Creates Wallets for every CurrencyType
     *
     */
    private void createWallets() {
        for (String t : CurrencyType.currencys) {
            wallets.add(new Wallet(t, BigDecimal.ZERO, BigDecimal.ZERO, this));
        }
        for (String t : CurrencyType.currencys) {
            outsideWallets.add(new Wallet(t, BigDecimal.ZERO, BigDecimal.ZERO, this));
        }
    }

    /**
     * Fills the Wallets with the given transaction list
     *
     * @param tr the transaction list to be processed
     */
    private void fillWallet(ArrayList<Transaction> tr) {
        for (Transaction t : tr) {
            wallets.get(0).addTransaction(t);
        }
        System.out.println("We have " + wallets.size() + " Wallets");
    }

    public Double getValueOfAssets(){

        AssetValue asset = new AssetValue();

        Double valueOfAll = (double) 0;

        for (Wallet w : wallets) {
            if (Objects.equals(w.getCurrencyType(), "EUR")) continue;
            double price = asset.getPrice(w.getCurrencyType());
            BigDecimal amount = w.getAmount();
            valueOfAll += price * amount.doubleValue();
        }

        return valueOfAll;
    }



}

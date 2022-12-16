package at.msd.friehs_bicha.cdcsvparser.general;

import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction;
import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionType;
import at.msd.friehs_bicha.cdcsvparser.util.CurrencyType;
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet;

import java.io.Serializable;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import static at.msd.friehs_bicha.cdcsvparser.util.Converter.ttConverter;

/**
 * The main class of the parser
 *
 */
public class TxApp implements Serializable {

    public ArrayList<Wallet> wallets = new ArrayList<>();
    public ArrayList<Wallet> outsideWallets = new ArrayList<>();
    public ArrayList<Transaction> transactions = new ArrayList<>();
    public int amountTxFailed = 0;

    public TxApp(ArrayList<String> file) {
        try {
            transactions = getTransactions(file);
        } catch (Exception e) {
            throw new IllegalArgumentException("This file seems to be not supported yet.");
        }
        System.out.println("We have " + transactions.size() + " transaction(s).");
        createWallets();
        fillWallet(transactions);
        if (amountTxFailed > 0){
            throw new RuntimeException(amountTxFailed + " transaction(s) failed");
        }
    }


    /**
     * Csv file to Transaction list
     *
     * @param input csv file as String list
     * @return Transactions list
     * @throws IllegalArgumentException when the file is not supported
     */
    private ArrayList<Transaction> getTransactions(ArrayList<String> input) {
        if (!Objects.equals(input.get(0), "Timestamp (UTC),Transaction Description,Currency,Amount,To Currency,To Amount,Native Currency,Native Amount,Native Amount (in USD),Transaction Kind,Transaction Hash")){
            throw new IllegalArgumentException("This file seems to be not supported yet.");
        }
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
                amountTxFailed++;
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


}
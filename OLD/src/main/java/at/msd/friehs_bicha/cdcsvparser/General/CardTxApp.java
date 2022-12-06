package at.msd.friehs_bicha.cdcsvparser.General;

import at.msd.friehs_bicha.cdcsvparser.Transactions.CardTX;
import at.msd.friehs_bicha.cdcsvparser.Util.IOHandler;
import at.msd.friehs_bicha.cdcsvparser.Wallet.CardWallet;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;


/**
 * @deprecated
 */
public class CardTxApp {

    public static ArrayList<CardWallet> cardWallets = new ArrayList<>();
    public static ArrayList<CardTX> transactions = new ArrayList<>();

    public static void main(String[] args) {
        try {
            transactions = getTransactions(IOHandler.readFile(args[0]));
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("We have " + transactions.size() + " transaction(s).");
        fillWallet(transactions);
        System.out.println("we have " + cardWallets.size() + " different transactions.");
        //CardWallet.writeAmount();
    }


    public static ArrayList<CardTX> getTransactions() {
        return transactions;
    }

    /**
     * Csv file to CardTX list
     *
     * @param input csv file as String list
     * @return CardTX list
     */
    private static ArrayList<CardTX> getTransactions(ArrayList<String> input) {
        input.remove(0);
        ArrayList<CardTX> transactions = new ArrayList<>();

        // Create a DecimalFormat that fits your requirements
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        String pattern = "#,##";
        DecimalFormat decimalFormat = new DecimalFormat(pattern, symbols);
        decimalFormat.setParseBigDecimal(true);

        for (String transaction : input) {
            try {
                String[] sa = transaction.split(",");
                if (sa.length == 9) {
                    CardTX t = new CardTX(sa[0], sa[1], sa[2], (BigDecimal) decimalFormat.parse(sa[7]), (BigDecimal) decimalFormat.parse(sa[7]), (sa[1]));

                    transactions.add(t);

                } else {
                    System.out.println(Arrays.toString(sa));
                    System.out.println(sa.length);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return transactions;
    }


    private static void fillWallet(ArrayList<CardTX> tr) {
        for (CardTX t : tr) {
            CardWallet.addTransaction(t);
        }
    }


}

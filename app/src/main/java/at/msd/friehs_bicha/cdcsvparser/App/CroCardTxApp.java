package at.msd.friehs_bicha.cdcsvparser.App;

import java.io.Serializable;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;

import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction;
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction;
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet;
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet;

public class CroCardTxApp extends BaseApp implements Serializable {

    private boolean useStrictWalletType;

    CroCardTxApp(ArrayList<String> file, boolean useStrictWallet) {

        setUseStrictWalletType(useStrictWallet);
        try {
            this.transactions.addAll(getTransactions(file));
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("We have " + this.transactions.size() + " transaction(s).");
        fillWallet();
        System.out.println("we have " + getWallets().size() + " different transactions.");
        //((CroCardWallet)wallets.get(0)).writeAmount();
    }


    /**
     * Csv file to CroCardTransaction list
     *
     * @param input csv file as String list
     * @return CroCardTransaction list
     */
    private ArrayList<CroCardTransaction> getTransactions(ArrayList<String> input) {
        input.remove(0);
        ArrayList<CroCardTransaction> transactions = new ArrayList<>();

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
                    CroCardTransaction t = new CroCardTransaction(sa[0], sa[1], sa[2], (BigDecimal) decimalFormat.parse(sa[7]), (BigDecimal) decimalFormat.parse(sa[7]), (sa[1]));

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


    private void fillWallet() {
        System.out.println("Filling Wallets");
        wallets.add(new CroCardWallet("EUR", BigDecimal.ZERO, "EUR -> EUR", this));
        for (Transaction t : transactions) {
            wallets.get(0).addTransaction(t);
        }
        System.out.println("Wallets filled");
    }

    public ArrayList<Wallet> getWallets() {
        return wallets;
    }

    public void setWallets(ArrayList<Wallet> wallets) {
        this.wallets = wallets;
    }

    public ArrayList<Transaction> getTransactions() {
        return transactions;
    }

    public void setTransactions(ArrayList<Transaction> transactions) {
        this.transactions = transactions;
    }

    /**
     * Can lead to some false wallets bc of Currencies TODO
     */
    public boolean isUseStrictWalletType() {
        return useStrictWalletType;
    }

    public void setUseStrictWalletType(boolean useStrictWalletType) {
        this.useStrictWalletType = useStrictWalletType;
    }
}

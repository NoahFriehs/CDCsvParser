package at.msd.friehs_bicha.cdcsvparser.general;

import java.io.Serializable;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;

import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction;
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet;
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet;

public class CroCardTxApp extends BaseApp implements Serializable {

    private ArrayList<CroCardTransaction> transactions = new ArrayList<>();

    private boolean useStrictWalletType;

    CroCardTxApp(ArrayList<String> file, boolean useStrictWallet) {

        setUseStrictWalletType(useStrictWallet);
        try {
            setTransactions(getTransactions(file));
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("We have " + getTransactions().size() + " transaction(s).");
        fillWallet(getTransactions());
        System.out.println("we have " + getWallets().size() + " different transactions.");
        //CardWallet.writeAmount();
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


    private void fillWallet(ArrayList<CroCardTransaction> tr) {
        wallets.add(new CroCardWallet("EUR", BigDecimal.ZERO, "EUR -> EUR", this));
        for (CroCardTransaction t : tr) {
            getWallets().get(0).addTransaction(t);
        }
    }

    public ArrayList<Wallet> getWallets() {
        return wallets;
    }

    public void setWallets(ArrayList<Wallet> wallets) {
        this.wallets = wallets;
    }

    public ArrayList<CroCardTransaction> getTransactions() {
        return transactions;
    }

    public void setTransactions(ArrayList<CroCardTransaction> transactions) {
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

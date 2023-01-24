package at.msd.friehs_bicha.cdcsvparser.general;

import at.msd.friehs_bicha.cdcsvparser.price.AssetValue;
import at.msd.friehs_bicha.cdcsvparser.wallet.CDCWallet;
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The parser control for the CDCsvParser
 *
 */
public class AppModel implements Serializable {

    public BaseApp txApp;
    public static AssetValue asset;
    public boolean isRunning;

    /**
     * Creates a new AppModel
     *
     * @param file the file to parse
     * @param usage which app to use
     *              0 = CDCsvParser
     *              1 = CroCardParser
     */
    public AppModel(ArrayList<String> file, int usage) {
        String exception = "";
        try {
            switch(usage) {
                case 0:
                    this.txApp = new TxApp(file);
                    break;
                case 1:
                    this.txApp = new CroCardTxApp(file, false);
                    break;
                default:
                    throw new RuntimeException("Usage not found");}
        }catch (Exception e) {
            exception = e.getMessage();
        }
        asset = new AssetValue();
        isRunning = true;

        if (!exception.equals("")){
            throw new RuntimeException(exception);
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
        try {
            AtomicReference<Double> valueOfAll = new AtomicReference<>((double) 0);
            for (Wallet wallet : txApp.wallets) {
                if (Objects.equals(wallet.getCurrencyType(), "EUR")) continue;
                double price = asset.getPrice(wallet.getCurrencyType());
                BigDecimal amount = wallet.getAmountBonus();
                valueOfAll.updateAndGet(v -> v + price * amount.doubleValue());
            }

            return valueOfAll.get();
        }catch (Exception e) {
            return 0;
        }

    }

    /**
     * Returns the total amount earned as a bonus
     *
     * @return the total amount earned as a bonus
     */
    public double getTotalBonus(CDCWallet wallet) {
        try {
            AtomicReference<Double> valueOfAll = new AtomicReference<>((double) 0);
            double price = asset.getPrice(wallet.getCurrencyType());
            BigDecimal amount = wallet.getAmountBonus();
            valueOfAll.updateAndGet(v -> v + price * amount.doubleValue());

            return valueOfAll.get();
        }catch (Exception e) {
            return 0;
        }
    }

    /**
     * Returns the total amount the assets are worth in EUR
     *
     * @return the total amount the assets are worth in EUR
     */
    public double getValueOfAssets(){
        try {
            double valueOfAll = (double) 0;

            for (Wallet w : txApp.wallets) {
                if (Objects.equals(w.getCurrencyType(), "EUR")) continue;
                double price = asset.getPrice(w.getCurrencyType());
                BigDecimal amount = w.getAmount();
                valueOfAll += price * amount.doubleValue();
            }

            return valueOfAll;
        }catch (Exception e) {
            return 0;
        }
    }

    /**
     * Returns the amount the asset is worth in EUR
     *
     * @return the amount the asset is worth in EUR
     */
    public double getValueOfAssets(CDCWallet w){
        try {
        double valueOfWallet;
        double price = asset.getPrice(w.getCurrencyType());
        BigDecimal amount = w.getAmount();
        valueOfWallet = price * amount.doubleValue();

        return valueOfWallet;
        }catch (Exception e) {
            return 0;
        }
    }

}

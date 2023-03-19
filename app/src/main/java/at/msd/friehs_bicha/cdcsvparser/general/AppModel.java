package at.msd.friehs_bicha.cdcsvparser.general;

import static java.lang.Thread.sleep;

import android.content.Context;

import at.msd.friehs_bicha.cdcsvparser.App.AppType;
import at.msd.friehs_bicha.cdcsvparser.App.CroCardTxApp;
import at.msd.friehs_bicha.cdcsvparser.App.TxApp;
import at.msd.friehs_bicha.cdcsvparser.R;
import at.msd.friehs_bicha.cdcsvparser.SettingsActivity;
import at.msd.friehs_bicha.cdcsvparser.db.AppDatabase;
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue;
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction;
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper;
import at.msd.friehs_bicha.cdcsvparser.wallet.CDCWallet;
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet;
import at.msd.friehs_bicha.cdcsvparser.wallet.WalletWithTransactions;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The parser control for the Parser
 *
 */
public class AppModel extends BaseAppModel implements Serializable {

    public static AssetValue asset;

    /**
     * Creates a new AppModel
     *  @param file the file to parse
     * @param appType which app to use
     * @param useStrictType
     */
    public AppModel(ArrayList<String> file, AppType appType, Boolean useStrictType) {
        super(appType);
        String exception = "";
        try {
            switch(appType) {
                case CdCsvParser:
                    this.txApp = new TxApp(file);
                    break;
                case CroCard:
                    this.txApp = new CroCardTxApp(file, useStrictType);
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


    public AppModel(AppType appType, Boolean useStrictType, Context context) {
        super(appType);

        getFromAndroidDB(context, useStrictType);

        asset = new AssetValue();
        //isRunning = true;
    }


    /**
     * Returns the total amount spent
     *
     * @return the total amount spent
     */
    public BigDecimal getTotalPrice() {

        BigDecimal totalPrice = new BigDecimal(0);

        switch(appType) {
            case CdCsvParser:
                for (Wallet wallet : txApp.wallets) {
                    totalPrice = totalPrice.add(wallet.getMoneySpent());
                }
                break;
            case CroCard:
                ArrayList<Wallet> wallets = (ArrayList<Wallet>) txApp.wallets.clone();
                wallets.remove(0);
                for (Wallet wallet : wallets) {
                    totalPrice = totalPrice.add(wallet.getAmount());
                }
                break;
            default:
                throw new RuntimeException("Usage not found");}
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
    public double getTotalBonus(Wallet wallet) {
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
                if (valueOfAll < 0.0)
                {
                    int i = 0;
                }
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
    public double getValueOfAssets(Wallet w){
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


    public Map<String, String> getAssetMap(Wallet wallet) {
        //TODO add sleep //if (!isRunning) sleep(1000);
        BigDecimal total = wallet.getMoneySpent().round(new MathContext(0));

        Map<String, String> map = new HashMap<>();
        switch(appType) {
            case CdCsvParser:
                double amountOfAsset = getValueOfAssets(wallet);
                double rewardValue = getTotalBonus(wallet);

                if (AppModel.asset.isRunning) {
                    map.put(String.valueOf(R.id.assets_value), Math.round(amountOfAsset * 100.0) / 100.0 + " €");
                    map.put(String.valueOf(R.id.rewards_value), Math.round(rewardValue * 100.0) / 100.0 + " €");
                    map.put(String.valueOf(R.id.profit_loss_value), Math.round((amountOfAsset - total.doubleValue()) * 100.0) / 100.0 + " €");
                    map.put(String.valueOf(R.id.money_spent_value), total.toString() + " €");
                } else {
                    map.put(String.valueOf(R.id.assets_value), "no internet connection");
                    map.put(String.valueOf(R.id.rewards_value), "no internet connection");
                    map.put(String.valueOf(R.id.profit_loss_value), "no internet connection");
                    map.put(String.valueOf(R.id.money_spent_value), total.toString() + " €");
                }
                break;
            case CroCard:
                map.put(String.valueOf(R.id.money_spent_value), total.toString() + " €");
                map.put(String.valueOf(R.id.assets_value), null);
                map.put(String.valueOf(R.id.rewards_value), null);
                map.put(String.valueOf(R.id.profit_loss_value), null);
                map.put(String.valueOf(R.id.assets_value_label), null);
                map.put(String.valueOf(R.id.rewards_label), null);
                map.put(String.valueOf(R.id.profit_loss_label), null);
                break;
            default:
                throw new RuntimeException("Usage not found");
        }

        return map;
    }


    public Map<String, String> getParseMap() throws InterruptedException {
        while (!isRunning) sleep(500);
        try {
            BigDecimal total = getTotalPrice();

            double amountOfAsset = getValueOfAssets();
            double rewardValue = getTotalBonus();

            String totalMoneySpent = StringHelper.formatAmountToString(total.doubleValue());
            Map<String, String> map = new HashMap<>();
            switch (appType) {
                case CdCsvParser:

                    if (AppModel.asset.isRunning) {
                        map.put(String.valueOf(R.id.assets_value), StringHelper.formatAmountToString(amountOfAsset));
                        map.put(String.valueOf(R.id.rewards_value), StringHelper.formatAmountToString(rewardValue));
                        map.put(String.valueOf(R.id.profit_loss_value), StringHelper.formatAmountToString(amountOfAsset - total.doubleValue()));
                        map.put(String.valueOf(R.id.money_spent_value), totalMoneySpent);
                    } else {
                        map.put(String.valueOf(R.id.assets_value), "no internet connection");
                        map.put(String.valueOf(R.id.rewards_value), "no internet connection");
                        map.put(String.valueOf(R.id.profit_loss_value), "no internet connection");
                        map.put(String.valueOf(R.id.money_spent_value), totalMoneySpent);
                    }
                    break;
                case CroCard:
                    map.put(String.valueOf(R.id.money_spent_value), totalMoneySpent);
                    map.put(String.valueOf(R.id.assets_value), null);
                    map.put(String.valueOf(R.id.rewards_value), null);
                    map.put(String.valueOf(R.id.profit_loss_value), null);
                    map.put(String.valueOf(R.id.assets_value_label), null);
                    map.put(String.valueOf(R.id.rewards_label), null);
                    map.put(String.valueOf(R.id.profit_loss_label), null);
                    break;
                default:
                    throw new RuntimeException("Usage not found");
            }

            return map;
        }catch (Exception e) {
            return null;
        }
    }


    public boolean setInAndroidDB(Context context)
    {
        Thread t = new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                //clear db
                db.clearAllTables();
                db.walletDao().deleteAll();
                db.transactionDao().deleteAll();

                db.transactionDao().insertAll(txApp.transactions);
                db.walletDao().insertAll(txApp.wallets);
                db.walletDao().insertAll(txApp.outsideWallets);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t.start();

        return true;
    }


    public boolean getFromAndroidDB(Context context, Boolean useStrictType) {
        Thread t = new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                List<Transaction> tXs = db.transactionDao().getAll();
                List<WalletWithTransactions> wTXs = db.walletDao().getAll();
                switch (appType) {
                    case CdCsvParser:
                        List<CDCWallet> ws = new ArrayList<>();
                        wTXs.forEach(w ->{
                            CDCWallet wallet = new CDCWallet(w.wallet);
                            ws.add(wallet);
                        });
                        this.txApp = new TxApp(tXs, ws);
                        this.isRunning = true;
                        break;
                    case CroCard:
                        //TODO
                        throw new RuntimeException("Usage not Implemented");
                        //break;
                    default:
                        throw new RuntimeException("Usage not found");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t.start();

        return true;
    }
}

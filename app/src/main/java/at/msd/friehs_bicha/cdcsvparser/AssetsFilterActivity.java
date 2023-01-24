package at.msd.friehs_bicha.cdcsvparser;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

import at.msd.friehs_bicha.cdcsvparser.general.AppModel;
import at.msd.friehs_bicha.cdcsvparser.App.AppType;
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction;
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet;
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet;

public class AssetsFilterActivity extends AppCompatActivity {

    AppModel appModel;
    Context context;

        /**
         * sets the spinner and context the first time
         */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assets_filter);
        // calling the action bar
        ActionBar actionBar = getSupportActionBar();

        context = getApplicationContext();

        // showing the back button in action bar
        actionBar.setDisplayHomeAsUpEnabled(true);

        Spinner dropdown = findViewById(R.id.asset_spinner);
        //create a list of items for the spinner.

        appModel = (AppModel) getIntent().getExtras().get("AppModel");

        //test Internet connection
        Thread t1 = new Thread(() ->{
            try {
                appModel.getValueOfAssets();
                AppModel.asset.isRunning = true;
            } catch (Exception e) {
                System.out.println("no internet connection");
            }
        });
        t1.start();

        // make List with all Wallets
        ArrayList<String> wallets = new ArrayList<>();
        switch(appModel.appType) {
            case CdCsvParser:
                appModel.txApp.wallets.forEach(wallet -> wallets.add(wallet.getCurrencyType()));
                break;
            case CroCard:
                appModel.txApp.wallets.forEach(wallet -> wallets.add(((CroCardWallet)wallet).getTransactionType()));
                break;
            default:
                wallets.add("This should not happen");
        }



        wallets.remove("EUR");

        String[] items = wallets.toArray(new String[0]);
        //create an adapter to describe how the items are displayed
        ArrayAdapter<String> assetNamesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        //set the spinners adapter to the previously created one.
        dropdown.setAdapter(assetNamesAdapter);

        //get the specific wallet
        String index = dropdown.getSelectedItem().toString();
        Wallet specificWallet = appModel.txApp.wallets.get(appModel.txApp.wallets.get(0).getWallet(index));

        TextView assetsValue = findViewById(R.id.assets_value);

        // display prices
        Thread t2 = new Thread(() ->{
            try {
                appModel.getValueOfAssets();
                AppModel.asset.isRunning = true;
            } catch (Exception e) {
                System.out.println("no internet connection");
            }
            if (AppModel.asset.isRunning) {
                double amountOfAsset = appModel.getValueOfAssets(specificWallet);

                AssetsFilterActivity.this.runOnUiThread(() -> assetsValue.setText(Math.round(amountOfAsset) + " €"));
            }else {
                AssetsFilterActivity.this.runOnUiThread(() -> assetsValue.setText(R.string.no_internet_connection));
            }
        });
        t2.start();

        displayTxs(specificWallet);

        //if spinner item gets changed
        dropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                //get the specific wallet
                Wallet specificWallet = appModel.txApp.wallets.get(appModel.txApp.wallets.get(0).getWallet(dropdown.getSelectedItem().toString()));

                //display Transactions
                displayTxs(specificWallet);

                TextView assetsValue = findViewById(R.id.assets_value);
                TextView rewards_value = findViewById(R.id.rewards_value);
                TextView profit_loss_value = findViewById(R.id.profit_loss_value);
                TextView all_regarding_tx = findViewById(R.id.all_regarding_tx);

                displayInformation(specificWallet, assetsValue, rewards_value, profit_loss_value, all_regarding_tx, dropdown);

            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {

            }

        });



    }

    private void displayInformation(Wallet specificWallet, TextView assetsValue, TextView rewards_value, TextView profit_loss_value, TextView all_regarding_tx, Spinner dropdown) {
        all_regarding_tx.setText("All transactions regarding " + specificWallet.getCurrencyType());
        if (appModel.appType == AppType.CroCard) {
            all_regarding_tx.setText("All transactions regarding " + ((CroCardWallet) specificWallet).getTransactionType());
        }

        BigDecimal total = appModel.txApp.wallets.get(appModel.txApp.wallets.get(0).getWallet(dropdown.getSelectedItem().toString())).getMoneySpent().round(new MathContext(0));

        //get and set prices
        Thread t = new Thread(() -> {
            if (AppModel.asset.isRunning) {
                double amountOfAsset = appModel.getValueOfAssets(specificWallet);
                double rewardValue = appModel.getTotalBonus(specificWallet);
                AssetsFilterActivity.this.runOnUiThread(() -> assetsValue.setText(Math.round(amountOfAsset * 100.0) / 100.0 + " €"));
                AssetsFilterActivity.this.runOnUiThread(() -> rewards_value.setText(Math.round(rewardValue * 100.0) / 100.0 + " €"));
                AssetsFilterActivity.this.runOnUiThread(() -> profit_loss_value.setText(Math.round((amountOfAsset - total.doubleValue()) * 100.0) / 100.0 + " €"));
            }else {
                AssetsFilterActivity.this.runOnUiThread(() -> assetsValue.setText((getString(R.string.no_internet_connection))));
                AssetsFilterActivity.this.runOnUiThread(() -> rewards_value.setText((R.string.no_internet_connection)));
                AssetsFilterActivity.this.runOnUiThread(() -> profit_loss_value.setText((R.string.no_internet_connection)));
            }
        });
        t.start();
        TextView money_spent_value = findViewById(R.id.money_spent_value);
        money_spent_value.setText(total.toString() + " €");
    }

    /**
         * Displays the Transactions of specificWallet
         *
         * @param specificWallet the CDCWallet which should be displayed
         */
        private void displayTxs(Wallet specificWallet) {
            // Get a reference to the ListView
            ListView listView = findViewById(R.id.lv_txs);

            List<Transaction> transactions = specificWallet.getTransactions();

            // Create an adapter for the ListView
            ArrayAdapter<Transaction> adapterLV = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, transactions);

            // Set the adapter on the ListView
            listView.setAdapter(adapterLV);
        }
    /**
     * Set the back button in action bar
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
            if (item.getItemId() == android.R.id.home) {
                this.finish();
                return true;
            }
        return super.onOptionsItemSelected(item);
    }
}
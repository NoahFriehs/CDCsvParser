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
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction;
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet;

    public class AssetsFilterActivity extends AppCompatActivity {

    AppModel appModel;
    Context context;

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

        ArrayList<String> wallets = new ArrayList<>();
        appModel.txApp.wallets.forEach(wallet -> {
            wallets.add(wallet.getCurrencyType());
        });

        wallets.remove("EUR");

        String[] items = wallets.toArray(new String[0]);
        //create an adapter to describe how the items are displayed, adapters are used in several places in android.
        //There are multiple variations of this, but this is the basic variant.
        ArrayAdapter<String> assetNamesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        //set the spinners adapter to the previously created one.
        dropdown.setAdapter(assetNamesAdapter);

        //get the specific wallet
        Wallet specificWallet = appModel.txApp.wallets.get(appModel.txApp.wallets.get(0).getWallet(dropdown.getSelectedItem().toString()));

        TextView assetsValue = findViewById(R.id.assets_value);
        if (AppModel.asset.isRunning) {
            double amountOfAsset = appModel.getValueOfAssets(specificWallet);

            assetsValue.setText(amountOfAsset + " €");
        }else {
            assetsValue.setText("no internet connection");
        }

        displayTxs(specificWallet);

        dropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                //get the specific wallet
                Wallet specificWallet = appModel.txApp.wallets.get(appModel.txApp.wallets.get(0).getWallet(dropdown.getSelectedItem().toString()));

                displayTxs(specificWallet);

                TextView assetsValue = findViewById(R.id.assets_value);
                TextView rewards_value = findViewById(R.id.rewards_value);
                TextView profit_loss_value = findViewById(R.id.profit_loss_value);
                TextView all_regarding_tx = findViewById(R.id.all_regarding_tx);

                all_regarding_tx.setText("All transactions regarding " + specificWallet.getCurrencyType());

                BigDecimal total = appModel.txApp.wallets.get(appModel.txApp.wallets.get(0).getWallet(dropdown.getSelectedItem().toString())).getMoneySpent().round(new MathContext(0));

                Thread t = new Thread(() -> {
                    if (AppModel.asset.isRunning) {
                        double amountOfAsset = appModel.getValueOfAssets(specificWallet);
                        double rewardValue = appModel.getTotalBonus(specificWallet);
                        AssetsFilterActivity.this.runOnUiThread(() -> assetsValue.setText((amountOfAsset * 100.0) / 100.0 + " €"));
                        AssetsFilterActivity.this.runOnUiThread(() -> rewards_value.setText((rewardValue * 100.0) / 100.0 + " €"));
                        AssetsFilterActivity.this.runOnUiThread(() -> profit_loss_value.setText(Math.round((amountOfAsset - total.doubleValue()) * 100.0) / 100.0 + " €"));
                    }else {
                        AssetsFilterActivity.this.runOnUiThread(() -> assetsValue.setText(("no internet connection")));
                        AssetsFilterActivity.this.runOnUiThread(() -> rewards_value.setText(("no internet connection")));
                        AssetsFilterActivity.this.runOnUiThread(() -> profit_loss_value.setText(("no internet connection")));
                    }
                });
                t.start();
                TextView money_spent_value = findViewById(R.id.money_spent_value);
                money_spent_value.setText(total.toString() + " €");


            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                //TODO
            }

        });



    }

        private void displayTxs(Wallet specificWallet) {
            // Get a reference to the ListView
            ListView listView = findViewById(R.id.lv_txs);

            List<Transaction> transactions = specificWallet.getTransactions();

            // Create an adapter for the ListView
            ArrayAdapter<Transaction> adapterLV = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, transactions);

            // Set the adapter on the ListView
            listView.setAdapter(adapterLV);
        }

        @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
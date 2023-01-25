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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                wallets.remove("EUR");
                break;
            case CroCard:
                appModel.txApp.wallets.forEach(wallet -> wallets.add(((CroCardWallet)wallet).getTransactionType()));
                break;
            default:
                wallets.add("This should not happen");
        }





        String[] items = wallets.toArray(new String[0]);
        //create an adapter to describe how the items are displayed
        ArrayAdapter<String> assetNamesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        //set the spinners adapter to the previously created one.
        dropdown.setAdapter(assetNamesAdapter);

        //get the specific wallet
        String index = dropdown.getSelectedItem().toString();
        Wallet specificWallet = appModel.txApp.wallets.get(appModel.txApp.wallets.get(0).getWallet(index));
        
        // display prices
        displayInformation(specificWallet, findViewById(R.id.all_regarding_tx));

        displayTxs(specificWallet);

        //if spinner item gets changed
        dropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                //get the specific wallet
                Wallet specificWallet = appModel.txApp.wallets.get(appModel.txApp.wallets.get(0).getWallet(dropdown.getSelectedItem().toString()));

                //display Transactions
                displayTxs(specificWallet);

                displayInformation(specificWallet, findViewById(R.id.all_regarding_tx));

            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {

            }

        });



    }

    private void displayInformation(Wallet specificWallet, TextView all_regarding_tx) {
        all_regarding_tx.setText("All transactions regarding " + specificWallet.getCurrencyType());
        if (appModel.appType == AppType.CroCard) {
            all_regarding_tx.setText("All transactions regarding " + ((CroCardWallet) specificWallet).getTransactionType());
        }

        //get and set prices
        Thread t = new Thread(() -> displayTexts(appModel.getAssetMap(specificWallet)));
        t.start();

    }


    private void displayTexts(Map<String, String> texts) {
        texts.forEach((key, value) -> {
            TextView textView = findViewById(getResources().getIdentifier(key, "id", getPackageName()));
            if (value == null){
                textView.setVisibility(View.INVISIBLE);
            } else {
                AssetsFilterActivity.this.runOnUiThread(() -> textView.setText(value));
            }
        });

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
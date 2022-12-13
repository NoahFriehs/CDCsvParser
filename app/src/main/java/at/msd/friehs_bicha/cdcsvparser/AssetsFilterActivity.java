package at.msd.friehs_bicha.cdcsvparser;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;

import at.msd.friehs_bicha.cdcsvparser.general.AppModel;
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet;

public class AssetsFilterActivity extends AppCompatActivity {

    AppModel appModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assets_filter);
        // calling the action bar
        ActionBar actionBar = getSupportActionBar();

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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
//set the spinners adapter to the previously created one.
        dropdown.setAdapter(adapter);

        //get the specific wallet
        Wallet specificWallet = appModel.txApp.wallets.get(appModel.txApp.wallets.get(0).getWallet(dropdown.getSelectedItem().toString()));

        //TODO right implamentation
        TextView assetsValue = findViewById(R.id.assets_value);
        double amountOfAsset = appModel.getValueOfAssets(specificWallet);

        assetsValue.setText(amountOfAsset + " €");
        System.out.println(amountOfAsset);
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
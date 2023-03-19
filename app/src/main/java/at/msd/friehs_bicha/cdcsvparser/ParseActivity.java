package at.msd.friehs_bicha.cdcsvparser;

import static java.lang.Thread.sleep;
import static at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper.getUseAndroidDB;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import java.math.BigDecimal;
import java.util.Map;

import at.msd.friehs_bicha.cdcsvparser.App.AppType;
import at.msd.friehs_bicha.cdcsvparser.general.AppModel;
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper;

public class ParseActivity extends AppCompatActivity {

    AppModel appModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parse);

        // calling the action bar
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        getAppModel();

        Button btnFilter = findViewById(R.id.btn_filter);
        Button btnTx = findViewById(R.id.btn_all_tx);

        btnFilter.setOnClickListener(view -> {
            if (appModel.isRunning) {
                Intent intent = new Intent(ParseActivity.this, AssetsFilterActivity.class);
                //intent.putExtra("NAME_KEY","Value");
                intent.putExtra("AppModel", appModel);
                startActivity(intent);
            }
        });
        btnTx.setOnClickListener(view -> {
            if (appModel.isRunning) {
                Intent intent = new Intent(ParseActivity.this, TransactionsActivity.class);
                //intent.putExtra("NAME_KEY","Value");
                intent.putExtra("AppModel", appModel);
                startActivity(intent);
            }
        });

        while (!appModel.isRunning) {
            try {
                sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        //trys to get the prices from api and the prints the values depending on the answer of coingeko api
        displayInformation();
    }

    private void getAppModel() {
        if ((PreferenceHelper.getSelectedType(getApplicationContext()) == AppType.CdCsvParser) && getUseAndroidDB(getApplicationContext()))
        {
            appModel = new AppModel(PreferenceHelper.getSelectedType(this), PreferenceHelper.getUseStrictType(this), getApplicationContext());
        }
        else
        {
            appModel = (AppModel) getIntent().getExtras().get("AppModel");
        }
    }


    /**
     * Displays the prices of all assets
     *
     */
    private void displayInformation() {
        //get and set prices
        Thread t = new Thread(() -> {
            try {
                displayTexts(appModel.getParseMap());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
    }


    /**
     * Displays the prices of specificWallet
     *
     * @param texts the Map<String, String> which should be displayed with id of View and text to set pairs
     */
    private void displayTexts(Map<String, String> texts) {
        texts.forEach((key, value) -> {
            TextView textView = findViewById(getResources().getIdentifier(key, "id", getPackageName()));
            if (value == null){
                ParseActivity.this.runOnUiThread(() -> textView.setVisibility(View.INVISIBLE));
            } else {
                ParseActivity.this.runOnUiThread(() -> textView.setText(value));
            }
        });
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
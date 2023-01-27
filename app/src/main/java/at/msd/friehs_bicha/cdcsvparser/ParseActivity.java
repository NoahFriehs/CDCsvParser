package at.msd.friehs_bicha.cdcsvparser;

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

import at.msd.friehs_bicha.cdcsvparser.general.AppModel;

public class ParseActivity extends AppCompatActivity {

    AppModel appModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parse);

        // calling the action bar
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        appModel = (AppModel) getIntent().getExtras().get("AppModel");

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

        //trys to get the prices from api and the prints the values depending on the answer of coingeko api
        displayInformation();
    }


    /**
     * Displays the prices of all assets
     *
     */
    private void displayInformation() {
        //get and set prices
        Thread t = new Thread(() -> displayTexts(appModel.getParseMap()));
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
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

        Thread t1 = new Thread(() ->{
            try {
                getValueOfAssets();
                AppModel.asset.isRunning = true;
            } catch (Exception e) {
                System.out.println("no internet connection");
            }
        });
        t1.start();



        Button btnFilter = findViewById(R.id.btn_filter);
        Button btnTx = findViewById(R.id.btn_all_tx);

        btnFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (appModel.isRunning) {
                    Intent intent = new Intent(ParseActivity.this, AssetsFilterActivity.class);
                    //intent.putExtra("NAME_KEY","Value");
                    intent.putExtra("AppModel", appModel);
                    startActivity(intent);
                }
            }

        });
        btnTx.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (appModel.isRunning) {
                    Intent intent = new Intent(ParseActivity.this, TransactionsActivity.class);
                    //intent.putExtra("NAME_KEY","Value");
                    intent.putExtra("AppModel", appModel);
                    startActivity(intent);
                }
            }

        });

        TextView money_spent_value = findViewById(R.id.money_spent_value);
        BigDecimal total = getTotalPrice();
        money_spent_value.setText(Math.round(total.doubleValue() * 100.0) / 100.0 + " €");


        //trys to get the prices from api and the prints the values depending on the answer of coingeko api
        Thread t = new Thread(() ->{
            try {
                getValueOfAssets();
                AppModel.asset.isRunning = true;
            } catch (Exception e) {
                System.out.println("no internet connection");
            }
            if (AppModel.asset.isRunning) {
                double valueOfAssets = getValueOfAssets();
                double rewardsEarned = getRewardsEarned();

                ParseActivity.this.runOnUiThread(() -> setPrice(valueOfAssets, total, Math.round(rewardsEarned * 100.0) / 100.0 + " €"));
            }else {
                ParseActivity.this.runOnUiThread(() -> setPrice(0.0, BigDecimal.ZERO, "no internet connection"));
            }
        });
        t.start();

    }

    /**
     * sets the views ammount text
     *
     * @param valueOfA profit or loss
     * @param total total amount
     * @param rewardsValue rewards
     */
    private void setPrice(Double valueOfA, BigDecimal total, String rewardsValue) {
        if (AppModel.asset.isRunning) {
            TextView assets_value = findViewById(R.id.assets_value);
            assets_value.setText(Math.round(valueOfA * 100.0) / 100.0 + " €");

            TextView profit_loss_value = findViewById(R.id.profit_loss_value);
            profit_loss_value.setText(Math.round((valueOfA - total.doubleValue()) * 100.0) / 100.0 + " €");
        } else {
            TextView assets_value = findViewById(R.id.assets_value);
            assets_value.setText("no internet connection");

            TextView profit_loss_value = findViewById(R.id.profit_loss_value);
            profit_loss_value.setText("no internet connection");
        }
        TextView rewards_value = findViewById(R.id.rewards_value);
        rewards_value.setText(rewardsValue);
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

    public double getRewardsEarned(){
        return appModel.getTotalBonus();
    }

    public BigDecimal getTotalPrice(){
        return appModel.getTotalPrice();
    }

    public Double getValueOfAssets(){
        return appModel.getValueOfAssets();
    }
}
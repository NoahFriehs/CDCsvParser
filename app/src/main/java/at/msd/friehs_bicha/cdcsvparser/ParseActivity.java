package at.msd.friehs_bicha.cdcsvparser;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.concurrent.atomic.AtomicReference;

import at.msd.friehs_bicha.cdcsvparser.General.AppModel;

public class ParseActivity extends AppCompatActivity {

    AppModel appModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parse);
        // calling the action bar
        ActionBar actionBar = getSupportActionBar();

        appModel = (AppModel) getIntent().getExtras().get("AppModel");

        // showing the back button in action bar
        actionBar.setDisplayHomeAsUpEnabled(true);

        Button btnFilter = findViewById(R.id.btn_filter);
        Button btnTx = findViewById(R.id.btn_all_tx);

        btnFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ParseActivity.this, AssetsFilterActivity.class);
                //intent.putExtra("NAME_KEY","Value");
                intent.putExtra("AppModel", appModel);
                startActivity(intent);
            }

        });
        btnTx.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ParseActivity.this, TransactionsActivity.class);
                //intent.putExtra("NAME_KEY","Value");
                intent.putExtra("AppModel", appModel);
                startActivity(intent);
            }

        });

        TextView money_spent_value = findViewById(R.id.money_spent_value);
        BigDecimal total = getTotalPrice().round(new MathContext(0));
        money_spent_value.setText(total.toString() + " €");

        Thread t = new Thread(() ->{

            double valueOfAssets = getValueOfAssets();
            double rewardsEarned = getRewardsEarned();

            ParseActivity.this.runOnUiThread(() -> setPrice(valueOfAssets , total, Math.round(rewardsEarned*100.0)/100.0 + " €"));
        });
        t.start();

    }

    private void setPrice(Double valueOfA, BigDecimal total, String rewardsValue) {
        TextView assets_value = findViewById(R.id.assets_value);
        assets_value.setText( Math.round(valueOfA*100.0)/100.0 + " €");

        TextView profit_loss_value = findViewById(R.id.profit_loss_value);
        profit_loss_value.setText(Math.round((valueOfA - total.doubleValue())*100.0)/100.0 + " €");

        TextView rewards_value = findViewById(R.id.rewards_value);
        rewards_value.setText(rewardsValue);
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
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parse);
        // calling the action bar
        ActionBar actionBar = getSupportActionBar();

        // showing the back button in action bar
        actionBar.setDisplayHomeAsUpEnabled(true);

        Button btnFilter = findViewById(R.id.btn_filter);
        Button btnTx = findViewById(R.id.btn_all_tx);

        btnFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ParseActivity.this, AssetsFilterActivity.class);
                //intent.putExtra("NAME_KEY","Value");
                startActivity(intent);
            }

        });
        btnTx.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ParseActivity.this, TransactionsActivity.class);
                //intent.putExtra("NAME_KEY","Value");
                startActivity(intent);
            }

        });

        TextView rewards_value = findViewById(R.id.rewards_value);
        rewards_value.setText(Math.round(getRewardsEarned()*100.0)/100.0 + " €");

        TextView money_spent_value = findViewById(R.id.money_spent_value);
        BigDecimal total = getTotalPrice().round(new MathContext(0));
        money_spent_value.setText(total.toString() + " €");

        TextView assets_value = findViewById(R.id.assets_value);
        AtomicReference<Double> valueOfA = new AtomicReference<>((double) 0);
        Thread t = new Thread(() ->{
            valueOfA.set(getValueOfAssets());
        });
        t.start();
        while (t.isAlive()){
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Waiting");
        }
        double value = valueOfA.get();
        assets_value.setText( Math.round(value*100.0)/100.0 + " €");

        TextView profit_loss_value = findViewById(R.id.profit_loss_value);
        profit_loss_value.setText(Math.round((value - total.doubleValue())*100.0)/100.0 + " €");
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
        AppModel appModel = (AppModel) getIntent().getExtras().get("AppModel");
        return appModel.getTotalBonus();
    }

    public BigDecimal getTotalPrice(){
        AppModel appModel = (AppModel) getIntent().getExtras().get("AppModel");
        return appModel.getTotalPrice();
    }

    public Double getValueOfAssets(){
        AppModel appModel = (AppModel) getIntent().getExtras().get("AppModel");
        return appModel.txApp.getValueOfAssets();
    }
}
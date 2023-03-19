package at.msd.friehs_bicha.cdcsvparser;

import static java.lang.Thread.sleep;

import static at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper.getUseAndroidDB;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import at.msd.friehs_bicha.cdcsvparser.App.AppType;
import at.msd.friehs_bicha.cdcsvparser.general.AppModel;
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction;
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper;

public class TransactionsActivity extends AppCompatActivity {

    AppModel appModel;

    /**
     * Create list of all Transactions on creat this view
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transactions);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        getAppModel();
        while (!appModel.isRunning) {
            try {
                sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        List<Transaction> mTransactionList = appModel.txApp.transactions;


        // Get a reference to the ListView
        ListView listView = findViewById(R.id.lv_tx);
        // Create an adapter for the ListView
        ArrayAdapter<Transaction> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mTransactionList);
        // Set the adapter on the ListView
        listView.setAdapter(adapter);
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
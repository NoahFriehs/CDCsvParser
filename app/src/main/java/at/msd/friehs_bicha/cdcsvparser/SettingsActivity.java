package at.msd.friehs_bicha.cdcsvparser;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import at.msd.friehs_bicha.cdcsvparser.App.AppType;

public class SettingsActivity extends AppCompatActivity {

    static AppType selectedType;
    public static boolean useStrictType;
    Spinner appTypeSpinner;
    CheckBox useStrictTypeCheckbox;

    private static final String PREFS_NAME = "settings_prefs";
    private static final String TYPE_KEY = "app_type";
    private static final String USE_STRICT_TYPE_KEY = "use_strict_app_type";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        appTypeSpinner = findViewById(R.id.wallet_type_spinner);
        useStrictTypeCheckbox = findViewById(R.id.use_strict_wallet_type_checkbox);

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        // load the stored values for the spinner and checkbox
        int storedType = settings.getInt(TYPE_KEY, 0);
        useStrictType = settings.getBoolean(USE_STRICT_TYPE_KEY, false);

        // set the selected app type
        selectedType = AppType.values()[storedType];
        appTypeSpinner.setSelection(storedType);
        useStrictTypeCheckbox.setChecked(useStrictType);

        useStrictTypeCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                useStrictType = isChecked;
                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean(USE_STRICT_TYPE_KEY, useStrictType);
                editor.apply();
            }
        });
        appTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedType = AppType.values()[position];
                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putInt(TYPE_KEY, position);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });
    }
}

package at.msd.friehs_bicha.cdcsvparser.util;

import android.content.Context;
import android.content.SharedPreferences;

import at.msd.friehs_bicha.cdcsvparser.App.AppType;

public class PreferenceHelper {
    public static final String PREFS_NAME = "settings_prefs";
    public static final String TYPE_KEY = "app_type";
    public static final String USE_STRICT_TYPE_KEY = "use_strict_app_type";


    /**
     * returns the selected app type
     *
     * @param context the context
     * @return the selected app type
     */
    public static AppType getSelectedType(Context context){
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
        int storedType = settings.getInt(TYPE_KEY, 0);
        return AppType.values()[storedType];
    }


    /**
     * returns if the strict type is used
     *
     * @param context the context
     * @return if the strict type is used
     */
    public static boolean getUseStrictType(Context context){
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
        return settings.getBoolean(USE_STRICT_TYPE_KEY, false);
    }
}


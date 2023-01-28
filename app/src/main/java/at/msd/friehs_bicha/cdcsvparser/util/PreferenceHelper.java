package at.msd.friehs_bicha.cdcsvparser.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import at.msd.friehs_bicha.cdcsvparser.App.AppType;

public class PreferenceHelper {
    public static final String PREFS_NAME = "settings_prefs";
    public static final String TYPE_KEY = "app_type";
    public static final String USE_STRICT_TYPE_KEY = "use_strict_app_type";
    public static final String ENCRYPTION_KEY = "encryption_key";


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

    public static String getEncryptionKey(Context context) {
        SharedPreferences settings = context.getSharedPreferences(ENCRYPTION_KEY, 0);
        String storedkey = settings.getString(TYPE_KEY, null);

        if (storedkey == null) {
            SecretKey key = null;
            try {
                key = generateKey();
            } catch (NoSuchAlgorithmException ex) {
                ex.printStackTrace();
            }
            if (key == null)
                return null;
            Base64.Encoder encoder = Base64.getEncoder();
            storedkey = encoder.encodeToString(key.getEncoded());

            SharedPreferences.Editor editor = settings.edit();
            editor.putString(TYPE_KEY, storedkey);
            editor.apply();
        }

        return storedkey;
    }

    private static SecretKey generateKey() throws NoSuchAlgorithmException {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128); // key size in bits
            return keyGen.generateKey();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}


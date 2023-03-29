package at.msd.friehs_bicha.cdcsvparser.util

import android.content.Context
import at.msd.friehs_bicha.cdcsvparser.App.AppType

object PreferenceHelper {
    const val PREFS_NAME = "settings_prefs"
    const val TYPE_KEY = "app_type"
    const val USE_STRICT_TYPE_KEY = "use_strict_app_type"

    /**
     * returns the selected app type
     *
     * @param context the context
     * @return the selected app type
     */
    fun getSelectedType(context: Context): AppType {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        val storedType = settings.getInt(TYPE_KEY, 0)
        return AppType.values()[storedType]
    }

    /**
     * returns if the strict type is used
     *
     * @param context the context
     * @return if the strict type is used
     */
    fun getUseStrictType(context: Context): Boolean {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        return settings.getBoolean(USE_STRICT_TYPE_KEY, false)
    }

}
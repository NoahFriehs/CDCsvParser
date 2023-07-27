package at.msd.friehs_bicha.cdcsvparser.util

import android.content.Context
import at.msd.friehs_bicha.cdcsvparser.app.AppType

/**
 * Helper class for the shared preferences
 */
object PreferenceHelper {
    const val PREFS_NAME = "settings_prefs"
    const val TYPE_KEY = "app_type"
    const val USE_STRICT_TYPE_KEY = "use_strict_app_type"
    const val IS_APPMODEL_SAVED_LOCAL = "is_appmodel_saved_local"
    const val FAST_START_ENABLED = "fast_start_enabled"

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

    /**
     * sets the selected app type
     *
     * @param context the context
     * @param type the selected app type
     */
    fun setSelectedType(context: Context, type: AppType) {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        val editor = settings.edit()
        editor.putInt(TYPE_KEY, type.ordinal)
        editor.apply()
    }

    /**
     * sets if the strict type is used
     *
     * @param context the context
     * @param useStrictType if the strict type is used
     */
    fun setUseStrictType(context: Context, useStrictType: Boolean) {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        val editor = settings.edit()
        editor.putBoolean(USE_STRICT_TYPE_KEY, useStrictType)
        editor.apply()
    }

    /**
     * sets if the app model is saved locally
     *
     * @param context the context
     * @param isAppModelSavedLocal if the app model is saved locally
     */
    fun setIsAppModelSavedLocal(context: Context, isAppModelSavedLocal: Boolean) {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        val editor = settings.edit()
        editor.putBoolean(IS_APPMODEL_SAVED_LOCAL, isAppModelSavedLocal)
        editor.apply()
    }

    /**
     * returns if the app model is saved locally
     *
     * @param context the context
     * @return if the app model is saved locally
     */
    fun getIsAppModelSavedLocal(context: Context): Boolean {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        return settings.getBoolean(IS_APPMODEL_SAVED_LOCAL, false)
    }

    /**
     * sets if the fast start is enabled
     *
     * @param context the context
     * @param fastStartEnabled if the fast start is enabled
     */
    fun setFastStartEnabled(context: Context, fastStartEnabled: Boolean) {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        val editor = settings.edit()
        editor.putBoolean(FAST_START_ENABLED, fastStartEnabled)
        editor.apply()
    }

    /**
     * returns if the fast start is enabled
     *
     * @param context the context
     * @return if the fast start is enabled
     */
    fun getFastStartEnabled(context: Context): Boolean {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        return settings.getBoolean(FAST_START_ENABLED, false)
    }

}
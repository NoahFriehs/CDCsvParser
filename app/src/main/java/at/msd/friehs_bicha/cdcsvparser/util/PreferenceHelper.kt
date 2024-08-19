package at.msd.friehs_bicha.cdcsvparser.util

import android.content.Context
import android.util.Log
import at.msd.friehs_bicha.cdcsvparser.app.AppType

/**
 * Helper class for the shared preferences
 */
object PreferenceHelper {
    const val PREFS_NAME = "settings_prefs"
    const val TYPE_KEY = "app_type"
    const val USE_STRICT_TYPE_KEY = "use_strict_app_type"
    const val IS_DATA_LOCAL = "is_data_local"
    const val IS_APPMODEL_SAVED_LOCAL = "is_appmodel_saved_local"
    const val FAST_START_ENABLED = "fast_start_enabled"
    const val LOG_FILENAME = "LOG_FILENAME"
    const val MAX_LOG_LEVEL = "MAX_LOG_LEVEL"
    const val IS_FIRST_START = "IS_FIRST_START"
    const val USE_CPP = "USE_CPP"

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
     * sets if the data is to be stored local
     *
     * @param context the context
     * @param isDataLocal if the data is local
     */
    fun setIsDataLocal(context: Context, isDataLocal: Boolean) {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        val editor = settings.edit()
        editor.putBoolean(IS_DATA_LOCAL, isDataLocal)
        editor.apply()
    }

    /**
     * returns if the data is to be stored local
     *
     * @param context the context
     * @return if the data is local
     */
    fun getIsDataLocal(context: Context): Boolean {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        return settings.getBoolean(IS_DATA_LOCAL, false)
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

    /**
     * sets the log filename
     *
     * @param context the context
     * @param logFilename the log filename
     */
    fun setLogFilename(context: Context, logFilename: String) {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        val editor = settings.edit()
        editor.putString(LOG_FILENAME, logFilename)
        editor.apply()
    }

    /**
     * returns the log filename
     *
     * @param context the context
     * @return the log filename
     */
    fun getLogFilename(context: Context): String {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        return settings.getString(LOG_FILENAME, "log/CDCsvParser.log")!!
    }


    /**
     * sets the max log level
     *
     * @param context the context
     * @param maxLogLevel the max log level
     */
    fun setMaxLogLevel(context: Context, maxLogLevel: Int) {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        val editor = settings.edit()
        editor.putInt(MAX_LOG_LEVEL, maxLogLevel)
        editor.apply()
    }


    /**
     * returns the max log level
     *
     * @param context the context
     * @return the max log level
     */
    fun getMaxLogLevel(context: Context): Int {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        return settings.getInt(MAX_LOG_LEVEL, Log.DEBUG)
    }


    /**
     * sets if the app is started for the first time
     *
     * @param context the context
     * @param isFirstStart if the app is started for the first time
     */
    fun setIsFirstStart(context: Context, isFirstStart: Boolean) {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        val editor = settings.edit()
        editor.putBoolean(IS_FIRST_START, isFirstStart)
        editor.apply()
    }


    /**
     * returns if the app is started for the first time
     *
     * @param context the context
     * @return if the app is started for the first time
     */
    fun getIsFirstStart(context: Context): Boolean {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        return settings.getBoolean(IS_FIRST_START, true)
    }

    fun setUseCpp(context: Context, useCpp: Boolean) {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        val editor = settings.edit()
        editor.putBoolean(USE_CPP, useCpp)
        editor.apply()
    }

    fun getUseCpp(context: Context): Boolean {
        val settings = context.getSharedPreferences(PREFS_NAME, 0)
        return settings.getBoolean(USE_CPP, true)
    }

}
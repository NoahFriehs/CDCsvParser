package at.msd.friehs_bicha.cdcsvparser.util

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.preferencesDataStore
import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File

/**
 * App settings facade.
 *
 * Backed by Jetpack DataStore (Preferences) instead of SharedPreferences
 * (Phase 3.3). DataStore is asynchronous, but this codebase reads settings
 * synchronously in several constructors and in `FileLog.init`, so the
 * facade keeps the exact same synchronous API and fronts the store with an
 * in-memory cache:
 *
 * - [init] (called from `App.onCreate`) loads the store once and migrates
 *   the legacy `settings_prefs` SharedPreferences into it.
 * - getters read the cache (bounded [LOAD_TIMEOUT_MS] wait until the first
 *   load finishes; the store is a few-kilobyte protobuf, so this is
 *   sub-millisecond in practice).
 * - setters update the cache immediately and persist asynchronously.
 *
 * If the store file is corrupt, it is replaced (fresh defaults) rather than
 * blocking the app; the legacy SP migration only fills keys the store does
 * not hold yet, so a second upgrade never overwrites a newer value.
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
    // Plan 004: user-supplied price API keys (Settings -> API keys).
    const val CG_API_KEY = "cg_api_key"
    const val CC_API_KEY = "cc_api_key"

    private const val LOAD_TIMEOUT_MS = 500L
    private const val DATASTORE_FILE = "settings.preferences_pb"

    private val Context.dataStore by preferencesDataStore(name = DATASTORE_FILE)

    private var dataStore: DataStore<androidx.datastore.preferences.core.Preferences>? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = HashMap<String, Any?>()
    private val cacheLock = Any()
    private val loadDone = AtomicBoolean(false)
    private val loaded = CountDownLatch(1)
    private var initialized = false

    /**
     * Loads the DataStore into the in-memory cache and migrates the legacy
     * SharedPreferences once. Safe to call more than once.
     */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val appContext = context.applicationContext
        val ds = appContext.dataStore
        dataStore = ds
        scope.launch {
            val prefs = try {
                ds.data.first()
            } catch (_: Exception) {
                androidx.datastore.preferences.core.emptyPreferences()
            }
            val migrated = HashMap<String, Any?>()
            prefs.asMap().forEach { (key, value) ->
                migrated[key.name] = value
            }
            // app_type is the only key that may exist under BOTH types in the
            // store (legacy int ordinal vs. name string); the string is the
            // newer format and wins.
            prefs[stringPreferencesKey(TYPE_KEY)]?.let { migrated[TYPE_KEY] = it }
            migrateLegacyPrefs(appContext, migrated)
            synchronized(cacheLock) {
                cache.clear()
                cache.putAll(migrated)
            }
            loadDone.set(true)
            loaded.countDown()
        }
    }

    /**
     * Copies legacy SharedPreferences values for keys the DataStore does
     * not hold yet. Int values that were stored as strings (and vice
     * versa) are normalized to Int where the key is known to be numeric.
     */
    private fun migrateLegacyPrefs(context: Context, target: HashMap<String, Any?>) {
        val legacy = try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            return
        }
        for ((key, value) in legacy.all) {
            if (target.containsKey(key)) continue
            when (value) {
                is Boolean -> target[key] = value
                is Int -> target[key] = value
                is Long -> target[key] = value.toInt()
                is Float -> target[key] = value.toLong()
                is String -> {
                    // `app_type` stored the pre-name ordinal as an int under
                    // the same key; keep it as an int so getSelectedType's
                    // fallback can still read it.
                    target[key] = if (key == TYPE_KEY && value.toIntOrNull() != null) {
                        value.toInt()
                    } else {
                        value
                    }
                }
                else -> { /* unsupported type: drop */ }
            }
        }
    }

    private fun awaitLoaded() {
        if (!loadDone.get()) {
            loaded.await(LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
    }

    private fun cachedString(key: String): String? =
        synchronized(cacheLock) { cache[key] as? String }

    private fun cachedInt(key: String): Int? =
        synchronized(cacheLock) { (cache[key] as? Int) ?: (cache[key] as? Long)?.toInt() }

    private fun cachedBoolean(key: String): Boolean? =
        synchronized(cacheLock) { cache[key] as? Boolean }

    private fun put(key: String, value: Any?) {
        synchronized(cacheLock) { cache[key] = value }
    }

    private fun persist(key: String, value: Any?) {
        val ds = dataStore ?: return
        scope.launch {
            try {
                ds.edit { prefs ->
                    when (value) {
                        is Boolean -> prefs[booleanPreferencesKey(key)] = value
                        is Int -> prefs[intPreferencesKey(key)] = value
                        is String -> {
                            if (key == TYPE_KEY) {
                                // drop a legacy ordinal that survived migration
                                prefs.remove(intPreferencesKey(TYPE_KEY))
                            }
                            prefs[stringPreferencesKey(key)] = value
                        }
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                FileLog.e(TAG, "persist $key failed: $e")
            }
        }
    }

    // ------------------------------------------------------------------
    // Public API (identical signatures to the SharedPreferences version)
    // ------------------------------------------------------------------

    /**
     * Returns the selected app type.
     *
     * The type is persisted by name (stable across enum re-orderings).
     * Installs from before that change stored the ordinal under the same
     * key, so the integer value is still honored as a fallback.
     */
    fun getSelectedType(context: Context): AppType {
        awaitLoaded()
        val name = cachedString(TYPE_KEY)
        if (name != null) {
            return AppType.values().firstOrNull { it.name == name } ?: AppType.CdCsvParser
        }
        val legacyOrdinal = cachedInt(TYPE_KEY) ?: -1
        return AppType.values().getOrElse(legacyOrdinal) { AppType.CdCsvParser }
    }

    /**
     * returns if the strict type is used
     *
     * @param context the context
     * @return if the strict type is used
     */
    fun getUseStrictType(context: Context): Boolean {
        awaitLoaded()
        return cachedBoolean(USE_STRICT_TYPE_KEY) ?: false
    }

    /**
     * sets the selected app type
     *
     * @param context the context
     * @param type the selected app type
     */
    fun setSelectedType(context: Context, type: AppType) {
        awaitLoaded()
        put(TYPE_KEY, type.name)
        persist(TYPE_KEY, type.name)
    }

    /**
     * sets if the strict type is used
     *
     * @param context the context
     * @param useStrictType if the strict type is used
     */
    fun setUseStrictType(context: Context, useStrictType: Boolean) {
        awaitLoaded()
        put(USE_STRICT_TYPE_KEY, useStrictType)
        persist(USE_STRICT_TYPE_KEY, useStrictType)
    }

    /**
     * sets if the app model is saved locally
     *
     * @param context the context
     * @param isAppModelSavedLocal if the app model is saved locally
     */
    fun setIsAppModelSavedLocal(context: Context, isAppModelSavedLocal: Boolean) {
        awaitLoaded()
        put(IS_APPMODEL_SAVED_LOCAL, isAppModelSavedLocal)
        persist(IS_APPMODEL_SAVED_LOCAL, isAppModelSavedLocal)
    }

    /**
     * returns if the app model is saved locally
     *
     * @param context the context
     * @return if the app model is saved locally
     */
    fun getIsAppModelSavedLocal(context: Context): Boolean {
        awaitLoaded()
        return cachedBoolean(IS_APPMODEL_SAVED_LOCAL) ?: false
    }

    /**
     * sets if the data is to be stored local
     *
     * @param context the context
     * @param isDataLocal if the data is local
     */
    fun setIsDataLocal(context: Context, isDataLocal: Boolean) {
        awaitLoaded()
        put(IS_DATA_LOCAL, isDataLocal)
        persist(IS_DATA_LOCAL, isDataLocal)
    }

    /**
     * returns if the data is to be stored local
     *
     * @param context the context
     * @return the data is local
     */
    fun getIsDataLocal(context: Context): Boolean {
        awaitLoaded()
        return cachedBoolean(IS_DATA_LOCAL) ?: false
    }

    /**
     * sets if the fast start is enabled
     *
     * @param context the context
     * @param fastStartEnabled if the fast start is enabled
     */
    fun setFastStartEnabled(context: Context, fastStartEnabled: Boolean) {
        awaitLoaded()
        put(FAST_START_ENABLED, fastStartEnabled)
        persist(FAST_START_ENABLED, fastStartEnabled)
    }

    /**
     * returns if the fast start is enabled
     *
     * @param context the context
     * @return if the fast start is enabled
     */
    fun getFastStartEnabled(context: Context): Boolean {
        awaitLoaded()
        return cachedBoolean(FAST_START_ENABLED) ?: false
    }

    /**
     * sets the log filename
     *
     * @param context the context
     * @param logFilename the log filename
     */
    fun setLogFilename(context: Context, logFilename: String) {
        awaitLoaded()
        put(LOG_FILENAME, logFilename)
        persist(LOG_FILENAME, logFilename)
    }

    /**
     * returns the log filename
     *
     * @param context the context
     * @return the log filename
     */
    fun getLogFilename(context: Context): String {
        awaitLoaded()
        return cachedString(LOG_FILENAME) ?: "log/CDCsvParser.log"
    }

    /**
     * sets the max log level
     *
     * @param context the context
     * @param maxLogLevel the max log level
     */
    fun setMaxLogLevel(context: Context, maxLogLevel: Int) {
        awaitLoaded()
        put(MAX_LOG_LEVEL, maxLogLevel)
        persist(MAX_LOG_LEVEL, maxLogLevel)
    }

    /**
     * returns the max log level
     *
     * @param context the context
     * @return the max log level
     */
    fun getMaxLogLevel(context: Context): Int {
        awaitLoaded()
        return cachedInt(MAX_LOG_LEVEL) ?: Log.DEBUG
    }

    /**
     * sets if the app is started for the first time
     *
     * @param context the context
     * @param isFirstStart if the app is started for the first time
     */
    fun setIsFirstStart(context: Context, isFirstStart: Boolean) {
        awaitLoaded()
        put(IS_FIRST_START, isFirstStart)
        persist(IS_FIRST_START, isFirstStart)
    }

    /**
     * returns if the app is started for the first time
     *
     * @param context the context
     * @return if the app is started for the first time
     */
    fun getIsFirstStart(context: Context): Boolean {
        awaitLoaded()
        return cachedBoolean(IS_FIRST_START) ?: true
    }

    /**
     * sets if the C++ core is used
     *
     * @param context the context
     * @param useCpp if the C++ core is used
     */
    fun setUseCpp(context: Context, useCpp: Boolean) {
        awaitLoaded()
        put(USE_CPP, useCpp)
        persist(USE_CPP, useCpp)
    }

    /**
     * returns if the C++ core is used
     *
     * @param context the context
     * @return if the C++ core is used
     */
    fun getUseCpp(context: Context): Boolean {
        awaitLoaded()
        return cachedBoolean(USE_CPP) ?: true
    }

    /**
     * sets the user-supplied CoinGecko API key (plan 004; blank = unset,
     * the provider falls back to the build-time key / keyless).
     */
    fun setCoinGeckoApiKey(context: Context, apiKey: String) {
        awaitLoaded()
        put(CG_API_KEY, apiKey)
        persist(CG_API_KEY, apiKey)
    }

    /** Returns the user-supplied CoinGecko API key ("" = unset). */
    fun getCoinGeckoApiKey(context: Context): String {
        awaitLoaded()
        return cachedString(CG_API_KEY).orEmpty()
    }

    /**
     * sets the user-supplied CryptoCompare API key (plan 004; blank =
     * unset, the deep source is then disabled).
     */
    fun setCryptoCompareApiKey(context: Context, apiKey: String) {
        awaitLoaded()
        put(CC_API_KEY, apiKey)
        persist(CC_API_KEY, apiKey)
    }

    /** Returns the user-supplied CryptoCompare API key ("" = unset). */
    fun getCryptoCompareApiKey(context: Context): String {
        awaitLoaded()
        return cachedString(CC_API_KEY).orEmpty()
    }

    private const val TAG = "PreferenceHelper"
}

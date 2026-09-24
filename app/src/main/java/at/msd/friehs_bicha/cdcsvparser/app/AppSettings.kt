package at.msd.friehs_bicha.cdcsvparser.app

import android.widget.Toast
import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars.applicationContext
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper

/**
 * App settings class.
 *
 * Stored in Firestore as plain primitives: enums are persisted by name and
 * every read uses safe casts, because Firestore returns 32-bit integers as
 * Int and cannot round-trip enum objects.
 */
class AppSettings(var userID: String, var appType: AppType, var useStrictType: Boolean) {

    constructor() : this("", AppType.Default, false)

    var hasCryptoTx: String = ""
    var hasCardTx: String = ""
    var dbVersion: String = "1.0.0"

    /**
     * returns the settings as a HashMap
     */
    fun toHashMap(): HashMap<String, Any> {
        val result = HashMap<String, Any>()
        result.put("userID", userID)
        result.put("appType", appType.name)
        result.put("useStrictType", useStrictType)
        result.put("hasCryptoTx", hasCryptoTx)
        result.put("hasCardTx", hasCardTx)
        result.put("dbVersion", dbVersion)
        return result
    }

    fun fromHashMap(map: HashMap<String, Any>): AppSettings {
        this.userID = map["userID"] as? String ?: ""
        // Older documents stored the enum object (round-trips as a Map) - the
        // safe cast falls back to [AppType.Default] for those.
        this.appType = (map["appType"] as? String)
            ?.let { name -> AppType.values().firstOrNull { it.name == name } }
            ?: AppType.Default
        this.useStrictType = map["useStrictType"] as? Boolean ?: false
        this.hasCryptoTx = map["hasCryptoTx"] as? String ?: ""
        this.hasCardTx = map["hasCardTx"] as? String ?: ""
        this.dbVersion = map["dbVersion"] as? String ?: "1.0.0"
        return this
    }

    fun compareVersionsWithDefault(): Boolean {
        if (StringHelper.compareVersions(dbVersion, "1.0.0")) {
            //when lower than this than it does not work with the db, has to switch to older version
            val text =
                "Your database is not compatible with this version of the app. Please downgrade the app or override the database with a new upload."
            Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }
}

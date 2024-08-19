package at.msd.friehs_bicha.cdcsvparser.app

import android.widget.Toast
import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars.applicationContext
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper

/**
 * App settings class
 */
class AppSettings(var userID: String, var appType: AppType, var useStrictType: Boolean) {

    constructor() : this("", AppType.Default, false)

    var hasCryptoTx: String = ""
    var hasCardTx: String = ""
    var placeholder3: String = ""
    var placeholder4: String = ""
    var placeholder5: String = ""
    var placeholder6: String = ""
    var placeholder7: String = ""
    var placeholder8: String = ""
    var placeholder9: String = ""
    var placeholder10: String = ""
    var dbVersion: String = "1.0.0"

    init {
        this.hasCryptoTx = ""
        this.hasCardTx = ""
        this.placeholder3 = ""
        this.placeholder4 = ""
        this.placeholder5 = ""
        this.placeholder6 = ""
        this.placeholder7 = ""
        this.placeholder8 = ""
        this.placeholder9 = ""
        this.placeholder10 = ""
    }

    /**
     * returns the settings as a HashMap
     */
    fun toHashMap(): HashMap<String, Any> {
        val result = HashMap<String, Any>()
        result.put("userID", userID)
        result.put("appType", appType)
        result.put("useStrictType", useStrictType)
        result.put("hasCryptoTx", hasCryptoTx)
        result.put("hasCardTx", hasCardTx)
        result.put("placeholder3", placeholder3)
        result.put("placeholder4", placeholder4)
        result.put("placeholder5", placeholder5)
        result.put("placeholder6", placeholder6)
        result.put("placeholder7", placeholder7)
        result.put("placeholder8", placeholder8)
        result.put("placeholder9", placeholder9)
        result.put("placeholder10", placeholder10)
        result.put("dbVersion", dbVersion)
        return result
    }

    fun fromHashMap(map: HashMap<String, Any>): AppSettings {
        this.userID = map["userID"] as String
        this.appType = AppType.valueOf(map["appType"] as String)
        this.useStrictType = map["useStrictType"] as Boolean
        this.hasCryptoTx = map["hasCryptoTx"] as String
        this.hasCardTx = map["hasCardTx"] as String
        this.placeholder3 = map["placeholder3"] as String
        this.placeholder4 = map["placeholder4"] as String
        this.placeholder5 = map["placeholder5"] as String
        this.placeholder6 = map["placeholder6"] as String
        this.placeholder7 = map["placeholder7"] as String
        this.placeholder8 = map["placeholder8"] as String
        this.placeholder9 = map["placeholder9"] as String
        this.placeholder10 = map["placeholder10"] as String
        this.dbVersion = map["dbVersion"] as String
        return this
    }

    fun compareVersionsWithDefault(): Boolean {
        if (StringHelper.compareVersions(dbVersion as String, "1.0.0")) {
            //when lower than this than it does not work with the db, has to switch to older version
            val text =
                "Your database is not compatible with this version of the app. Please downgrade the app or override the database with a new upload."
            Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }
}
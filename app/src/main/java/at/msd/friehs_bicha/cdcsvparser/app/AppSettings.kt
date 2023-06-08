package at.msd.friehs_bicha.cdcsvparser.app

class AppSettings(var userID: String, var appType: AppType, var useStrictType: Boolean) {

    var placeholder1: String = ""
    var placeholder2: String = ""
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
        this.placeholder1 = ""
        this.placeholder2 = ""
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
        result.put("placeholder1", placeholder1)
        result.put("placeholder2", placeholder2)
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
}
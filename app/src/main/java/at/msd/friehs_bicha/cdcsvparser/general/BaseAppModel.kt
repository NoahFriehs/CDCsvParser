package at.msd.friehs_bicha.cdcsvparser.general

import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.app.BaseApp
import java.io.Serializable

open class BaseAppModel
/**
 * Creates a new AppModel
 *
 * @param appType which app to use
 */(var appType: AppType) : Serializable {
    var txApp: BaseApp? = null
    var isRunning = false
}
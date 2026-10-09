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

    /**
     * True once the model is fully populated. The synchronous constructors
     * set their data before returning, so the default is true; ONLY the
     * no-arg (Room) constructor loads on a background scope and starts at
     * false, flipping this when the load finishes (card-only data aliases
     * `txApp`, so `true` means every posted source is final). CoreService waits on this instead of polling individual
     * fields, which could observe a half-loaded model.
     */
    @Volatile
    var isFullyLoaded: Boolean = true
}
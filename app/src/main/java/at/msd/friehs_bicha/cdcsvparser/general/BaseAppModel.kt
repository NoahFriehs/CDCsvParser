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
    /**
     * The model's app - the >=1-app invariant, made explicit: every
     * constructor ends with this set, except the no-arg Room model's
     * logged no-data edge (a saved-local flag with an emptied Room),
     * where the Room load logs and leaves this null; the start flow
     * then carries the null safely through to an empty posted wallet
     * set (treated as "nothing to post"). Callers may therefore rely
     * on it non-null once the model is usable; a null observed at a
     * use site is a broken-restore edge, and the throwing use sites
     * there are that edge's loud failure by design instead of a silent
     * zero.
     */
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
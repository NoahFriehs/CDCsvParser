package at.msd.friehs_bicha.cdcsvparser.app

import at.msd.friehs_bicha.cdcsvparser.general.AppModel
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog


/**
 * Singleton class for managing the app model.
 */
object AppModelManager {
    @Volatile
    private var instance: AppModel? = null

    /**
     * Returns the app model.
     *
     * @return the app model
     */
    fun getInstance(): AppModel {
        if (instance == null) {
            FileLog.e(
                "AppModelManager",
                "AppModel has not been initialized. Call setInstance() first."
            )
            throw IllegalStateException("AppModel has not been initialized. Call setInstance() first.")
        }
        return instance!!
    }

    /**
     * Sets the app model.
     *
     * @param appModel the app model
     */
    fun setInstance(appModel: AppModel) {
        instance = appModel
    }
}

package at.msd.friehs_bicha.cdcsvparser.app

import at.msd.friehs_bicha.cdcsvparser.general.AppModel

object AppModelManager {
    @Volatile
    private var instance: AppModel? = null

    fun getInstance(): AppModel {
        if (instance == null) {
            throw IllegalStateException("AppModel has not been initialized. Call setInstance() first.")
        }
        return instance!!
    }

    fun setInstance(appModel: AppModel) {
        instance = appModel
    }
}

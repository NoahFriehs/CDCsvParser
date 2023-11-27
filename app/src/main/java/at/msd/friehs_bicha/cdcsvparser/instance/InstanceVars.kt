package at.msd.friehs_bicha.cdcsvparser.instance

import android.content.Context
import android.content.Intent
import at.msd.friehs_bicha.cdcsvparser.Core.CoreService
import at.msd.friehs_bicha.cdcsvparser.db.AppDatabase
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.networkstateservice.NetworkStateService

object InstanceVars {

    lateinit var applicationContext: Context
    lateinit var db: AppDatabase

    fun init(context: Context) {
        applicationContext = context
        initLogging()
        initDB()
        initServices()
        initGlobalReceivers()
    }

    /**
     * Shutdown
     *
     * Call this on app shutdown
     */
    fun shutdown() {
        FileLog.i("InstanceVars", "Shutting down")
        stopServices()
    }

    private fun stopServices() {
        applicationContext.stopService(Intent(applicationContext, NetworkStateService::class.java))
    }

    private fun initGlobalReceivers() {
        //register global receivers here
    }

    private fun initServices() {
        registerNetworkStateService()
        CoreService.isInitialized   //init core service companion object to check for .so
    }

    private fun registerNetworkStateService() {
        val intent = Intent(applicationContext, NetworkStateService::class.java)
        applicationContext.startService(intent)
    }

    private fun initDB() {
        val db = AppDatabase.getInstance(applicationContext)
        if (db != null) {
            this.db = db
        } else {
            throw Exception("Database not initialized")
        }
    }

    private fun initLogging() {
        FileLog.init()
    }

}
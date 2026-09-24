package at.msd.friehs_bicha.cdcsvparser.instance

import android.content.Context
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.db.AppDatabase
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog

object InstanceVars {
    @Volatile
    lateinit var applicationContext: Context
    lateinit var db: AppDatabase

    fun init(context: Context) {
        applicationContext = context
        initLogging()
        initDB()
        initServices()
        initGlobalReceivers()
    }

    private fun initGlobalReceivers() {
        //register global receivers here
    }

    private fun initServices() {
        CoreService.isInitialized   //init core service companion object to check for .so
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